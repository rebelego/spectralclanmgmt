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
import org.apache.commons.validator.routines.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@PluginDescriptor(
name = "Spectral Clan Mgmt",
description = "A member management plugin for the OSRS Spectral clan's Admin ranks only."
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
	
	private ArrayList<Integer> adminRanks = new ArrayList<>(Arrays.asList(-4, -3, 264, 252));
	
	private int localPlayerRank = 0;
	
	private static final int CLAN_SETTINGS_INTERFACE = 690;
	
	private static final int CLAN_SETTINGS_MEMBERS_INTERFACE = 693;
	
	private static final int CLAN_SETTINGS_MEMBERS_INTERFACE_HEADER = 45416450;
	
	@Provides
	SpectralClanMgmtConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SpectralClanMgmtConfig.class);
	}
	
	@Override
	protected void startUp() throws Exception
	{
		log.info("Spectral Clan Mgmt Plugin started!");
	}
	
	@Override
	protected void shutDown() throws Exception
	{
		log.info("Spectral Clan Mgmt Plugin stopped!");
	}
	
	public SpectralClanMgmtConfig returnConfig()
	{
		return config;
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
			UrlValidator validator = new UrlValidator(UrlValidator.ALLOW_2_SLASHES + UrlValidator.ALLOW_ALL_SCHEMES);
			
			if (validator.isValid(config.scriptURL()))
			{
				return true;
			}
			else
			{
				return false;
			}
		}
	}
	
	// Populates the members hashmap with the member's name and position in the list of members returned from ClanSettings.
	// Also clears the other hashmaps so they can be populated later with the most current key/value pairs.
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
			
			// We get the members names into a temp arraylist, because we need to sort them alphabetically for later.
			ArrayList<String> mems = new ArrayList<String>();
			
			clanMembers.forEach((me) -> mems.add(me.getName()));
			
			// This should sort the arraylist of member names alphabetically while ignoring the letter cases.
			Collections.sort(mems, String.CASE_INSENSITIVE_ORDER);
			
			int i = 0;
			
			for (String m : mems)
			{
				// Now that we've got a sorted list of member names, we'll put them into a hashmap, using i to act as an index for the member names.
				// This will be used later in the button's class to match the name the player clicked on the UI to the name in this hashmap
				// so we won't have to deal with the annoyance of getting text from a widget and sanitizing the text.
				members.put(i, m);
				i++;
			}
			
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
		System.out.println("Name: " + member.getName() + ", Join Date: " + spectralJoinDate);
		
		return spectralJoinDate;
	}
	
	// I have to do this to match the clan's name against the string "Spectral", 
	// because for whatever reason checking if clanSettings.getName() == "Spectral" comes back false, even though it should be true.
	private boolean isSpectralClan()
	{
		String clan1 = client.getClanSettings(0).getName();
		String clan2 = "Spectral";
		
		char[] clanChars1 = clan1.toCharArray();
		char[] clanChars2 = clan2.toCharArray();
		
		String chars1 = "";
		String chars2 = "";
		
		for (int i = 0; i < clanChars1.length; i++)
		{
			int temp1 = (int) clanChars1[i];
			
			if (chars1 == "")
			{
				chars1 = String.valueOf(temp1);
			}
			else
			{
				chars1 = chars1 + ", " + String.valueOf(temp1);
			}
		}
		
		for (int j = 0; j < clanChars2.length; j++)
		{
			int temp2 = (int) clanChars2[j];
			
			if (chars2 == "")
			{
				chars2 = String.valueOf(temp2);
			}
			else
			{
				chars2 = chars2 + ", " + String.valueOf(temp2);
			}
		}
		
		chars1 = chars1.trim();
		chars2 = chars2.trim();
		
		return chars1.equals(chars2);
	}
	
	private void createClanMemberButton(int w, HashMap<Integer, String> clanmembers, HashMap<String, String> clanmemberJoinDates)
	{
		spectralClanMemberButton = new SpectralClanMgmtButton(client, clientThread, chatboxPanelManager, w, clanmembers, clanmemberJoinDates, clanSettings, this);
	}
	
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widget)
	{
		if (widget.getGroupId() == CLAN_SETTINGS_INTERFACE)
		{
			// Technically if they view this UI, regardless of which method they used to add a new member, they would have to have
			// already finished adding them when this widget loads.
			clanSettings = client.getClanSettings(0);
			
			// Get the local player's clan rank. This will be used later to check if the mgmt button can be created.
			localPlayerRank = clanSettings.titleForRank(clanSettings.findMember(client.getLocalPlayer().getName()).getRank()).getId();
		}
		else if (widget.getGroupId() == CLAN_SETTINGS_MEMBERS_INTERFACE)
		{
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
								// if the local player isn't a member of the Spectral clan.
								if (isSpectralClan() == true)
								{
									// Since this plugin is meant solely for the admin ranked members of Spectral clan to use, 
									// we don't want the button to be created and displayed
									// if the local player isn't an admin ranked member of the Spectral clan.
									if (adminRanks.contains(localPlayerRank))
									{
										clientThread.invoke(() ->
										{
											createClanMemberButton(CLAN_SETTINGS_MEMBERS_INTERFACE_HEADER, members, memberJoinDates);
											
											if (spectralClanMemberButton != null)
											{
												spectralClanMemberButton.enableButton();
											}
										});
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
		// If the members list widget is closed, reset everything just in case.
		if (widget.getGroupId() == CLAN_SETTINGS_MEMBERS_INTERFACE)
		{
			if (clanMembers != null)
			{
				clanMembers.clear();
			}
			
			members.clear();
			memberJoinDates.clear();
			localPlayerRank = 0;
		}
	}
}
