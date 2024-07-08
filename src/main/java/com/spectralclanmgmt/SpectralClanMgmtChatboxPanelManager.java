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
package com.spectralclanmgmt;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientInt;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;

// I made my own version of the ChatboxPanelManager class just because there were things I wanted to change in my class version of 
// the ChatboxTextMenuInput class, and trying to use the existing ChatboxPanelManager class while using my modified ChatboxTextMenuInput class
// was causing problems, and it was easier to just recreate the class as my own version and use that.
@Singleton
@Slf4j
public class SpectralClanMgmtChatboxPanelManager
{
	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;
	
	private final KeyManager keyManager;
	private final MouseManager mouseManager;
	
	private final Provider<ChatboxTextInput> chatboxTextInputProvider;
	private final Provider<SpectralClanMgmtChatboxTextMenuInput> clanMgmtChatboxTextMenuInputProvider;
	
	@Getter
	private SpectralClanMgmtChatboxInput currentInput = null;
	
	@Inject
	private SpectralClanMgmtChatboxPanelManager(EventBus eventBus, Client client, ClientThread clientThread,
	KeyManager keyManager, MouseManager mouseManager,
	Provider<SpectralClanMgmtChatboxTextMenuInput> clanMgmtChatboxTextMenuInputProvider, Provider<ChatboxTextInput> chatboxTextInputProvider)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.eventBus = eventBus;
		
		this.keyManager = keyManager;
		this.mouseManager = mouseManager;
		
		this.clanMgmtChatboxTextMenuInputProvider = clanMgmtChatboxTextMenuInputProvider;
		this.chatboxTextInputProvider = chatboxTextInputProvider;
		
		eventBus.register(this);
	}
	
	public void close()
	{
		clientThread.invokeLater(this::unsafeCloseInput);
	}
	
	private void unsafeCloseInput()
	{
		client.runScript(
		ScriptID.MESSAGE_LAYER_CLOSE,
		0,
		1,
		0
		);
		
		if (currentInput != null)
		{
			killCurrentPanel();
		}
	}
	
	private void unsafeOpenInput(SpectralClanMgmtChatboxInput input)
	{
		client.runScript(ScriptID.MESSAGE_LAYER_OPEN, 0);
		
		eventBus.register(input);
		
		if (input instanceof KeyListener)
		{
			keyManager.registerKeyListener((KeyListener) input);
		}
		
		if (input instanceof MouseListener)
		{
			mouseManager.registerMouseListener((MouseListener) input);
		}
		
		if (input instanceof MouseWheelListener)
		{
			mouseManager.registerMouseWheelListener((MouseWheelListener) input);
		}
		
		if (currentInput != null)
		{
			killCurrentPanel();
		}
		
		currentInput = input;
		client.setVarcIntValue(VarClientInt.INPUT_TYPE, InputType.RUNELITE_CHATBOX_PANEL.getType());
		client.getWidget(ComponentID.CHATBOX_TITLE).setHidden(true);
		client.getWidget(ComponentID.CHATBOX_FULL_INPUT).setHidden(true);
		
		Widget c = getContainerWidget();
		c.deleteAllChildren();
		c.setOnDialogAbortListener((JavaScriptCallback) ev -> this.unsafeCloseInput());
		input.open();
	}
	
	public void openInput(SpectralClanMgmtChatboxInput input)
	{
		clientThread.invokeLater(() -> unsafeOpenInput(input));
	}
	
	public SpectralClanMgmtChatboxTextMenuInput openTextMenuInput(String title)
	{
		return clanMgmtChatboxTextMenuInputProvider.get().title(title);
	}
	
	public ChatboxTextInput openTextInput(String prompt)
	{
		return chatboxTextInputProvider.get().prompt(prompt);
	}
	
	@Subscribe
	public void onScriptPreFired(ScriptPreFired ev)
	{
		if (currentInput != null && ev.getScriptId() == ScriptID.MESSAGE_LAYER_CLOSE)
		{
			killCurrentPanel();
		}
	}
	
	@Subscribe
	private void onGameStateChanged(GameStateChanged ev)
	{
		if (currentInput != null && ev.getGameState() == GameState.LOGIN_SCREEN)
		{
			killCurrentPanel();
		}
	}
	
	private void killCurrentPanel()
	{
		try
		{
			currentInput.close();
		}
		catch (Exception e)
		{
			log.warn("Exception closing {}", currentInput.getClass(), e);
		}
		
		eventBus.unregister(currentInput);
		
		if (currentInput instanceof KeyListener)
		{
			keyManager.unregisterKeyListener((KeyListener) currentInput);
		}
		if (currentInput instanceof MouseListener)
		{
			mouseManager.unregisterMouseListener((MouseListener) currentInput);
		}
		if (currentInput instanceof MouseWheelListener)
		{
			mouseManager.unregisterMouseWheelListener((MouseWheelListener) currentInput);
		}
		
		currentInput = null;
	}
	
	public Widget getContainerWidget()
	{
		return client.getWidget(ComponentID.CHATBOX_CONTAINER);
	}
	
	public boolean shouldTakeInput()
	{
		// the search box on the world map can be focused, and chat input goes there, even
		// though the chatbox still has its key listener.
		Widget worldMapSearch = client.getWidget(ComponentID.WORLD_MAP_SEARCH);
		return worldMapSearch == null || client.getVarcIntValue(VarClientInt.WORLD_MAP_SEARCH_FOCUSED) != 1;
	}
}