package com.spectralclanmgmt;

import javax.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
name = "Spectral Clan Mgmt",
description = "A Runelite plugin to help the OSRS Spectral clan's Admin ranks perform their management duties."
)
public class SpectralClanMgmtPlugin extends Plugin
{
	@Inject
	private SpectralClanMgmtConfig config;
	
	@Inject
	private Client client;
	
	@Inject
	private ClientThread clientThread;
	
	@Inject
	private SpectralClanMgmtChatboxPanelManager chatboxPanelManager;
	
	private SpectralClanMgmtButton spectralClanMemberButton;
	
	private ClanSettings clanSettings;
	
	private List<ClanMember> clanMembers;
	
	private HashMap<Integer, String> members = new HashMap<Integer, String>();
	
	private HashMap<String, String> memberJoinDates = new HashMap<String, String>();
	
	// The clan's admin ranks. The numbers are the key values in the ranks enum for the 
	// Owner, Deputy Owner, Moderator, and Completionist (Recruiter) ranks.
	private ArrayList<Integer> adminRanks = new ArrayList<>(Arrays.asList(-4, -3, 264, 252));
	
	private int localPlayerRank = 0;
	
	private static final int CLAN_SETTINGS_INTERFACE = 690;
	
	private static final int CLAN_SETTINGS_MEMBERS_INTERFACE = 693;
	
	private static final int CLAN_SETTINGS_MEMBERS_INTERFACE_HEADER = 45416450;
	
	// Used to track if the user is currently viewing the Members List UI (which means the button exists).
	private boolean memberWidgetLoaded = false;
	
	private SpectralClanMgmtHttpRequest httpRequest;
	
