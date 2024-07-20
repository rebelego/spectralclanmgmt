package com.spectralclanmgmt;

import com.google.gson.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.*;

public class SpectralClanMgmtHttpRequest
{
	protected ExecutorService executorService;
	private SpectralClanMgmtButton spectralClanMgmtButton;
	private SpectralClanMgmtConfig config;
	private SpectralClanMgmtPlugin spectralClanMgmtPlugin;
	private Client client;
	
	protected SpectralClanMgmtHttpRequest(SpectralClanMgmtPlugin spectralClanMgmtPlugin, SpectralClanMgmtConfig config, Client client)
	{
		this.spectralClanMgmtPlugin = spectralClanMgmtPlugin;
		this.config = config;
		this.client = client;
		this.executorService = null;
	}
	
	protected void setSpectralClanMgmtButton(SpectralClanMgmtButton spectralClanMgmtButton)
	{
		this.spectralClanMgmtButton = spectralClanMgmtButton;
	}
	
	protected void initializeExecutor()
	{
		if (executorService == null)
		{
			executorService = Executors.newSingleThreadExecutor();
		}
	}
	
	// For getting the permissions, config links, and phrases all at once.
	// This will be called after start up and when a command is used and it's been at least 5 minutes since the permissions were last checked.
	protected void getRequestAsyncPluginData(String configLink, String player, Optional<SpectralClanMgmtPlugin.SpectralCommand> command)
	{
		if (executorService != null)
		{
			executorService.execute(() ->
			{
				SpectralClanMgmtPlugin.SpectralCommand spectralCommand = command.orElse(null);
				
				try
				{
					// URL of the web app with parameters for GET request.
					String url = String.format("%s?configLink=%s&player=%s",
					config.scriptURL(),
					URLEncoder.encode(configLink, "UTF-8"),
					URLEncoder.encode(player, "UTF-8"));
					
					URL obj = new URL(url);
					HttpURLConnection con = (HttpURLConnection)obj.openConnection();
					
					con.setRequestMethod("GET");
					
					int responseCode = con.getResponseCode();
					
					if (responseCode == 200)
					{
						BufferedReader incoming = new BufferedReader(new InputStreamReader(con.getInputStream()));
						String inputLine;
						StringBuffer response = new StringBuffer();
						
						while ((inputLine = incoming.readLine()) != null)
						{
							response.append(inputLine);
						}
						
						incoming.close();
						
						JsonObject resp = new JsonParser().parse(response.toString()).getAsJsonObject();
						boolean permission = resp.get("permission").getAsBoolean();
						String phraseList = resp.get("phrases").getAsString();
						JsonArray conLink = resp.get("configLink").getAsJsonArray();
						String[] links = new String[conLink.size()];
						
						if (configLink.equalsIgnoreCase("discord"))
						{
							links[0] = String.valueOf(conLink.get(0));
						}
						else if (configLink.equalsIgnoreCase("both"))
						{
							links[0] = String.valueOf(conLink.get(0));
							links[1] = String.valueOf(conLink.get(1));
						}
						
						if (spectralCommand == null)
						{
							responseReceivedPluginData("success", permission, links, phraseList, Optional.empty());
						}
						else
						{
							responseReceivedPluginData("success", permission, links, phraseList, Optional.of(spectralCommand));
						}
					}
					else
					{
						if (spectralCommand == null)
						{
							responseReceivedPluginData("failure", false, new String[]{}, "", Optional.empty());
						}
						else
						{
							responseReceivedPluginData("failure", false, new String[]{}, "", Optional.of(spectralCommand));
						}
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			});
		}
	}
	
	protected void responseReceivedPluginData(String status, boolean perm, String[] configLinks, String phrases, Optional<SpectralClanMgmtPlugin.SpectralCommand> command)
	{
		SpectralClanMgmtPlugin.SpectralCommand spectralCommand = command.orElse(null);
		
		if (spectralCommand != null)
		{
			spectralClanMgmtPlugin.setPluginData(status, perm, configLinks, phrases, Optional.of(spectralCommand));
		}
		else
		{
			spectralClanMgmtPlugin.setPluginData(status, perm, configLinks, phrases, Optional.empty());
		}
	}
	
	/* 
	This is for the Admin-related export tasks in the SpectralClanMgmtButton class (new member additions and name changes).
	
	 1. New Main member: It will send the in-game name of the new clan member, their clan join date, and the admin's name to the clan's web app.
	 2. New Alt Member: It will send the in-game name of the new clan member, their clan join date, 
	    the name of the new member's Main in the clan, and the admin's name to the clan's web app.
	 3. Member Name Change: It will send the current in-game name and the previous in-game name of the selected clan member, 
	    and their clan member type ('main', 'alt', 'both') to the clan's web app.
	    The member type is not a personally identifying value linked to the player's data.
	    
	 * firstArg will either be the new member's join date OR the current name of a member name change.
	 * secondArg will either be the name of the new Main OR the name of the new Alt's Main OR the previous name of a member name change.
	 * thirdArg will either be the name of the local player OR the name of the new Alt member OR member type ('main', 'alt', or 'both') from name change export.
	 */
	protected void postRequestAsyncAdmin(String task, String firstArg, String secondArg, String thirdArg)
	{
		if (executorService != null)
		{
			executorService.execute(() ->
			{
				try
				{
					// URL of the web app for the script.
					String url = config.adminScriptURL();
					
					StringBuilder postData = new StringBuilder();
					
					if (task.equalsIgnoreCase("add-new"))
					{
						postData.append(URLEncoder.encode("task", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(task, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("joinDate", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(firstArg, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("mainPlayer", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(secondArg, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("admin", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(thirdArg, "UTF-8"));
					}
					else if (task.equalsIgnoreCase("add-alt"))
					{
						postData.append(URLEncoder.encode("task", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(task, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("joinDate", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(firstArg, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("mainPlayer", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(secondArg, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("altPlayer", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(thirdArg, "UTF-8"));
					}
					else if (task.equalsIgnoreCase("name-change"))
					{
						postData.append(URLEncoder.encode("task", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(task, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("currentName", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(firstArg, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("oldName", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(secondArg, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("memberType", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(thirdArg, "UTF-8"));
					}
					
					URL obj = new URL(url);
					HttpURLConnection con = (HttpURLConnection)obj.openConnection();
					
					con.setRequestMethod("POST");
					con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
					
					con.setDoOutput(true);
					DataOutputStream wr = new DataOutputStream(con.getOutputStream());
					wr.writeBytes(postData.toString());
					wr.flush();
					wr.close();
					
					int responseCode = con.getResponseCode();
					
					BufferedReader incoming = new BufferedReader(new InputStreamReader(con.getInputStream()));
					String inputLine;
					StringBuffer response = new StringBuffer();
					
					while ((inputLine = incoming.readLine()) != null)
					{
						response.append(inputLine);
					}
					
					incoming.close();
					
					if (responseCode == 200)
					{
						JsonObject resp = new JsonParser().parse(response.toString()).getAsJsonObject();
						String status = resp.get("status").getAsString();
						String data = resp.get("data").getAsString();
						
						responseReceivedAdmin(task, status, data);
					}
					else
					{
						String data = "Something went wrong. Contact the developer about this issue.";
						responseReceivedAdmin(task, "failure", data);
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			});
		}
		else
		{
			String data = "The executor wasn't initialized. Contact the developer about this issue.";
			responseReceivedAdmin("", "failure", data);
		}
	}
	
	private void responseReceivedAdmin(String task, String status, String data)
	{
		// If the GameState isn't "LOGGED_IN" when the response is received, just shut down. Otherwise, call exportDone.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			// Make sure that the members list widget is still loaded when a response is returned
			// before calling the exportDone method in the button's class.
			if (spectralClanMgmtPlugin.isMemberWidgetLoaded())
			{
				if (spectralClanMgmtButton != null)
				{
					// If the button isn't null and the members list widget is loaded,
					// route the response to the exportDone method in the button's class.
					spectralClanMgmtButton.exportDone(task, status, data);
				}
				else
				{
					// If for some reason the button is null, but the members list widget is loaded, 
					// route the response to the exportDone method in the main class.
					spectralClanMgmtPlugin.exportDone(task, status, data);
				}
			}
			else
			{
				// If the members list widget isn't loaded when a response is returned (knowing them, they'll probably close it)
				// route the response to the exportDone method in the main class.
				spectralClanMgmtPlugin.exportDone(task, status, data);
			}
		}
		else
		{
			shutdown();
		}
	}
	
	/*
	This is the postRequestAsync method for the Discord-related commands. The requests are sent to Spectral's Discord web api.
	The task argument is 'discord', it's used for routing the request in the web api.
	The spectralCommand object is used to retrieve the command the local player sent to the chat. The object itself won't be included in the request.
	The player argument is for the name of the local player.
	The task and player arguments, along with the command text, are included as parameters in the post request.
	*/
	protected void postRequestAsyncRecruitMod(String task, SpectralClanMgmtPlugin.SpectralCommand spectralCommand, String player)
	{
		if (executorService != null)
		{
			executorService.execute(() ->
			{
				try
				{
					// URL of the web app for the script.
					String url = config.spectralDiscordAppURL();
					String command = spectralCommand.getSpectralCommand().substring(1);
					String payload = "{\"task\":\"" + task + "\",\"command\":\"" + command + "\",\"player\":\"" + player + "\"}";
					
					URL obj = new URL(url);
					HttpURLConnection con = (HttpURLConnection)obj.openConnection();
					
					con.setRequestMethod("POST");
					con.setRequestProperty("Content-Type", "application/json");
					
					con.setDoOutput(true);
					OutputStream stream = con.getOutputStream();
					byte[] payloadBytes = payload.getBytes("utf-8");
					stream.write(payloadBytes, 0, payloadBytes.length);
					stream.flush();
					stream.close();
					
					int responseCode = con.getResponseCode();
					
					BufferedReader incoming = new BufferedReader(new InputStreamReader(con.getInputStream()));
					String inputLine;
					StringBuffer response = new StringBuffer();
					
					while ((inputLine = incoming.readLine()) != null)
					{
						response.append(inputLine);
					}
					
					incoming.close();
					
					if (responseCode == 200)
					{
						JsonObject resp = new JsonParser().parse(response.toString()).getAsJsonObject();
						String status = resp.get("status").getAsString();
						String data = resp.get("data").getAsString();
						responseReceivedRecruitMod(status, data, spectralCommand);
					}
					else
					{
						String data = "Something went wrong. The Discord app couldn't process the request. Contact the developer about this issue.";
						responseReceivedRecruitMod("failure", data, spectralCommand);
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			});
		}
	}
	
	// This receives the returned value from the postRequestAsyncRecruitMod method.
	private void responseReceivedRecruitMod(String status, String data, SpectralClanMgmtPlugin.SpectralCommand spectralCommand)
	{
		// If the GameState isn't "LOGGED_IN" when the response is received, just shut down. Otherwise, call setRecruitMod.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			spectralClanMgmtPlugin.setRecruitMod(status, data, spectralCommand);
		}
		else
		{
			shutdown();
		}
	}
	
	protected void shutdown()
	{
		if (executorService != null)
		{
			executorService.shutdown();
			
			try 
			{
				executorService = null;
				// Once executorService is null, it's ready to be initialized again.
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
		}
	}
	
	protected boolean isReady()
	{
		if (executorService != null)
		{
			return false;
		}
		else
		{
			return true;
		}
	}
}