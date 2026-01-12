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
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatInput;
import net.runelite.client.events.ConfigChanged;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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
	
	private ClanSettings clanSettings;
	
	// The clan's admin ranks. The numbers are the key values in the ranks enum for the 
	// Owner, Deputy Owner, Moderator, and Completionist (Recruiter) ranks.
	protected static ArrayList<Integer> adminRanks = new ArrayList<>(Arrays.asList(-4, -3, 264, 252));
	
	// The clan's non-admin ranks. The numbers are the key values in the ranks enum for the 
	// Gnome Child, Lieutenant, Captain, General, Colonel, Brigadier, Admiral, Marshal, Astral, Soul, 
	// Sapphire, Emerald, Ruby, Diamond, Dragonstone, Onyx, Zenyte, Paladin, Skiller, Armadylean, TzKal, and Assistant ranks.
	protected static ArrayList<Integer> normalRanks = new ArrayList<>(Arrays.asList(9, 35, 37, 39, 43, 44, 45, 46, 58, 60, 65, 66, 67, 68, 69, 70, 71, 111, 143, 161, 179, 227));
	
	private static final int CLAN_SETTINGS_MEMBERS_INTERFACE = 693;
	
	private static final int CLAN_SETTINGS_MEMBERS_INTERFACE_HEADER = 45416450;
	
	@Getter
	private String adminURL = "";
	
	private boolean canUseDiscordCommands;
	private boolean canUseSpectralCommand;
	
	protected boolean validAccessKey;
	
	private boolean pluginLoaded;
	
	private boolean commandProcessing;
	
	private boolean memberWidgetLoaded;
	
	private boolean firstGameTick;

	// Controls how many times an http request will be resent if 
	// a previous request failed to receive a response.
	// Up to 5 attempts can be made after a failed request.
	private int attemptCount;
	
	private int gameTickCount;
	
	// They'll need to wait a certain amount of time after they use one of spectral's command before they can use one again.
	private int coolDown;
	
	private boolean coolDownFinished;
	
	private int coolDownTime = 50;
	
	// Tracks if the plugin has finished its initial data and permissions loading.
	private boolean ready;
	
	protected GameState gameState;
	
	protected Boolean reg;
	
	private final String COMMAND_KEY = "!key";
	
	private final String COMMAND_ADDME = "!addme";
	
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
		
		private final String acctHash;
		
		private final int rank;
		
		private final ChatInput chatInput;
		
		protected SpectralCommand(String player, String acctHash, int rank, String spectralCommand, ChatInput chatInput)
		{
			this.spectralCommand = spectralCommand;
			this.rank = rank;
			this.player = player;
			this.acctHash = acctHash;
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
		httpRequest = new SpectralClanMgmtHttpRequest(this, config, client, okHttpClient);
		attemptCount = 0;
		coolDown = -1;
		coolDownFinished = true;
		gameTickCount = 0;
		firstGameTick = false;
		adminURL = "";
		canUseSpectralCommand = false;
		canUseDiscordCommands = false;
		validAccessKey = false;
		ready = false;
		pluginLoaded = false;
		commandProcessing = false;
		spectralClanMemberButton = new SpectralClanMgmtButton(this, chatboxPanelManager, config, client, httpRequest, gson);
		chatCommandManager.registerCommand(COMMAND_ADDME,null, this::getCommand);
		chatCommandManager.registerCommand(COMMAND_KEY,null, this::getCommand);
		chatCommandManager.registerCommand(COMMAND_MOD,null, this::getCommand);
		chatCommandManager.registerCommand(COMMAND_RECRUIT,null, this::getCommand);
		reg = true;
	}
	
	@Override
	protected void shutDown() throws Exception
	{
		chatboxPanelManager.shutdown();
		httpRequest = null;
		chatCommandManager.unregisterCommand(COMMAND_MOD);
		chatCommandManager.unregisterCommand(COMMAND_RECRUIT);
		chatCommandManager.unregisterCommand(COMMAND_KEY);
		chatCommandManager.unregisterCommand(COMMAND_ADDME);
		log.info("Spectral Clan Mgmt Plugin stopped!");
	}
	
	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		gameState = gameStateChanged.getGameState();
		boolean isGameStateLoggedIn = checkGameState(gameState);
		
		return;
	}
	
	@Subscribe
	public void onGameTick(GameTick gameTick) throws ExecutionException, InterruptedException
	{
		if (gameState == GameState.LOGGED_IN)
		{
			if (client.getLocalPlayer() != null)
			{
				if (client.getLocalPlayer().getName() != null)
				{
					if (client.getClanSettings() != null)
					{
						// This is for the command use cooldown.
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
						
						if (ready)
						{
							return;
						}
						
						if (!commandProcessing && !pluginLoaded && !config.scriptURL().equals("") && !config.memberKey().equals("") && attemptCount < 5)
						{
							commandProcessing = true;
							final String acctHash = String.valueOf(client.getAccountHash());
							int playerRank = 0;
							ClanSettings clan = client.getClanSettings();
							final String player = client.getLocalPlayer().getName();
							
							if (clan != null && clan.getName().equals("Spectral"))
							{
								final ClanMember member = clan.findMember(player);
								
								if (member != null)
								{
									playerRank = clan.titleForRank(member.getRank()).getId();
								}
								else
								{
									commandProcessing = false;
									return;
								}
							}
							else
							{
								commandProcessing = false;
								return;
							}
							
							final int rank = playerRank;
							
							executor.execute(() ->
							{
								try
								{
									getPluginData(rank, Text.sanitize(player), acctHash);
								}
								catch (Exception e)
								{
									return;
								}
							});
						}
					}
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
		if (event.getGroup().equals("spectralclanmgmt") && event.getKey().equals("memberKey"))
		{
			if (!config.memberKey().equals(""))
			{
				attemptCount = 0;
				pluginLoaded = false;
			}
			
			validAccessKey = false;
		}
	}
	
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widget)
	{
		if (widget.getGroupId() != CLAN_SETTINGS_MEMBERS_INTERFACE || config.memberKey().equals("") || !validAccessKey || !checkURL(config.scriptURL()) || !reg)
		{
			return;
		}
		
		clanSettings = client.getClanSettings(0);
		
		if (clanSettings == null || !clanSettings.getName().equals("Spectral") || clanSettings.getMembers().isEmpty())
		{
			return;
		}
		
		int rank = 0;
		
		String player = client.getLocalPlayer().getName();
		ClanMember member = clanSettings.findMember(player);
		
		if (member != null)
		{
			rank = clanSettings.titleForRank(member.getRank()).getId();
		}
		
		// Since this part of the plugin is meant solely for the admin members of Spectral to use, 
		// we don't want the button to be created if the local player's rank isn't an admin one.
		if (rank == 0 || !adminRanks.contains(rank))
		{
			return;
		}
		
		memberWidgetLoaded = true;
		
		// ** This method was copied from the Wise Old Man Runelite Plugin code and modified to fit this plugin's usage. 
		// All credit for the original code goes to dekvall.
		clientThread.invoke(() ->
		{
			createClanMemberButton(CLAN_SETTINGS_MEMBERS_INTERFACE_HEADER);
			
			if (spectralClanMemberButton.isButtonCreated())
			{
				eventBus.register(spectralClanMemberButton);
				spectralClanMemberButton.enableButton();
			}
		});
		// **
	}
	
	@Subscribe
	protected void onWidgetClosed(WidgetClosed widget)
	{
		if (widget.getGroupId() == CLAN_SETTINGS_MEMBERS_INTERFACE)
		{
			// This is being set so that whenever a request is posted, if the members list UI isn't open when the response is received,
			// the HttpRequest class will route the results to the exportDone method in this class instead of the clan mgmt button's class.
			memberWidgetLoaded = false;
			
			if (spectralClanMemberButton.isButtonCreated())
			{
				eventBus.unregister(spectralClanMemberButton);
				spectralClanMemberButton.destroyButton();
			}
		}
	}
	
	@Subscribe
	public void onSpectralCommand(SpectralCommand spectralCommand)
	{
		spectralCommand.getChatInput().consume(); // Input is always consumed, so I moved it up here.
		
		if (!spectralCommand.getSpectralCommand().equalsIgnoreCase(COMMAND_KEY) && !spectralCommand.getSpectralCommand().equalsIgnoreCase(COMMAND_ADDME))
		{
			executor.execute(() ->
			{
				try
				{
					boolean result = CompletableFuture.supplyAsync(() ->
					{
						if (spectralCommand.getSpectralCommand().equalsIgnoreCase(COMMAND_MOD) || spectralCommand.getSpectralCommand().equalsIgnoreCase(COMMAND_RECRUIT))
						{
							return getModRecruit(spectralCommand);
						}
						else
						{
							return false;
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
						if (spectralCommand.getSpectralCommand().equalsIgnoreCase(COMMAND_MOD) || spectralCommand.getSpectralCommand().equalsIgnoreCase(COMMAND_RECRUIT))
						{
							String com = "";
							
							if (spectralCommand.getSpectralCommand().equalsIgnoreCase(COMMAND_MOD))
							{
								com = "You've pinged the Mods in Discord.";
							}
							else if (spectralCommand.getSpectralCommand().equalsIgnoreCase(COMMAND_RECRUIT))
							{
								com = "You've pinged the Recruiters in Discord.";
							}
							
							final String chatText = com;
							clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", chatText, null));
						}
					}
				}
				finally
				{
					return;
				}
			});
			
		}
		else if (spectralCommand.getSpectralCommand().equalsIgnoreCase(COMMAND_KEY))
		{
			executor.execute(() ->
			{
				try
				{
					boolean result = CompletableFuture.supplyAsync(() ->
					{
						return getAccessKey(spectralCommand);
					})
					.thenApplyAsync(res ->
					{
						commandProcessing = false;
						coolDown = 0;
						coolDownFinished = false;
						return res;
					})
					.join();
				}
				finally
				{
					return;
				}
			});
		}
		else if (spectralCommand.getSpectralCommand().equalsIgnoreCase(COMMAND_ADDME))
		{
			executor.execute(() ->
			{
				try
				{
					boolean result = CompletableFuture.supplyAsync(() ->
					{
						return registerPlayerID(spectralCommand);
					})
					.thenApplyAsync(res ->
					{
						commandProcessing = false;
						coolDown = 0;
						coolDownFinished = false;
						return res;
					})
					.join();
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
		final int[] intStack = client.getIntStack();
		int intStackCount = client.getIntStackSize();
		final int chatType = intStack[intStackCount - 2];
		
		if (chatType != 3 || !checkGameState(gameState))
		{
			return true;
		}
		
		int rank = 0;
		final String player = client.getLocalPlayer().getName();
		
		clanSettings = client.getClanSettings(0);
		
		if (clanSettings != null && clanSettings.getName().equals("Spectral"))
		{
			final ClanMember member = clanSettings.findMember(player);
			
			if (member != null)
			{
				rank = clanSettings.titleForRank(member.getRank()).getId();
			}
			
			if (rank != 0)
			{
				final String acctHash = String.valueOf(client.getAccountHash());
				
				if (normalRanks.contains(rank) || adminRanks.contains(rank))
				{
					String msg = "";
					
					if (!checkURL(config.scriptURL()))
					{
						msg = "A valid URL for Spectral's web app isn't set in the plugin's settings. If the issue persists when a valid URL is set and you're a ranked member of Spectral, contact the developer.";
					}
					
					if (!message.trim().toLowerCase().equals(COMMAND_KEY) && !message.trim().toLowerCase().equals(COMMAND_ADDME))
					{
						if (!reg)
						{
							msg = "Your player ID hasn't been registered. If you're a ranked member of Spectral, use the !addme command in the clan chat to register your player ID first. If the issue persists afterwards, contact the developer.";
						}
						
						if (!validAccessKey || config.memberKey().equals(""))
						{
							msg = "Your access key either isn't valid or isn't set. If you're a ranked member of Spectral and you've registered your player ID, use the !key command in the clan chat to get your access key first. If the issue persists after your access key is set, contact the developer.";
						}
					}
					
					if (msg.trim().equals(""))
					{
						if (!message.trim().toLowerCase().equals(COMMAND_KEY) && !message.trim().toLowerCase().equals(COMMAND_ADDME))
						{
							if (!commandProcessing && attemptCount < 5 && coolDownFinished && pluginLoaded && ready && validAccessKey && reg && canUseDiscordCommands)
							{
								commandProcessing = true;
								coolDown = -1;
								coolDownFinished = false;
								
								SpectralCommand spectralCommand = new SpectralCommand(player, acctHash, rank, message.trim(), chatInput);
								eventBus.post(spectralCommand);
								return true;
							}
						}
						else if (message.trim().toLowerCase().equals(COMMAND_KEY))
						{
							if (!commandProcessing && attemptCount < 5 && coolDownFinished && !validAccessKey && reg)
							{
								commandProcessing = true;
								coolDown = -1;
								coolDownFinished = false;
								
								String command = message.trim().toLowerCase();
								SpectralCommand spectralCommand = new SpectralCommand(player, acctHash, rank, command, chatInput);
								eventBus.post(spectralCommand);
								return true;
							}
						}
						else if (message.trim().toLowerCase().equals(COMMAND_ADDME))
						{
							if (!commandProcessing && attemptCount < 5 && coolDownFinished && !reg)
							{
								commandProcessing = true;
								coolDown = -1;
								coolDownFinished = false;
								
								String command = message.trim().toLowerCase();
								SpectralCommand spectralCommand = new SpectralCommand(player, acctHash, rank, command, chatInput);
								eventBus.post(spectralCommand);
								return true;
							}
						}
						
						// If we reached this point, then one of the conditional checks failed.
						if (!message.trim().toLowerCase().equals(COMMAND_KEY) && !message.trim().toLowerCase().equals(COMMAND_ADDME) && !pluginLoaded && !ready)
						{
							msg = "The plugin's data hasn't finished loading yet. Wait a minute before trying again.";
						}
						else if (!message.trim().toLowerCase().equals(COMMAND_KEY) && !message.trim().toLowerCase().equals(COMMAND_ADDME) && !canUseDiscordCommands)
						{
							msg = "You don't have permission to use the Discord commands.";
						}
						else if ((message.trim().toLowerCase().equals(COMMAND_KEY) || message.trim().toLowerCase().equals(COMMAND_ADDME)) && !canUseSpectralCommand)
						{
							msg = "You don't have permission to use that command.";
						}
						else if (message.trim().toLowerCase().equals(COMMAND_ADDME) && reg)
						{
							msg = "You've already registered your player ID, there's no reason for you to use this command.";
						}
						else if (message.trim().toLowerCase().equals(COMMAND_KEY) && !reg)
						{
							msg = "The command failed because your player ID doesn't seem to be registered. If you're a ranked member of Spectral and you haven't registered your player ID, use the !addme command in the clan chat to do so first. If you've registered your player ID before, but you've changed your name since then, ask a Recruiter or Mod to export your name change. Wait for them to confirm they've exported it before turning the plugin off and on again. If the issue persists, contact the developer.";
						}
						else if (message.trim().toLowerCase().equals(COMMAND_KEY) && validAccessKey)
						{
							msg = "You already have a valid access key, there's no reason for you to use this command.";
						}
						else
						{
							if (commandProcessing)
							{
								msg = "You can't use Spectral's commands right now. Wait a minute before trying again.";
							}
							else if (attemptCount >= 5)
							{
								msg = "The command failed because your permissions couldn't be verified. Make sure your player ID is registered, and that a valid URL for Spectral's web app along with your access key are set in the plugin's settings first. If the issue persists afterwards and you're a ranked member of Spectral, contact the developer.";
							}
							else if (!coolDownFinished)
							{
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
							}
						}
					}
					
					if (!msg.trim().equals(""))
					{
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
					}
					
					return true;
				}
			}
		}
		
		if (clanSettings == null || (clanSettings != null && !clanSettings.getName().equals("Spectral")) || rank == 0 || (rank != 0 && !normalRanks.contains(rank) && !adminRanks.contains(rank)))
		{
			canUseSpectralCommand = false;
			canUseDiscordCommands = false;
			validAccessKey = false;
			commandProcessing = false;
			ready = true;
			attemptCount = 5;
			reg = false;
		}
		
		return true;
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
	
	private void getPluginData(int rank, String player, String acctHash)
	{
		CompletableFuture.supplyAsync(() ->
		{
			if (rank == 0 || (!normalRanks.contains(rank) && !adminRanks.contains(rank)))
			{
				return "perma-fail;You don't have permission to use this plugin.";
			}
			
			if (attemptCount >= 5)
			{
				return "attempt-fail;The plugin has made the max allowed attempts to retrieve its data.";
			}
			
			if (rank != 0 && (normalRanks.contains(rank) || adminRanks.contains(rank)) && !reg)
			{
				return "reg-fail;Your player ID doesn't seem to be registered. If you're a ranked member of Spectral and you haven't registered your player ID, use the !addme command in the clan chat to do so first. If you've registered your player ID before, but you've changed your name since then, ask a Recruiter or Mod to export your name change. Wait for them to confirm they've exported it before turning the plugin off and on again. If the issue persists, contact the developer.";
			}
			
			if (!checkURL(config.scriptURL()))
			{
				return "url-fail;A valid URL for Spectral's web app is not set in the plugin's settings. If the issue persists when there is a valid URL set, and you're a ranked member of Spectral, contact the developer.";
			}
			
			if (config.memberKey().equals(""))
			{
				return "key-fail;Your access key isn't set in the plugin's settings. If you're a ranked member of Spectral and you've already registered your player ID, use the !key command in the clan chat to get your access key first. If the issue persists after your access key is set, contact the developer.";
			}
			
			return "proceed";
		})
		.thenApplyAsync(result ->
		{
			if (!result.equalsIgnoreCase("proceed"))
			{
				return result;
			}
			else
			{
				return httpRequest.getRequestAsyncPluginData(player, acctHash, rank);
			}
		})
		.thenApplyAsync(res ->
		{
			String[] result = res.split("\\;");
			
			if (result[0].equalsIgnoreCase("perma-fail") || result[0].equalsIgnoreCase("url-fail"))
			{
				canUseSpectralCommand = false;
				canUseDiscordCommands = false;
				validAccessKey = false;
				pluginLoaded = false;
				attemptCount = 5;
				ready = true;
			}
			else if (result[0].equalsIgnoreCase("attempt-fail") || result[0].equalsIgnoreCase("reg-fail") || result[0].equalsIgnoreCase("key-fail"))
			{
				attemptCount = 5;
				pluginLoaded = false;
				ready = true;
			}
			else if (result[0].equalsIgnoreCase("failure"))
			{
				attemptCount = 5;
				pluginLoaded = false;
				ready = true;
			}
			else if (result[0].equalsIgnoreCase("resp-failure"))
			{
				attemptCount++;
				
				if (attemptCount < 5)
				{
					ready = false;
					pluginLoaded = false;
				}
				else
				{
					pluginLoaded = false;
					ready = true;
				}
			}
			else if (result[0].equalsIgnoreCase("success"))
			{
				attemptCount = 0;
				pluginLoaded = true;
				ready = true;
			}
			
			commandProcessing = false;
			clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", result[1], null));
			return true;
			
		}).join();
	}
	
	protected String setPluginData(Response response) throws IOException
	{
		canUseSpectralCommand = false;
		canUseDiscordCommands = false;
		
		if (!response.isSuccessful())
		{
			return "resp-failure;An error occurred. The request either wasn't received or it wasn't accepted.";
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
			return "resp-failure;An error occurred. Response body wasn't a JSON string.";
		}
		
		if (resp == null)
		{
			return "resp-failure;Something went wrong and the plugin's data couldn't be retrieved. If you're a ranked member of Spectral, contact the developer about this issue.";
		}
		
		stat = resp.get("status").getAsString();
		JsonArray permission = resp.get("permission").getAsJsonArray();
		reg = resp.get("registered").getAsBoolean();
		int downTime = resp.get("downTime").getAsInt();
		validAccessKey = permission.get(0).getAsBoolean();
		adminURL = resp.get("admin").getAsString();
		canUseSpectralCommand = permission.get(1).getAsBoolean();
		canUseDiscordCommands = permission.get(2).getAsBoolean();
		
		if (downTime > 0)
		{
			coolDownTime = downTime;
		}
		
		String result = "";
		
		if (stat.equalsIgnoreCase("success"))
		{
			result = ";The plugin's data was successfully retrieved and set.";
		}
		else if (stat.equalsIgnoreCase("failure"))
		{
			result = ";" + resp.get("reason").getAsString();
		}
		
		return stat + result;
	}
	
	// ** This method was copied from the Wise Old Man Runelite Plugin code and rewritten to fit this plugin's usage. 
	// All credit for the original code goes to dekvall.
	private void createClanMemberButton(int w)
	{
		spectralClanMemberButton.createButton(w);
	}
	// **
	
	private boolean checkGameState(GameState game)
	{
		if (game == GameState.LOGIN_SCREEN)
		{
			attemptCount = 0;
			coolDown = -1;
			coolDownFinished = true;
			firstGameTick = false;
			gameTickCount = 0;
			ready = false;
			pluginLoaded = false;
			commandProcessing = false;
			adminURL = "";
			canUseSpectralCommand = false;
			canUseDiscordCommands = false;
			reg = true;
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
	
	private Boolean getModRecruit(SpectralCommand spectralCommand)
	{
		String player = spectralCommand.getPlayer();
		String msg = "";
		boolean flag = false;
		
		if (!checkURL(config.scriptURL()))
		{
			msg = "A valid URL for Spectral's web app is not set in the plugin's settings. If the issue persists when there is a valid URL set, and you're a ranked member of Spectral, contact the developer.";
			flag = true;
		}
		else if (config.memberKey().equals("") || !validAccessKey)
		{
			msg = "You aren't allowed to use this plugin's commands. If you're a ranked member of the Spectral clan, contact the developer.";
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
			CompletableFuture<Boolean> future = httpRequest.postRequestAsyncRecruitMod("discord", spectralCommand.getSpectralCommand(), player, spectralCommand.getAcctHash(), spectralCommand.getRank())
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
			return "failure;An error occurred. The request either wasn't received or it wasn't accepted.";
		}
		
		JsonObject resp;
		
		try
		{
			resp = gson.fromJson(response.body().charStream(), JsonObject.class);
		}
		catch (JsonSyntaxException ex)
		{
			return "failure;An error occurred. Response body wasn't a JSON string.";
		}
		
		String stat = "";
		String dat = "";
		
		if (resp == null)
		{
			stat = "failure";
			dat = "Something went wrong and the command couldn't be completed. If you're a ranked member of Spectral, contact the developer about this issue.";
		}
		else
		{
			stat = resp.get("status").getAsString();
			dat = resp.get("data").getAsString();
		}
		
		return stat + ";" + dat;
	}
	
	private Boolean registerPlayerID(SpectralCommand spectralCommand)
	{
		if (!checkURL(config.scriptURL()))
		{
			clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "A valid URL for Spectral's web app is not set in the plugin's settings. If the issue persists when there is a valid URL set, and you're a ranked member of Spectral, contact the developer.", null));
			return false;
		}
		else
		{
			clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Attempting to register your player ID...", null));
			
			CompletableFuture<Boolean> future = httpRequest.postRequestAsyncRegisterPlayerID("addme", spectralCommand.getPlayer(), spectralCommand.getAcctHash(), spectralCommand.getRank())
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
	
	protected String updateRegistered(Response response) throws IOException
	{
		reg = false;
		
		if (!response.isSuccessful())
		{
			return "failure;An error occurred. The request either wasn't received or it wasn't accepted.";
		}
		
		JsonObject resp;
		
		try
		{
			resp = gson.fromJson(response.body().charStream(), JsonObject.class);
		}
		catch (JsonSyntaxException ex)
		{
			return "failure;An error occurred. Response body wasn't a JSON string.";
		}
		
		if (resp == null)
		{
			return "failure;Something went wrong and your player ID couldn't be registered. If you're a ranked member of Spectral, contact the developer about this issue.";
		}
		
		String stat = resp.get("status").getAsString();
		String dat = resp.get("data").getAsString();
		reg = resp.get("registered").getAsBoolean();
		String result = stat + ";" + dat;
		
		if (stat.equalsIgnoreCase("success"))
		{
			pluginLoaded = false;
		}
		else
		{
			pluginLoaded = false;
			ready = true;
		}
		
		if (reg && config.memberKey().equals(""))
		{
			result = result + " You can use the !key command in the clan chat now to get your access key.";
		}
		
		if (reg && !config.memberKey().equals(""))
		{
			ready = false;
		}
		
		return result;
	}
	
	private Boolean getAccessKey(SpectralCommand spectralCommand)
	{
		if (!checkURL(config.scriptURL()))
		{
			clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "A valid URL for Spectral's web app is not set in the plugin's settings. If the issue persists when there is a valid URL set, and you're a ranked member of Spectral, contact the developer.", null));
			return false;
		}
		else
		{
			clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Attempting to retrieve your access key...", null));
			
			CompletableFuture<Boolean> future = httpRequest.postRequestAsyncAccessKey("get-key", spectralCommand.getPlayer(), spectralCommand.getAcctHash(), spectralCommand.getRank())
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
		validAccessKey = false;
		
		if (!response.isSuccessful())
		{
			return "failure;An error occurred. The request either wasn't received or it wasn't accepted.";
		}
		
		JsonObject resp;
		
		try
		{
			resp = gson.fromJson(response.body().charStream(), JsonObject.class);
		}
		catch (JsonSyntaxException ex)
		{
			return "failure;An error occurred. Response body wasn't a JSON string.";
		}
		
		String stat = "";
		String dat = "";
		
		if (resp == null)
		{
			stat = "failure";
			dat = "Something went wrong and your access key couldn't be found. If you're a ranked member of Spectral, contact the developer about this issue.";
		}
		else
		{
			stat = resp.get("status").getAsString();
			dat = resp.get("data").getAsString();
			validAccessKey = resp.get("isValid").getAsBoolean();
		}
		
		String result = "";
		
		if (stat.equalsIgnoreCase("success"))
		{
			if (!dat.equals(""))
			{
				config.setMemberKey(dat);
				
				if (reg)
				{
					ready = false;
				}
				
				result = stat + ";Your access key was set.";
			}
			else
			{
				validAccessKey = false;
				ready = true;
				result = stat + ";Something went wrong during the access key request. If you're a ranked member of Spectral, contact the developer about this issue.";
			}
		}
		else
		{
			ready = true;
			result = stat + ";" + dat;
		}
		
		return result;
	}
}