	@Provides
	SpectralClanMgmtConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SpectralClanMgmtConfig.class);
	}
	
	@Override
	protected void startUp() throws Exception
	{
		log.info("Spectral Clan Mgmt Plugin started!");
		
		// Need to create a new instance of Spectral's HttpRequest class at startup
		// so that whenever the button is created, the same instance of Spectral's HttpRequest class
		// will be passed to it each time and the isReady method in that class can be used to
		// determine if the player can click the button or needs to wait for a previous export to finish.
		// I'm also doing this so that if the members list widget is closed, making the button null,
		// after a request is posted and still closed when the response is returned, 
		// the response and clean up can be handled by this class instead of the button's class.
		httpRequest = new SpectralClanMgmtHttpRequest(this, config, client);
	}
	
	@Override
	protected void shutDown() throws Exception
	{
		log.info("Spectral Clan Mgmt Plugin stopped!");
		
		// Just calling this here just in case something weird happened and the executorService hasn't been shut down and set to null yet.
		httpRequest.shutdown();
	}
	
	// Checks if the script URL in the config is a valid URL. If it's missing, or it's not a valid URL, it'll return false
	// so we can respond and block the execution from continuing before an HttpRequest is created.
	public boolean checkURL()
	{
		if (config.scriptURL() == "")
		{
			return false;
		}
		else
		{
			// For Spectral's purposes, there's no reason for the protocol to be anything other than http or https.
			Pattern urlRegexPattern = Pattern.compile("^((http|https)://)?([a-zA-Z0-9]+[.])?[a-zA-Z0-9-]+(.[a-zA-Z]{2,6})?(:[0-9]{1,5})?(/[a-zA-Z0-9-._?,'+&%$#=~]*)*$");
			boolean isValid = urlRegexPattern.matcher(config.scriptURL()).matches();
			return isValid;
		}
	}
	
	// Clears and populates the members and membersJoinDates hashmaps with sorted values.
	// These will be used after this finishes.
	private void getMembersData()
	{
		clanSettings = client.getClanSettings();
		
		if (clanMembers != null)
		{
			clanMembers.clear();
		}
		
		// We clear and set these every time this is run, because it's only called when the members list UI is loaded.
		members.clear();
		memberJoinDates.clear();
		
		if (clanSettings != null)
		{
			clanMembers = clanSettings.getMembers();
			
			// We get the members names into a temp arraylist, so they can be sorted next.
			ArrayList<String> mems = new ArrayList<String>();
			
			clanMembers.forEach((me) -> mems.add(me.getName()));
			
			// This sorts the arraylist of member names alphabetically while ignoring the letter cases.
			Collections.sort(mems, String.CASE_INSENSITIVE_ORDER);
			
			int i = 0;
			
			for (String m : mems)
			{
				// Now that we've got a sorted list of member names, we'll put them into a hashmap, 
				// using i to act as an index for the member names. This will be used later in the button's class 
				// to match the name the player the user clicked on in the UI to the name in this hashmap 
				// so we won't have to deal with the annoyance of getting the text from a widget and sanitizing it.
				members.put(i, m);
				i++;
			}
			
			// Finally, we get the converted join date set for the EST/EDT timezone for each member
			// and add it to the memberJoinDates hashmap. The member's name is the key and the join date is its value.
			for (ClanMember cm : clanMembers)
			{
				String joinDate = convertJoinDate(cm);
				memberJoinDates.put(cm.getName(), joinDate);
			}
		}
	}
	
	// We need to convert each member's LocalDate type joinDate value from the ClanMember class into the correct number of epoch seconds.
	// To do this, we get the epoch seconds of the LocalDate value, then multiply that number by 1000.
	// With the new total epoch seconds, we'll get the right date when we convert it to a date value for the specified time zone.
	private String convertJoinDate(ClanMember member)
	{
		long joined = member.getJoinDate().atStartOfDay(ZoneId.of("Europe/Belfast")).toEpochSecond() * 1000L;
		ZonedDateTime convertedJoinDate = Instant.ofEpochMilli(joined).atZone(ZoneId.of("America/New_York"));
		String spectralJoinDate = convertedJoinDate.format(DateTimeFormatter.ofPattern("M/d/uuuu"));
		
		return spectralJoinDate;
	}
	
	public boolean isMemberWidgetLoaded()
	{
		return memberWidgetLoaded;
	}
	
	// ** This method was copied from the Wise Old Man Runelite Plugin code and rewritten to fit this plugin's usage. 
	// All credit for the original code goes to dekvall.
	private void createClanMemberButton(int w, HashMap<Integer, String> clanmembers, HashMap<String, String> clanmemberJoinDates)
	{
		spectralClanMemberButton = new SpectralClanMgmtButton(client, clientThread, chatboxPanelManager, w, clanmembers, clanmemberJoinDates, clanSettings, this, httpRequest);
	}
	// **
	
	// This method is called from our HttpRequest class when a response is received and the members list widget is not loaded.
	// It passes the status (success/failure) and the data holding the message from the web app.
	// Once we've received the response, we'll store the parameters in local variables and shutdown the request's thread.
	// The listeners are removed and the variables reset before the response is displayed in the chatbox.
	// Additional text is appended before the response is displayed depending on the task's value if the export's status is "success".
	public void exportDone(String task, String stat, String dat)
	{
		String status = stat;
		String response = dat;
		String t = task;
		
		if (status.equals("success"))
		{
			if (t.equals("add-new"))
			{
				response = response + "<br>Don't forget to update the member's Discord name and role!";
			}
			else if (t.equals("add-alt"))
			{
				response = response + "<br>Don't forget to add the alt to the Main's Discord name!";
			}
			else if (t.equals("name-change"))
			{
				response = response + "<br>Don't forget to update the member's Discord name!";
			}
		}
		
		// Just in case there's a chatbox prompt open at the time.
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput(response)
		.option("OK", () -> finished())
		.build();
	}
	
	// I had to put these out here, otherwise the chatbox wouldn't show the result prompt
	// when the response was received and this class' exportDone method was called.
	private void finished()
	{
		chatboxPanelManager.close();
		httpRequest.shutdown();
	}
	
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widget)
	{
		if (widget.getGroupId() == CLAN_SETTINGS_INTERFACE)
		{
			// Logically, if the user is viewing this UI, then regardless of which method they used to add the new member, 
			// they would've already finished doing so when this widget loads.
			clanSettings = client.getClanSettings(0);
			
			// Get the local player's clan rank. This will be used later to check if the clan mgmt button can be created.
			localPlayerRank = clanSettings.titleForRank(clanSettings.findMember(client.getLocalPlayer().getName()).getRank()).getId();
		}
		else if (widget.getGroupId() == CLAN_SETTINGS_MEMBERS_INTERFACE)
		{
			// This is being set so that whenever a request is posted, if the members list UI is still open when the response is received,
			// the HttpRequest class will route the results to the exportDone method in the clan mgmt button's class instead of in this class.
			memberWidgetLoaded = true;
			
			getMembersData();
			
			if (members != null)
			{
				if (members.size() > 0)
				{
					if (memberJoinDates != null)
					{
						if (memberJoinDates.size() > 0)
						{
							// Now we determine if the button can be created.
							if (memberJoinDates.size() == members.size() && localPlayerRank != 0)
							{
								// Since this plugin is meant solely for the Spectral clan to use, we don't want the button show
								// if the local player isn't a member of the clan.
								if (client.getClanSettings(0).getName().equals("Spectral"))
								{
									// Since this plugin is meant solely for the admin ranked members of Spectral clan to use, 
									// we don't want the button to be created if the local player isn't an admin in Spectral.
									if (adminRanks.contains(localPlayerRank))
									{
										// ** This method was copied from the Wise Old Man Runelite Plugin code and modified to fit this plugin's usage. 
										// All credit for the original code goes to dekvall.
										clientThread.invoke(() ->
										{
											createClanMemberButton(CLAN_SETTINGS_MEMBERS_INTERFACE_HEADER, members, memberJoinDates);
											
											if (spectralClanMemberButton != null)
											{
												spectralClanMemberButton.enableButton();
											}
										});
										// **
									}
								}
							}
						}
					}
				}
			}
		}
	}
	
	@Subscribe
	public void onWidgetClosed(WidgetClosed widget)
	{
		// If the Members List widget is closed, reset everything (just in case).
		if (widget.getGroupId() == CLAN_SETTINGS_MEMBERS_INTERFACE)
		{
			if (clanMembers != null)
			{
				clanMembers.clear();
			}
			
			members.clear();
			memberJoinDates.clear();
			localPlayerRank = 0;
			
			// This is being set so that whenever a request is posted, if the members list UI isn't open when the response is received,
			// the HttpRequest class will route the results to the exportDone method in this class instead of the clan mgmt button's class.
			memberWidgetLoaded = false;
		}
	}
}
