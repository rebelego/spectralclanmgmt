package com.spectralclanmgmt;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import javax.inject.Inject;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatInput;
import net.runelite.client.events.ConfigChanged;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;
import okhttp3.Response;

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
	private EventBus eventBus;
	
	@Inject
	private SpectralClanMgmtConfig config;
	
	@Inject
	private Client client;
	
	@Inject
	private ClientThread clientThread;
	
	@Inject
	private SpectralChatboxPanel chatboxPanelManager;
	
	@Inject
	private ScheduledExecutorService executor;
	
	@Inject
	private ChatCommandManager chatCommandManager;
	
	@Inject
	private Gson gson;
	
	@Inject
	private OkHttpClient okHttpClient;
	
	@Inject
	private SpectralClanMgmtHttpRequest httpRequest;
	
	@Inject
	private SpectralClanMgmtButton spectralClanMemberButton;
	
	@Getter
	private String adminURL = "";
	
	@Getter
	private String discordURL = "";
	
	private ClanSettings clanSettings;
	
	private HashMap<Integer, ClanMember> sortedClanMembers = new HashMap<Integer, ClanMember>();
	
	private static final int CLAN_SETTINGS_INTERFACE = 690;
	
	private static final int CLAN_SETTINGS_MEMBERS_INTERFACE = 693;
	
	private static final int CLAN_SETTINGS_MEMBERS_INTERFACE_HEADER = 45416450;
	
	private SpectralClanMgmtCommandPhrases spectralPhrases;
	
	// These two booleans are the permission flags that determine if spectral's commands can be used.
	// The values comes from our online web app so we can revoke permissions at any time without having to modify the plugin.
	// Permission checks are done after startup, as well as when a command is used and 
	// it's been at least 5 minutes since permissions were last checked.
	private boolean canUseDiscordCommands;
	private boolean canUseSpectralCommand;
	
	protected boolean validAccessKey;
	
	private boolean pluginLoaded;
	
	private boolean commandProcessing;
	
	private boolean memberWidgetLoaded;
	
	private boolean firstGameTick;
	
	// Used to ensure you won't see the same phrase multiple times in a row when you use the !spectral command.
	private int previousPhrasePosition;
	
	// Controls how many times an http request will be resent if 
	// a previous request failed to receive a response.
	// Up to 5 attempts can be made after a failed request.
	private int attemptCount;
	
	private int gameTickCount;
	
	// They'll need to wait a certain amount of time after they use one of spectral's command before they can use one again.
	private int coolDown;
	
	private boolean coolDownFinished;
	
	/*
	For the command use, we'll limit the permission checks to once every 5 minutes they're logged in.
	Doing the permission check every time makes the commands too slow.
	*/
	private int permissionCheckTimer;
	
	// In case we decide to change one or both of these at some point, we'll set their default values here to make it easier.
	// These are updated from Spectral's web app so we won't need to update the plugin if these are too short/long.
	private int permissionCheckTime = 500;
	
	private int coolDownTime = 50;
	
	// Tracks if the plugin has finished its initial data and permissions loading.
	private boolean ready;
	
	protected GameState gameState;
	
	private final String COMMAND_KEY = "!key";
	
	private final String COMMAND_SPECTRAL = "!spectral";
	
	private final String COMMAND_MOD = "!mod";
	
	private final String COMMAND_RECRUIT = "!recruit";
	
	/*
	Since I can't seem to be able to simply override the open method in the ChatboxTextMenuInput class
	so I can change how the text menu is displayed in the chatbox when it's built,
	my only choice is to remake the classes and include the copyright notices for them.
	Since this class is tiny and needed for the ChatboxPanelManager and ChatboxTextMenuInput,
	I'd rather recreate it here as an inner class rather than add a new class file for it.
	I did end up modifying the open method.
	 */
	public abstract static class SpectralInput
	{
		/*
		 * Copyright (c) 2018 Abex
		 * All rights reserved.
		 *
		 * Redistribution and use in source and binary forms, with or without
		 * modification, are permitted provided that the following conditions are met:
		 *
		 * 1. Redistributions of source code must retain the above copyright notice, this
		 *    list of conditions and the following disclaimer.
		 * 2. Redistributions in binary form must reproduce the above copyright notice,
		 *    this list of conditions and the following disclaimer in the documentation
		 *    and/or other materials provided with the distribution.
		 *
		 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
		 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
		 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
		 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
		 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
		 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
		 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
		 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
		 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
		 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
		 */
		public void open(int lineNum)
		{
		}
		
		public void close()
		{
		}
	}
	
	@Getter
	public class SpectralCommand
	{
		private final String spectralCommand;
		
		private final String player;
		
		private final int rank;
		
		private final ChatInput chatInput;
		
		protected SpectralCommand(String player, int rank, String spectralCommand, ChatInput chatInput)
		{
			this.spectralCommand = spectralCommand;
			this.rank = rank;
			this.player = player;
			this.chatInput = chatInput;
		}
	}
	
	@Provides
	SpectralClanMgmtConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SpectralClanMgmtConfig.class);
	}
	
	@Override
	protected void startUp() throws Exception
	{
		log.info("Spectral Clan Mgmt Plugin started!");
		gameState = client.getGameState();
		spectralPhrases = new SpectralClanMgmtCommandPhrases();
		httpRequest = new SpectralClanMgmtHttpRequest(this, config, client, okHttpClient);
		attemptCount = 0;
		coolDown = -1;
		coolDownFinished = true;
		permissionCheckTimer = -1;
		gameTickCount = 0;
		previousPhrasePosition = -1;
		firstGameTick = false;
		adminURL = "";
		discordURL = "";
		canUseSpectralCommand = false;
		canUseDiscordCommands = false;
		validAccessKey = false;
		ready = false;
		pluginLoaded = false;
		commandProcessing = false;
		spectralClanMemberButton = new SpectralClanMgmtButton(this, chatboxPanelManager, config, client, httpRequest, gson);
		chatCommandManager.registerCommand(COMMAND_SPECTRAL,this::showCommand, this::getCommand);
		chatCommandManager.registerCommand(COMMAND_KEY,this::showCommand, this::getCommand);
		chatCommandManager.registerCommand(COMMAND_MOD,this::showCommand, this::getCommand);
		chatCommandManager.registerCommand(COMMAND_RECRUIT,this::showCommand, this::getCommand);
	}
	
	@Override
	protected void shutDown() throws Exception
	{
		chatboxPanelManager.shutdown();
		adminURL = "";
		discordURL = "";
		canUseSpectralCommand = false;
		canUseDiscordCommands = false;
		spectralPhrases = null;
		httpRequest = null;
		chatCommandManager.unregisterCommand(COMMAND_SPECTRAL);
		chatCommandManager.unregisterCommand(COMMAND_MOD);
		chatCommandManager.unregisterCommand(COMMAND_RECRUIT);
		chatCommandManager.unregisterCommand(COMMAND_KEY);
		log.info("Spectral Clan Mgmt Plugin stopped!");
	}
	
	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState previousGameState;
		
		if (gameState != null)
		{
			previousGameState = gameState;
		}
		else
		{
			previousGameState = gameStateChanged.getGameState();
		}
		
		gameState = gameStateChanged.getGameState();
		boolean throwAwayBoolean = checkGameState(gameState);
		
		return;
	}
	
	@Subscribe
	public void onGameTick(GameTick gameTick) throws ExecutionException, InterruptedException
	{
		if (gameState == GameState.LOGGED_IN)
		{
			// This is for the command cooldown. They have to wait 30 seconds after using any of spectral's commands to use another one.
			// This should help to keep the web server from being flooded with requests.
			if (!coolDownFinished)
			{
				if (coolDown != -1)
				{
					coolDown++;
					
					if (coolDown == coolDownTime)
					{
						coolDownFinished = true;
						coolDown = -1;
					}
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
			
			if (!commandProcessing && ready && attemptCount < 5 && !config.memberKey().equals("") && (permissionCheckTimer >= permissionCheckTime))
			{
				gameTickCount = 1;
				pluginLoaded = false;
			}
			
			if (gameTickCount == 1)
			{
				gameTickCount = -1;
				
				if (!commandProcessing && !pluginLoaded && !config.memberKey().equals("") && attemptCount < 5)
				{
					commandProcessing = true;
					final String player = client.getLocalPlayer().getName();
					int playerRank = 0;
					ClanSettings clan = client.getClanSettings();
					
					if (clan != null && clan.getName().equals("Spectral"))
					{
						if (clan.findMember(player) != null)
						{
							playerRank = clan.titleForRank(clan.findMember(player).getRank()).getId();
						}
					}
					
					final int rank = playerRank;
					
					executor.execute(() ->
					{
						try
						{
							getPluginData(rank, Text.sanitize(player), "onGameTick");
						}
						catch (Exception e)
						{
							return;
						}
					});
				}
				else if (commandProcessing && !config.memberKey().equals("") && !pluginLoaded && attemptCount < 5)
				{
					firstGameTick = false;
					pluginLoaded = false;
				}
				
				if (ready)
				{
					return;
				}
			}
			else
			{
				return;
			}
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
				adminURL = "";
				discordURL = "";
				canUseDiscordCommands = false;
				canUseSpectralCommand = false;
				validAccessKey = false;
			}
			else
			{
				attemptCount = 0;
			}
			
			firstGameTick = false;
			pluginLoaded = false;
			ready = false;
		}
		else if (event.getGroup().equals("spectralclanmgmt") && event.getKey().equals("memberKey"))
		{
			if (config.memberKey().equals(""))
			{
				adminURL = "";
				discordURL = "";
				canUseDiscordCommands = false;
				canUseSpectralCommand = false;
			}
			else
			{
				attemptCount = 0;
			}
			
			validAccessKey = false;
			firstGameTick = false;
			pluginLoaded = false;
			ready = false;
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
				adminURL = "";
				discordURL = "";
				return;
			}
			
			if (!isAdminRank(getLocalPlayerRank(Optional.empty())))
			{
				adminURL = "";
			}
		}
		else if (widget.getGroupId() == CLAN_SETTINGS_MEMBERS_INTERFACE)
		{
			memberWidgetLoaded = true;
			
			getMembersData();
			
			// Since this part of the plugin is meant solely for the admin members of Spectral to use, 
			// we don't want the button to be created if the local player's rank isn't an admin one.
			if (spectralAdminChecks())
			{
				// ** This method was copied from the Wise Old Man Runelite Plugin code and modified to fit this plugin's usage. 
				// All credit for the original code goes to dekvall.
				clientThread.invoke(() ->
				{
					createClanMemberButton(CLAN_SETTINGS_MEMBERS_INTERFACE_HEADER, sortedClanMembers);
					
					if (spectralClanMemberButton.isButtonCreated())
					{
						spectralClanMemberButton.enableButton();
					}
				});
				// **
			}
			else
			{
				sortedClanMembers.clear();
			}
		}
	}
	
	@Subscribe
	protected void onWidgetClosed(WidgetClosed widget)
	{
		// If the Members List widget is closed, reset everything (just in case).
		if (widget.getGroupId() == CLAN_SETTINGS_MEMBERS_INTERFACE)
		{
			if (getLocalPlayerRank(Optional.empty()) == 0)
			{
				adminURL = "";
				discordURL = "";
			}
			
			sortedClanMembers.clear();
			
			// This is being set so that whenever a request is posted, if the members list UI isn't open when the response is received,
			// the HttpRequest class will route the results to the exportDone method in this class instead of the clan mgmt button's class.
			memberWidgetLoaded = false;
			
			if (spectralClanMemberButton.isButtonCreated())
			{
				spectralClanMemberButton.destroyButton();
			}
		}
	}
	
	@Subscribe
	public void onSpectralCommand(SpectralCommand spectralCommand)
	{
		if (!spectralCommand.getSpectralCommand().equals("!key"))
		{
			if (permissionCheckTimer == -1 || permissionCheckTimer >= permissionCheckTime)
			{
				String player = Text.sanitize(spectralCommand.getPlayer());
				int rank = spectralCommand.getRank();
				
				executor.execute(() ->
				{
					try
					{
						boolean result = CompletableFuture.supplyAsync(() -> getPluginData(rank, player, spectralCommand.getSpectralCommand()))
						.thenApplyAsync(permissionResult ->
						{
							if (spectralCommand.getSpectralCommand().equalsIgnoreCase("!spectral"))
							{
								return getSpectral(spectralCommand, permissionResult);
							}
							else
							{
								return getModRecruit(spectralCommand, permissionResult, discordURL);
							}
						})
						.thenApplyAsync(res ->
						{
							commandProcessing = false;
							permissionCheckTimer = 0;
							coolDown = 0;
							coolDownFinished = false;
							return res;
						})
						.join();
						
						if (result)
						{
							spectralCommand.getChatInput().resume();
						}
						else
						{
							spectralCommand.getChatInput().consume();
						}
					}
					finally
					{
						return;
					}
				});
			}
			else
			{
				executor.execute(() ->
				{
					try
					{
						boolean result = CompletableFuture.supplyAsync(() ->
						{
							if (spectralCommand.getSpectralCommand().equalsIgnoreCase("!spectral"))
							{
								return getSpectral(spectralCommand, canUseSpectralCommand);
							}
							else
							{
								return getModRecruit(spectralCommand, canUseDiscordCommands, discordURL);
							}
						})
						.thenApplyAsync(res ->
						{
							commandProcessing = false;
							coolDown = 0;
							coolDownFinished = false;
							return res;
						})
						.join();
						
						if (result)
						{
							spectralCommand.getChatInput().resume();
						}
						else
						{
							spectralCommand.getChatInput().consume();
						}
					}
					finally
					{
						return;
					}
				});
			}
		}
		else
		{
			executor.execute(() ->
			{
				try
				{
					boolean result = CompletableFuture.supplyAsync(() ->
					{
						return getAccessKey(spectralCommand.getPlayer(), config.scriptURL());
					})
					.thenApplyAsync(res ->
					{
						commandProcessing = false;
						coolDown = 0;
						coolDownFinished = false;
						return res;
					})
					.join();
					
					spectralCommand.getChatInput().consume();
				}
				finally
				{
					return;
				}
			});
		}
	}
	
	// This method is a modified version of code provided by aHooder.
	private boolean getCommand(ChatInput chatInput, String message)
	{
		final String[] stringStack = client.getStringStack();
		final int[] intStack = client.getIntStack();
		int intStackCount = client.getIntStackSize();
		final int chatType = intStack[intStackCount - 2];
		int rank = 0;
		
		ClanSettings clan = client.getClanSettings(0);
		
		if (chatType == 3)
		{
			if (clan != null && clan.getName().equals("Spectral"))
			{
				final String player = client.getLocalPlayer().getName();
				rank = clan.titleForRank(clan.findMember(player).getRank()).getId();
				
				if (rank != 0)
				{
					if (isNormalRank(rank) || isAdminRank(rank))
					{
						if (!checkGameState(gameState))
						{
							return true;
						}
						
						if (!checkURL(config.scriptURL()))
						{
							client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "A valid URL is not set in the plugin's settings for Spectral's Web App URL. If the issue persists when the URL is correct and you're a ranked member of Spectral, contact the developer.", null);
							return true;
						}
						
						if (!message.trim().toLowerCase().equals("!key"))
						{
							if (config.memberKey().equals(""))
							{
								client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Your access key is not set in the plugin's settings. If the issue persists when your access key is set and you're a ranked member of Spectral, contact the developer.", null);
								return true;
							}
							
							if (!validAccessKey)
							{
								client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Your access key is not valid. If the issue persists when your access key is valid and you're a ranked member of Spectral, contact the developer.", null);
								return true;
							}
						}
						
						if (!message.trim().toLowerCase().equals("!key"))
						{
							if (!commandProcessing && attemptCount < 5 && coolDownFinished && ready && validAccessKey && !config.memberKey().equals(""))
							{
								commandProcessing = true;
								coolDown = -1;
								coolDownFinished = false;
								
								if (permissionCheckTimer == -1 || permissionCheckTimer >= permissionCheckTime)
								{
									client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "One moment, checking permissions again. Your command will be executed once the check is done.", null);
								}
								
								String command = message.trim().toLowerCase();
								SpectralCommand spectralCommand = new SpectralCommand(player, rank, command, chatInput);
								eventBus.post(spectralCommand);
							}
							else
							{
								// This could occur if they try to use a command right after the logging in when the plugin is still pulling the data.
								if (commandProcessing && !ready)
								{
									client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "You can't use Spectral's commands right now. Wait a minute before trying again.", null);
								}
								else if (attemptCount >= 5)
								{
									client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "The command could not be completed because your permissions couldn't be verified. Make sure a valid URL and access key is set in the plugin's settings for Spectral's Web App URL. If the issue persists even though the URL and access key are correct, and you're a ranked member of Spectral, contact the developer.", null);
								}
								else if (!coolDownFinished)
								{
									String msg = "";
									
									if (coolDown != -1)
									{
										int waitTime = (int)Math.round(0.6 * (coolDownTime - coolDown));
										
										if (waitTime > 1 || waitTime < 1)
										{
											msg = "You need to wait " + waitTime + " more seconds before you can use one of Spectral's commands again.";
										}
										else if (waitTime == 1)
										{
											msg = "You need to wait " + waitTime + " more second before you can use one of Spectral's commands again.";
										}
									}
									else
									{
										msg = "You need to wait for the previous command to finish before you can use one of Spectral's commands again.";
									}
									
									client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
								}
								else if (!ready)
								{
									client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "The plugin's data hasn't finished loading yet. Wait a minute before trying again.", null);
								}
								else if (!validAccessKey || config.memberKey().equals(""))
								{
									client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "The command could not be completed because your access key is invalid. Set a valid access key in the plugin's settings before trying again. If the issue persists even though the access key is correct, and you're a ranked member of Spectral, contact the developer.", null);
								}
							}
						}
						else
						{
							if (!commandProcessing && attemptCount < 5 && coolDownFinished && !validAccessKey)
							{
								commandProcessing = true;
								coolDown = -1;
								coolDownFinished = false;
								
								String command = message.trim().toLowerCase();
								SpectralCommand spectralCommand = new SpectralCommand(player, rank, command, chatInput);
								eventBus.post(spectralCommand);
							}
							else
							{
								if (commandProcessing)
								{
									client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "You can't use Spectral's commands right now. Wait a minute before trying again.", null);
								}
								else if (attemptCount >= 5)
								{
									client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "The command could not be completed because your permissions couldn't be verified. Make sure a valid URL and access key is set in the plugin's settings for Spectral's Web App URL. If the issue persists even though the URL and access key are correct, and you're a ranked member of Spectral, contact the developer.", null);
								}
								else if (!coolDownFinished)
								{
									String msg = "";
									
									if (coolDown != -1)
									{
										int waitTime = (int)Math.round(0.6 * (coolDownTime - coolDown));
										
										if (waitTime > 1 || waitTime < 1)
										{
											msg = "You need to wait " + waitTime + " more seconds before you can use one of Spectral's commands again.";
										}
										else if (waitTime == 1)
										{
											msg = "You need to wait " + waitTime + " more second before you can use one of Spectral's commands again.";
										}
									}
									else
									{
										msg = "You need to wait for the previous command to finish before you can use one of Spectral's commands again.";
									}
									
									client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
								}
								else if (validAccessKey)
								{
									client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "You already have a valid access key, there's no reason for you to use this command.", null);
								}
							}
						}
					}
				}
			}
			
			if (clan == null || (clan != null && !clan.getName().equals("Spectral")) || rank == 0 || (rank != 0 && !isNormalRank(rank) && !isAdminRank(rank)))
			{
				adminURL = "";
				discordURL = "";
				canUseSpectralCommand = false;
				canUseDiscordCommands = false;
				validAccessKey = false;
				pluginLoaded = true;
				commandProcessing = false;
				ready = true;
				attemptCount = 5;
				
				if (spectralPhrases != null)
				{
					spectralPhrases.setPhrases(null);
				}
			}
		}
		
		return true;
	}
	
	// This method is a modified version of code provided by aHooder.
	private void showCommand(ChatMessage chatMessage, String message)
	{
		// In case these commands are ever seen outside of the clan chat, don't replace the command text in the chat.
		if (chatMessage.getType() != ChatMessageType.CLAN_CHAT)
		{
			return;
		}
		
		if (config.memberKey().equals(""))
		{
			return;
		}
		
		if (!validAccessKey)
		{
			return;
		}
		
		MessageNode messageNode = chatMessage.getMessageNode();
		
		if (messageNode.getValue().trim().equalsIgnoreCase("!recruit") || messageNode.getValue().trim().equalsIgnoreCase("!mod") || messageNode.getValue().trim().equalsIgnoreCase("!spectral"))
		{
			final String sender = Text.removeTags(messageNode.getName());
			int playerRank = getLocalPlayerRank(Optional.empty());
			int senderRank = getLocalPlayerRank(Optional.of(sender));
			
			if (playerRank == 0 || senderRank == 0)
			{
				return;
			}
			
			updateChat(messageNode);
		}
		else
		{
			return;
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
	
	protected boolean spectralAdminChecks()
	{
		if (config.memberKey().equals(""))
		{
			return false;
		}
		
		if (!validAccessKey)
		{
			return false;
		}
		
		clanSettings = client.getClanSettings(0);
		
		if (clanSettings.getName().equals("Spectral"))
		{
			// Since this part of the plugin is meant solely for the admin members of Spectral to use, 
			// we don't want the button to be created if the local player's rank isn't an admin one and they don't have their access key.
			if (isAdminRank(getLocalPlayerRank(Optional.empty())))
			{
				if (checkURL(adminURL))
				{
					if (sortedClanMembers != null)
					{
						if (sortedClanMembers.size() > 0)
						{
							return true;
						}
					}
				}
			}
		}
		
		return false;
	}
	
	// Checks if the string passed to the method is a valid URL. If it's missing, or it's not a valid URL, it'll return false
	// so we can respond and block the execution from continuing before an HttpRequest is created.
	protected static boolean checkURL(String URL)
	{
		String scriptURL = URL.trim();
		boolean isValid = false;
		
		if (!scriptURL.equals(""))
		{
			// For Spectral's purposes, there's no reason for the protocol to be anything other than http or https.
			Pattern urlRegexPattern = Pattern.compile("^((http|https)://)?([a-zA-Z0-9]+[.])?[a-zA-Z0-9-]+(.[a-zA-Z]{2,6})?(:[0-9]{1,5})?(/[a-zA-Z0-9-._?,'+&%$#=~]*)*$");
			isValid = urlRegexPattern.matcher(scriptURL).matches();
		}
		
		return isValid;
	}
	
	private Boolean getPluginData(int rank, String player, String src)
	{
		return CompletableFuture.supplyAsync(() ->
		{
			if (attemptCount >= 5)
			{
				return "attempt-fail;The plugin has made the max allowed attempts to retrieve its data.";
			}
			
			if (rank == 0 || (!isNormalRank(rank) && !isAdminRank(rank)) || !checkURL(config.scriptURL()))
			{
				return "perma-fail;You don't have permission to use this plugin.";
			}
			else if (config.memberKey().equals(""))
			{
				return "perma-fail;Your access key is not set. If you're a ranked member of Spectral, use the !key command to get your access key. If the issue persists, contact the developer.";
			}
			
			if (isNormalRank(rank))
			{
				adminURL = "";
			}
			
			String configLink = "";
			
			if (isNormalRank(rank))
			{
				configLink = "discord";
			}
			else if (isAdminRank(rank))
			{
				configLink = "both";
			}
			
			return configLink;
		})
		.thenApplyAsync(configLink ->
		{
			return httpRequest.getRequestAsyncPluginData(configLink, player);
		})
		.thenApplyAsync(res ->
		{
			String[] result = res.split("\\;");
			
			if (result[0].equalsIgnoreCase("perma-fail"))
			{
				adminURL = "";
				discordURL = "";
				
				if (spectralPhrases != null)
				{
					spectralPhrases.setPhrases(null);
				}
				
				canUseSpectralCommand = false;
				canUseDiscordCommands = false;
				validAccessKey = false;
				pluginLoaded = true;
				attemptCount = 5;
				ready = true;
			}
			else if (result[0].equalsIgnoreCase("attempt-fail"))
			{
				pluginLoaded = true;
				ready = true;
			}
			else if (result[0].equalsIgnoreCase("failure"))
			{
				attemptCount = 5;
				pluginLoaded = true;
				ready = true;
			}
			else if (result[0].equalsIgnoreCase("resp-failure"))
			{
				attemptCount++;
				
				if (attemptCount < 5)
				{
					ready = false;
					firstGameTick = false;
					pluginLoaded = false;
				}
				else
				{
					pluginLoaded = true;
					ready = true;
				}
			}
			else if (result[0].equalsIgnoreCase("success"))
			{
				attemptCount = 0;
				pluginLoaded = true;
				ready = true;
			}
			
			if (src.equalsIgnoreCase("onGameTick"))
			{
				commandProcessing = false;
				clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", result[1], null));
			}
			else if (src.equalsIgnoreCase("!spectral") && result[0].equalsIgnoreCase("success"))
			{
				return canUseSpectralCommand;
			}
			else if ((src.equalsIgnoreCase("!mod") || src.equalsIgnoreCase("!recruit")) && result[0].equalsIgnoreCase("success"))
			{
				return canUseDiscordCommands;
			}
			
			return false;
			
		}).join();
	}
	
	protected String setPluginData(Response response) throws IOException
	{
		if (spectralPhrases != null)
		{
			spectralPhrases.setPhrases(null);
		}
		
		if (!response.isSuccessful())
		{
			throw new IOException("An error occurred: " + response);
		}
		
		JsonObject resp;
		String stat = "";
		String res = "";
		
		try
		{
			resp = gson.fromJson(response.body().charStream(), JsonObject.class);
		}
		catch (JsonSyntaxException ex)
		{
			throw new IOException("An error occurred when attempting to deserialize the response body.", ex);
		}
		
		if (resp == null)
		{
			validAccessKey = false;
			canUseSpectralCommand = false;
			canUseDiscordCommands = false;
			discordURL = "";
			adminURL = "";
			
			return "resp-failure;Something went wrong and the plugin's data could not be retrieved. Contact the developer about this issue.";
		}
		
		stat = resp.get("status").getAsString();
		JsonArray permission = resp.get("permission").getAsJsonArray();
		int permissionTime = resp.get("permissionTime").getAsInt();
		int downTime = resp.get("downTime").getAsInt();
		
		validAccessKey = permission.get(0).getAsBoolean();
		
		if (validAccessKey)
		{
			canUseSpectralCommand = permission.get(1).getAsBoolean();
			canUseDiscordCommands = permission.get(2).getAsBoolean();
			String phraseList = resp.get("phrases").getAsString();
			JsonArray conLink = resp.get("configLink").getAsJsonArray();
			String[] links = new String[conLink.size()];
			
			if (conLink.size() == 1)
			{
				links[0] = String.valueOf(conLink.get(0));
				adminURL = "";
				discordURL = links[0].substring(1, links[0].length() - 1);
			}
			else if (conLink.size() == 2)
			{
				links[0] = String.valueOf(conLink.get(0));
				links[1] = String.valueOf(conLink.get(1));
				discordURL = links[0].substring(1, links[0].length() - 1);
				adminURL = links[1].substring(1, links[1].length() - 1);
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
		}
		else
		{
			canUseSpectralCommand = false;
			canUseDiscordCommands = false;
			discordURL = "";
			adminURL = "";
		}
		
		if (permissionTime > 0)
		{
			permissionCheckTime = permissionTime;
		}
		
		if (downTime > 0)
		{
			coolDownTime = downTime;
		}
		
		permissionCheckTimer = 0;
		
		if (stat.equalsIgnoreCase("failure"))
		{
			String reason = resp.get("reason").getAsString();
			return stat + ";" + reason;
		}
		
		return "success;The plugin's data was successfully retrieved and set.";
	}
	
	// Clears and populates the members and membersJoinDates hashmaps with sorted values.
	// These will be used after this finishes.
	private void getMembersData()
	{
		clanSettings = client.getClanSettings();
		
		// We clear and set these every time this is run, because it's only called when the members list UI is loaded.
		sortedClanMembers.clear();
		
		if (clanSettings != null)
		{
			List<ClanMember> clanMembers = clanSettings.getMembers();
			
			Collections.sort(clanMembers, (m1, m2) -> m1.getName().compareToIgnoreCase(m2.getName()));
			
			int i = 0;
			
			// Now that we've got a sorted list of member names, we'll put them into a hashmap, 
			// using i to act as an index for the members. This will be used later in the button's class.
			for (ClanMember cm : clanMembers)
			{
				sortedClanMembers.put(i, cm);
				i++;
			}
		}
	}
	
	// ** This method was copied from the Wise Old Man Runelite Plugin code and rewritten to fit this plugin's usage. 
	// All credit for the original code goes to dekvall.
	private void createClanMemberButton(int w, HashMap<Integer, ClanMember> clanmembers)
	{
		ClanSettings clanSettings = client.getClanSettings(0);
		spectralClanMemberButton.createButton(w, clanmembers, clanSettings);
	}
	// **
	
	protected int getLocalPlayerRank(Optional<String> playerName)
	{
		int rank = 0;
		
		if (client.getClanSettings(0) != null && client.getClanSettings(0).getName().equals("Spectral"))
		{
			String player = playerName.orElse(client.getLocalPlayer().getName());
			
			if (client.getClanSettings(0).findMember(player) != null)
			{
				rank = client.getClanSettings(0).titleForRank(client.getClanSettings(0).findMember(player).getRank()).getId();
			}
		}
		
		return rank;
	}
	
	protected static boolean isAdminRank(int rank)
	{
		// The clan's admin ranks. The numbers are the key values in the ranks enum for the 
		// Owner, Deputy Owner, Moderator, and Completionist (Recruiter) ranks.
		ArrayList<Integer> adminRanks = new ArrayList<>(Arrays.asList(-4, -3, 264, 252));
		
		if (adminRanks.contains(rank))
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
		// The clan's non-admin ranks. The numbers are the key values in the ranks enum for the 
		// Gnome Child, Lieutenant, Captain, General, Colonel, Brigadier, Admiral, Marshal, Astral, Soul, 
		// Sapphire, Emerald, Ruby, Diamond, Dragonstone, Onyx, Zenyte, Paladin, Skiller, Armadylean, TzKal, and Assistant ranks.
		ArrayList<Integer> normalRanks = new ArrayList<>(Arrays.asList(9, 35, 37, 39, 43, 44, 45, 46, 58, 60, 65, 66, 67, 68, 69, 70, 71, 111, 143, 161, 179, 227));
		
		if (normalRanks.contains(rank))
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	private boolean checkGameState(GameState game)
	{
		if (game == GameState.LOGIN_SCREEN)
		{
			attemptCount = 0;
			coolDown = -1;
			coolDownFinished = true;
			permissionCheckTimer = -1;
			firstGameTick = false;
			gameTickCount = 0;
			ready = false;
			pluginLoaded = false;
			commandProcessing = false;
			adminURL = "";
			discordURL = "";
			canUseSpectralCommand = false;
			canUseDiscordCommands = false;
			previousPhrasePosition = -1;
			
			if (spectralPhrases != null)
			{
				spectralPhrases.setPhrases(null);
			}
		}
		
		if (game == GameState.LOGGED_IN)
		{
			return true;
		}
		else
		{
			return false;
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
	This command will only work when the permission flag is set to true. A permission check will be run 
	when a command is used and it's been at least 5 minutes since permissions were last checked. 
	verify that the clan, or the clan member, still has permission to use the the command.
	*/
	private Boolean getSpectral(SpectralCommand spectralCommand, boolean perm)
	{
		String player = Text.sanitize(spectralCommand.getPlayer());
		boolean flag = false;
		String msg = "";
		
		if (!perm || config.memberKey().equals("") || !validAccessKey)
		{
			msg = "You aren't allowed to use this plugin's commands. Contact the developer if you're a ranked member of the Spectral clan and you see this message.";
			flag = true;
		}
		else if (spectralPhrases == null || (spectralPhrases != null && spectralPhrases.getPhrases() == null) || (spectralPhrases != null && spectralPhrases.getPhrases() != null && spectralPhrases.getPhrases().length == 0))
		{
			msg = "No phrases found for the command. Contact the developer about this issue.";
			flag = true;
		}
		
		if (flag)
		{
			final String message = msg;
			clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null));
			return false;
		}
		else
		{
			return true;
		}
	}
	
	/* 
	The !mod and !recruit command can be used by any of Spectral's ranked members.
	The !mod command can be used to send an alert to the Moderators channel in Spectral's Discord server
	in the event that someone is causing trouble in the clan chat and none of the Moderators are online to handle it.
	The !recruit command can be used to send an alert to the Recruiters channel in Spectral's Discord server 
	(Moderators also have access to this channel and would be pinged),
	in the event that someone wants to join the clan and none of the the admins are online.
	Most of our members are resistant to using or keeping Discord open while playing, 
	even though they are a part of our Discord server. The Mods aren't always able to have
	at least one of them online in-game every hour of the day, but all of the Mods have Discord on their phones
	so they can be reached via Discord even when not playing.
	This command will only work when used in the clan chat channel.
	 */
	private Boolean getModRecruit(SpectralCommand spectralCommand, boolean perm, String discordLink)
	{
		String player = Text.sanitize(spectralCommand.getPlayer());
		String msg = "";
		boolean flag = false;
		
		if (!checkURL(discordLink))
		{
			msg = "The URL for Spectral's Discord Web App is either missing or not valid. Contact the developer about this issue.";
			flag = true;
		}
		else if (!perm || config.memberKey().equals("") || !validAccessKey)
		{
			msg = "You aren't allowed to use this plugin's commands. Contact the developer if you're a ranked member of the Spectral clan and you see this message.";
			flag = true;
		}
		
		if (flag)
		{
			final String message = msg;
			clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null));
			return false;
		}
		else
		{
			CompletableFuture<Boolean> future = httpRequest.postRequestAsyncRecruitMod("discord", spectralCommand.getSpectralCommand(), player)
			.thenApply(result ->
			{
				String[] results = result.split("\\;");
				
				if (!results[0].equalsIgnoreCase("success"))
				{
					clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", results[1], null));
					return false;
				}
				
				return true;
			});
			
			return future.join();
		}
	}
	
	// The response from the http requests from both the !recruit and the !mod commands will be passed to this method.
	protected String setModRecruit(Response response) throws IOException
	{
		if (!response.isSuccessful())
		{
			throw new IOException("An error occurred: " + response);
		}
		
		JsonObject resp;
		String stat = "";
		String dat = "";
		
		try
		{
			resp = gson.fromJson(response.body().charStream(), JsonObject.class);
		}
		catch (JsonSyntaxException ex)
		{
			throw new IOException("An error occurred when attempting to deserialize the response body.", ex);
		}
		
		if (resp == null)
		{
			stat = "failure";
			dat = "Something went wrong and the command could not be completed. Contact the developer about this issue.";
		}
		else
		{
			stat = resp.get("status").getAsString();
			dat = resp.get("data").getAsString();
		}
		
		return stat + ";" + dat;
	}
	
	private Boolean getAccessKey(String playerName, String url)
	{
		String player = Text.sanitize(playerName);
		
		if (!checkURL(url))
		{
			clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "The URL for Spectral's Web App is either missing or not valid. Contact the developer about this issue.", null));
			return false;
		}
		else
		{
			CompletableFuture<Boolean> future = httpRequest.postRequestAsyncAccessKey("get-key", player)
			.thenApply(result ->
			{
				String[] results = result.split("\\;");
				
				clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", results[1], null));
				
				if (!results[0].equalsIgnoreCase("success"))
				{
					return false;
				}
				
				return true;
			});
			
			return future.join();
		}
	}
	
	protected String setAccessKey(Response response) throws IOException
	{
		if (!response.isSuccessful())
		{
			throw new IOException("An error occurred: " + response);
		}
		
		JsonObject resp;
		String stat = "";
		String dat = "";
		
		try
		{
			resp = gson.fromJson(response.body().charStream(), JsonObject.class);
		}
		catch (JsonSyntaxException ex)
		{
			throw new IOException("An error occurred when attempting to deserialize the response body.", ex);
		}
		
		if (resp == null)
		{
			validAccessKey = false;
			stat = "failure";
			dat = "Something went wrong and your access key could not be found. Contact the developer about this issue.";
		}
		else
		{
			stat = resp.get("status").getAsString();
			dat = resp.get("data").getAsString();
		}
		
		String result = "";
		
		if (stat.equalsIgnoreCase("success"))
		{
			if (!dat.equals(""))
			{
				config.setMemberKey(dat);
				validAccessKey = true;
				result = stat + ";Your access key was set.";
			}
			else
			{
				validAccessKey = false;
				config.setMemberKey("");
				result = stat + ";Something went wrong during the access key request. If you're a ranked member of Spectral, contact the developer about this issue.";
			}
		}
		else
		{
			result = stat + ";" + dat;
			validAccessKey = false;
			config.setMemberKey("");
		}
		
		return result;
	}
}