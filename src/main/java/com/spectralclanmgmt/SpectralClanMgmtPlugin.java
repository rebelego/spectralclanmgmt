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
	
	private boolean isLoaded = false;
	
	private HashMap<Integer, String> members = new HashMap<Integer, String>();
	
	private HashMap<Integer, Integer> memberIndex = new HashMap<Integer, Integer>();
	
	private HashMap<String, Integer> memberJoinDates = new HashMap<String, Integer>();
	
	private ArrayList<Integer> adminRanks = new ArrayList<>(Arrays.asList(-4, -3, 264, 252));
	
	private int slotNum = -1;
	
	private int indexNum = 0;
	
	private int localPlayerRank = 0;
	
	private boolean buttonCreated = false;
	
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
		isLoaded = false;
		buttonCreated = false;
	}
	
	@Override
	protected void shutDown() throws Exception
	{
		log.info("Spectral Clan Mgmt Plugin stopped!");
		isLoaded = false;
		buttonCreated = false;
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
	private void getMemberNames()
	{
		clanSettings = client.getClanSettings(0);
		
		if (clanMembers != null)
		{
			clanMembers.clear();
		}
		
		members.clear();
		memberIndex.clear();
		memberJoinDates.clear();
		
		if (clanSettings != null)
		{
			clanMembers = clanSettings.getMembers();
			int i = 0;
			
			for (ClanMember c : clanMembers)
			{
				members.put(i, c.getName());
				i++;
			}
		}
	}
	
	// I have to do this to match the clan's name against the string "Spectral", 
	// because for whatever reason checking if clanSettings.getName() == "Spectral" comes back false, even though it should be true.
	private boolean isSpectralClan()
	{
		clanSettings = client.getClanSettings(0);
		String clan1 = clanSettings.getName();
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
	
	private void createClanMemberButton(int w, HashMap<Integer, String> clanmembers, HashMap<String, Integer> clanmemberJoinDates, HashMap<Integer, Integer> clanmemberIndexes)
	{
		spectralClanMemberButton = new SpectralClanMgmtButton(client, clientThread, chatboxPanelManager, w, clanmembers, clanmemberJoinDates, clanmemberIndexes, clanSettings, this);
	}
	
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widget)
	{
		if (widget.getGroupId() == CLAN_SETTINGS_INTERFACE)
		{
			// When this widget is loaded, we need to reset these so certain code segments will be executed if the members list widget is loaded.
			buttonCreated = false;
			isLoaded = false;
		}
		else if (widget.getGroupId() == CLAN_SETTINGS_MEMBERS_INTERFACE)
		{
			getMemberNames();
			
			// Get the local player's clan rank. This will be used later to check if the mgmt button can be created.
			localPlayerRank = clanSettings.titleForRank(clanSettings.findMember(client.getLocalPlayer().getName()).getRank()).getId();
			isLoaded = true;
		}
	}
	
	@Subscribe
	public void onWidgetClosed(WidgetClosed widget)
	{
		// If the members list widget is closed, reset everything.
		if (widget.getGroupId() == CLAN_SETTINGS_MEMBERS_INTERFACE)
		{
			isLoaded = false;
			buttonCreated = false;
			clanMembers.clear();
			members.clear();
			memberIndex.clear();
			memberJoinDates.clear();
			indexNum = 0;
			slotNum = -1;
			localPlayerRank = 0;
		}
	}
	
	// onScriptCallbackEvent is where we get the member names and their join dates as they're being passed from the server to the client.
	// Getting those values this way ensures that we don't need to worry about what's being displayed in the middle or right columns.
	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		// We need the slot number to make it easier to get the name of the selected member later.
		if ("getslot".equals(event.getEventName()))
		{
			int[] intStack = client.getIntStack();
			this.slotNum = intStack[0];
			return;
		}
		else if ("getjoinruneday".equals(event.getEventName()))
		{
			// This code segment is for getting the actual value a clan member's join date is stored as (an int) 
			// before it's converted into a localized date string.
			int[] intStack = client.getIntStack();
			int joinDate = intStack[0];
			String memberName = "";
			
			// If the members list widget was loaded
			if (isLoaded == true)
			{
				// If we previously got the slotNum value when the getslot event occurred. 
				// The slotnum value is passed to the cs2 script from the server
				if (this.slotNum > -1)
				{
					// If the members hashmap has values, get the name of the member that maps to that slotNum.
					// The reason for this is because the names will be displayed in alphabetical order on the widget,
					// but they aren't stored in alphabetical order, they're stored in the order they joined the clan.
					if (this.members.size() > 0)
					{
						memberName = this.members.get(this.slotNum);
					}
					
					if (memberName != "")
					{
						if (joinDate > 0)
						{
							// Here is where the int join date is converted into a String join date, which we want to always be in EDT/EST.
							// The member's name and their join date are then stored in a HashMap so we can get the join date for a selected clan member later.
							long jDate = (((long)joinDate + 11745L) * 86400000L);
							ZonedDateTime jdna = Instant.ofEpochMilli(jDate).atZone(ZoneId.of("America/New_York"));
							String jna = jdna.format(DateTimeFormatter.ofPattern("M/d/uuuu"));
							
							// If this is the very first name that's been passed to the cs2 script, then nothing should be in the index hashmap
							// and the index of member names going into the hashmap should start at 0.
							if (this.indexNum >= this.members.size())
							{
								this.indexNum = 0;
							}
							
							if (this.memberJoinDates != null)
							{
								// If the memberJoinDates hashmap isn't empty
								if (this.memberJoinDates.size() > 0)
								{
									// This just checks to make sure the name of the member isn't already in the hashmap, 
									// because this whole thing actually runs twice, so if I didn't have this, each name would be added twice.
									if (this.memberJoinDates.containsKey(memberName) == false)
									{
										// Put the member's name and their int join date in the memberJoinDates hashmap.
										this.memberJoinDates.put(memberName, joinDate);
										
										// We need to store the member's index and slot number in another HashMap to make it easy to find the name
										// of the member that was clicked on the clan members list interface later. We don't want to get the text of the widget
										// that was clicked because the results are sometimes odd and I want to limit how many chars I need to replace later.
										// The indexNum is for the position the member appears on the widget.
										// The slotNum is for the actual position of the member in the clan settings list of members from the server.
										// To illustrate, a member may have an index of 0, because they're listed first in the members list widget,
										// but also have a slotNum of 312, because 312 existing members joined the clan before them.
										
										// If the memberIndex hashmap isn't empty
										if (this.memberIndex.size() > 0)
										{
											// If the index and slot number pair haven't already been added, add them to the memberIndex hashmap.
											// Then increment the indexNum for the next member.
											if (this.memberIndex.containsKey(this.indexNum) == false)
											{
												this.memberIndex.put(this.indexNum, this.slotNum);
												this.indexNum += 1;
											}
										}
										else // if the hashmap is empty
										{
											this.memberIndex.put(this.indexNum, this.slotNum);
											this.indexNum += 1;
										}
									}
								}
								else // if the memberJoinDates hashmap is empty
								{
									this.memberJoinDates.put(memberName, joinDate);
									
									// Same as what happens if the hashmap isn't empty.
									if (this.memberIndex.size() > 0)
									{
										if (this.memberIndex.containsKey(this.indexNum) == false)
										{
											this.memberIndex.put(this.indexNum, this.slotNum);
											this.indexNum += 1;
										}
									}
									else
									{
										this.memberIndex.put(this.indexNum, this.slotNum);
										this.indexNum += 1;
									}
								}
								
								// Here is the point where we need to decide if the button can be created.
								// I had to put it here, because if I put it in WidgetLoaded, it would actually check this part before the
								// hashmaps have been populated with values, so the button would never be created.
								// All of these conditions ensure that this check only happens at the very end, 
								// when all hashmaps have been populated and the members list widget is loaded.
								if (this.memberJoinDates.size() == this.members.size() && this.memberIndex.size() == this.members.size() && localPlayerRank != 0 && isLoaded == true && buttonCreated == false)
								{
									// Set the flag so this segment of code can only run once each time the members list widget is loaded and this method is triggered.
									buttonCreated = true;
									
									// Since this plugin is meant solely for the Spectral clan to use, we don't want the button to be created and displayed
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
												createClanMemberButton(CLAN_SETTINGS_MEMBERS_INTERFACE_HEADER, members, memberJoinDates, memberIndex);
												
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
			
			// Reset these variables for the next pass then return to the cs2 script.
			joinDate = -1;
			memberName = "";
			this.slotNum = -1;
			return;
		}
	}
}
