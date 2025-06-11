package com.spectralclanmgmt.competition;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.*;
import net.runelite.client.game.*;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;
import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;

@Slf4j
public class SpectralCompetition
{
	@Inject
	private EventBus eventBus;
	
	@Inject
	private Client client;
	
	private SpectralCompetitionClient competitionClient;
	
	private final List<Integer> superiors = List.of(
NpcID.CRUSHING_HAND, 
		NpcID.CHASM_CRAWLER, 
		NpcID.SCREAMING_BANSHEE, 
		NpcID.SCREAMING_TWISTED_BANSHEE, 
		NpcID.GIANT_ROCKSLUG, 
		NpcID.COCKATHRICE, 
		NpcID.FLAMING_PYRELORD, 
		NpcID.INFERNAL_PYRELORD, 
		NpcID.MONSTROUS_BASILISK, 
		NpcID.MALEVOLENT_MAGE, 
		NpcID.INSATIABLE_BLOODVELD, 
		NpcID.INSATIABLE_MUTATED_BLOODVELD, 
		NpcID.VITREOUS_JELLY, 
		NpcID.VITREOUS_WARPED_JELLY, 
		NpcID.SPIKED_TUROTH, 
		NpcID.SHADOW_WYRM, 
		NpcID.SHADOW_WYRM_10399, 
		NpcID.MUTATED_TERRORBIRD, 
		NpcID.MUTATED_TORTOISE, 
		NpcID.CAVE_ABOMINATION, 
		NpcID.ABHORRENT_SPECTRE, 
		NpcID.REPUGNANT_SPECTRE, 
		NpcID.CHOKE_DEVIL, 
		NpcID.KING_KURASK, 
		NpcID.NUCLEAR_SMOKE_DEVIL, 
		NpcID.MARBLE_GARGOYLE, 
		NpcID.MARBLE_GARGOYLE_7408, 
		NpcID.BASILISK_SENTINEL, 
		NpcID.NIGHT_BEAST, 
		NpcID.GREATER_ABYSSAL_DEMON, 
		NpcID.NECHRYARCH, 
		NpcID.GUARDIAN_DRAKE, 
		NpcID.GUARDIAN_DRAKE_10401, 
		NpcID.COLOSSAL_HYDRA, 
		NpcID.DREADBORN_ARAXYTE
	);
	
	private final List<Skill> skills = List.of(
Skill.PRAYER, 
		Skill.COOKING, 
		Skill.WOODCUTTING, 
		Skill.FLETCHING, 
		Skill.FISHING, 
		Skill.FIREMAKING, 
		Skill.CRAFTING, 
		Skill.SMITHING, 
		Skill.MINING, 
		Skill.HERBLORE, 
		Skill.AGILITY, 
		Skill.THIEVING, 
		Skill.SLAYER, 
		Skill.FARMING, 
		Skill.RUNECRAFT, 
		Skill.HUNTER, 
		Skill.CONSTRUCTION
	);
	
	private Map<Skill, Integer> playerXP = new HashMap<>();
	
	private ArrayList<Integer> currentXP = new ArrayList<Integer>();
	
	private String playerName;
	
	private Boolean startingXPSent;
	
	private Boolean currentXPSet;
	
	private String regexPattern = "";
	
	private String challengeType = "";
	
	private String challengeValidation = "";
	
	private JsonObject competitionData;
	
	// Also need to account for how the plugin will handle when the competition ends and they're still logged in.
	// Once it ends, we don't want the websocket server to continue transmitting data or the competition object to listen for events.
	
	@Inject
	public SpectralCompetition(EventBus eventBus, Client client, OkHttpClient okHttpClient, Gson gson, JsonObject competitionData)
	{
		this.client = client;
		this.eventBus = eventBus;
		this.competitionData = competitionData;
		startup(okHttpClient, gson);
	}
	
	public void startup(OkHttpClient okHttpClient, Gson gson)
	{
		if (this.competitionData != null)
		{
			this.playerName = competitionData.get("playerName").getAsString();
			String acctHash = competitionData.get("acctHash").getAsString();
			String competitionID = competitionData.get("competitionID").getAsString();
			String teamID = competitionData.get("teamID").getAsString();
			String startDate = competitionData.get("startDate").getAsString();
			String endDate = competitionData.get("endDate").getAsString();
			// This should instead be received from the WS server as part of its initial data after connecting and authenticating
			// String currentChallengeID = competitionData.get("currentChallengeID").getAsString();
			this.startingXPSent = competitionData.get("startingXPSent").getAsBoolean();
			
			if (!playerName.equals("") && !acctHash.equals("") && !competitionID.equals("") && !teamID.equals("") && !startDate.equals(""))
			{
				
				
				this.competitionClient = new SpectralCompetitionClient(okHttpClient, gson, playerName, acctHash, competitionID, teamID, startingXPSent);
				
				if (this.competitionClient != null)
				{
					eventBus.register(this);
					this.currentXPSet = setCurrentXP();
				}
			}
			else
			{
				this.competitionClient = null;
			}
		
			/*
			if (competitionClient != null)
			{
				competitionClient.connect();
			}
			*/
		}
	}
	
