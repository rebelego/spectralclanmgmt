package com.spectralclanmgmt;

import javax.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
name = "Spectral Clan Mgmt",
description = "A Runelite plugin to help the members of Spectral, an OSRS clan."
)
public class SpectralClanMgmtPlugin extends Plugin
{
	/*
	For anyone looking at this, I'm aware that this plugin has some ugly fucking code, but I'm gonna have to ask you to forgive me
	cause it's been over 3 years since I did any major coding with an OOL, and I am beyond rusty. And it shows.
	But the code works, and at this point, I'm satisfied with that.
	*/
	
	@Inject
	private SpectralClanMgmtConfig config;
	
	@Inject
	private Client client;
	
	@Inject
	private ClientThread clientThread;
	
	@Inject
	private SpectralClanMgmtChatboxPanelManager chatboxPanelManager;
	
	@Inject
	private SpectralClanMgmtChatCommandManager clanChatCommandManager;
	
	@Inject
	private SpectralClanMgmtChatCommandInputManager cmdMgr;
	
	private SpectralClanMgmtButton spectralClanMemberButton;
	
	private ClanSettings clanSettings;
	
	private List<ClanMember> clanMembers;
	
	private HashMap<Integer, String> members = new HashMap<Integer, String>();
	
	private HashMap<String, String> memberJoinDates = new HashMap<String, String>();
	
	private static final int CLAN_SETTINGS_INTERFACE = 690;
	
	private static final int CLAN_SETTINGS_MEMBERS_INTERFACE = 693;
	
	private static final int CLAN_SETTINGS_MEMBERS_INTERFACE_HEADER = 45416450;
	
	private SpectralClanMgmtHttpRequest httpRequest;
	
	private SpectralClanMgmtCommandPhrases spectralPhrases;
	
	private boolean canUseDiscordCommands = false;
	
	// The permission flag that determines if clan members can use the !spectral command. The value comes from our online web app
	// so we can change it at any time without having to modify the plugin.
	// This value is checked and set the first time the player logs in after start-up,
	// as well as when they use the command but before anything has been output to the clan chat.
	private boolean canUseSpectralCommand = false;
	
	private boolean pluginLoaded = false;
	
	private boolean configChecked = false;
	
	// Used to track if the user is currently viewing the Members List UI (which means the button exists if they're one of the admins).
	private boolean memberWidgetLoaded = false;
	
	// This is just for checking if the next tick has occurred after logging in.
	private boolean firstGameTick = false;
	
	private int previousPhrasePosition = -1;
	
	private static final String MOD_COMMAND = "!mod";
	
	private static final String RECRUIT_COMMAND = "!recruit";
	
	private static final String SPECTRAL_COMMAND = "!spectral";
	
	private int localPlayerRank = 0;
	
	private int attemptCount = 0;
	
	private int gameTickCount = 0;
	
