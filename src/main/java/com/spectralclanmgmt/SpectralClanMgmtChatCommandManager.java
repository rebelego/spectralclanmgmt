/*
 * Copyright (c) 2019, Adam <Adam@sigterm.info>
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

/*
The following conditions must be met for the Spectral's commands to be used:
(It's a given that this plugin has to be installed for the commands to work).
1. The local player must be a ranked member of the Spectral clan (players with the guest rank in the clan chat cannot use the commands).
2. The command must be sent in the clan chat.
3. The local player must have permission to use the commands.
There's a 30 second cooldown before a command can be used again.
*/

@Singleton
public class SpectralClanMgmtChatCommandManager
{
	private final EventBus eventBus;
	
	private final Map<String, SpectralClanMgmtChatCommand> commands = new ConcurrentHashMap<>();
	
	private final ScheduledExecutorService scheduledExecutorService;
	
	private final ClientThread clientThread;
	
	@Inject
	private SpectralClanMgmtChatCommandManager(ClientThread clientThread, EventBus eventBus, SpectralClanMgmtChatCommandInputManager chatInputManager, ScheduledExecutorService scheduledExecutorService)
	{
		this.scheduledExecutorService = scheduledExecutorService;
		this.clientThread = clientThread;
		this.eventBus = eventBus;
		eventBus.register(this);
	}
	
	protected void shutdown()
	{
		eventBus.unregister(this);
	}
	
	protected void registerSpectralCommandAsync(String command, BiConsumer<SpectralClanMgmtChatboxCommandInput, String> execute)
	{
		commands.put(command.toLowerCase(), new SpectralClanMgmtChatCommand(command, true, execute));
	}
	
	protected void unregisterSpectralCommand(String command)
	{
		commands.remove(command.toLowerCase());
	}
	
	@Subscribe
	protected void onSpectralClanMgmtChatboxCommandInput(SpectralClanMgmtChatboxCommandInput chatboxInput)
	{
		final String message = chatboxInput.getValue();
		final String spectralCommand = chatboxInput.getSpectralCommand();
		String command = "";
		
		if (!spectralCommand.trim().equalsIgnoreCase(""))
		{
			// If spectralCommand is 'ignore', the one of spectral's commands was used in a situation where it's not allowed to be,
			// so we want to consume the command so it won't end up in the chat and return.
			if (spectralCommand.trim().equalsIgnoreCase("ignore"))
			{
				chatboxInput.consume();
				return;
			}
			else
			{
				command = extractCommand(spectralCommand);
			}
		}
		else
		{
			command = extractCommand(message);
		}
		
		SpectralClanMgmtChatCommand chatCommand = commands.get(command.toLowerCase());
		
		if (chatCommand == null)
		{
			return;
		}
		
		// Since these are for spectral's commands, we need to wait until the checks are done before the chatboxInput (unmodified)
		// is sent to the chat. We have to consume the chatboxInput instance here because we don't want it to be sent the chat yet,
		// and if we don't consume it here, it'll end up being sent twice, or it'll be sent once when it shouldn't have.
		clientThread.invoke(() -> { 
			scheduledExecutorService.execute(() -> chatCommand.getExecute().accept(chatboxInput, message));
			chatboxInput.consume(); 
		});
	}
	
	private static String extractCommand(String message)
	{
		int idx = message.indexOf(' ');
		
		if (idx == -1)
		{
			return message;
		}
		
		return message.substring(0, idx);
	}
}