	public void shutdown()
	{
		/*
		if (competitionClient != null)
		{
			eventBus.unregister(this);
			//competitionClient.close();
			competitionClient = null;
			this.competitionData = null;
		}
		*/
	}
	
	public void competitionOver()
	{
		
	}
	
	private Boolean isCompetitionActive(String compDate, String dateCat)
	{
		try
		{
			Instant checkDate = LocalDateTime.parse(compDate).atZone(ZoneId.of("America/New_York")).toInstant();
			Instant localDateTime = ZonedDateTime.now().toInstant();
			
			if (dateCat.equals("start"))
			{
				return !localDateTime.isBefore(checkDate);
			}
			else if (dateCat.equals("end"))
			{
				return localDateTime.isBefore(checkDate);
			}
			
			return false;
		}
		catch (DateTimeParseException ex)
		{
			return false;
		}
	}
	
	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Finish up sending any data that needs to be sent
			challengeType = "";
			regexPattern = "";
		}
	}
	
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged containerChanged)
	{
		ItemContainer container = containerChanged.getItemContainer();
		int containerID = container.getId();
		
		if (containerID == InventoryID.BANK.getId() || containerID == InventoryID.INVENTORY.getId() || containerID == InventoryID.EQUIPMENT.getId())
		{
			return;
		}
		
		if (containerID == InventoryID.BARROWS_REWARD.getId())
		{
			// Check for Barrows chest items or clue casket rewards
		}
		else if (containerID == InventoryID.LUNAR_CHEST.getId())
		{
			// Check for Lunar chest items
		}
		else if (containerID == InventoryID.THEATRE_OF_BLOOD_CHEST.getId())
		{
			// Check for ToB chest items
		}
		else if (containerID == InventoryID.CHAMBERS_OF_XERIC_CHEST.getId())
		{
			// Check for CoX chest items
		}
		else if (containerID == InventoryID.TOA_REWARD_CHEST.getId())
		{
			// Check for ToA chest items
		}
		else if (containerID == InventoryID.FISHING_TRAWLER_REWARD.getId())
		{
			// Check for Trawler reward items
		}
	}
	
	@Subscribe
	private void onStatChanged(StatChanged stat)
	{
		final Skill changedSkill = stat.getSkill();
		final int newXP = stat.getXp();
		
		if (currentXPSet && currentXP.size() > 0)
		{
			final ArrayList<Integer> oldXPs = currentXP;
			final int oldXP = oldXPs.get(changedSkill.ordinal());
			
			if (oldXP < newXP)
			{
				// If setCurrentXP was called before the initial StatChanged events, then oldXP will be 0 for every skill
				// and the initial StatChanged events will update currentXP.
				// We don't want to export the XP changes when this event occurs right after the player first logs in.
				// But we do want to update currentXP in case the player's XP values weren't available when setCurrentXP was called.
				if (oldXP > 0)
				{
					final int xpGain = newXP - oldXP;
					
					if (xpGain > 0)
					{
						// This is actually where we'll export the XP gain to our server.
						log.info("PLAYER: " + playerName + " SKILL NAME: " + changedSkill.getName() + " OLD XP TOTAL: " + String.valueOf(oldXP) + " NEW XP TOTAL: " + String.valueOf(newXP) + " XP GAINED: " + String.valueOf(xpGain));
					}
				}
				
				currentXP.set(changedSkill.ordinal(), newXP);
			}
		}
		
		return;
	}
	
	@Subscribe
	public void onFakeXpDrop(FakeXpDrop fakeXpDrop)
	{
		if (skills.contains(fakeXpDrop.getSkill()))
		{
			int xp = fakeXpDrop.getXp();
		}
	}
	
	@Subscribe
	public void onLootReceived(LootReceived lootReceived)
	{
		// We only use this for the Whisperer
		String sourceName = lootReceived.getName();
		
		if (!sourceName.equalsIgnoreCase("The Whisperer"))
		{
			return;
		}
		
		Collection<ItemStack> stacks = lootReceived.getItems();
		log.info("Name: " + sourceName);
		
		for (ItemStack stack : stacks)
		{
			String item = client.getItemDefinition(stack.getId()).getMembersName();
			int quantity = stack.getQuantity();
			log.info("Item: " + item + ", Quantity: " + String.valueOf(quantity));
		}
	}
	
	@Subscribe
	public void onNpcLootReceived(NpcLootReceived npcLootReceived)
	{
		final NPC src = npcLootReceived.getNpc();
		Collection<ItemStack> itemStacks = npcLootReceived.getItems();
		checkLoot(src, itemStacks);
		return;
	}
	
	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		ChatMessageType msgType = chatMessage.getType();
		
		if (msgType != ChatMessageType.MESBOX && msgType != ChatMessageType.DIALOG && msgType != ChatMessageType.GAMEMESSAGE && msgType != ChatMessageType.SPAM)
		{
			return;
		}
		
		final String msg = Text.removeTags(chatMessage.getMessage().trim());
		final String playerName = client.getLocalPlayer().getName();
		int[] points;
		
		if (!regexPattern.equals(""))
		{
			Pattern regex = Pattern.compile(regexPattern);
			Matcher regexMatcher = regex.matcher(msg);
			
			if (msgType == ChatMessageType.GAMEMESSAGE || msgType == ChatMessageType.SPAM)
			{
				if (regexMatcher.matches())
				{
					if (challengeType.equalsIgnoreCase("mixology"))
					{
						if (regexMatcher.groupCount() == 3)
						{
							// 0 = mox resin, 1 = aga resin, 2 = lye resin
							int mox = Integer.parseInt(regexMatcher.group(1));
							int aga = Integer.parseInt(regexMatcher.group(2));
							int lye = Integer.parseInt(regexMatcher.group(3));
							points = new int[] { mox, aga, lye };
							updatePoints(challengeType, points);
						}
					}
					else if (challengeType.equalsIgnoreCase("gotr"))
					{
						if (regexMatcher.groupCount() == 2)
						{
							// 0 = elemental, 1 = catalytic
							int elemental = Integer.parseInt(regexMatcher.group(1));
							int catalytic = Integer.parseInt(regexMatcher.group(2));
							points = new int[] { elemental, catalytic };
							updatePoints(challengeType, points);
						}
					}
				}
			}
			else if (chatMessage.getType() == ChatMessageType.MESBOX)
			{
				
			}
			else if (chatMessage.getType() == ChatMessageType.DIALOG)
			{
				
			}
		}
	}
	
	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		if (challengeValidation == "VB")
		{
			int vbID = varbitChanged.getVarbitId();
			int vpID = varbitChanged.getVarpId();
			int vbValue = varbitChanged.getValue();
			var playerLocation = client.getLocalPlayer().getWorldLocation().getRegionID();
			
			if (vpID == 157 && playerLocation == 10549) // Target minigame
			{
				// If this is greater than zero, then the player hasn't received their archery tickets yet for their score,
				// so the score should still reflect their actual points and not the amount of tickets they earned.
				int targetMinigameShotsUsed = client.getVarpValue(156);
				
				if (targetMinigameShotsUsed > 0 && vbValue >= 900)
				{
					// Target Minigame challenge complete
				}
			}
			else if (vbID == 4900 && playerLocation == 46044) // Tithe Farm minigame challenge
			{
				if (vbValue >= 1000)
				{
					// Tithe Farm Minigame challenge complete
				}
			}
		}
	}
	
	private void updatePoints(String src, int[] points)
	{
		if (src.equalsIgnoreCase("mixology"))
		{
			// 0 = mox resin, 1 = aga resin, 2 = lye resin
			
		}
		else if (src.equalsIgnoreCase("gotr"))
		{
			// 0 = elemental, 1 = catalytic
			
		}
		else if (src.equalsIgnoreCase("pest"))
		{
			
		}
		
		// Export the earned points to the server tracking the competition data here.
		return;
	}
	
	private void updateCompletionCount(String src)
	{
		if (src.equalsIgnoreCase("MH"))
		{
			// Send update to the server tracking the competition data here to increment the Mahogany Homes contract total.
		}
		else if (src.equalsIgnoreCase("FG"))
		{
			// Send update to the server tracking the competition data here to increment the Farming Guild contract total.
		}
		else if (src.equalsIgnoreCase("SJ"))
		{
			// Send update to the server tracking the competition data here to increment the Sq'irkjuice glass total.
		}
		else if (src.equalsIgnoreCase("GF"))
		{
			// Send update to the server tracking the competition data here to increment the Giants' Foundry commissions total.
		}
		else if (src.equalsIgnoreCase("CS"))
		{
			// Send update to the server tracking the competition data here to complete the find crashed star first task.
		}
		
		return;
	}
	
	private void checkTrawlerDrop()
	{
		ItemContainer container = client.getItemContainer(InventoryID.FISHING_TRAWLER_REWARD);
		
		if (container != null)
		{
			Item[] items = container.getItems();
			
			for (Item item : items)
			{
				if (item.getId() == ItemID.ANGLER_BOOTS || item.getId() == ItemID.ANGLER_HAT || item.getId() == ItemID.ANGLER_TOP || item.getId() == ItemID.ANGLER_WADERS)
				{
					log.info("");
				}
			}
		}
	}
	
	private void getInitialData()
	{
		int skills[] = client.getSkillExperiences();
	}
	
	// ##############REMOVE BEFORE PUSHING TO GIT##############
	// Not actually using this, just keeping it here temporarily in case the code is useful.
	private void getBank()
	{
		// Make sure bank interface is open.
		ItemContainer container = client.getItemContainer(InventoryID.BANK);
		
		if (container != null)
		{
			Item[] items = container.getItems();
			
			for (Item item : items)
			{
				ItemComposition itemComp = client.getItemDefinition(item.getId());
				String itemName = itemComp.getMembersName();
				
				if (itemComp.getMembersName() != "null")
				{
					int quantity = item.getQuantity();
					log.info("Item Name: " + itemName + ", Quantity: " + String.valueOf(quantity));
				}
			}
		}
	}
	
	private void checkLoot(NPC src, Collection<ItemStack> itemStacks)
	{
		int region = client.getLocalPlayer().getWorldLocation().getRegionID();
		
		if (src != null)
		{
			if ((src.getId() == NpcID.GIANT_MOLE || src.getId() == NpcID.GIANT_MOLE) && (region == 6992 || region == 6993))
			{
				// Increment Giant Mole Competition KC counter
				// Also check for pet drop
			}
			else if ((src.getId() == NpcID.AMOXLIATL || src.getId() == NpcID.AMOXLIATL_13686 || src.getId() == NpcID.AMOXLIATL_13687 || src.getId() == NpcID.AMOXLIATL_13689) && (region == 6992 || region == 6993))
			{
				// Increment Amoxliatl Competition KC counter
				// Also check for Moxi pet drop
			}
			else if (src.getId() == NpcID.HESPORI || src.getId() == NpcID.HESPORI_11192)
			{
				// Increment Hespori Competition KC counter
				// Also check for Tangleroot pet drop
			}
			else if (src.getId() == NpcID.THE_MIMIC || src.getId() == NpcID.THE_MIMIC_8633)
			{
				// Increment Mimic Competition KC counter
			}
			else if (src.getId() == NpcID.DERANGED_ARCHAEOLOGIST)
			{
				// Increment Deranged Archeologist Competition KC counter
			}
			else if (src.getId() == NpcID.BRYOPHYTA)
			{
				// Increment Bryophyta Competition KC counter
			}
			else if (src.getId() == NpcID.OBOR)
			{
				// Increment Obor Competition KC counter
			}
			else if (superiors.contains(src.getId()))
			{
				// Check if eternal gem, imbued heart, dust battlestaff, or mist battlestaff items were dropped
			}
			else if (src.getId() >= 5648 && src.getId() <= 5720)
			{
				// Check if lumberjack outfit pieces were dropped.
			}
		}
		
		for (ItemStack stack : itemStacks)
		{
			String item = client.getItemDefinition(stack.getId()).getMembersName();
			int quantity = stack.getQuantity();
			log.info("Item: " + item + ", Quantity: " + String.valueOf(quantity));
		}
	}
	
	private boolean setCurrentXP()
	{
		if (!currentXPSet)
		{
			int[] xp = client.getSkillExperiences();
			
			
			if (xp == null || xp.length == 0)
			{
				log.info("ERROR: getSkillExperiences returned nothing.");
				return false;
			}
			
			if (currentXP.size() == 0)
			{
				for (int x = 0; x < xp.length - 2; x++)
				{
					log.info("Skill index: " + String.valueOf(x) + " Set Skill Value: " + String.valueOf(xp[x]));
					currentXP.add(xp[x]);
				}
				
				log.info("Current XP list was set.");
				return true;
			}
			else if (currentXP.size() == xp.length - 2)
			{
				for (int y = 0; y < xp.length - 2; y++)
				{
					log.info("Skill index: " + String.valueOf(y) + " Updated Skill Value: " + String.valueOf(xp[y]));
					currentXP.set(y, xp[y]);
				}
				
				log.info("Current XP list was updated.");
				return true;
			}
		}
		
		return false;
	}
}
