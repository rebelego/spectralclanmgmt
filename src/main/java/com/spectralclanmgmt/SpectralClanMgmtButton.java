package com.spectralclanmgmt;

import net.runelite.api.clan.ClanSettings;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.*;
import net.runelite.api.widgets.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
	private String task;
	private String newMemberName;
	private String newMemberDate;
	private String altMemberName;
	private String altMemberDate;
	private String mainMemberName;
	private final List<Widget> cornersAndEdges = new ArrayList<>();
	private Widget textWidget;
	private HashMap<Integer, String> clanmembers = new HashMap<Integer, String>();
	private HashMap<String, String> clanmemberJoinDates = new HashMap<String, String>();
	
	public SpectralClanMgmtButton(Client client, ClientThread clientThread, SpectralClanMgmtChatboxPanelManager chatboxPanelManager, int parent, HashMap<Integer, String> members, HashMap<String, String> memberJoinDates, ClanSettings clanSettings, SpectralClanMgmtPlugin spectralClanMgmtPlugin)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.chatboxPanelManager = chatboxPanelManager;
		this.parent = client.getWidget(parent);
		this.clanmembers = members;
		this.clanmemberJoinDates = memberJoinDates;
		this.clanSettings = clanSettings;
		this.spectralClanMgmtPlugin = spectralClanMgmtPlugin;
		
		task = "";
		newMemberName = "";
		newMemberDate = "";
		altMemberName = "";
		altMemberDate = "";
		mainMemberName = "";
		
		// **
		// The following code segment was copied from the Wise Old Man Runelite Plugin. All credit for this code segment goes to dekvall.
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
	}
	
	// ** This method was copied from the Wise Old Man Runelite Plugin. All credit for this code segment goes to dekvall.
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
	
	// ** This method was copied from the Wise Old Man Runelite Plugin and modified. All credit for this code segment goes to dekvall.
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
	
	// ** This method was copied from the Wise Old Man Runelite Plugin code. All credit for this code segment goes to dekvall.
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
		
		if (task == "add-new")
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
		else if (task == "add-alt-get-new")
		{
			if (altMemberName != "" && altMemberDate != "")
			{
				chatboxPanelManager
				.openTextMenuInput("You've selected '" + altMemberName + "' as the Alt. Is this correct?<br>Click Yes to proceed, No to select again, or Cancel to exit.")
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
		else if (task == "add-alt")
		{
			if (altMemberName != "" && altMemberDate != "" && mainMemberName != "")
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
		else if (task == "invalid-new")
		{
			task = "add-new";
			
			String newMem = newMemberName;
			newMemberName = "";
			
			chatboxPanelManager
			.openTextMenuInput("The member you've selected, '" + newMem + "', has the Alt rank.<br>Mains can only have normal ranks.<br>Select a different member for the Main, or click cancel to exit.")
			.option("Cancel", () -> removeListeners())
			.build();
		}
		else if (task == "invalid-alt")
		{
			task = "add-alt-get-new";
			
			String altMem = altMemberName;
			altMemberName = "";
			
			chatboxPanelManager
			.openTextMenuInput("The member you've selected, '" + altMem + "', doesn't have the Alt rank.<br>Alts can only have the Alt rank.<br>Select a different member for the Alt, or click cancel to exit.")
			.option("Cancel", () -> removeListeners())
			.build();
		}
		else if (task == "invalid-main")
		{
			if (altMemberName != "" && altMemberDate != "")
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
		
		// Something really odd has to happen for it to end up here, but this will catch those cases and just reset everything.
		if (task == "error")
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
			if (task == "add-new")
			{
				String tempPlayerName = newMemberName;
				newMemberName = replaceBadChars(tempPlayerName);
				
				spectralClanMgmtHttpRequest = new SpectralClanMgmtHttpRequest(this, spectralClanMgmtPlugin, spectralClanMgmtPlugin.returnConfig(), client);
				spectralClanMgmtHttpRequest.postRequestAsync(task, newMemberDate, newMemberName);
			}
			else if (task == "add-alt")
			{
				String tempPlayerName = altMemberName;
				altMemberName = replaceBadChars(tempPlayerName);
				tempPlayerName = mainMemberName;
				mainMemberName = replaceBadChars(tempPlayerName);
				
				spectralClanMgmtHttpRequest = new SpectralClanMgmtHttpRequest(this, spectralClanMgmtPlugin, spectralClanMgmtPlugin.returnConfig(), client);
				spectralClanMgmtHttpRequest.postRequestAsync(task, altMemberDate, mainMemberName, altMemberName);
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
	
	// This method is called from our HttpRequest class when a response is received.
	// It passes the status (success/failure) and the data holding the message from the web app.
	// Once we've received the response, we'll store the parameters in local variables and shutdown the request's thread.
	// The listeners are removed and the variables reset before the response is displayed in the chatbox.
	// Additional text is appended before the response is displayed depending on the task's value if the export's status is "success".
	public void exportDone(String stat, String dat)
	{
		String status = stat;
		String response = dat;
		String t = task;
		
		spectralClanMgmtHttpRequest.shutdown();
		removeListeners();
		
		if (status.equals("success"))
		{
			if (t == "add-new")
			{
				response = response + "<br>Don't forget to update the member's Discord name and role!";
			}
			else if (t == "add-alt")
			{
				response = response + "<br>Don't forget to add the alt to the Main's Discord name!";
			}
		}
		
		chatboxPanelManager
		.openTextMenuInput(response)
		.option("OK", () -> chatboxPanelManager.close())
		.build();
	}
	
	// Adds the click listeners to the widgets in the member names column. These will return the 
	public void setListeners()
	{
		newMemberSelected = false;
		altMemberSelected = false;
		mainMemberSelected = false;
		newMemberName = "";
		newMemberDate = "";
		altMemberName = "";
		altMemberDate = "";
		mainMemberName = "";
		task = "";
		
		// This gets the child widgets of the member names column widget.
		Widget[] memberWidgets = client.getWidget(693, 10).getChildren();
		
		// This attaches a click listener to the second child widget (i = 1) of the member names column
		// and then every 3rd child widget after that, because those are the widgets with the member's name for its text.
		// We don't get the member's name from the widget's text though; instead we pass the value for i, stored in temp variable j,
		// to the method of the widget's click listener. j is the child widget's position in the array of children, and that will
		// allow us to get the value of the name displayed on the widget (without all the invisible or weird characters that fuck things up)
		// which will then be used to get the member's int join date.
		// We already got the member names and int join dates earlier from the cs2 script that was run when the members list widget was loaded.
		for (int i = 1; i < memberWidgets.length; i = i + 3)
		{
			int j = i;
			memberWidgets[i].setHasListener(true);
			memberWidgets[i].setOnClickListener((JavaScriptCallback) e -> getSelectedMember(j));
		}
		
		client.getWidget(693, 10).setChildren(memberWidgets);
		
		listenersSet = true;
	}
	
	// This is essentially a reset, everything is cleared and the listeners are removed in preparation for the button being clicked again
	// or the members list widget being closed.
	public void removeListeners()
	{
		newMemberSelected = false;
		altMemberSelected = false;
		mainMemberSelected = false;
		newMemberName = "";
		newMemberDate = "";
		altMemberName = "";
		altMemberDate = "";
		mainMemberName = "";
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
	// Depending on the task value, different parts are run to select a new main, a new alt, or a new alt's main.
	// The first two parts are essentially the same, we use the value of j to get the index of the member's name in the hashmap. 
	// Then we use the member's name to get their int join date. The int join date is then converted into a date string in the EST/EDT timezone.
	// As for the third part, that's just for selecting the alt's main and getting their name from the hashmap after we've selected the alt.
	private void getSelectedMember(int j)
	{
		if (task == "add-new") // adding a new main task
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
								
								if (selectedNewMemberName != "" && selectedNewMemberDate != "")
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
								else
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
		else if (task == "add-alt-get-new") // first half of the add-alt overall task
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
										altMemberName = selectedNewMemberName;
										altMemberDate = selectedNewMemberDate;
										confirmSelection();
									}
									else if (altMemberRank != 9)
									{
										// Update the task value so we'll pass the conditional check in confirmSelection for our error message.
										// This occurs if the admin selected a member that has a normal rank, which is a no-no for alts.
										task = "invalid-alt";
										// Store the local variable's value in its corresponding global variable. This will be reset when confirmSelection runs,
										// but we want the selected name for the error message that's shown.
										altMemberName = selectedNewMemberName;
										altMemberSelected = false;
										altMemberDate = "";
										// Proceed to the next step.
										confirmSelection();
									}
								}
								else
								{
									// Just in case something else fucks up
									task = "error";
									altMemberSelected = false;
									altMemberName = "";
									altMemberDate = "";
									// Proceed to the next step.
									confirmSelection();
								}
							}
						}
					}
				}
			}
		}
		else if (task == "add-alt-get-main") // second half of the add-alt overall task
		{
			// If an alt member has been selected, but the alt's main hasn't been selected, this code segment will be run.
			if (altMemberSelected == true && mainMemberSelected == false)
			{
				int sn = (j - 1) / 3;
				
				// For selecting an alt's main, we only want to get the name and store it in a local variable.
				String selectedMainMemberName = clanmembers.get(sn);
				
				if (selectedMainMemberName != "")
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
				else
				{
					task = "error";
					mainMemberSelected = false;
					mainMemberName = "";
					// Proceed to the next step.
					confirmSelection();
				}
			}
		}
	}
	
	public void selectNew()
	{
		// Since there's two methods where setListeners can be called and these methods can be visited more than once,
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
	
	public void selectAlt()
	{
		// Since there's two methods where setListeners can be called and these methods can be visited more than once,
		// we need to check if the flag for them has been set, and if the listeners haven't been added, we'll add them.
		if (listenersSet == false)
		{
			setListeners();
		}
		
		// Set the task to the admin's choice, in this case it's the first half of the task, 
		// reset the flag and global variables for that part of the task, then proceed.
		task = "add-alt-get-new";
		altMemberSelected = false;
		altMemberName = "";
		altMemberDate = "";
		
		chatboxPanelManager.close();
		
		chatboxPanelManager
		.openTextMenuInput("Select the new Alt member's name from the left column.<br>Or click cancel to exit.")
		.option("Cancel", () -> removeListeners())
		.build();
	}
	
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
	
	// Gets the value from the main class' checkURL method so we can use it in the enableButton method of this class.
	private boolean urlCheck()
	{
		return spectralClanMgmtPlugin.checkURL();
	}
	
	public void enableButton()
	{
		textWidget.setText("<col=ffffff>" + "Export Clan Member" + "</col>");
		
		textWidget.setOnClickListener((JavaScriptCallback) e ->
		{
			// If the script's url is missing or isn't valid, we don't want anything to happen when the button is clicked beyond
			// a prompt in the chatbox.
			if (urlCheck() == true)
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
					.openTextMenuInput("Are you exporting a new Main or Alt member?<br>Select an option below, or click cancel to exit.")
					.option("Main", () -> selectNew())
					.option("Alt", () -> selectAlt())
					.option("Cancel", () -> removeListeners())
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
		});
	}
}
