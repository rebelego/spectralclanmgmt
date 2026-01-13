package com.spectralclanmgmt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.*;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.*;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;
import okhttp3.Response;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SpectralClanMgmtButton
{
	@Inject
	private Gson gson;
	private SpectralClanMgmtPlugin plugin;
	private SpectralClanMgmtConfig config;
	private final Client client;
	private ClanSettings clanSettings;
	private final SpectralChatboxPanel chatboxPanelManager;
	private final SpectralClanMgmtHttpRequest httpRequest;
	private Widget parent;
	private boolean wasClicked = false;
	private boolean listenersSet = false;
	private boolean firstMemberSelected = false;
	private boolean secondMemberSelected = false;
	private String task;
	private String firstMemberName;
	private String firstMemberDate;
	private String secondMemberName;
	private String category;
	private String playerRank; // For adding new members and rank swaps
	private int adminRank = 0;
	private final List<Widget> cornersAndEdges = new ArrayList<>();
	private Widget textWidget;
	private HashMap<String, ClanMember> clanmembers = new HashMap<String, ClanMember>();
	private boolean buttonCreated;
	
	@Inject
	protected SpectralClanMgmtButton(SpectralClanMgmtPlugin plugin, SpectralChatboxPanel chatboxPanelManager, SpectralClanMgmtConfig config, Client client, SpectralClanMgmtHttpRequest httpRequest, Gson gson)
	{
		this.plugin = plugin;
		this.httpRequest = httpRequest;
		this.chatboxPanelManager = chatboxPanelManager;
		this.config = config;
		this.client = client;
		this.textWidget = null;
		this.buttonCreated = false;
		task = "";
		firstMemberName = "";
		firstMemberDate = "";
		secondMemberName = "";
		category = "";
		this.gson = gson;
		this.httpRequest.setButton(this);
	}
	
	public void createButton(int parent)
	{
		clanSettings = client.getClanSettings(0);
		this.parent = client.getWidget(parent);
		
		// **
		// The following code segment was copied from the Wise Old Man Runelite Plugin and modified. 
		// All credit for this code segment goes to dekvall.
		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_METAL_CORNER_TOP_LEFT, 153, 6, 9, 9);
		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_METAL_CORNER_TOP_RIGHT, 38, 6, 9, 9);
		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_METAL_CORNER_BOTTOM_LEFT, 153, 20, 9, 9);
		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_METAL_CORNER_BOTTOM_RIGHT, 38, 20, 9, 9);
		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_EDGE_LEFT, 153, 15, 9, 5);
		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_EDGE_TOP, 47, 6, 106, 9);
		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_EDGE_RIGHT, 38, 15, 9, 5);
		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_EDGE_BOTTOM, 47, 20, 106, 9);
		this.textWidget = this.createWidgetWithText();
		// **
		
		this.buttonCreated = true;
	}
	
	// ** This method was copied from the Wise Old Man Runelite Plugin and modified. 
	// All credit for this code segment goes to dekvall.
	private void createWidgetWithSprite(int spriteId, int x, int y, int width, int height)
	{
		Widget w = this.parent.createChild(-1, WidgetType.GRAPHIC);
		
		w.setSpriteId(spriteId);
		w.setOriginalX(x);
		w.setOriginalY(y);
		w.setOriginalWidth(width);
		w.setOriginalHeight(height);
		w.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		w.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		w.revalidate();
		cornersAndEdges.add(w);
	}
	// **
	
	// ** This method was copied from the Wise Old Man Runelite Plugin and modified. 
	// All credit for this code segment goes to dekvall.
	private Widget createWidgetWithText()
	{
		Widget textWidget = this.parent.createChild(-1, WidgetType.TEXT);
		
		textWidget.setOriginalX(38);
		textWidget.setOriginalY(6);
		textWidget.setOriginalWidth(124);
		textWidget.setOriginalHeight(23);
		textWidget.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		textWidget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		textWidget.setXTextAlignment(WidgetTextAlignment.CENTER);
		textWidget.setYTextAlignment(WidgetTextAlignment.CENTER);
		textWidget.setText("<col=9f9f9f>" + "Export Clan Member" + "</col>");
		textWidget.setFontId(FontID.PLAIN_11);
		textWidget.setTextShadowed(true);
		textWidget.setHasListener(true);
		textWidget.setOnMouseOverListener((JavaScriptCallback) e -> update(true));
		textWidget.setOnMouseLeaveListener((JavaScriptCallback) e -> update(false));
		textWidget.revalidate();
		
		return textWidget;
	}
	// **
	
	protected void destroyButton()
	{
		wasClicked = false;
		listenersSet = false;
		clanmembers.clear();
		this.textWidget = null;
		this.buttonCreated = false;
	}
	
	protected boolean isButtonCreated()
	{
		return this.buttonCreated;
	}
	
	// ** This method was copied from the Wise Old Man Runelite Plugin code and modified. 
	// All credit for the original code goes to dekvall.
	private void update(boolean hovered)
	{
		if (this.textWidget != null)
		{
			for (Widget w : cornersAndEdges)
			{
				int spriteId = w.getSpriteId();
				w.setSpriteId(hovered ? spriteId + 8 : spriteId - 8);
				w.revalidate();
			}
		}
	}
	// **
	
	// This method deals with the issues that come up when the user clicks the button
	// and then clicks to filter or search without canceling first.
	@Subscribe
	public void onScriptPreFired(ScriptPreFired scriptPreFired)
	{
		if (this.buttonCreated)
		{
			if (scriptPreFired.getScriptId() == 4249 || scriptPreFired.getScriptId() == 4243)
			{
				// We need to reset wasClicked, and remove the listeners if they were set, if they click the search button or one of the
				// filter buttons after clicking the export button, otherwise those won't be reset until the entire interface is closed.
				if (wasClicked)
				{
					if (listenersSet)
					{
						Widget[] memberWidgets = client.getWidget(693, 10).getChildren();
						
						for (int i = 1; i < memberWidgets.length; i = i + 3)
						{
							memberWidgets[i].setOnClickListener((Object[])null);
							memberWidgets[i].setHasListener(false);
						}
						
						client.getWidget(693, 10).setChildren(memberWidgets);
						clanmembers.clear();
						listenersSet = false;
						wasClicked = false;
					}
					else
					{
						clanmembers.clear();
						wasClicked = false;
					}
					
					if (scriptPreFired.getScriptId() == 4243)
					{
						chatboxPanelManager.close();
					}
				}
			}
		}
	}
	
	private void getMembersData()
	{
		clanmembers.clear();
		clanSettings = client.getClanSettings(0);
		
		if (clanSettings != null && clanSettings.getName().equals("Spectral") && !clanSettings.getMembers().isEmpty())
		{
			List<ClanMember> clanMembers = clanSettings.getMembers();
			
			Collections.sort(clanMembers, (m1, m2) -> m1.getName().compareToIgnoreCase(m2.getName()));
			
			for (ClanMember cm : clanMembers)
			{
				String fixedName = cm.getName().replace('\u00A0', ' ');
				clanmembers.put(fixedName, cm);
			}
		}
	}
	
	// We need to convert the member's LocalDate type joinDate value from the ClanMember class into the correct number of epoch seconds.
	// To do this, we get the epoch seconds of the LocalDate value, then multiply that number by 1000.
	// With the new total epoch seconds, we'll get the right date when we convert it to a date value for the specified time zone.
	private String convertJoinDate(ClanMember member)
	{
		long joined = member.getJoinDate().atStartOfDay(ZoneId.of("Europe/Belfast")).toEpochSecond() * 1000L;
		ZonedDateTime convertedJoinDate = Instant.ofEpochMilli(joined).atZone(ZoneId.of("America/New_York"));
		String spectralJoinDate = convertedJoinDate.format(DateTimeFormatter.ofPattern("M/d/uuuu"));
		return spectralJoinDate;
	}
	
	// The method for the click listeners on the member names child widgets of the member names column.
	// Depending on the option the user first selected, different methods will be run.
	// The user can either select a new main or a new alt (along with the new alt's main in the clan), or a member for a name chang export.
	// The first member selection is essentially the same, we use the value of j to get the index of the member's name in the hashmap. 
	// For new member (Main or Alt) exports, we use the member's name to get their join date. 
	// The join date has already been converted into a date string for the EST/EDT timezone.
	// For selecting the Main member of a new Alt member export, we only need the selected main's name from the hashmap.
	private void getSelectedMember(int j)
	{
		String widgetText = "";
		getMembersData();
		
		if (clanmembers.size() > 0)
		{
			if (task.equalsIgnoreCase("add-new")) // adding a new main task
			{
				if (!firstMemberSelected)
				{
					// With the slot number, we get the selected member's name, 
					// and with the member's name we get their join date and store it in these variables for later.
					widgetText = client.getWidget(693, 10).getChild(j).getText().replace('\u00A0', ' ');
					ClanMember selectedNewMember = clanmembers.get(widgetText);
					String selectedNewMemberDate = "";
					
					if (selectedNewMember != null)
					{
						selectedNewMemberDate = convertJoinDate(selectedNewMember);
					}
					
					if (selectedNewMember != null && !selectedNewMemberDate.equals(""))
					{
						int memberRank = clanSettings.titleForRank(selectedNewMember.getRank()).getId();
						
						if (memberRank == 9 || memberRank == -1) // 9 is for the Alt rank. -1 is for the Guest rank. Mains can't have either of these ranks.
						{
							// This occurs if the admin selected a member that has the rank for Alt accounts, which is a no-no for mains.
							task = "invalid-new";
							firstMemberName = widgetText;
						}
						else if (memberRank != 9 && (SpectralClanMgmtPlugin.normalRanks.contains(memberRank) || SpectralClanMgmtPlugin.adminRanks.contains(memberRank)))
						{
							// Flip the flag and set the local variable values to their corresponding global variables.
							firstMemberSelected = true;
							firstMemberName = widgetText;
							firstMemberDate = selectedNewMemberDate;
							playerRank = String.valueOf(memberRank);
							// Proceed to the next step.
							confirmSelection();
							return;
						}
					}
			
					// We should only reach this point if the member selected wasn't a valid choice.
					// If the task wasn't already changed, then it means the task's value is meant to be "error".
					if (task.equalsIgnoreCase("add-new"))
					{
						task = "error";
					}
					
					if (!task.equalsIgnoreCase("add-new"))
					{
						if (task.equalsIgnoreCase("error"))
						{
							firstMemberName = "";
						}
						
						firstMemberSelected = false;
						firstMemberDate = "";
						playerRank = "";
						// Proceed to the next step.
						displayError();
						return;
					}
				}
			}
			else if (task.equalsIgnoreCase("add-alt-get-new")) // first half of the add-alt overall task
			{
				// We're getting the new alt member here
				if (!firstMemberSelected)
				{
					widgetText = client.getWidget(693, 10).getChild(j).getText().replace('\u00A0', ' ');
					ClanMember selectedNewMember = clanmembers.get(widgetText);
					String selectedNewMemberDate = "";
					
					if (selectedNewMember != null)
					{
						selectedNewMemberDate = convertJoinDate(selectedNewMember);
					}
					
					if (selectedNewMember != null && !selectedNewMemberDate.equals(""))
					{
						int memberRank = clanSettings.titleForRank(selectedNewMember.getRank()).getId();
						
						// Check that the selected member has the required rank for Alts, or is one of the Admin ranks.
						// The Alts of Admins might have the Admin rank so they don't have to switch accounts while playing.
						// 9 is the ID for the Alt rank's title.
						if (memberRank == 9 || SpectralClanMgmtPlugin.adminRanks.contains(memberRank))
						{
							firstMemberSelected = true;
							firstMemberName = widgetText;
							firstMemberDate = selectedNewMemberDate;
							confirmSelection();
							return;
						}
						else
						{
							task = "invalid-alt";
							firstMemberName = widgetText;
						}
					}
			
					// We should only reach this point if the member selected wasn't a valid choice.
					// If the task wasn't already changed, then it means the task's value is meant to be "error".
					if (task.equalsIgnoreCase("add-alt-get-new"))
					{
						task = "error";
					}
					
					if (!task.equalsIgnoreCase("add-alt-get-new"))
					{
						if (task.equalsIgnoreCase("error"))
						{
							firstMemberName = "";
						}
						
						firstMemberSelected = false;
						firstMemberDate = "";
						// Proceed to the next step.
						displayError();
						return;
					}
				}
			}
			else if (task.equalsIgnoreCase("add-alt-get-main")) // second half of the add-alt overall task
			{
				// If an Alt has been selected, but the Alt's Main hasn't been selected, this code segment will be run.
				if (firstMemberSelected && !secondMemberSelected)
				{
					// For the Alt's Main, we only need its name.
					widgetText = client.getWidget(693, 10).getChild(j).getText().replace('\u00A0', ' ');
					ClanMember selectedMainMember = clanmembers.get(widgetText);
					
					if (selectedMainMember != null)
					{
						if (firstMemberName.equals(widgetText))
						{
							task = "invalid-add-alt";
						}
						else
						{
							int memberRank = clanSettings.titleForRank(selectedMainMember.getRank()).getId();
							
							// Check that the selected member has one of the ranks for Mains.
							if (memberRank == 9 || memberRank == -1) // 9 is the ID for the Alt rank's title. -1 is the Guest rank's title. Mains can't have either of those ranks.
							{
								task = "invalid-main";
								secondMemberName = widgetText;
							}
							else if (memberRank != 9 && (SpectralClanMgmtPlugin.normalRanks.contains(memberRank) || SpectralClanMgmtPlugin.adminRanks.contains(memberRank)))
							{ // Have to include the check for Alt rank in the check here as well since it's a normal rank, but Mains can't have it.
								task = "add-alt";
								secondMemberSelected = true;
								secondMemberName = widgetText;
								// Proceed to the next step.
								confirmSelection();
								return;
							}
						}
					}
			
					// We should only reach this point if the member selected wasn't a valid choice.
					// If the task wasn't already changed, then it means the task's value is meant to be "error".
					if (task.equalsIgnoreCase("add-alt-get-main"))
					{
						task = "error";
					}
					
					if (!task.equalsIgnoreCase("add-alt-get-main"))
					{
						if (task.equalsIgnoreCase("error") || task.equalsIgnoreCase("invalid-add-alt"))
						{
							secondMemberName = "";
						}
						
						secondMemberSelected = false;
						displayError();
						return;
					}
				}
			}
			else if (task.equalsIgnoreCase("name-change"))
			{
				if (!firstMemberSelected)
				{
					// For selecting a name change, we only want to get the current name and store it in a local variable.
					widgetText = client.getWidget(693, 10).getChild(j).getText().replace('\u00A0', ' ');
					ClanMember selectedChangedMember = clanmembers.get(widgetText);
					
					if (selectedChangedMember != null)
					{
						String adminMember = client.getLocalPlayer().getName().replace('\u00A0', ' ');
						
						if (!adminMember.equals(widgetText))
						{
							int memberRank = clanSettings.titleForRank(selectedChangedMember.getRank()).getId();
							
							if (memberRank == -1) // This shouldn't be possible, but if they somehow select a member with the Guest rank, have it error out.
							{
								category = "";
							}
							else if (memberRank == 9)
							{
								category = "alt";
							}
							else if (SpectralClanMgmtPlugin.normalRanks.contains(memberRank))
							{
								category = "main";
							}
							else if (SpectralClanMgmtPlugin.adminRanks.contains(memberRank)) // Admins
							{
								// Since admin members usually have the same rank for all their accts, the member type could be main or alt.
								category = "both";
							}
							
							if (!category.equals(""))
							{
								// Check if there's at least one friend on the admin's Friends list.
								if (client.getFriendContainer().getCount() > 0)
								{
									Friend[] friends = client.getFriendContainer().getMembers();
									Friend changedMember = null;
									
									for (Friend f : friends)
									{
										String fixedFriendName = f.getName().replace('\u00A0', ' ');
										
										if (fixedFriendName.equals(widgetText))
										{
											changedMember = f;
											break;
										}
									}
									
									// Check if the selected member is on the admin's Friends list.
									if (changedMember != null)
									{
										// Check if the member has changed their name before.
										if (changedMember.getPrevName() != null && !changedMember.getPrevName().trim().equals(""))
										{
											firstMemberSelected = true;
											firstMemberName = widgetText;
											secondMemberName = changedMember.getPrevName().replace('\u00A0', ' ');
											// Proceed to the next step.
											confirmSelection();
											return;
										}
										else // The member hasn't changed their name before.
										{
											task = "no-name-change";
											firstMemberName = widgetText;
										}
									}
									else // The member isn't on the admin's Friends list.
									{
										task = "not-friend";
										firstMemberName = widgetText;
									}
								}
								else // The admin's Friends list is empty.
								{
									task = "no-friends";
								}
							}
						}
						else // You can't submit a name change for yourself, because you can't add yourself to your Friends list.
						{
							task = "same-person";
						}
					}
			
					// We should only reach this point if the member selected wasn't a valid choice.
					// If the task wasn't already changed, then it means the task's value is meant to be "error".
					if (task.equalsIgnoreCase("name-change"))
					{
						task = "error";
					}
					
					if (!task.equalsIgnoreCase("name-change"))
					{
						if (task.equalsIgnoreCase("error") || task.equalsIgnoreCase("no-friends") || task.equalsIgnoreCase("same-player"))
						{
							firstMemberName = "";
						}
						
						firstMemberSelected = false;
						secondMemberName = "";
						category = "";
						// Proceed to the next step.
						displayError();
						return;
					}
				}
			}
			else if (task.equalsIgnoreCase("rank-swap-old")) // first half of the rank-swap overall task
			{
				if (!firstMemberSelected)
				{
					widgetText = client.getWidget(693, 10).getChild(j).getText().replace('\u00A0', ' ');
					ClanMember selectedOldMainMember = clanmembers.get(widgetText);
					
					if (selectedOldMainMember != null)
					{
						int memberRank = clanSettings.titleForRank(selectedOldMainMember.getRank()).getId();
						
						// Check that the selected member has the required rank for Alts, or an Admin rank.
						// The Alts of Admins might have the Admin rank so they don't have to switch accounts while playing.
						// 9 is the ID for the Alt rank's title.
						if (memberRank == 9 || SpectralClanMgmtPlugin.adminRanks.contains(memberRank))
						{
							firstMemberSelected = true;
							firstMemberName = widgetText;
							confirmSelection();
							return;
						}
						else
						{
							task = "invalid-old-main";
							firstMemberName = widgetText;
						}
					}
			
					// We should only reach this point if the member selected wasn't a valid choice.
					// If the task wasn't already changed, then it means the task's value is meant to be "error".
					if (task.equalsIgnoreCase("rank-swap-old"))
					{
						task = "error";
					}
					
					if (!task.equalsIgnoreCase("rank-swap-old"))
					{
						if (task.equalsIgnoreCase("error"))
						{
							firstMemberName = "";
						}
						
						firstMemberSelected = false;
						// Proceed to the next step.
						displayError();
						return;
					}
				}
			}
			else if (task.equalsIgnoreCase("rank-swap-new")) // second half of the rank-swap overall task
			{
				if (firstMemberSelected && !secondMemberSelected)
				{
					widgetText = client.getWidget(693, 10).getChild(j).getText().replace('\u00A0', ' ');
					ClanMember selectedOldAltMember = clanmembers.get(widgetText);
					
					if (selectedOldAltMember != null)
					{
						if (firstMemberName.equals(widgetText))
						{
							task = "invalid-rank-swap";
						}
						else
						{
							int memberRank = clanSettings.titleForRank(selectedOldAltMember.getRank()).getId();
							
							// Check that the selected member has one of the ranks for Mains.
							if (memberRank == 9 || memberRank == -1) // 9 is the ID for the Alt rank's title. -1 is the Guest rank's title. Mains can't have either of those ranks.
							{
								task = "invalid-old-alt";
								secondMemberName = widgetText;
							}
							else if (memberRank != 9 && (SpectralClanMgmtPlugin.normalRanks.contains(memberRank) || SpectralClanMgmtPlugin.adminRanks.contains(memberRank)))
							{ // Have to include the check for Alt rank in the check here as well since it's a normal rank, but Mains can't have it.
								
								task = "rank-swap";
								secondMemberSelected = true;
								secondMemberName = widgetText;
								playerRank = String.valueOf(memberRank);
								confirmSelection();
								return;
							}
						}
					}
			
					// We should only reach this point if the member selected wasn't a valid choice.
					// If the task wasn't already changed, then it means the task's value is meant to be "error".
					if (task.equalsIgnoreCase("rank-swap-new"))
					{
						task = "error";
					}
					
					if (!task.equalsIgnoreCase("rank-swap-new"))
					{
						if (task.equalsIgnoreCase("error") || task.equalsIgnoreCase("invalid-rank-swap"))
						{
							secondMemberName = "";
						}
						
						secondMemberSelected = false;
						playerRank = "";
						displayError();
						return;
					}
				}
			}
			else if (task.equalsIgnoreCase("discord-deserter") || task.equalsIgnoreCase("discord-returnee"))
			{
				if (!firstMemberSelected)
				{
					widgetText = Text.removeTags(client.getWidget(693, 10).getChild(j).getText());
					ClanMember selectedMember = clanmembers.get(widgetText);
							
					if (selectedMember != null)
					{
						firstMemberSelected = true;
						firstMemberName = widgetText;
						confirmSelection();
						return;
					}
					
					// We should only reach this point if the member selected wasn't a valid choice.
					// If the task wasn't already changed, then it means the task's value is meant to be "error".
					if (task.equalsIgnoreCase("discord-deserter") || task.equalsIgnoreCase("discord-returnee"))
					{
						task = "error";
					}
					
					if (!task.equalsIgnoreCase("discord-deserter") && !task.equalsIgnoreCase("discord-returnee"))
					{
						firstMemberName = "";
						firstMemberSelected = false;
						// Proceed to the next step.
						displayError();
						return;
					}
				}
			}
		}
	}
	
	private void confirmSelection()
	{
		chatboxPanelManager.close();
		
		if (task.equals("add-new"))
		{
			chatboxPanelManager
			.openTextMenuInput("You have selected '" + firstMemberName + "'. Is this correct?<br>Click Yes to export the data, No to select again, or Cancel to exit.")
			.option("Yes", () -> exportChange(task, firstMemberDate, firstMemberName, playerRank))
			.option("No", () -> selectNew())
			.option("Cancel", () -> removeListeners())
			.build(2);
		}
		else if (task.equals("add-alt-get-new"))
		{
			chatboxPanelManager
			.openTextMenuInput("You've selected '" + firstMemberName + "' as the Alt. Is this correct?<br>Click Yes to proceed, No to select again, or Cancel to exit.")
			.option("Yes", () -> selectMain())
			.option("No", () -> selectAlt())
			.option("Cancel", () -> removeListeners())
			.build(2);
		}
		else if (task.equals("add-alt"))
		{
			chatboxPanelManager
			.openTextMenuInput("You've selected '" + secondMemberName + "' as the Main. Is this correct?<br>Click Yes to export the data, No to reselect the Main, or Cancel to exit.")
			.option("Yes", () -> exportChange(task, firstMemberDate, secondMemberName, firstMemberName))
			.option("No", () -> selectMain())
			.option("Cancel", () -> removeListeners())
			.build(2);
		}
		else if (task.equals("name-change"))
		{
			chatboxPanelManager
			.openTextMenuInput("You have selected '" + firstMemberName + "'. Is this correct?<br>Click Yes to export the change, No to select again, or Cancel to exit.")
			.option("Yes", () -> exportChange(task, firstMemberName, secondMemberName, category))
			.option("No", () -> selectNameChange())
			.option("Cancel", () -> removeListeners())
			.build(2);
		}
		else if (task.equals("rank-swap-old"))
		{
			chatboxPanelManager
			.openTextMenuInput("You've selected '" + firstMemberName + "' as the old Main. Is this correct?<br>Click Yes to proceed, No to reselect, or Cancel to exit.")
			.option("Yes", () -> selectOldAlt())
			.option("No", () -> selectOldMain())
			.option("Cancel", () -> removeListeners())
			.build(2);
		}
		else if (task.equals("rank-swap"))
		{
			chatboxPanelManager
			.openTextMenuInput("You've selected '" + secondMemberName + "' as the new Main. Is this correct?<br>Click Yes to export the data, No to reselect, or Cancel to exit.")
			.option("Yes", () -> exportChange(task, firstMemberName, secondMemberName, playerRank))
			.option("No", () -> selectOldAlt())
			.option("Cancel", () -> removeListeners())
			.build(2);
		}
		else if (task.equals("discord-deserter"))
		{
			chatboxPanelManager
			.openTextMenuInput("You have selected '" + firstMemberName + "'. Is this correct?<br>Click Yes to export the change, No to reselect, or Cancel to exit.")
			.option("Yes", () -> exportChange(task, firstMemberName, "", ""))
			.option("No", () -> discordDeserterExport())
			.option("Cancel", () -> removeListeners())
			.build(2);
		}
		else if (task.equals("discord-returnee"))
		{
			chatboxPanelManager
			.openTextMenuInput("You have selected '" + firstMemberName + "'. Is this correct?<br>Click Yes to export the change, No to reselect, or Cancel to exit.")
			.option("Yes", () -> exportChange(task, firstMemberName, "", ""))
			.option("No", () -> discordReturneeExport())
			.option("Cancel", () -> removeListeners())
			.build(2);
		}
	}
	
	private void displayError()
	{
		chatboxPanelManager.close();
		
		if (task.equals("invalid-new"))
		{
			task = "add-new";
			
			String newMem = firstMemberName;
			firstMemberName = "";
			
			chatboxPanelManager
			.openTextMenuInput("The member you've selected, '" + newMem + "', has the Alt rank.<br>Mains can only have normal ranks.<br>Select a different member for the Main, or click Cancel to exit.")
			.option("Cancel", () -> removeListeners())
			.build(3);
		}
		else if (task.equals("invalid-alt"))
		{
			task = "add-alt-get-new";
			
			String altMem = firstMemberName;
			firstMemberName = "";
			
			chatboxPanelManager
			.openTextMenuInput("The member you've selected, '" + altMem + "', doesn't have the Alt rank.<br>Alts can only have the Alt rank.<br>Select a different member for the Alt, or click Cancel to exit.")
			.option("Cancel", () -> removeListeners())
			.build(3);
		}
		else if (task.equals("invalid-main"))
		{
			// Double check that the selected alt member's name and join date are still set in their variables.
			if (!firstMemberName.equals("") && !firstMemberDate.equals(""))
			{
				task = "add-alt-get-main";
				
				String mainMem = secondMemberName;
				secondMemberName = "";
				
				chatboxPanelManager
				.openTextMenuInput("The member you've selected, '" + mainMem + "', has the Alt rank.<br>Mains can only have normal ranks.<br>Select a different member for the Main, or click Cancel to exit.")
				.option("Cancel", () -> removeListeners())
				.build(3);
			}
			else
			{
				task = "error";
			}
		}
		else if (task.equals("no-friends"))
		{
			removeListeners();
			
			chatboxPanelManager
			.openTextMenuInput("Your Friends list is empty.<br>For name change exports, the member must be on your Friends list.")
			.option("OK", () -> chatboxPanelManager.close())
			.build(2);
		}
		else if (task.equals("not-friend"))
		{
			String changedMem = firstMemberName;
			
			removeListeners();
			
			chatboxPanelManager
			.openTextMenuInput("'" + changedMem + "' isn't on your Friends list.<br>For name change exports, the member must be on your Friends list.")
			.option("OK", () -> chatboxPanelManager.close())
			.build(2);
		}
		else if (task.equals("same-person"))
		{
			removeListeners();
			
			chatboxPanelManager
			.openTextMenuInput("You can't export a name change for yourself.<br>Ask another admin member to export your name change.")
			.option("OK", () -> chatboxPanelManager.close())
			.build(2);
		}
		else if (task.equals("no-name-change"))
		{
			task = "name-change";
			
			String changedMem = firstMemberName;
			firstMemberName = "";
			
			chatboxPanelManager
			.openTextMenuInput("'" + changedMem + "' doesn't have a previous name.<br>Select a different member, or click Cancel to exit.")
			.option("Cancel", () -> removeListeners())
			.build(2);
		}
		else if (task.equals("invalid-old-main"))
		{
			//The old Main should have the Alt rank, not one of the ranks for mains.
			// Double check that the selected member's name is still set in its variable.
			if (!firstMemberName.equals(""))
			{
				task = "rank-swap-old";
				
				String oldMain = firstMemberName;
				firstMemberName = "";
				
				chatboxPanelManager
				.openTextMenuInput("The member you've selected, '" + oldMain + "', doesn't have the Alt rank.<br>The old Main should have the Alt rank.<br>Select a different member for the old Main, or click Cancel to exit.")
				.option("Cancel", () -> removeListeners())
				.build(3);
			}
			else
			{
				task = "error";
			}
		}
		else if (task.equals("invalid-old-alt"))
		{
			//The old Alt should have whatever rank the old Main had before, not the Alt rank.
			// Double check that the selected member's name is still set in its variable.
			if (!secondMemberName.equals(""))
			{
				task = "rank-swap-new";
				
				String oldAlt = secondMemberName;
				secondMemberName = "";
				
				chatboxPanelManager
				.openTextMenuInput("The member you've selected, '" + oldAlt + "', has the Alt rank.<br>The new Main should have a normal rank.<br>Select a different member for the new Main, or click Cancel to exit.")
				.option("Cancel", () -> removeListeners())
				.build(3);
			}
			else
			{
				task = "error";
			}
		}
		else if (task.equals("invalid-add-alt") || task.equals("invalid-rank-swap"))
		{
			removeListeners();
			
			chatboxPanelManager
			.openTextMenuInput("You can't choose the same member for both selections.")
			.option("OK", () -> chatboxPanelManager.close())
			.build(1);
		}
		else if (task.equals("error"))
		{
			removeListeners();
			
			chatboxPanelManager
			.openTextMenuInput("Something went wrong.")
			.option("OK", () -> chatboxPanelManager.close())
			.build(1);
		}
	}
	
	protected void exportChange(String task, String firstArg, String secondArg, String thirdArg)
	{
		chatboxPanelManager.close();
		
		String admin = client.getLocalPlayer().getName().replace('\u00A0', ' ');
		
		if (!config.memberKey().equals("") && plugin.validAccessKey && plugin.reg && plugin.checkURL(plugin.getAdminURL()))
		{
			if (httpRequest.getIsReady())
			{
				String fArg = firstArg;
				String sArg = secondArg;
				String tArg = thirdArg;
				String acctHash = String.valueOf(client.getAccountHash());
				
				httpRequest.setIsReady(false);
				
				httpRequest.postRequestAsyncAdmin(task, fArg, sArg, tArg, admin, String.valueOf(adminRank), acctHash).whenCompleteAsync((result, ex) ->
				{
					httpRequest.setIsReady(true);
					removeListeners();
					
					chatboxPanelManager
					.openTextMenuInput(result)
					.option("OK", () -> chatboxPanelManager.close())
					.build(2);
				});
			}
		}
		else
		{
			httpRequest.setIsReady(true);
			removeListeners();
			String errorMsg = "";
			
			if (config.memberKey().equals("") || !plugin.validAccessKey)
			{
				errorMsg = "The access key set in the plugin's settings isn't valid.<br>Use the !key command in the clan chat to get your access key first.<br>If the issue persists after your access key is set, contact the developer.";
			}
			else if (!plugin.reg)
			{
				errorMsg = "Your player ID doesn't seem to be registered. If you've registered but recently changed your name,<br>ask another Recruiter+ to export your name change first. Once they have, turn the plugin off and on again.<br>If the issue persists, contact the developer.";
			}
			else if (!plugin.checkURL(plugin.getAdminURL()))
			{
				errorMsg = "A valid URL for Spectral's Admin web app isn't set. If you have an admin rank,<br>a valid access key, and you've registered your player ID, try turning the plugin<br>off and on again to fix the issue. If the issue persists, contact the developer.";
			}
			
			chatboxPanelManager
			.openTextMenuInput(errorMsg)
			.option("OK", () -> chatboxPanelManager.close())
			.build(3);
		}
	}
	
	// This method is called from our HttpRequest class when a response is received and the members list widget is not loaded.
	// It passes the status (success/failure) and the data holding the message from the web app.
	// Once we've received the response, we'll store the parameters in local variables and shutdown the request's thread.
	// The listeners are removed and the variables reset before the response is displayed in the chatbox.
	// Additional text is appended before the response is displayed depending on the task's value if the export's status is "success".
	protected String exportDone(String task, Response response) throws IOException
	{
		if (!response.isSuccessful())
		{
			return "Something went wrong.<br>Export couldn't be completed.";
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
			return "Something went wrong.<br>Response body wasn't a JSON string.";
		}
		
		if (resp == null)
		{
			return "Something went wrong.<br>Nothing was returned from the export.";
		}
		
		stat = resp.get("status").getAsString();
		res = resp.get("data").getAsString();
		
		return res;
	}
	
	// This adds the click listeners to specific widgets in the member names column.
	private void setListeners()
	{
		if (buttonCreated)
		{
			firstMemberSelected = false;
			secondMemberSelected = false;
			firstMemberName = "";
			firstMemberDate = "";
			secondMemberName = "";
			category = "";
			task = "";
			playerRank = "";
			
			// This gets the child widgets of the member names column widget.
			Widget[] memberWidgets = client.getWidget(693, 10).getChildren();
			
			// This attaches a click listener to the second child widget (i = 1) of the member names column
			// and then every 3rd child widget after that, because those are the widgets with the member's name for its text.
			// We don't get the member's name from the widget's text though; instead we pass the value for i, stored in temp variable j,
			// to the method of the widget's click listener. j is the child widget's position in the array of children, and that will
			// allow us to get the value of the name displayed on the widget (without all the invisible or weird characters that fuck things up)
			// which will then be used to get the member's int join date.
			// We already got the members' names and their join dates earlier when the members list widget was loaded.
			for (int i = 1; i < memberWidgets.length; i = i + 3)
			{
				int j = i;
				memberWidgets[i].setHasListener(true);
				memberWidgets[i].setOnClickListener((JavaScriptCallback)e -> getSelectedMember(j));
			}
			
			client.getWidget(693, 10).setChildren(memberWidgets);
			
			listenersSet = true;
		}
	}
	
	// This is essentially a reset, everything is cleared and the listeners are removed in preparation 
	// for the button being clicked again or the members list widget being closed.
	protected void removeListeners()
	{
		wasClicked = false;
		firstMemberSelected = false;
		secondMemberSelected = false;
		firstMemberName = "";
		firstMemberDate = "";
		secondMemberName = "";
		category = "";
		task = "";
		playerRank = "";
		
		if (buttonCreated)
		{
			Widget[] memberWidgets = client.getWidget(693, 10).getChildren();
			
			for (int i = 1; i < memberWidgets.length; i = i + 3)
			{
				memberWidgets[i].setOnClickListener((Object[])null);
				memberWidgets[i].setHasListener(false);
			}
			
			client.getWidget(693, 10).setChildren(memberWidgets);
		}
		
		listenersSet = false;
		clanmembers.clear();
		
		chatboxPanelManager.close();
	}
	
	// Admin chose to export a new Main clan member.
	private void selectNew()
	{
		// Since there's multiple methods where setListeners can be called and these methods can be visited more than once,
		// we need to check if the flag for them has been set and, if the listeners haven't been added, we'll add them.
		if (listenersSet == false)
		{
			setListeners();
		}
		
		// Set the task to the admin's choice, reset the flag and global variables, then proceed.
		task = "add-new";
		firstMemberSelected = false;
		firstMemberName = "";
		firstMemberDate = "";
		playerRank = "";
		
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Select the new Main member's name from the left column.<br>Or click Cancel to exit.")
		.option("Cancel", () -> removeListeners())
		.build(2);
	}
	
	// Admin chose to export a new Alt clan member.
	private void selectAlt()
	{
		// Since there's multiple methods where setListeners can be called and these methods can be visited more than once,
		// we need to check if the flag for them has been set and, if the listeners haven't been added, we'll add them.
		if (listenersSet == false)
		{
			setListeners();
		}
		
		// Set the task to the admin's choice, in this case it's the first half of the task, 
		// reset the flag and global variables for that part of the task, then proceed.
		task = "add-alt-get-new";
		firstMemberSelected = false;
		firstMemberName = "";
		firstMemberDate = "";
		
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Select the new Alt member's name from the left column.<br>Or click Cancel to exit.")
		.option("Cancel", () -> removeListeners())
		.build(2);
	}
	
	// Admin chose to export a new Alt clan member. Get the Main for the new Alt in the clan.
	private void selectMain()
	{
		// We would only reach this point after selectAlt has been run, 
		// so the listeners would've already been added and we don't need the check here.
		
		// Set the task to the admin's choice, in this case it's the second half of the task, 
		// reset the flag and global variables for that part of the task, then proceed.
		task = "add-alt-get-main";
		secondMemberSelected = false;
		secondMemberName = "";
		
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Select the Main's name for the new Alt from the left column.<br>Or click Cancel to exit.")
		.option("Cancel", () -> removeListeners())
		.build(2);
	}
	
	// Admin chose to export a rank swap. Get the member's old Main in the clan.
	private void selectOldMain()
	{
		// Since there's multiple methods where setListeners can be called and these methods can be visited more than once,
		// we need to check if the flag for them has been set and, if the listeners haven't been added, we'll add them.
		if (listenersSet == false)
		{
			setListeners();
		}
		
		// Set the task to the admin's choice, in this case it's the first half of the task, 
		// reset the flag and global variables for that part of the task, then proceed.
		task = "rank-swap-old";
		firstMemberSelected = false;
		firstMemberName = "";
		
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Select the member's old Main from the left column.<br>Or click Cancel to exit.")
		.option("Cancel", () -> removeListeners())
		.build(2);
	}
	
	// Admin chose to export a rank swap. Get the member's new Main in the clan.
	private void selectOldAlt()
	{
		// We would only reach this point after selectOldMain has been run, 
		// so the listeners would've already been added and we don't need the check here.
		
		// Set the task to the admin's choice, in this case it's the second half of the task, 
		// reset the flag and global variables for that part of the task, then proceed.
		task = "rank-swap-new";
		secondMemberSelected = false;
		secondMemberName = "";
		playerRank = "";
		
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Select the member's new Main from the left column.<br>Or click Cancel to exit.")
		.option("Cancel", () -> removeListeners())
		.build(2);
	}
	
	private void discordMemberChange()
	{
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Are you exporting a Discord Deserter or Returnee?<br>Select an option below, or click Cancel to exit.")
		.option("Discord Deserter", () -> discordDeserterExport())
		.option("Discord Returnee", () -> discordReturneeExport())
		.option("Cancel", () -> cancelOptions())
		.build(2);
	}
	
	// Admin chose to export a Discord Deserter. Get the member's Main in the clan.
	private void discordDeserterExport()
	{
		// Since there's multiple methods where setListeners can be called and these methods can be visited more than once,
		// we need to check if the flag for them has been set and, if the listeners haven't been added, we'll add them.
		if (listenersSet == false)
		{
			setListeners();
		}
		
		task = "discord-deserter";
		firstMemberSelected = false;
		firstMemberName = "";
		
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Select the member's Main from the left column.<br>Or click Cancel to exit.")
		.option("Cancel", () -> removeListeners())
		.build(2);
	}
	
	// Admin chose to export a Discord Returnee. Get the member's Main in the clan.
	private void discordReturneeExport()
	{
		// Since there's multiple methods where setListeners can be called and these methods can be visited more than once,
		// we need to check if the flag for them has been set and, if the listeners haven't been added, we'll add them.
		if (listenersSet == false)
		{
			setListeners();
		}
		
		task = "discord-returnee";
		firstMemberSelected = false;
		firstMemberName = "";
		
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Select the member's Main from the left column.<br>Or click Cancel to exit.")
		.option("Cancel", () -> removeListeners())
		.build(2);
	}
	
	// Admin chose to export a name change.
	private void selectNameChange()
	{
		// Since there's multiple methods where setListeners can be called and these methods can be visited more than once,
		// we need to check if the flag for them has been set and, if the listeners haven't been added, we'll add them.
		if (listenersSet == false)
		{
			setListeners();
		}
		
		// Set the task to the admin's choice, reset the flag and global variables, then proceed.
		task = "name-change";
		firstMemberSelected = false;
		firstMemberName = "";
		secondMemberName = "";
		// memberType will determine which column is searched on one of the sheets that has to be updated for name changes.
		category = "";
		
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Select a member from the left column for the name change export.<br>Or click Cancel to exit.")
		.option("Cancel", () -> removeListeners())
		.build(2);
	}
	
	// Admin selected No after choosing the Name Change option at the initial prompt.
	private void abortNameChange()
	{
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("For name change exports, the member must be on<br>your Friends list until the export is complete.")
		.option("OK", () -> cancelOptions())
		.build(2);
	}
	
	private void rankSwapOrDiscordChange()
	{
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Rank Swap or Discord Deserter/Returnee?")
		.option("Rank Swap", () -> selectOldMain())
		.option("Discord Deserter/Returnee", () -> discordMemberChange())
		.option("Cancel", () -> cancelOptions())
		.build(2);
	}
	
	// A check prompt for the admin to confirm the prerequisite condition, the clan member being on their Friends list, is met.
	// An admin will need to add the clan member that changed their name to their Friends list first (they're aware of this)
	// before they can export the clan member's current and previous name to the script that will update the spreadsheet pages.
	// There are checks that will happen prior to the export being posted to ensure the member is on their Friends list
	// and has changed their name before.
	private void nameChangeCheckPreReq()
	{
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Is the member you intend to select on your Friends list?<br>Please note that you can't export your own name change.")
		.option("Yes", () -> selectNameChange())
		.option("No", () -> abortNameChange())
		.option("Cancel", () -> cancelOptions())
		.build(2);
	}
	
	private void newMemberExport()
	{
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Are you exporting a new Main or Alt member?<br>Select an option below, or click Cancel to exit.")
		.option("Main", () -> selectNew())
		.option("Alt", () -> selectAlt())
		.option("Cancel", () -> cancelOptions())
		.build(2);
	}
	
	private void cancelOptions()
	{
		wasClicked = false;
		clanmembers.clear();
		chatboxPanelManager.close();
	}
	
	protected void enableButton()
	{
		if (buttonCreated)
		{
			textWidget.setText("<col=ffffff>Export Clan Change</col>");
			
			textWidget.setOnClickListener((JavaScriptCallback)e ->
			{
				adminRank = 0;
				
				String player = client.getLocalPlayer().getName();
				clanSettings = client.getClanSettings(0);
				
				if (clanSettings != null && clanSettings.getName().equals("Spectral") && !clanSettings.getMembers().isEmpty())
				{
					ClanMember member = clanSettings.findMember(player);
					
					if (member != null)
					{
						adminRank = clanSettings.titleForRank(member.getRank()).getId();
					}
				}
				
				if (!config.memberKey().equals("") && plugin.validAccessKey && plugin.reg && adminRank != 0 && SpectralClanMgmtPlugin.adminRanks.contains(adminRank) && SpectralClanMgmtPlugin.checkURL(plugin.getAdminURL()) && httpRequest.getIsReady())
				{
					// wasClicked is used as a flag that stops the button from reacting to additional clicks
					// after the first click until the admin either finishes an export, cancels, or causes the members list widget to close.
					// We don't want them clicking the button then starting the export process, only to click the button
					// again at a point when everything wouldn't be reset (like after selecting an alt but not a main yet).
					if (wasClicked == false)
					{
						wasClicked = true;
						
						chatboxPanelManager
						.openTextMenuInput("Select an export option below, or click Cancel to exit.")
						.option("Add Member", () -> newMemberExport())
						.option("Name Change", () -> nameChangeCheckPreReq())
						.option("Rank Swap or Discord Deserter/Returnee", () -> rankSwapOrDiscordChange())
						.option("Cancel", () -> cancelOptions())
						.build(1);
					}
					
				}
				else 
				{
					String errorMsg = "";
					
					if (config.memberKey().equals("") || !plugin.validAccessKey)
					{
						errorMsg = "A valid access key isn't set in the plugin's settings. Use the !key command<br>in the clan chat to get your access key first before trying again.";
					}
					else if (!plugin.reg)
					{
						errorMsg = "Your player ID isn't registered. Use the !addme command in the clan chat<br>to register your player ID first before trying again.";
					}
					else if (adminRank == 0 || !SpectralClanMgmtPlugin.adminRanks.contains(adminRank))
					{
						errorMsg = "You don't have the required rank to use this feature.<br>Contact the developer if you are an admin member of Spectral.";
					}
					else if (!SpectralClanMgmtPlugin.checkURL(plugin.getAdminURL()))
					{
						errorMsg = "The URL for Spectral's Admin web app isn't valid. Try turning the plugin<br>off and on again to fix the issue. If this issue persists, contact the developer.";
					}
					else if (!httpRequest.getIsReady())
					{
						errorMsg = "You can't start another export right now.<br>Wait a minute before trying again.";
					}
					
					chatboxPanelManager
					.openTextMenuInput(errorMsg)
					.option("OK", () -> chatboxPanelManager.close())
					.build(2);
				}
			});
		}
	}
}