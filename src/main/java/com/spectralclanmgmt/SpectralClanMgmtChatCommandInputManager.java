/*
 * Copyright (c) 2018, Kamiel
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
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
package com.spectralclanmgmt;

import javax.inject.Inject;
import javax.inject.Singleton;
import com.google.common.util.concurrent.Runnables;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
@Singleton
public class SpectralClanMgmtChatCommandInputManager
{
	private static final String CHATBOX_INPUT = "chatboxInput";
	private final Client client;
	private final EventBus eventBus;
	private final ClientThread clientThread;
	private boolean sending;
	
	@Inject
	private SpectralClanMgmtChatCommandInputManager(Client client, EventBus eventBus, ClientThread clientThread)
	{
		this.client = client;
		this.eventBus = eventBus;
		this.clientThread = clientThread;
		eventBus.register(this);
	}
	
	@Subscribe
	private void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (sending)
		{
			return;
		}
		
		if (event.getEventName().equals(CHATBOX_INPUT))
		{
			handleInput(event);
		}
	}
	
	// I know this is an ugly way to handle it, but it works, so I'm fine with that.
	// If one of spectral's commands was entered, it'll go to a handleCommandInput and be processed from there.
	// If it's not one of spectral's commands that was entered, it'll be processed like usual. 
	// It's ugly, but it lets spectral's commands be handled separately so the normal chat messages 
	// or commands from other plugins won't be affected by this plugin's code.
	private void handleInput(ScriptCallbackEvent event)
	{
		final String[] stringStack = client.getStringStack();
		final int[] intStack = client.getIntStack();
		int stringStackCount = client.getStringStackSize();
		int intStackCount = client.getIntStackSize();
		
		final int chatType = intStack[intStackCount - 2];
		final int clanTarget = intStack[intStackCount - 1];
		final String typed = stringStack[stringStackCount - 1];
		String sCommand = "";
		
		
		if (typed.equalsIgnoreCase("!mod") || typed.equalsIgnoreCase("!recruit") || typed.equalsIgnoreCase("!spectral"))
		{
			if (client.getClanSettings(0) != null)
			{
				/* 
				The sCommand variable is used as a sort of flag to control when the input 
				should finish processing through this method or through the handleCommandInput method.
				
				The variable's value will be set to "ignore" any of the following is true when the command is used:
					The local player isn't a member of Spectral, or isn't a ranked member of Spectral (such as guest members).
					The local player is a ranked member of Spectral but the command wasn't entered in the clan chat.
				
				Any of Spectral's commands will be consumed and nothing else will be sent to the chat if either of the above are true.
				Anything that isn't a Spectral command, such as normal chat input or commands from other plugins,
				are processed and sent to the chat like normal.
				*/
				
				if (!client.getClanSettings(0).getName().equals("Spectral"))
				{
					sCommand = "ignore";
				}
				else
				{
					int rank = client.getClanSettings(0).titleForRank(client.getClanSettings(0).findMember(client.getLocalPlayer().getName()).getRank()).getId();
					
					if (SpectralClanMgmtPlugin.isNormalRank(rank) || SpectralClanMgmtPlugin.isAdminRank(rank))
					{
						if (chatType != 3)
						{
							sCommand = "ignore";
						}
						else
						{
							handleCommandInput(typed, chatType, clanTarget);
							return;
						}
					}
					else
					{
						sCommand = "ignore";
					}
				}
			}
			else
			{
				sCommand = "ignore";
			}
		}
		
		final String typedText = stringStack[stringStackCount - 1];
		final String spectralCommand = sCommand;
		
		// If it reaches this point, the input either wasn't one of spectral's commands
		// (i.e. it was normal chat input or another plugin's command), or it was one of spectral's commands
		// but one of the required conditions to use it wasn't met to allow it to be processed like it normally would.
		SpectralClanMgmtChatboxCommandInput chatboxInput = new SpectralClanMgmtChatboxCommandInput(0, spectralCommand, clanTarget, typedText, chatType, () -> clientThread.invokeLater(() -> sendChatboxInput(typedText, chatType, clanTarget)));
		eventBus.post(chatboxInput);
		
		if (chatboxInput.isConsumed())
		{
			// input was blocked.
			stringStack[stringStackCount - 1] = ""; // prevent script from sending
		}
	}
	
	// This should run when one of the Spectral-specific commands is sent and all the required conditions to use it were met.
	private void handleCommandInput(String command, int cType, int cTarget)
	{
		final String[] stringStack = client.getStringStack();
		int stringStackCount = client.getStringStackSize();
		
		int rank = client.getClanSettings(0).titleForRank(client.getClanSettings(0).findMember(client.getLocalPlayer().getName()).getRank()).getId();
		
		// I don't want it to have the sendChatboxInput method in the resume parameter if one of Spectral's commands was entered, 
		// because I'll be doing that in the methods for each of them.
		SpectralClanMgmtChatboxCommandInput chatboxInput = new SpectralClanMgmtChatboxCommandInput(rank, command, cTarget, command, cType, () -> Runnables.doNothing());
		eventBus.post(chatboxInput);
		
		if (chatboxInput.isConsumed())
		{
			// Should hit here if we called consume on the chatboxInput in one of the other methods it was passed to so it won't be sent by the script.
			stringStack[stringStackCount - 1] = "";
		}
	}
	
	
	// Had to make this protected instead of private so it could be called from the main class, but not called outside this plugin's package.
	protected void sendChatboxInput(String input, int chatType, int clanTarget)
	{
		sending = true;
		
		try
		{
			client.runScript(ScriptID.CHAT_SEND, input, chatType, clanTarget, 0, -1);
		}
		finally
		{
			sending = false;
		}
	}
}