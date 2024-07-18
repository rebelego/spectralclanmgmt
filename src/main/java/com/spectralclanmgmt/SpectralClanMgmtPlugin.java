package com.spectralclanmgmt;

import javax.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
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
	
	// These two booleans are the permission flags that determine if spectral's commands can be used.
	// The values comes from our online web app so we can revoke permissions at any time without having to modify the plugin.
	// Permission checks are done after startup, as well as when a command is used and 
	// it's been at least 5 minutes since permissions were last checked.
	private boolean canUseDiscordCommands;
	private boolean canUseSpectralCommand;
	
	private boolean pluginLoaded = false;
	
	private boolean memberWidgetLoaded = false;
	
	private boolean firstGameTick;
	
	// Used to ensure you won't see the same phrase multiple times in a row when you use the spectral.
	private int previousPhrasePosition;
	
	private static final String MOD_COMMAND = "!mod";
	
	private static final String RECRUIT_COMMAND = "!recruit";
	
	private static final String SPECTRAL_COMMAND = "!spectral";
	
	// Controls how many times an http request will be resent if 
	// a previous request failed to receive a response.
	// Up to 5 attempts can be made after a failed request.
	private int attemptCount;
	
	private int gameTickCount;
	
	// They'll need to wait 30 seconds after they use one of spectral's command before they can use one again.
	private int coolDown;
	
	private boolean coolDownFinished;
	
	// For the command use, we'll limit the permission checks to once every 5 minutes they're logged in.
	// Doing the permission check every time was just had too slow of a response time.
	private int permissionCheckTimer;
	
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
		clanChatCommandManager.registerSpectralCommandAsync(MOD_COMMAND, this::getMod);
		clanChatCommandManager.registerSpectralCommandAsync(RECRUIT_COMMAND, this::getRecruit);
		clanChatCommandManager.registerSpectralCommandAsync(SPECTRAL_COMMAND, this::getSpectral);
		attemptCount = 0;
		coolDown = -1;
		coolDownFinished = true;
		permissionCheckTimer = -1;
		gameTickCount = 0;
		previousPhrasePosition = -1;
		firstGameTick = false;
		canUseSpectralCommand = false;
		canUseDiscordCommands = false;
	}
	
	@Override
	protected void shutDown() throws Exception
	{
		clanChatCommandManager.unregisterSpectralCommand(MOD_COMMAND);
		clanChatCommandManager.unregisterSpectralCommand(RECRUIT_COMMAND);
		clanChatCommandManager.unregisterSpectralCommand(SPECTRAL_COMMAND);
		chatboxPanelManager.shutdown();
		cmdMgr.shutdown();
		clanChatCommandManager.shutdown();
		spectralPhrases = null;
		httpRequest.shutdown();
		log.info("Spectral Clan Mgmt Plugin stopped!");
	}
	
	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			// This is for the command cooldown. They have to wait 30 seconds after using any of spectral's commands to use another one.
			// This should help to keep the web server from being flooded with requests.
			if (!coolDownFinished)
			{
				coolDown++;
				
				if (coolDown == 50)
				{
					coolDownFinished = true;
					coolDown = -1;
				}
			}
			
			if (permissionCheckTimer != -1)
			{
				permissionCheckTimer++;
			}
			
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
				checkOnStartedOrChanged();
			}
			else
			{
				return;
			}
		}
		else if (client.getGameState() == GameState.LOGIN_SCREEN)
		{
			coolDownFinished = true;
			coolDown = -1;
			permissionCheckTimer = -1;
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
		}
		else
		{
			return;
		}
	}
	
	@Subscribe
	protected void onWidgetLoaded(WidgetLoaded widget)
	{
		if (widget.getGroupId() == CLAN_SETTINGS_INTERFACE)
		{
			if (getLocalPlayerRank(Optional.empty()) == 0)
			{
				config.setAdminScriptURL("");
				config.setSpectralDiscordAppURL("");
				return;
			}
			
			if (isNormalRank(getLocalPlayerRank(Optional.empty())))
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
				if (isAdminRank(getLocalPlayerRank(Optional.empty())))
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
			
			if (getLocalPlayerRank(Optional.empty()) == 0)
			{
				config.setAdminScriptURL("");
				config.setSpectralDiscordAppURL("");
			}
			
			members.clear();
			memberJoinDates.clear();
			
			// This is being set so that whenever a request is posted, if the members list UI isn't open when the response is received,
			// the HttpRequest class will route the results to the exportDone method in this class instead of the clan mgmt button's class.
			memberWidgetLoaded = false;
		}
	}
	
	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		// In case these commands are ever seen outside of the clan chat, don't replace the command text in the chat.
		switch (chatMessage.getType())
		{
			case CLAN_CHAT:
				break;
			default:
				return;
		}
		
		MessageNode message = chatMessage.getMessageNode();
		
		if (message.getValue().trim().equalsIgnoreCase("!recruit") || message.getValue().trim().equalsIgnoreCase("!mod") || message.getValue().trim().equalsIgnoreCase("!spectral"))
		{
			String localPlayer = client.getLocalPlayer().getName();
			String sender = Text.removeTags(message.getName());
			
			// If the local player isn't in Spectral, return (we don't want to replace the text).
			if (getLocalPlayerRank(Optional.of(localPlayer)) == 0)
			{
				return;
			}
			
			// If the sender isn't in Spectral, return (we don't want to replace the text).
			if (getLocalPlayerRank(Optional.of(sender)) == 0)
			{
				return;
			}
			
			updateChat(message);
		}
	}
	
	protected void updateChat(MessageNode message)
	{
		String result = "";
		
		if (message.getValue().trim().equalsIgnoreCase("!recruit"))
		{
			result = "I pinged the recruiters in Discord for help!";
		}
		else if (message.getValue().trim().equalsIgnoreCase("!mod"))
		{
			result = "I pinged the mods in Discord for help!";
		}
		else if (message.getValue().trim().equalsIgnoreCase("!spectral"))
		{
			if (spectralPhrases != null && spectralPhrases.getPhrases() != null && spectralPhrases.getPhrases().length > 0)
			{
				// Get a randomly picked phrase from the phrases array. The phrase will replace the command text in the clan chat for the local player.
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
				result = spectralPhrases.getPhrases()[rn];
			}
			else
			{
				result = "Uh oh, no phrases found! ;^;";
			}
		}
		
		final String cInput = result;
		
		String response = new ChatMessageBuilder()
		.append(ChatColorType.NORMAL)
		.append(cInput)
		.build();
		
		message.setRuneLiteFormatMessage(response);
		client.refreshChat();
	}
	
	// Checks if the string passed to the method is a valid URL. If it's missing, or it's not a valid URL, it'll return false
	// so we can respond and block the execution from continuing before an HttpRequest is created.
	protected boolean checkURL(String URL)
	{
		String scriptURL = "";
		boolean isValid = false;
		
		if (URL.equalsIgnoreCase("normal"))
		{
			scriptURL = config.scriptURL().trim();
		}
		else if (URL.equalsIgnoreCase("admin"))
		{
			scriptURL = config.adminScriptURL().trim();
		}
		else if (URL.equalsIgnoreCase("discord"))
		{
			scriptURL = config.spectralDiscordAppURL().trim();
		}
		
		if (!scriptURL.equals(""))
		{
			// For Spectral's purposes, there's no reason for the protocol to be anything other than http or https.
			Pattern urlRegexPattern = Pattern.compile("^((http|https)://)?([a-zA-Z0-9]+[.])?[a-zA-Z0-9-]+(.[a-zA-Z]{2,6})?(:[0-9]{1,5})?(/[a-zA-Z0-9-._?,'+&%$#=~]*)*$");
			isValid = urlRegexPattern.matcher(scriptURL).matches();
		}
		
		return isValid;
	}
	
	// Called the next game tick after logging in if the config was either changed or the plugin just started up.
	private void checkOnStartedOrChanged()
	{
		if (attemptCount <= 5)
		{
			// Checks if the local Player is a member of Spectral.
			if (!isRankedSpectralMember(Optional.empty()) || !checkURL("normal"))
			{
				pluginLoaded = true;
				canUseSpectralCommand = false;
				canUseDiscordCommands = false;
				
				if (spectralPhrases != null)
				{
					spectralPhrases.setPhrases(null);
				}
				
				return;
			}
			
			if (!pluginLoaded)
			{
				pluginLoaded = true;
				String player = client.getLocalPlayer().getName();
				getPluginData(player, Optional.empty());
			}
		}
		else
		{
			return;
		}
	}
	
	private void getPluginData(String playerName, Optional<SpectralClanMgmtChatboxCommandInput> chatCommandInput)
	{
		if (attemptCount <= 5)
		{
			canUseSpectralCommand = false;
			canUseDiscordCommands = false;
			
			if (!checkURL("normal"))
			{
				config.setSpectralDiscordAppURL("");
				config.setAdminScriptURL("");
				
				if (spectralPhrases != null)
				{
					spectralPhrases.setPhrases(null);
				}
				
				return;
			}
			
			String configLink = "";
			int rank = getLocalPlayerRank(Optional.of(playerName));
			
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
				SpectralClanMgmtChatboxCommandInput chatboxCommandInput = chatCommandInput.orElse(null);
				
				httpRequest.initializeExecutor();
				
				String player = Text.sanitize(playerName);
				
				if (chatboxCommandInput == null)
				{
					httpRequest.getRequestAsyncPluginData(configLink, player, Optional.empty());
				}
				else
				{
					httpRequest.getRequestAsyncPluginData(configLink, player, Optional.of(chatboxCommandInput));
				}
			}
			else
			{
				firstGameTick = false;
				pluginLoaded = false;
			}
		}
		else
		{
			return;
		}
	}
	
	protected void setPluginData(String status, boolean permission, String[] links, String phraseList, Optional<SpectralClanMgmtChatboxCommandInput> chatCommandInput)
	{
		if (status.equalsIgnoreCase("failure"))
		{
			attemptCount++;
		}
		else
		{
			attemptCount = 0;
			permissionCheckTimer = 0;
		}
		
		canUseSpectralCommand = permission;
		
		if (links.length > 0)
		{
			if (links.length == 2)
			{
				String discordURL = links[0].substring(1, links[0].length() - 1);
				String adminURL = links[1].substring(1, links[1].length() - 1);
				config.setSpectralDiscordAppURL(discordURL);
				config.setAdminScriptURL(adminURL);
			}
			else if (links.length == 1)
			{
				String discordURL = links[0].substring(1, links[0].length() - 1);
				config.setSpectralDiscordAppURL(discordURL);
				config.setAdminScriptURL("");
			}
			
			canUseDiscordCommands = true;
		}
		
		if (phraseList.trim() != "")
		{
			String[] phrases = phraseList.split("\\;");
			
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
		
		SpectralClanMgmtChatboxCommandInput chatboxCommandInput = chatCommandInput.orElse(null);
		
		if (chatboxCommandInput != null)
		{
			setSpectral(canUseSpectralCommand, chatboxCommandInput);
		}
		else
		{
			httpRequest.shutdown();
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
	
	protected int getLocalPlayerRank(Optional<String> playerName)
	{
		int rank = 0;
		
		if (client.getClanSettings(0) != null)
		{
			if (client.getClanSettings(0).getName().equals("Spectral"))
			{
				String player = playerName.orElse(client.getLocalPlayer().getName());
				
				if (client.getClanSettings(0).findMember(player) != null)
				{
					rank = client.getClanSettings(0).titleForRank(client.getClanSettings(0).findMember(player).getRank()).getId();
				}
			}
		}
		
		return rank;
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
	
	private boolean isRankedSpectralMember(Optional<Integer> clanrank)
	{
		int rank = clanrank.orElse(1000);
		
		if (rank == 1000)
		{
			rank = getLocalPlayerRank(Optional.empty());
			
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
	// Checks if the local player is a ranked member of Spectral, if the game state is LOGGED_IN, and if the command was entered in the clan chat.
	private boolean commandChecks(int chatType, int rank)
	{
		if (!isRankedSpectralMember(Optional.of(rank)) || !checkGameState() || chatType != 3)
		{
			return false;
		}
		
		return true;
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
		String player = Text.sanitize(client.getLocalPlayer().getName());
		String msg = "";
		boolean flag = false;
		
		if (!commandChecks(chatboxCommandInput.getChatType(), chatboxCommandInput.getRank()))
		{
			chatboxCommandInput.consume();
			return;
		}
		
		if (!checkURL("normal"))
		{
			msg = "Enter a valid URL for Spectral's web app in the plugin's settings first. If it's valid, contact the developer about this issue.";
			flag = true;
		}
		
		if (!checkURL("discord"))
		{
			msg = "The URL for Spectral's Discord Web API is either missing or not valid. Contact the developer about this issue.";
			flag = true;
		}
		
		if (!canUseDiscordCommands)
		{
			msg = "You aren't allowed to use this plugin's commands. Contact the developer if you're a ranked member of the Spectral clan and you see this message.";
			flag = true;
		}
		
		if (!coolDownFinished)
		{
			int waitTime = (int)Math.round(0.6 * (50 - coolDown));
			msg = "You need to wait " + waitTime + " more seconds before you can use one of Spectral's commands again.";
			flag = true;
		}
		
		if (flag)
		{
			chatboxCommandInput.consume();
			final String alert = msg;
			
			clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", alert, null));
		}
		else
		{
			discordCommands(chatboxCommandInput, player);
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
		String player = Text.sanitize(client.getLocalPlayer().getName());
		String msg = "";
		boolean flag = false;
		
		if (!commandChecks(chatboxCommandInput.getChatType(), chatboxCommandInput.getRank()))
		{
			chatboxCommandInput.consume();
			return;
		}
		
		if (!checkURL("normal"))
		{
			msg = "Enter a valid URL for Spectral's web app in the plugin's settings first. If it's valid, contact the developer about this issue.";
			flag = true;
		}
		
		if (!checkURL("discord"))
		{
			msg = "The URL for Spectral's Discord Web API is either missing or not valid. Contact the developer about this issue.";
			flag = true;
		}
		
		if (!coolDownFinished)
		{
			int waitTime = (int)Math.round(0.6 * (50 - coolDown));
			msg = "You need to wait " + waitTime + " more seconds before you can use one of Spectral's commands again.";
			flag = true;
		}
		
		if (flag)
		{
			chatboxCommandInput.consume();
			final String alert = msg;
			
			clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", alert, null));
		}
		else
		{
			discordCommands(chatboxCommandInput, player);
		}
	}
	
	// Player refers to the local player's name.
	// chatboxCommandInput comes from when the spectral command the player sent to the chat.
	private void discordCommands(SpectralClanMgmtChatboxCommandInput chatboxCommandInput, String player)
	{
		if (httpRequest.isReady())
		{
			httpRequest.initializeExecutor();
		}
		
		httpRequest.postRequestAsyncRecruitMod("discord", chatboxCommandInput, player);
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
	This command will only work when the permission flag is set to true. A permission check will be run 
	when a command is used and it's been at least 5 minutes since permissions were last checked. 
	verify that the clan, or the clan member, still has permission to use the the command.
	*/
	private void getSpectral(SpectralClanMgmtChatboxCommandInput chatboxCommandInput, String message)
	{
		String msg = "";
		boolean flag = false;
		
		if (!commandChecks(chatboxCommandInput.getChatType(), chatboxCommandInput.getRank()))
		{
			chatboxCommandInput.consume();
			return;
		}
		
		if (!checkURL("normal"))
		{
			msg = "Enter a valid URL for Spectral's web app in the plugin's settings first. If it's valid, contact the developer about this issue.";
			flag = true;
		}
		
		if (!coolDownFinished)
		{
			int waitTime = (int)Math.round(0.6 * (50 - coolDown));
			msg = "You need to wait " + waitTime + " more seconds before you can use one of Spectral's commands again.";
			flag = true;
		}
		
		if (flag)
		{
			chatboxCommandInput.consume();
			final String alert = msg;
			
			clientThread.invoke(() ->
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", alert, null);
			});
		}
		else
		{
			if (permissionCheckTimer == -1 || permissionCheckTimer >= 500)
			{
				if (httpRequest.isReady())
				{
					String player = client.getLocalPlayer().getName();
					getPluginData(player, Optional.of(chatboxCommandInput));
				}
				else
				{
					chatboxCommandInput.consume();
					
					clientThread.invoke(() ->
					{
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "You need to wait a minute before trying to use this command again.", null);
					});
				}
			}
			else
			{
				setSpectral(canUseSpectralCommand, chatboxCommandInput);
			}
		}
	}
	
	private void setSpectral(boolean permission, SpectralClanMgmtChatboxCommandInput chatboxCommandInput)
	{
		// Don't forget to change it back so the commands can only be entered in the clan chat.
		String msg = "";
		boolean flag = false;
		
		if (!permission)
		{
			msg = "Permissions revoked. You aren't allowed to use the !spectral command right now.";
			flag = true;
		}
		
		if (spectralPhrases == null)
		{
			msg = "Something went wrong. No phrases found for command. Contact the plugin dev about this issue.";
			flag = true;
		}
		
		if (spectralPhrases != null && spectralPhrases.getPhrases() == null)
		{
			msg = "Something went wrong. No phrases found for command. Contact the plugin dev about this issue.";
			flag = true;
		}
		
		if (spectralPhrases != null && spectralPhrases.getPhrases() != null && spectralPhrases.getPhrases().length == 0)
		{
			msg = "Something went wrong. No phrases found for command. Contact the plugin dev about this issue.";
			flag = true;
		}
		
		if (flag)
		{
			// Something went wrong, so we don't want to send the chatboxInput to the chat, we need to send a game message instead.
			chatboxCommandInput.consume();
			final String message = msg;
			
			clientThread.invoke(() ->
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
				httpRequest.shutdown();
			});
		}
		else
		{
			coolDown = 0;
			coolDownFinished = false;
			
			// The chatboxInput can now be sent. Once the ChatMessage event is fired,
			// the command text will be replaced with a randomly selected phrase for anyone who has the plugin installed.
			// The phrase won't be the same for everyone, since the random number is generated locally.
			// Anyone in the clan chat without the plugin will just see the command.
			clientThread.invoke(() ->
			{
				cmdMgr.sendChatboxInput(chatboxCommandInput.getSpectralCommand(), chatboxCommandInput.getChatType(), chatboxCommandInput.getChatTarget());
				httpRequest.shutdown();
			});
		}
	}
	
	// The response from the http requests from both the !recruit and the !mod commands will be passed to this method.
	protected void setRecruitMod(String status, String data, SpectralClanMgmtChatboxCommandInput chatboxCommandInput)
	{
		String msg = "";
		boolean flag = false;
		
		if (!canUseDiscordCommands)
		{
			msg = "You aren't allowed to use this plugin's commands. Contact the developer if you're a ranked member of the Spectral clan and you see this message.";
			flag = true;
		}
		
		if (status.equalsIgnoreCase("failure"))
		{
			flag = true;
			msg = data;
		}
		
		if (flag)
		{
			// Something went wrong, so we don't want to send the chatboxInput to the chat, we need to send a game message instead.
			chatboxCommandInput.consume();
			final String message = msg;
			
			clientThread.invoke(() ->
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
				httpRequest.shutdown();
			});
		}
		else
		{
			coolDown = 0;
			coolDownFinished = false;
			
			// The chatboxInput can now be sent. Once the ChatMessage event is fired,
			// the command text will be replaced with the set phrase the command that was used
			// for anyone who has the plugin installed.
			// Anyone in the clan chat without the plugin will just see the command.
			clientThread.invoke(() ->
			{
				cmdMgr.sendChatboxInput(chatboxCommandInput.getSpectralCommand(), chatboxCommandInput.getChatType(), chatboxCommandInput.getChatTarget());
				httpRequest.shutdown();
			});
		}
	}
}