package com.spectralclanmgmt;

import net.runelite.api.clan.ClanSettings;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.*;
import net.runelite.api.widgets.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SpectralClanMgmtButton
{
	public SpectralClanMgmtPlugin spectralClanMgmtPlugin;
	private final Client client;
	private ClanSettings clanSettings;
	private SpectralClanMgmtHttpRequest spectralClanMgmtHttpRequest;
	private final ClientThread clientThread;
	private final SpectralClanMgmtChatboxPanelManager chatboxPanelManager;
	private final Widget parent;
	private boolean wasClicked = false;
	private boolean listenersSet = false;
	private boolean newMemberSelected = false;
	private boolean altMemberSelected = false;
	private boolean mainMemberSelected = false;
	private boolean changedMemberSelected = false;
	private String task;
	private String newMemberName;
	private String newMemberDate;
	private String mainMemberName;
	private String memberCurrentName;
	private String memberOldName;
	private String memberType;
	private final List<Widget> cornersAndEdges = new ArrayList<>();
	private Widget textWidget;
	private HashMap<Integer, String> clanmembers = new HashMap<Integer, String>();
	private HashMap<String, String> clanmemberJoinDates = new HashMap<String, String>();
	
	public SpectralClanMgmtButton(Client client, ClientThread clientThread, SpectralClanMgmtChatboxPanelManager chatboxPanelManager, int parent, HashMap<Integer, String> members, HashMap<String, String> memberJoinDates, ClanSettings clanSettings, SpectralClanMgmtPlugin spectralClanMgmtPlugin, SpectralClanMgmtHttpRequest httpRequest)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.chatboxPanelManager = chatboxPanelManager;
		this.parent = client.getWidget(parent);
		this.clanmembers = members;
		this.clanmemberJoinDates = memberJoinDates;
		this.clanSettings = clanSettings;
		this.spectralClanMgmtPlugin = spectralClanMgmtPlugin;
		spectralClanMgmtHttpRequest = httpRequest;
		
		task = "";
		newMemberName = "";
		newMemberDate = "";
		mainMemberName = "";
		memberCurrentName = "";
		memberOldName = "";
		memberType = "";
		
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
		
		spectralClanMgmtHttpRequest.setSpectralClanMgmtButton(this);
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
	
	// ** This method was copied from the Wise Old Man Runelite Plugin code. 
	// All credit for this code segment goes to dekvall.
	private void update(boolean hovered)
	{
		for (Widget w : cornersAndEdges)
		{
			int spriteId = w.getSpriteId();
			w.setSpriteId(hovered ? spriteId + 8 : spriteId - 8);
			w.revalidate();
		}
	}
	// **
	
	// This will be called either once, if the admin is adding a new main member, 
	// or twice when the admin is adding a new alt member, because they need to select the new alt's main account in the clan.
	// From here, the admin will confirm their selections and, if everything is set correctly, the code would proceed to the 
	// export method, or they could choose to change their last selection, or cancel entirely.
	// If an error occurs, it'll remove the listeners, show a message, and abort the process.
	// The task refers to what option the admin selected at the start and/or where the selection process is currently at.
	// For new mains, there must be a value for the main's name and join date before it can proceed to export.
	// For new alts, there must be a value for the alt's name, the alt's join date, 
	// and the name of the alt's main before it can proceed to export.
	// confirmSelection also includes the code for handling cases where the admin selected a member that doesn't meet the requirements.
	private void confirmSelection()
	{
		chatboxPanelManager.close();
		
		if (task.equals("add-new"))
		{
			if (newMemberName != "" && newMemberDate != "")
			{
				chatboxPanelManager
				.openTextMenuInput("You have selected '" + newMemberName + "'. Is this correct?<br>Click Yes to export the data, No to select again, or Cancel to exit.")
				.option("Yes", () -> exportMember())
				.option("No", () -> selectNew())
				.option("Cancel", () -> removeListeners())
				.build();
			}
			else
			{
				task = "error";
			}
		}
		else if (task.equals("add-alt-get-new"))
		{
			if (newMemberName != "" && newMemberDate != "")
			{
				chatboxPanelManager
				.openTextMenuInput("You've selected '" + newMemberName + "' as the Alt. Is this correct?<br>Click Yes to proceed, No to select again, or Cancel to exit.")
				.option("Yes", () -> selectMain())
				.option("No", () -> selectAlt())
				.option("Cancel", () -> removeListeners())
				.build();
			}
			else
			{
				task = "error";
			}
		}
		else if (task.equals("add-alt"))
		{
			if (newMemberName != "" && newMemberDate != "" && mainMemberName != "")
			{
				chatboxPanelManager
				.openTextMenuInput("You've selected '" + mainMemberName + "' as the Main. Is this correct?<br>Click Yes to export the data, No to reselect the Main, or Cancel to exit.")
				.option("Yes", () -> exportMember())
				.option("No", () -> selectMain())
				.option("Cancel", () -> removeListeners())
				.build();
			}
			else
			{
				task = "error";
			}
		}
		else if (task.equals("name-change"))
		{
			if (!memberCurrentName.equals("") && !memberOldName.equals(""))
			{
				chatboxPanelManager
				.openTextMenuInput("You have selected '" + memberCurrentName + "'. Is this correct?<br>Click Yes to export the data, No to select again, or Cancel to exit.")
				.option("Yes", () -> exportMember())
				.option("No", () -> selectNameChange())
				.option("Cancel", () -> removeListeners())
				.build();
			}
			else
			{
				task = "error";
			}
		}
		else if (task.equals("invalid-new"))
		{
			task = "add-new";
			
			String newMem = newMemberName;
			newMemberName = "";
			
			chatboxPanelManager
			.openTextMenuInput("The member you've selected, '" + newMem + "', has the Alt rank.<br>Mains can only have normal ranks.<br>Select a different member for the Main, or click cancel to exit.")
			.option("Cancel", () -> removeListeners())
			.build();
		}
		else if (task.equals("invalid-alt"))
		{
			task = "add-alt-get-new";
			
			String altMem = newMemberName;
			newMemberName = "";
			
			chatboxPanelManager
			.openTextMenuInput("The member you've selected, '" + altMem + "', doesn't have the Alt rank.<br>Alts can only have the Alt rank.<br>Select a different member for the Alt, or click cancel to exit.")
			.option("Cancel", () -> removeListeners())
			.build();
		}
		else if (task.equals("invalid-main"))
		{
			// Double check that the selected alt member's name and join date are still set in their variables.
			if (!newMemberName.equals("") && !newMemberDate.equals(""))
			{
				task = "add-alt-get-main";
				
				String mainMem = mainMemberName;
				mainMemberName = "";
				
				chatboxPanelManager
				.openTextMenuInput("The member you've selected, '" + mainMem + "', has the Alt rank.<br>Mains can only have normal ranks.<br>Select a different member for the Main, or click cancel to exit.")
				.option("Cancel", () -> removeListeners())
				.build();
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
			.build();
		}
		else if (task.equals("not-friend"))
		{
			String changedMem = memberCurrentName;
			
			removeListeners();
			
			chatboxPanelManager
			.openTextMenuInput("'" + changedMem + "' isn't on your Friends list.<br>For name change exports, the member must be on your Friends list.")
			.option("OK", () -> chatboxPanelManager.close())
			.build();
		}
		else if (task.equals("no-name-change"))
		{
			task = "name-change";
			
			String changedMem = memberCurrentName;
			memberCurrentName = "";
			memberOldName = "";
			memberType = "";
			
			chatboxPanelManager
			.openTextMenuInput("'" + changedMem + "' doesn't have a previous name.<br>Select a different member, or click cancel to exit.")
			.option("Cancel", () -> removeListeners())
			.build();
		}
		
		// Something really odd has to happen for it to end up here, but this should catch those cases and reset everything.
		if (task.equals("error"))
		{
			removeListeners();
			
			chatboxPanelManager
			.openTextMenuInput("Something went wrong.")
			.option("OK", () -> chatboxPanelManager.close())
			.build();
		}
	}
	
	// Because sometimes a space will be a non-breaking one instead, so when the names passed to the web app script
	// and checked against the member names on the spreadsheet, even if there is a matching name, it'll return false.
	// To fix that, I replace those non-breaking spaces when it's time to export.
	private String replaceBadChars(String playerName)
	{
		char[] chars = playerName.toCharArray();
		
		for (int i = 0; i < chars.length; i++)
		{
			if ((int) chars[i] == 160)
			{
				chars[i] = (char)32;
			}
		}
		
		String newPlayerName = new String(chars);
		return newPlayerName;
	}
	
	// The script's URL needs to be set in the plugin's settings, and be a valid URL, 
	// to get to the inner code where the HttpRequest will be created.
	// Otherwise, they'll see a prompt to set the URL and the entire process will be aborted.
	// The post request is made to the script's web app asynchronously on its own thread 
	// so it doesn't hold up the main thread while we wait for a response.
	private void exportMember()
	{
		// Before we proceed, we'll check that the script's URL is set and valid.
		if (spectralClanMgmtPlugin.checkURL() == true)
		{
			// An extra check, just in case...
			if (spectralClanMgmtHttpRequest.isReady())
			{
				spectralClanMgmtHttpRequest.initializeExecutor();
				
				if (task.equals("add-new"))
				{
					String tempPlayerName = newMemberName;
					newMemberName = replaceBadChars(tempPlayerName);
					spectralClanMgmtHttpRequest.postRequestAsync(task, newMemberDate, newMemberName);
				}
				else if (task.equals("add-alt"))
				{
					String tempPlayerName = newMemberName;
					newMemberName = replaceBadChars(tempPlayerName);
					tempPlayerName = mainMemberName;
					mainMemberName = replaceBadChars(tempPlayerName);
					
					spectralClanMgmtHttpRequest.postRequestAsync(task, newMemberDate, mainMemberName, newMemberName);
				}
				else if (task.equals("name-change"))
				{
					String tempPlayerName = memberCurrentName;
					memberCurrentName = replaceBadChars(tempPlayerName);
					tempPlayerName = memberOldName;
					memberOldName = replaceBadChars(tempPlayerName);
					spectralClanMgmtHttpRequest.postRequestAsync(task, memberCurrentName, memberOldName, memberType, true);
				}
			}
		}
		else
		{
			chatboxPanelManager
			.openTextMenuInput("Enter a valid URL for the script in the plugin's settings first.")
			.option("OK", () -> removeListeners())
			.build();
		}
	}
	
	// This method is called from our HttpRequest class when a response is received and the members list widget is currently loaded.
	// It passes the status (success/failure) and the data holding the message from the web app.
	// Once we've received the response, we'll store the parameters in local variables and shutdown the request's thread.
	// The listeners are removed and the variables reset before the response is displayed in the chatbox.
	// Additional text is appended before the response is displayed depending on the task's value if the export's status is "success".
	public void exportDone(String tk, String stat, String dat)
	{
		String status = stat;
		String response = dat;
		String t = tk;
		
		removeListeners();
		
		if (status.equals("success"))
		{
			if (t.equals("add-new"))
			{
				response = response + "<br>Don't forget to update the member's Discord name and role!";
			}
			else if (t.equals("add-alt"))
			{
				response = response + "<br>Don't forget to add the alt to the Main's Discord name!";
			}
			else if (t.equals("name-change"))
			{
				response = response + "<br>Don't forget to update the member's Discord name!";
			}
		}
		
		// Just in case there's a chatbox prompt open at the time.
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput(response)
		.option("OK", () -> finished())
		.build();
	}
	
	// I had to put these out here, otherwise the chatbox wouldn't show the result prompt
	// when the response was received and this class' exportDone method was called.
	private void finished()
	{
		chatboxPanelManager.close();
		spectralClanMgmtHttpRequest.shutdown();
	}
	
	// This adds the click listeners to specific widgets in the member names column.
	public void setListeners()
	{
		newMemberSelected = false;
		altMemberSelected = false;
		mainMemberSelected = false;
		changedMemberSelected = false;
		newMemberName = "";
		newMemberDate = "";
		mainMemberName = "";
		memberCurrentName = "";
		memberOldName = "";
		memberType = "";
		task = "";
		
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
			memberWidgets[i].setOnClickListener((JavaScriptCallback) e -> getSelectedMember(j));
		}
		
		client.getWidget(693, 10).setChildren(memberWidgets);
		
		listenersSet = true;
	}
	
	// This is essentially a reset, everything is cleared and the listeners are removed in preparation 
	// for the button being clicked again or the members list widget being closed.
	public void removeListeners()
	{
		newMemberSelected = false;
		altMemberSelected = false;
		mainMemberSelected = false;
		changedMemberSelected = false;
		newMemberName = "";
		newMemberDate = "";
		mainMemberName = "";
		memberCurrentName = "";
		memberOldName = "";
		memberType = "";
		wasClicked = false;
		task = "";
		
		Widget[] memberWidgets = client.getWidget(693, 10).getChildren();
		
		for (int i = 1; i < memberWidgets.length; i = i+3)
		{
			memberWidgets[i].setOnClickListener((Object[]) null);
			memberWidgets[i].setHasListener(false);
		}
		
		client.getWidget(693, 10).setChildren(memberWidgets);
		
		listenersSet = false;
		
		chatboxPanelManager.close();
	}
	
	// The method for the click listeners on the member names child widgets of the member names column.
	// Depending on the option the user first selected, different methods will be run.
	// The user can either select a new main or a new alt (along with the new alt's main in the clan).
	// The first member selection is essentially the same, we use the value of j to get the index of the member's name in the hashmap. 
	// Then we use the member's name to get their join date. The join date has already been converted into a date string for the EST/EDT timezone.
	// As for the second part (for alts), it just retrieves the selected main's name from the hashmap, we don't need the alt's main's join date.
	private void getSelectedMember(int j)
	{
		if (task.equals("add-new")) // adding a new main task
		{
			if (newMemberSelected == false)
			{
				// j is for the position of the child widget in the children array. 
				// sn is the calculated index value that acts as the key for the member's name in the clanmembers hashmap.
				// So if j = 1, then sn will be 0. If j = 7, sn will be 2.
				int sn = (j - 1) / 3;
				
				if (clanmembers != null)
				{
					if (clanmembers.size() > 0)
					{
						if (clanmemberJoinDates != null)
						{
							if (clanmemberJoinDates.size() > 0)
							{
								// With the slot number, we get the selected member's name, 
								// and with the member's name we get their join date and store it in these variables for later.
								String selectedNewMemberName = clanmembers.get(sn);
								String selectedNewMemberDate = clanmemberJoinDates.get(selectedNewMemberName);
								
								if (!selectedNewMemberName.equals("") && !selectedNewMemberDate.equals(""))
								{
									int mainMemberRank = clanSettings.titleForRank(clanSettings.findMember(selectedNewMemberName).getRank()).getId();
									
									if (mainMemberRank != 9 && mainMemberRank != -1)
									{
										// Flip the flag and set the local variable values to their corresponding global variables.
										newMemberSelected = true;
										newMemberName = selectedNewMemberName;
										newMemberDate = selectedNewMemberDate;
										// Proceed to the next step.
										confirmSelection();
									}
									else if (mainMemberRank == 9)
									{
										// This occurs if the admin selected a member that has the rank for Alt accounts, which is a no-no for mains.
										task = "invalid-new";
										newMemberName = selectedNewMemberName;
										newMemberSelected = false;
										newMemberDate = "";
										// Proceed to the next step.
										confirmSelection();
									}
								}
								else // Something went wrong.
								{
									task = "error";
									newMemberSelected = false;
									newMemberName = "";
									newMemberDate = "";
									// Proceed to the next step.
									confirmSelection();
								}
							}
						}
					}
				}
			}
		}
		else if (task.equals("add-alt-get-new")) // first half of the add-alt overall task
		{
			// We're getting the new alt member here
			if (altMemberSelected == false)
			{
				int sn = (j - 1) / 3;
				
				if (clanmembers != null)
				{
					if (clanmembers.size() > 0)
					{
						if (clanmemberJoinDates != null)
						{
							if (clanmemberJoinDates.size() > 0)
							{
								// With the slot number, we get the selected member's name, 
								// and with the member's name we get their join date and store it in these variables for later.
								String selectedNewMemberName = clanmembers.get(sn);
								String selectedNewMemberDate = clanmemberJoinDates.get(selectedNewMemberName);
								
								if (selectedNewMemberName != "" && selectedNewMemberDate != "")
								{
									// Make sure the selected member's rank is the Alt rank (Gnome Child), because Alt member accounts have to have that rank.
									int altMemberRank = clanSettings.titleForRank(clanSettings.findMember(selectedNewMemberName).getRank()).getId();
									
									// Selected member has the Alt rank, we can proceed.
									if (altMemberRank == 9)
									{
										// Flip the flag and set the local variable values to their corresponding global variables.
										altMemberSelected = true;
										newMemberName = selectedNewMemberName;
										newMemberDate = selectedNewMemberDate;
										confirmSelection();
									}
									else if (altMemberRank != 9)
									{
										// Update the task value so we'll pass the conditional check in confirmSelection for our error message.
										// This occurs if the admin selected a member that has a normal rank, which is a no-no for alts.
										task = "invalid-alt";
										// Store the local variable's value in its corresponding global variable. This will be reset when confirmSelection runs,
										// but we want the selected name for the error message that's shown.
										newMemberName = selectedNewMemberName;
										altMemberSelected = false;
										newMemberDate = "";
										// Proceed to the next step.
										confirmSelection();
									}
								}
								else  // Something went wrong.
								{
									task = "error";
									altMemberSelected = false;
									newMemberName = "";
									newMemberDate = "";
									// Proceed to the next step.
									confirmSelection();
								}
							}
							else  // Something went wrong.
							{
								task = "error";
								altMemberSelected = false;
								newMemberName = "";
								newMemberDate = "";
								// Proceed to the next step.
								confirmSelection();
							}
						}
						else  // Something went wrong.
						{
							task = "error";
							altMemberSelected = false;
							newMemberName = "";
							newMemberDate = "";
							// Proceed to the next step.
							confirmSelection();
						}
					}
					else  // Something went wrong.
					{
						task = "error";
						altMemberSelected = false;
						newMemberName = "";
						newMemberDate = "";
						// Proceed to the next step.
						confirmSelection();
					}
				}
				else  // Something went wrong.
				{
					task = "error";
					altMemberSelected = false;
					newMemberName = "";
					newMemberDate = "";
					// Proceed to the next step.
					confirmSelection();
				}
			}
		}
		else if (task.equals("add-alt-get-main")) // second half of the add-alt overall task
		{
			// If an alt member has been selected, but the alt's main hasn't been selected, this code segment will be run.
			if (altMemberSelected == true && mainMemberSelected == false)
			{
				// j is for the position of the child widget in the children array. 
				// sn is the calculated index value that acts as the key for the member's name in the clanmembers hashmap.
				// So if j = 1, then sn will be 0. If j = 7, sn will be 2.
				int sn = (j - 1) / 3;
				
				if (clanmembers != null)
				{
					if (clanmembers.size() > 0)
					{
						// For selecting an alt's main, we only want to get the name and store it in a local variable.
						String selectedMainMemberName = clanmembers.get(sn);
						
						if (!selectedMainMemberName.equals(""))
						{
							// Now I need to check the rank of the selected main for the alt, 
							// because main accounts can't have the Guest rank (this was added just in case something bizarre happens 
							// that makes this possible, it really shouldn't be) or the Gnome Child rank, since that's strictly for Alt accounts.
							int mainMemberRank = clanSettings.titleForRank(clanSettings.findMember(selectedMainMemberName).getRank()).getId();
							
							if (mainMemberRank != 9 && mainMemberRank != -1)
							{
								// The selected member for the main has a normal rank. Since we've selected both an alt and a main, 
								// we set the task value to the value it needs to be at the end to signal it's time to export.
								task = "add-alt";
								// Flip the flag and set the local variable values to their corresponding global variables.
								mainMemberSelected = true;
								mainMemberName = selectedMainMemberName;
								// Proceed to the next step.
								confirmSelection();
							}
							else if (mainMemberRank == 9)
							{
								// Update the task value so we'll pass the conditional check in confirmSelection for our error message.
								// This will happen if the admin selected a member that has the rank for Alt accounts, which is a no-no for mains.
								task = "invalid-main";
								// Store the local variable's value in its corresponding global variable. This will be reset when confirmSelection runs,
								// but we want the selected name for the error message that's shown.
								// Make sure the flag remains set to false.
								mainMemberName = selectedMainMemberName;
								mainMemberSelected = false;
								// Proceed to the next step.
								confirmSelection();
							}
						}
						else // Something went wrong.
						{
							task = "error";
							mainMemberSelected = false;
							mainMemberName = "";
							// Proceed to the next step.
							confirmSelection();
						}
					}
					else // Something went wrong.
					{
						task = "error";
						mainMemberSelected = false;
						mainMemberName = "";
						// Proceed to the next step.
						confirmSelection();
					}
				}
				else // Something went wrong.
				{
					task = "error";
					mainMemberSelected = false;
					mainMemberName = "";
					// Proceed to the next step.
					confirmSelection();
				}
			}
		}
		else if (task .equals("name-change"))
		{
			if (changedMemberSelected == false)
			{
				// j is for the position of the child widget in the children array. 
				// sn is the calculated index value that acts as the key for the member's name in the clanmembers hashmap.
				// So if j = 1, then sn will be 0. If j = 7, sn will be 2.
				int sn = (j - 1) / 3;
				
				if (clanmembers != null)
				{
					if (clanmembers.size() > 0)
					{
						// For selecting a name change, we only want to get the current name and store it in a local variable.
						String selectedChangedMember = clanmembers.get(sn);
						
						if (!selectedChangedMember.equals(""))
						{
							int memberRank = clanSettings.titleForRank(clanSettings.findMember(selectedChangedMember).getRank()).getId();
							
							if (memberRank != 9 && memberRank != -1)
							{
								memberType = "main";
							}
							else if (memberRank == 9)
							{
								memberType = "alt";
							}
							else if (memberRank == -1) // This shouldn't be possible, but if they somehow select a member with the Guest rank, have it error out.
							{
								memberType = "";
							}
							
							if (!memberType.equals(""))
							{
								// Check if there's at least one friend on the admin's Friends list.
								if (client.getFriendContainer().getCount() > 0)
								{
									Friend changedMember = client.getFriendContainer().findByName(selectedChangedMember);
									
									// Check if the selected member is on the admin's Friends list.
									if (changedMember != null)
									{
										// Check if the member has changed their name before.
										if (changedMember.getPrevName() != null && !changedMember.getPrevName().equals(""))
										{
											changedMemberSelected = true;
											memberCurrentName = selectedChangedMember;
											memberOldName = changedMember.getPrevName();
											// Proceed to the next step.
											confirmSelection();
										}
										else // The member hasn't changed their name before.
										{
											task = "no-name-change";
											changedMemberSelected = false;
											memberCurrentName = selectedChangedMember;
											memberOldName = "";
											memberType = "";
											// Proceed to the next step.
											confirmSelection();
										}
									}
									else // The member isn't on the admin's Friends list.
									{
										task = "not-friend";
										changedMemberSelected = false;
										memberCurrentName = selectedChangedMember;
										memberOldName = "";
										memberType = "";
										// Proceed to the next step.
										confirmSelection();
									}
								}
								else // The admin's Friends list is empty.
								{
									task = "no-friends";
									changedMemberSelected = false;
									memberCurrentName = "";
									memberOldName = "";
									memberType = "";
									// Proceed to the next step.
									confirmSelection();
								}
							}
							else
							{
								task = "error";
								changedMemberSelected = false;
								memberCurrentName = "";
								memberOldName = "";
								memberType = "";
								// Proceed to the next step.
								confirmSelection();
							}
						}
						else // Something went wrong.
						{
							task = "error";
							changedMemberSelected = false;
							memberCurrentName = "";
							memberOldName = "";
							memberType = "";
							// Proceed to the next step.
							confirmSelection();
						}
					}
					else // Something went wrong.
					{
						task = "error";
						changedMemberSelected = false;
						memberCurrentName = "";
						memberOldName = "";
						memberType = "";
						// Proceed to the next step.
						confirmSelection();
					}
				}
				else // Something went wrong.
				{
					task = "error";
					changedMemberSelected = false;
					memberCurrentName = "";
					memberOldName = "";
					memberType = "";
					// Proceed to the next step.
					confirmSelection();
				}
			}
		}
	}
	
	// Admin chose to add a new Main clan member
	public void selectNew()
	{
		// Since there's three methods where setListeners can be called and these methods can be visited more than once,
		// we need to check if the flag for them has been set, and if the listeners haven't been added, we'll add them.
		if (listenersSet == false)
		{
			setListeners();
		}
		
		// Set the task to the admin's choice, reset the flag and global variables, then proceed.
		task = "add-new";
		newMemberSelected = false;
		newMemberName = "";
		newMemberDate = "";
		
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Select the new Main member's name from the left column.<br>Or click cancel to exit.")
		.option("Cancel", () -> removeListeners())
		.build();
	}
	
	// Admin chose to add a new Alt clan member
	public void selectAlt()
	{
		// Since there's three methods where setListeners can be called and these methods can be visited more than once,
		// we need to check if the flag for them has been set, and if the listeners haven't been added, we'll add them.
		if (listenersSet == false)
		{
			setListeners();
		}
		
		// Set the task to the admin's choice, in this case it's the first half of the task, 
		// reset the flag and global variables for that part of the task, then proceed.
		task = "add-alt-get-new";
		altMemberSelected = false;
		newMemberName = "";
		newMemberDate = "";
		
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Select the new Alt member's name from the left column.<br>Or click cancel to exit.")
		.option("Cancel", () -> removeListeners())
		.build();
	}
	
	// Get the Main for the new Alt clan member
	public void selectMain()
	{
		// We would only reach this point after selectAlt has been run, 
		// so the listeners would've already been added and we don't need the check here.
		
		// Set the task to the admin's choice, in this case it's the second half of the task, 
		// reset the flag and global variables for that part of the task, then proceed.
		task = "add-alt-get-main";
		mainMemberSelected = false;
		mainMemberName = "";
		
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Select the Main's name for the new Alt from the left column.<br>Or click cancel to exit.")
		.option("Cancel", () -> removeListeners())
		.build();
	}
	
	// Admin chose to export a name change
	public void selectNameChange()
	{
		// Since there's three methods where setListeners can be called and these methods can be visited more than once,
		// we need to check if the flag for them has been set, and if the listeners haven't been added, we'll add them.
		if (listenersSet == false)
		{
			setListeners();
		}
		
		// Set the task to the admin's choice, reset the flag and global variables, then proceed.
		task = "name-change";
		changedMemberSelected = false;
		memberCurrentName = "";
		memberOldName = "";
		// memberType will determine which column is searched on one of the sheets that has to be updated for name changes.
		memberType = "";
		
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Select the member whose name was changed from the left column.<br>Or click cancel to exit.")
		.option("Cancel", () -> removeListeners())
		.build();
	}
	
	// Admin selected No after choosing the Name Change option at the initial prompt.
	public void abortNameChange()
	{
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("For name change exports, the member must be on your Friends list.<br>You can remove the member from your Friends list afterwards.")
		.option("OK", () -> {
			wasClicked = false;
			chatboxPanelManager.close();
		})
		.build();
	}
	
	// A check prompt for the admin to confirm the prerequisite condition, the clan member being on their Friends list, is met.
	// An admin will need to add the clan member that changed their name to their Friends list first (they're aware of this)
	// before they can export the clan member's current and previous name to the script that will update the spreadsheet pages.
	// There are checks that will happen prior to the export being posted to ensure the member is on their Friends list
	// and has changed their name before.
	public void nameChangeCheckPreReq()
	{
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Is the member whose name was changed on your Friends list?")
		.option("Yes", () -> selectNameChange())
		.option("No", () -> abortNameChange())
		.option("Cancel", () -> { 
			wasClicked = false; 
			chatboxPanelManager.close(); 
		})
		.build();
	}
	
	// Gets the value from the main class' checkURL method so we can use it in the enableButton method of this class.
	private boolean urlCheck()
	{
		return spectralClanMgmtPlugin.checkURL();
	}
	
	// I separated out the new member export options cause the chatbox looked too crowded when there were 4 options to click.
	// If the admin selects to export a new member, they'll see this prompt next.
	private void memberExport()
	{
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Are you exporting a new Main or Alt member?<br>Select an option below, or click cancel to exit.")
		.option("Main", () -> selectNew())
		.option("Alt", () -> selectAlt())
		.option("Cancel", () -> {
			wasClicked = false;
			chatboxPanelManager.close();
		})
		.build();
	}
	
	public void enableButton()
	{
		textWidget.setText("<col=ffffff>" + "Export Clan Member" + "</col>");
		
		textWidget.setOnClickListener((JavaScriptCallback) e ->
		{
			if (spectralClanMgmtHttpRequest.isReady())
			{
				// If the script's url is missing or isn't valid, we don't want anything to happen when the button is clicked beyond
				// a prompt in the chatbox.
				if (urlCheck())
				{
					// wasClicked is used as a flag that stops the button from reacting to additional clicks
					// after the first click until the admin either finishes selecting and exporting a member,
					// cancels, or causes the members list widget to close.
					// We don't want them clicking the button then starting the export process, only to click the button
					// again at a point when everything wouldn't be reset (like after selecting an alt but not a main yet).
					if (wasClicked == false)
					{
						wasClicked = true;
						
						chatboxPanelManager
						.openTextMenuInput("Are you exporting a new member or a name change?<br>Select an option below, or click cancel to exit.")
						.option("Member", () -> memberExport())
						.option("Name Change", () -> nameChangeCheckPreReq())
						.option("Cancel", () -> {
							wasClicked = false;
							chatboxPanelManager.close();
						})
						.build();
					}
				}
				else
				{
					chatboxPanelManager
					.openTextMenuInput("Enter a valid URL for the script in the plugin's settings first.")
					.option("OK", () -> chatboxPanelManager.close())
					.build();
				}
			}
			else
			{
				// If this occurs, then the response from a post request hasn't been received yet.
				// This will automatically be closed, if it's not already, when the request's response is received.
				chatboxPanelManager
				.openTextMenuInput("The previous export has not finished yet.")
				.option("OK", () -> chatboxPanelManager.close())
				.build();
			}
		});
	}
}
