package com.spectralclanmgmt;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("spectralclanmgmt")
public interface SpectralClanMgmtConfig extends Config
{
	@ConfigItem(
	keyName = "scriptURL",
	name = "Spectral's Web App URL",
	description = "The URL of Spectral's Web App.",
	position = 0
	)
	default String scriptURL()
	{
		return "";
	}
	
	@ConfigItem(
	keyName = "memberKey",
	name = "Access Key",
	description = "The unique access key assigned to each member.",
	position = 1
	)
	default String memberKey()
	{
		return "";
	}
	
	@ConfigItem(
	keyName = "memberKey",
	name = "",
	description = "",
	position = 2,
	hidden = true
	)
	void setMemberKey(String key);
}
