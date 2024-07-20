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
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import net.runelite.api.FontID;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.client.input.KeyListener;

public class SpectralTextMenuInput extends SpectralClanMgmtPlugin.SpectralInput implements KeyListener
{
	@Data
	@AllArgsConstructor
	private static final class Entry
	{
		private String text;
		private Runnable callback;
	}
	
	private final SpectralChatboxPanel chatboxPanelManager;
	
	@Getter
	private String title;
	
	@Getter
	private List<Entry> options = new ArrayList<>();
	
	@Getter
	private Runnable onClose;
	
	@Inject
	protected SpectralTextMenuInput(SpectralChatboxPanel chatboxPanelManager)
	{
		this.chatboxPanelManager = chatboxPanelManager;
	}
	
	public SpectralTextMenuInput title(String title)
	{
		this.title = title;
		return this;
	}
	
	public SpectralTextMenuInput option(String text, Runnable callback)
	{
		options.add(new Entry(text, callback));
		return this;
	}
	
	public SpectralTextMenuInput onClose(Runnable onClose)
	{
		this.onClose = onClose;
		return this;
	}
	
	public SpectralTextMenuInput build()
	{
		if (title == null)
		{
			throw new IllegalStateException("Title must be set.");
		}
		
		if (options.size() < 1)
		{
			throw new IllegalStateException("You must have at least 1 option.");
		}
		
		chatboxPanelManager.openInput(this);
		return this;
	}
	
	@Override
	public void open()
	{
		Widget container = chatboxPanelManager.getContainerWidget();
		Widget prompt = container.createChild(-1, WidgetType.TEXT);
		
		prompt.setText(title);
		prompt.setTextColor(0x000000);
		prompt.setFontId(FontID.QUILL_8);
		
		prompt.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		prompt.setOriginalX(0);
		prompt.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		prompt.setOriginalY(16);
		prompt.setOriginalHeight(28);
		
		prompt.setLineHeight(18);
		prompt.setXTextAlignment(WidgetTextAlignment.CENTER);
		prompt.setYTextAlignment(WidgetTextAlignment.CENTER);
		prompt.setWidthMode(WidgetSizeMode.MINUS);
		prompt.revalidate();
		
		int y = prompt.getRelativeY() + prompt.getHeight() + 2;
		int height = container.getHeight() - y - 16;
		int step = height / options.size();
		int maxStep = options.size() >= 3 ? 25 : 28;
		
		if (step > maxStep)
		{
			int ds = step - maxStep;
			step = maxStep;
			y += (ds * options.size()) / 2;
		}
		
		int optionNum = 1;
		
		for (Entry option : options)
		{
			if (optionNum == 1 && options.size() > 1)
			{
				y += 2;
			}
			
			Widget optWidget = container.createChild(-1, WidgetType.TEXT);
			
			optWidget.setText(option.text);
			optWidget.setFontId(FontID.QUILL_8);
			optWidget.setTextColor(0xFF0000);
			optWidget.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
			optWidget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
			optWidget.setOriginalX(0);
			optWidget.setOriginalY(y);
			optWidget.setOriginalHeight(20);
			
			optWidget.setXTextAlignment(WidgetTextAlignment.CENTER);
			optWidget.setYTextAlignment(WidgetTextAlignment.CENTER);
			optWidget.setWidthMode(WidgetSizeMode.MINUS);
			
			optWidget.setAction(0, "Continue");
			optWidget.setOnOpListener((JavaScriptCallback) ev -> callback(option));
			optWidget.setOnMouseOverListener((JavaScriptCallback) ev -> optWidget.setTextColor(0xFFFFFF));
			optWidget.setOnMouseLeaveListener((JavaScriptCallback) ev -> optWidget.setTextColor(0xFF0000));
			optWidget.setHasListener(true);
			optWidget.revalidate();
			
			y = y + step + 2;
			optionNum += 1;
		}
	}
	
	private void callback(Entry entry)
	{
		Widget container = chatboxPanelManager.getContainerWidget();
		container.setOnKeyListener((Object[]) null);
		chatboxPanelManager.close();
		entry.callback.run();
	}
	
	@Override
	public void close()
	{
		if (onClose != null)
		{
			onClose.run();
		}
	}
	
	@Override
	public void keyTyped(KeyEvent e)
	{
		if (!chatboxPanelManager.shouldTakeInput())
		{
			return;
		}
		
		char c = e.getKeyChar();
		
		if (c == '\033')
		{
			chatboxPanelManager.close();
			e.consume();
			return;
		}
		
		int n = c - '1';
		
		if (n >= 0 && n < options.size())
		{
			callback(options.get(n));
			e.consume();
		}
	}
	
	@Override
	public void keyPressed(KeyEvent e)
	{
		if (!chatboxPanelManager.shouldTakeInput())
		{
			return;
		}
		
		if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
		{
			e.consume();
		}
	}
	
	@Override
	public void keyReleased(KeyEvent e)
	{
	}
}