	@Provides
	SpectralClanMgmtConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SpectralClanMgmtConfig.class);
	}
	
	@Override
	protected void startUp() throws Exception
	{
		log.info("Spectral Clan Mgmt Plugin started!");
		spectralPhrases = new SpectralClanMgmtCommandPhrases();
		httpRequest = new SpectralClanMgmtHttpRequest(this, config, client);
		clanChatCommandManager.registerCommandAsync(MOD_COMMAND, this::getMod);
		clanChatCommandManager.registerCommandAsync(RECRUIT_COMMAND, this::getRecruit);
		clanChatCommandManager.registerCommandAsync(SPECTRAL_COMMAND, this::getSpectral);
	}
	
	@Override
	protected void shutDown() throws Exception
	{
		log.info("Spectral Clan Mgmt Plugin stopped!");
		clanChatCommandManager.unregisterCommand(MOD_COMMAND);
		clanChatCommandManager.unregisterCommand(RECRUIT_COMMAND);
		clanChatCommandManager.unregisterCommand(SPECTRAL_COMMAND);
		spectralPhrases = null;
		httpRequest.shutdown();
	}
	
	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			if (!firstGameTick)
			{
				firstGameTick = true;
				gameTickCount = 0;
				return;
			}
			else
			{
				if (gameTickCount == 0)
				{
					gameTickCount = 1;
				}
			}
			
			if (gameTickCount == 1)
			{
				gameTickCount = -1;
				
				loadConfigLinks();
			}
			else
			{
				return;
			}
		}
		else
		{
			return;
		}
	}
	
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// Update this so it only does the check once the local player has logged in even if they change the config on the loading screen.
		if (event.getGroup().equals("spectralclanmgmt") && event.getKey().equals("scriptURL"))
		{
			if (config.scriptURL().equals(""))
			{
				config.setAdminScriptURL("");
				config.setSpectralDiscordAppURL("");
				canUseDiscordCommands = false;
			}
			
			firstGameTick = false;
			configChecked = false;
		}
		else
		{
			return;
		}
	}
	
	// Called the next game tick after logging in if the config was either changed or the plugin started up.
	private void loadConfigLinks()
	{
		canUseSpectralCommand = false;
		canUseDiscordCommands = false;
		
		if (attemptCount <= 5)
		{
			// Checks if the local Player is a member of Spectral.
			if (!spectralChecks(Optional.empty()))
			{
				pluginLoaded = true;
				configChecked = true;
				canUseSpectralCommand = false;
				canUseDiscordCommands = false;
				return;
			}
			
			// Checks if the URL for Spectral's web app in the settings is a valid URL.
			if (!checkURL("normal"))
			{
				pluginLoaded = true;
				configChecked = true;
				canUseSpectralCommand = false;
				canUseDiscordCommands = false;
				
				if (spectralPhrases != null)
				{
					spectralPhrases.setPhrases(null);
				}
				
				return;
			}
			
			localPlayerRank = getLocalPlayerRank();
			
			if (!pluginLoaded)
			{
				pluginLoaded = true;
				
				if (!configChecked)
				{
					configChecked = true;
					checkConfigLinks(localPlayerRank, "startup");
				}
				else
				{
					if (httpRequest.isReady())
					{
						httpRequest.initializeExecutor();
						
						// I need this to run the plugin has finished starting and the player's logged in, 
						// but I don't want it to run every time the GameState is LOGGED_IN, 
						// so if the GameState wasn't LOGGED_IN when checkCommandPermission was called in setConfigLinks,
						// it'll be run on the next game tick after the player is logged in and httpRequest is ready.
						checkCommandPermission("startup", 0, 0);
					}
					else
					{
						pluginLoaded = false;
						firstGameTick = false;
						return;
					}
				}
			}
			else
			{
				if (!configChecked)
				{
					configChecked = true;
					checkConfigLinks(localPlayerRank, "configChanged");
				}
			}
		}
		else
		{
			return;
		}
	}
	
	private void checkConfigLinks(int rank, String src)
	{
		String configLink = "";
		
		if (isNormalRank(rank))
		{
			configLink = "discord";
		}
		else if (isAdminRank(rank))
		{
			configLink = "both";
		}
		
		if (httpRequest.isReady())
		{
			httpRequest.initializeExecutor();
			httpRequest.postRequestAsync("config", Optional.of(configLink), Optional.of(src), Optional.empty(), Optional.empty());
		}
		else
		{
			firstGameTick = false;
			
			if (src.equalsIgnoreCase("startup"))
			{
				pluginLoaded = false;
				configChecked = false;
			}
			else if (src.equalsIgnoreCase("configChanged"))
			{
				configChecked = false;
			}
		}
	}
	
	protected void setConfigLinks(String returnedLinks, String[] configLinks, Optional<String> src)
	{
		String source = src.orElse("");
		
		if (configLinks.length > 0)
		{
			attemptCount = 0;
			
			// If blank was returned for some reason for a link, they'll need to contact the developer about it.
			// Once whatever issue that was causing blank to be returned for a link is fixed, they can flip the plugin off and on again
			// to force it to pull the link's value from the web app again.
			if (returnedLinks.equalsIgnoreCase("both"))
			{
				config.setSpectralDiscordAppURL(configLinks[0]);
				config.setAdminScriptURL(configLinks[1]);
			}
			else if (returnedLinks.equalsIgnoreCase("discord"))
			{
				config.setSpectralDiscordAppURL(configLinks[0]);
			}
			
			if (!checkURL("discord"))
			{
				canUseDiscordCommands = false;
			}
			else
			{
				canUseDiscordCommands = true;
			}
			
			canUseSpectralCommand = true;
			configChecked = true;
			
			if (source.equalsIgnoreCase("startup"))
			{
				if (client.getGameState() == GameState.LOGGED_IN)
				{
					// I need this to run the plugin has finished starting and the player's logged in, 
					// but I don't want it to run every time the GameState is LOGGED_IN, 
					// so it will only be called when checkConfigLinks is called at startup.
					checkCommandPermission("startup", 0, 0);
				}
				else
				{
					pluginLoaded = false;
					firstGameTick = false;
					httpRequest.shutdown();
				}
			}
			else if (source.equalsIgnoreCase("configChanged"))
			{
				httpRequest.shutdown();
			}
		}
		else
		{
			attemptCount++;
			
			// If something happened and no links were returned from the post request to the web app,
			// clear the hidden config items and set pluginLoaded to false so it can try again.
			// Up to 5 attempts are allowed before it will have to be blocked and the member will need to contact the developer about it.
			config.setSpectralDiscordAppURL("");
			config.setAdminScriptURL("");
			canUseDiscordCommands = false;
			canUseSpectralCommand = false;
			
			if (attemptCount <= 5)
			{
				firstGameTick = false;
				
				if (source.equalsIgnoreCase("startup"))
				{
					pluginLoaded = false;
				}
				else if (source.equalsIgnoreCase("configChanged"))
				{
					configChecked = false;
				}
			}
			else
			{
				pluginLoaded = true;
				configChecked = true;
			}
			
			// If they run out of attempts, they need to contact the dev so the dev can check the web app and fix it if there's an issue.
			// If whatever was causing nothing to be returned is fixed, then can flip the plugin off and on again to reset the attempt count.
			httpRequest.shutdown();
		}
	}
	
	// The clan's admin ranks. The numbers are the key values in the ranks enum for the 
	// Owner, Deputy Owner, Moderator, and Completionist (Recruiter) ranks.
	protected static ArrayList<Integer> getAdminRanks()
	{
		return new ArrayList<>(Arrays.asList(-4, -3, 264, 252));
	}
	
	// The clan's non-admin ranks. The numbers are the key values in the ranks enum for the 
	// Gnome Child, Lieutenant, Astral, Sapphire, Emerald, Ruby, Diamond, Dragonstone, Onyx, and Zenyte ranks.
	protected static ArrayList<Integer> getNormalRanks()
	{
		return new ArrayList<>(Arrays.asList(9, 35, 58, 65, 66, 67, 68, 69, 70, 71, 143, 111));
	}
	
	private void checkCommandPermission(String src, int cType, int cTarget)
	{
		if (!checkURL("normal"))
		{
			canUseSpectralCommand = false;
			canUseDiscordCommands = false;
			
			if (spectralPhrases != null)
			{
				spectralPhrases.setPhrases(null);
			}
			
			if (src.equalsIgnoreCase("startup"))
			{
				httpRequest.shutdown();
			}
			
			return;
		}
		
		String localPlayer = Text.sanitize(client.getLocalPlayer().getName());
		
		if (!src.equalsIgnoreCase("startup"))
		{
			httpRequest.initializeExecutor();
		}
		
		httpRequest.postRequestAsync("permission", Optional.of(localPlayer), Optional.of(src), Optional.of(cType), Optional.of(cTarget));
	}
	
	private void discordCommands(String commandUsed, String localPlayer, int cType, int cTarget)
	{
		httpRequest.initializeExecutor();
		httpRequest.postRequestAsync(commandUsed, localPlayer, cType, cTarget);
	}
	
	protected void getCommandPermission(boolean result, String src, int cType, int cTarget)
	{
		canUseSpectralCommand = result;
		String source = src;
		int chatType = cType;
		int chatTarget = cTarget;
		
		if (result)
		{
			if (spectralPhrases == null)
			{
				spectralPhrases = new SpectralClanMgmtCommandPhrases();
			}
			
			if (spectralPhrases.getPhrases() == null)
			{
				checkPhrases(source, chatType, chatTarget);
				return;
			}
		}
		else
		{
			if (spectralPhrases != null)
			{
				spectralPhrases.setPhrases(null);
			}
		}
		
		if (source.equalsIgnoreCase("command"))
		{
			finishSpectralCommand(result, chatType, chatTarget);
		}
		else
		{
			httpRequest.shutdown();
		}
	}
	
	private void checkPhrases(String src, int cType, int cTarget)
	{
		if (checkURL("normal"))
		{
			httpRequest.postRequestAsync("phrases", Optional.empty(), Optional.of(src), Optional.of(cType), Optional.of(cTarget));
		}
		else
		{
			httpRequest.shutdown();
		}
	}
	
	protected void getPhrases(String status, String data, String src, int cType, int cTarget)
	{
		if (status.equalsIgnoreCase("success"))
		{
			if (data.length() > 0)
			{
				String[] phrases = data.split("\\;");
				
				if (spectralPhrases != null)
				{
					spectralPhrases.setPhrases(phrases);
				}
				else
				{
					spectralPhrases = new SpectralClanMgmtCommandPhrases();
					spectralPhrases.setPhrases(phrases);
				}
			}
			else
			{
				if (spectralPhrases != null)
				{
					spectralPhrases.setPhrases(null);
				}
			}
		}
		else
		{
			if (spectralPhrases != null)
			{
				spectralPhrases.setPhrases(null);
			}
		}
		
		String source = src;
		int chatType = cType;
		int chatTarget = cTarget;
		
		if (source.equalsIgnoreCase("command"))
		{
			finishSpectralCommand(canUseSpectralCommand, chatType, chatTarget);
		}
		else
		{
			httpRequest.shutdown();
		}
	}
	
	// Checks if the string passed to the method is a valid URL. If it's missing, or it's not a valid URL, it'll return false
	// so we can respond and block the execution from continuing before an HttpRequest is created.
	protected boolean checkURL(String URL)
	{
		String scriptURL = "";
		boolean isValid = false;
		
		if (URL.equalsIgnoreCase("normal"))
		{
			scriptURL = config.scriptURL();
		}
		else if (URL.equalsIgnoreCase("admin"))
		{
			scriptURL = config.adminScriptURL();
		}
		else if (URL.equalsIgnoreCase("discord"))
		{
			scriptURL = config.spectralDiscordAppURL();
		}
		
		if (scriptURL != "")
		{
			// For Spectral's purposes, there's no reason for the protocol to be anything other than http or https.
			Pattern urlRegexPattern = Pattern.compile("^((http|https)://)?([a-zA-Z0-9]+[.])?[a-zA-Z0-9-]+(.[a-zA-Z]{2,6})?(:[0-9]{1,5})?(/[a-zA-Z0-9-._?,'+&%$#=~]*)*$");
			isValid = urlRegexPattern.matcher(scriptURL).matches();
		}
		
		return isValid;
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
	
	protected boolean isMemberWidgetLoaded()
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
	protected void exportDone(String task, String stat, String dat)
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
	
	protected static boolean isAdminRank(int rank)
	{
		if (getAdminRanks().contains(rank))
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	protected static boolean isNormalRank(int rank)
	{
		if (getNormalRanks().contains(rank))
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	// I may end up needing to use this in more places later, so it gets its own method for now.
	protected int getLocalPlayerRank()
	{
		int rank = 0;
		
		if (client.getClanSettings(0) != null)
		{
			if (client.getClanSettings(0).getName().equals("Spectral"))
			{
				if (client.getClanSettings(0).findMember(client.getLocalPlayer().getName()) != null)
				{
					rank = client.getClanSettings(0).titleForRank(client.getClanSettings(0).findMember(client.getLocalPlayer().getName()).getRank()).getId();
				}
			}
		}
		
		return rank;
	}
	
	@Subscribe
	protected void onWidgetLoaded(WidgetLoaded widget)
	{
		if (widget.getGroupId() == CLAN_SETTINGS_INTERFACE)
		{
			if (getLocalPlayerRank() == 0)
			{
				config.setAdminScriptURL("");
				config.setSpectralDiscordAppURL("");
				return;
			}
			
			localPlayerRank = getLocalPlayerRank();
			
			if (isNormalRank(localPlayerRank))
			{
				config.setAdminScriptURL("");
			}
		}
		else if (widget.getGroupId() == CLAN_SETTINGS_MEMBERS_INTERFACE)
		{
			clanSettings = client.getClanSettings(0);
			
			// This is being set so that whenever a request is posted, if the members list UI is still open when the response is received,
			// the HttpRequest class will route the results to the exportDone method in the clan mgmt button's class instead of in this class.
			memberWidgetLoaded = true;
			
			// Since this plugin is meant solely for the members of Spectral to use, 
			// the code should exit if the local player isn't in the clan.
			if (clanSettings.getName().equals("Spectral"))
			{
				// Since this part of the plugin is meant solely for the admin members of Spectral to use, 
				// we don't want the button to be created if the local player's rank isn't an admin one.
				if (isAdminRank(getLocalPlayerRank()))
				{
					if (checkURL("admin"))
					{
						getMembersData();
						
						if (members != null)
						{
							if (members.size() > 0)
							{
								if (memberJoinDates != null)
								{
									if (memberJoinDates.size() > 0 && memberJoinDates.size() == members.size())
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
				else
				{
					config.setAdminScriptURL("");
				}
			}
		}
	}
	
	@Subscribe
	protected void onWidgetClosed(WidgetClosed widget)
	{
		// If the Members List widget is closed, reset everything (just in case).
		if (widget.getGroupId() == CLAN_SETTINGS_MEMBERS_INTERFACE)
		{
			if (clanMembers != null)
			{
				clanMembers.clear();
			}
			
			if (getLocalPlayerRank() == 0)
			{
				config.setAdminScriptURL("");
				config.setSpectralDiscordAppURL("");
			}
			
			localPlayerRank = getLocalPlayerRank();
			members.clear();
			memberJoinDates.clear();
			
			// This is being set so that whenever a request is posted, if the members list UI isn't open when the response is received,
			// the HttpRequest class will route the results to the exportDone method in this class instead of the clan mgmt button's class.
			memberWidgetLoaded = false;
		}
	}
	
	protected void disableButton()
	{
		if (spectralClanMemberButton != null)
		{
			spectralClanMemberButton = null;
		}
	}
	
	private boolean checkGameState()
	{
		if (client.getGameState().getState() == GameState.LOGGED_IN.getState())
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	private boolean spectralChecks(Optional<Integer> clanrank)
	{
		int rank = clanrank.orElse(1000);
		
		if (rank == 1000)
		{
			rank = getLocalPlayerRank();
			
			// The checks for being a member of Spectral clan are all pretty much encompassed in getLocalPlayerRank. 
			// If any of them fail, it returns 0.
			if (rank == 0)
			{
				config.setSpectralDiscordAppURL("");
				config.setAdminScriptURL("");
				canUseSpectralCommand = false;
				canUseDiscordCommands = false;
				
				if (spectralPhrases != null)
				{
					spectralPhrases.setPhrases(null);
				}
				
				return false;
			}
		}
		
		if (!isNormalRank(rank) && !isAdminRank(rank))
		{
			config.setSpectralDiscordAppURL("");
			config.setAdminScriptURL("");
			canUseSpectralCommand = false;
			canUseDiscordCommands = false;
			
			if (spectralPhrases != null)
			{
				spectralPhrases.setPhrases(null);
			}
			
			return false;
		}
		
		if (isNormalRank(rank))
		{
			config.setAdminScriptURL("");
		}
		
		return true;
	}
	
	// This encompasses the various checks that need to be run before one of Spectral's commands is allowed to be executed.
	private boolean commandChecks(int chatType, int rank)
	{
		if (!spectralChecks(Optional.of(rank)) || !checkGameState() || (chatType != 3))
		{
			return false;
		}
		
		return true;
	}
	
	/* 
	The !mod command can be used by any of Spectral's ranked members 
	to send an alert to the Moderators channel in Spectral's Discord server
	in the event that someone is causing trouble in the clan chat and none of the Moderators are online to handle it.
	Most of our members are resistant to using or keeping Discord open while playing, 
	even though they are a part of our Discord server. The Mods aren't always able to have
	at least one of them online in-game every hour of the day, but all of the Mods have Discord on their phones
	so they can be reached via Discord even when not playing.
	This command will only work when used in the clan chat channel.
	 */
	private void getMod(SpectralClanMgmtChatboxCommandInput chatboxCommandInput, String message)
	{
		int cTarget = chatboxCommandInput.getChatTarget();
		int cType = chatboxCommandInput.getChatType();
		String player = Text.sanitize(client.getLocalPlayer().getName());
		String comm = chatboxCommandInput.getSpectralCommand();
		int cRank = chatboxCommandInput.getRank();
		String msg = "";
		boolean flag = false;
		
		chatboxCommandInput.consume();
		
		if (!commandChecks(cType, cRank))
		{
			return;
		}
		
		if (!checkURL("normal"))
		{
			msg = "Enter a valid URL for Spectral's web app in the plugin's settings first. If it's valid, contact the developer about this issue.";
			flag = true;
		}
		else if (!checkURL("discord"))
		{
			msg = "The URL for Spectral's Discord Web API is either missing or not valid. Contact the developer about this issue.";
			flag = true;
		}
		
		if (!httpRequest.isReady())
		{
			msg = "The !mod command isn't ready to be used yet. Wait a minute before trying again.";
			flag = true;
		}
		
		if (flag)
		{
			final String alert = msg;
			clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", alert, null));
		}
		else
		{
			discordCommands(comm, player, cType, cTarget);
		}
	}
	
	/* 
	The !recruit command can be used by any of Spectral's ranked members
	to send an alert to the Recruiters channel in Spectral's Discord server 
	(Moderators also have access to this channel and would be pinged),
	in the event that someone wants to join the clan and none of the them are online.
	Most of our members are resistant to actually using or keeping Discord open while playing, 
	even though they are a part of our Discord server. The admins aren't always able to have
	at least one of them online in-game every hour of the day, but all of the admins have Discord on their phones
	so they can be reached via Discord even when not playing.
	This command will only work when used in the clan chat channel.
	*/
	private void getRecruit(SpectralClanMgmtChatboxCommandInput chatboxCommandInput, String message)
	{
		int cTarget = chatboxCommandInput.getChatTarget();
		int cType = chatboxCommandInput.getChatType();
		String player = Text.sanitize(client.getLocalPlayer().getName());
		String comm = chatboxCommandInput.getSpectralCommand();
		int cRank = chatboxCommandInput.getRank();
		String msg = "";
		boolean flag = false;
		
		chatboxCommandInput.consume();
		
		if (!commandChecks(cType, cRank))
		{
			return;
		}
		
		if (!checkURL("normal"))
		{
			msg = "Enter a valid URL for Spectral's web app in the plugin's settings first. If it's valid, contact the developer about this issue.";
			flag = true;
		}
		else if (!checkURL("discord"))
		{
			msg = "The URL for Spectral's Discord Web API is either missing or not valid. Contact the developer about this issue.";
			flag = true;
		}
		
		if (!httpRequest.isReady())
		{
			msg = "The !recruit command isn't ready to be used yet. Wait a minute before trying again.";
			flag = true;
		}
		
		if (flag)
		{
			final String alert = msg;
			clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", alert, null));
		}
		else
		{
			discordCommands(comm, player, cType, cTarget);
		}
	}
	
	/*
	This is meant to be a fun command for the ranked members of Spectral to use.
	When the command is used in the clan chat channel by a ranked member, 
	the input that actually shows in the clan chat after the command is sent
	will be a randomly picked value from a string array. The string array is populated by
	values from a private spreadsheet maintained by the clan's moderators and leaders.
	All phrases are verified to not violate OSRS's rules or Jagex's T&C and to not exceed the chatbox's input limit.
	All of the phrases available to the command are viewable in a private channel for clan members in Spectral's Discord server.
	This command will only work when used in the clan chat channel.
	This command will only work when the permission flag is set to true. A permission check is done whenever the command is used to 
	verify that the clan, or the clan member, still has permission to use the the command.
	*/
	private void getSpectral(SpectralClanMgmtChatboxCommandInput chatboxCommandInput, String message)
	{
		int cTarget = chatboxCommandInput.getChatTarget();
		int cType = chatboxCommandInput.getChatType();
		String comm = chatboxCommandInput.getSpectralCommand();
		int cRank = chatboxCommandInput.getRank();
		String msg = "";
		boolean flag = false;
		
		chatboxCommandInput.consume();
		
		if (!commandChecks(cType, cRank))
		{
			return;
		}
		
		if (!checkURL("normal"))
		{
			msg = "Enter a valid URL for Spectral's web app in the plugin's settings first. If it's valid, contact the developer about this issue.";
			flag = true;
		}
		
		if (flag)
		{
			final String alert = msg;
			clientThread.invoke(() -> {
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", alert, null);
			});
		}
		else
		{
			if (httpRequest.isReady())
			{
				checkCommandPermission("command", cType, cTarget);
			}
		}
	}
	
	// I need to wait for the permission and phrases check to finish before proceeding, in case the permissions were changed after start up.
	private void finishSpectralCommand(boolean permission, int cType, int cTarget)
	{
		// Don't forget to change it back so the commands can only be entered in the clan chat.
		String msg = "";
		boolean flag = false;
		
		if (!permission)
		{
			msg = "Permissions revoked. You aren't allowed to use the !spectral command right now.";
			flag = true;
		}
		else if (spectralPhrases == null)
		{
			msg = "Something went wrong. No phrases found for command. Contact the plugin dev about this issue.";
			flag = true;
		}
		else if (spectralPhrases != null && spectralPhrases.getPhrases() == null)
		{
			msg = "Something went wrong. No phrases found for command. Contact the plugin dev about this issue.";
			flag = true;
		}
		else if (spectralPhrases != null && spectralPhrases.getPhrases() != null && spectralPhrases.getPhrases().length == 0)
		{
			msg = "Something went wrong. No phrases found for command. Contact the plugin dev about this issue.";
			flag = true;
		}
		
		if (flag)
		{
			final String message = msg;
			clientThread.invoke(() -> { 
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null); 
				httpRequest.shutdown(); 
			});
		}
		else
		{
			// Get a randomly picked phrase from the phrases array. The phrase will appear in the clan chat instead of the command text.
			Random ran = new Random();
			int rn = ran.nextInt(spectralPhrases.getPhrases().length);
			
			if (previousPhrasePosition != -1)
			{
				while (rn == previousPhrasePosition)
				{
					rn = ran.nextInt(spectralPhrases.getPhrases().length);
				}
			}
			
			previousPhrasePosition = rn;
			
			final String cInput = spectralPhrases.getPhrases()[rn];
			
			clientThread.invoke(() -> { 
				cmdMgr.sendChatboxInput(cInput, cType, cTarget); 
				httpRequest.shutdown(); 
			});
		}
	}
	
	// I need to wait for the permission and phrases check to finish before proceeding, in case the permissions were changed after start up.
	protected void finishDiscordCommands(String status, String data, String commandUsed, int cType, int cTarget)
	{
		// Don't forget to change it back so the commands can only be entered in the clan chat.
		String msg = "";
		boolean flag = false;
		
		if (!canUseDiscordCommands)
		{
			msg = "You aren't allowed to use this plugin's commands. Contact the developer if you're a ranked member of the Spectral clan and you see this message.";
			flag = true;
		}
		else
		{
			if (status.equalsIgnoreCase("failure"))
			{
				flag = true;
				msg = data;
			}
			else if (status.equalsIgnoreCase("success"))
			{
				if (commandUsed.equalsIgnoreCase("!recruit"))
				{
					msg = "I pinged the recruiters in Discord for help!";
				}
				else if (commandUsed.equalsIgnoreCase("!mod"))
				{
					msg = "I pinged the mods in Discord for help!";
				}
			}
		}
		
		if (flag)
		{
			final String message = msg;
			clientThread.invoke(() -> {
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
				httpRequest.shutdown();
			});
		}
		else
		{
			final String cInput = msg;
			
			clientThread.invoke(() -> {
				cmdMgr.sendChatboxInput(cInput, cType, cTarget);
				httpRequest.shutdown();
			});
		}
	}
}
