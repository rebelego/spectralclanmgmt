package com.spectralclanmgmt;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("spectralclanmgmt")
public interface SpectralClanMgmtConfig extends Config
{
	@ConfigItem(
	keyName = "scriptURL",
	name = "Script URL",
	description = "The URL of the web app for the Spectral clan's script that modifies the clan's spreadsheet.",
	position = 0
	)
	default String scriptURL()
	{
		return "";
	}
}
