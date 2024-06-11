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

// My own version of the ChatboxTextMenuInput class, because there were a few things I wanted to change for the chatbox messages.
public class SpectralClanMgmtChatboxTextMenuInput extends SpectralClanMgmtChatboxInput implements KeyListener
{
	@Data
	@AllArgsConstructor
	private static final class Entry
	{
		private String text;
		private Runnable callback;
	}
	
	private final SpectralClanMgmtChatboxPanelManager chatboxPanelManager;
	
	@Getter
	private String title;
	
	@Getter
	private List<Entry> options = new ArrayList<>();
	
	@Getter
	private Runnable onClose;
	
	@Inject
	protected SpectralClanMgmtChatboxTextMenuInput(SpectralClanMgmtChatboxPanelManager chatboxPanelManager)
	{
		this.chatboxPanelManager = chatboxPanelManager;
	}
	
	public SpectralClanMgmtChatboxTextMenuInput title(String title)
	{
		this.title = title;
		return this;
	}
	
	public SpectralClanMgmtChatboxTextMenuInput option(String text, Runnable callback)
	{
		options.add(new Entry(text, callback));
		return this;
	}
	
	public SpectralClanMgmtChatboxTextMenuInput onClose(Runnable onClose)
	{
		this.onClose = onClose;
		return this;
	}
	
	public SpectralClanMgmtChatboxTextMenuInput build()
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
	protected void open()
	{
		Widget container = chatboxPanelManager.getContainerWidget();
		
		Widget prompt = container.createChild(-1, WidgetType.TEXT);
		
		prompt.setText(title);
		prompt.setTextColor(0x000000);
		prompt.setFontId(FontID.VERDANA_13_BOLD);
		
		prompt.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		prompt.setOriginalX(0);
		prompt.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		prompt.setOriginalY(20);
		prompt.setOriginalHeight(24);
		
		prompt.setLineHeight(20);
		prompt.setXTextAlignment(WidgetTextAlignment.CENTER);
		prompt.setYTextAlignment(WidgetTextAlignment.CENTER);
		prompt.setWidthMode(WidgetSizeMode.MINUS);
		prompt.revalidate();
		
		int y = prompt.getRelativeY() + prompt.getHeight() + 6;
		int height = container.getHeight() - y - 20;
		int step = height / options.size();
		int maxStep = options.size() >= 3 ? 25 : 30;
		
		if (step > maxStep)
		{
			int ds = step - maxStep;
			step = maxStep;
			y += (ds * options.size()) / 2;
		}
		
		for (Entry option : options)
		{
			Widget optWidget = container.createChild(-1, WidgetType.TEXT);
			
			optWidget.setText(option.text);
			optWidget.setFontId(FontID.VERDANA_13_BOLD);
			optWidget.setTextColor(0xFF0000);
			optWidget.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
			optWidget.setOriginalX(0);
			optWidget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
			optWidget.setOriginalY(y);
			optWidget.setOriginalHeight(24);
			
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
	protected void close()
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