package com.spectralclanmgmt;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("spectralclanmgmt")
public interface SpectralClanMgmtConfig extends Config
{
	@ConfigItem(
	keyName = "scriptURL",
	name = "Spectral Web App URL",
	description = "The URL of Spectral's web app for receiving POST requests from this plugin.",
	position = 0
	)
	default String scriptURL()
	{
		return "";
	}
	
	@ConfigItem(
	keyName = "adminScriptURL",
	name = "",
	description = "",
	hidden = true
	)
	default String adminScriptURL()
	{
		return "";
	}
	
	@ConfigItem(
	keyName = "adminScriptURL",
	name = "",
	description = "",
	hidden = true
	)
	void setAdminScriptURL(String URL);
	
	@ConfigItem(
	keyName = "spectralDiscordAppURL",
	name = "",
	description = "",
	hidden = true
	)
	default String spectralDiscordAppURL()
	{
		return "";
	}
	
	@ConfigItem(
	keyName = "spectralDiscordAppURL",
	name = "",
	description = "",
	hidden = true
	)
	void setSpectralDiscordAppURL(String URL);
}
