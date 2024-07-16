package com.spectralclanmgmt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
	
	// This is the postRequestAsync method for the Discord-related commands.
	// It sends the player's in-game name and a string value (not related to the player) to the clan's Discord web server.
	protected void postRequestAsync(String commandUsed, String localPlayer, int chatType, int chatTarget)
	{
		if (executorService != null)
		{
			// On the off-chance someone stupidly changes the value for the URL to something invalid, we'll check again here before executing.
			if (spectralClanMgmtPlugin.checkURL("discord"))
			{
				executorService.execute(() ->
				{
					try
					{
						// URL of the web app for the script.
						String url = config.spectralDiscordAppURL();
						StringBuilder postData = new StringBuilder();
						String command = commandUsed.substring(1);
						
						postData.append("{");
						postData.append("\"commandUsed\": \"").append(command).append("\", ");
						postData.append("\"player\": \"").append(localPlayer).append("\", ");
						postData.append("}");
						
						String payload = postData.toString();
						
						URL obj = new URL(url);
						HttpURLConnection con = (HttpURLConnection)obj.openConnection();
						
						con.setRequestMethod("POST");
						con.setRequestProperty("Content-Type", "application/json");
						
						con.setDoOutput(true);
						OutputStream output = con.getOutputStream();
						byte[] input = payload.getBytes(StandardCharsets.UTF_8);
						output.write(input, 0, input.length);
						
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
							responseReceived(status, chatType, chatTarget, data, commandUsed);
						}
						else
						{
							String data = "Something went wrong. The Discord app couldn't process the request. Contact the developer about this issue.";
							responseReceived("failure", chatType, chatTarget, data, commandUsed);
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
				String data = "The URL for Spectral's Discord Web API is either missing or not valid. Contact the developer about this issue.";
				responseReceived("failure", chatType, chatTarget, data, commandUsed);
			}
		}
		else
		{
			String data = "The executor wasn't initialized. Contact the developer about this issue.";
			responseReceived("failure", chatType, chatTarget, data, commandUsed);
		}
	}
	
	// This is the non-admin postRequestAsync method.
	// It sends the player's in-game name and several string values (not related to the player) to the clan's web app.
	protected void postRequestAsync(String task, Optional<String> localPlayer, Optional<String> source, Optional<Integer> chatType, Optional<Integer> chatTarget)
	{
		String src = source.orElse("");
		int cType = chatType.orElse(-1);
		int cTarget = chatTarget.orElse(-1);
		String secondArg = localPlayer.orElse("");
		
		if (executorService != null)
		{
			// On the off-chance someone stupidly changes the value for the URL to something invalid, we'll check again here before executing.
			if (spectralClanMgmtPlugin.checkURL("normal"))
			{
				executorService.execute(() ->
				{
					try
					{
						// URL of the web app for the script.
						String url = config.scriptURL();
						
						StringBuilder postData = new StringBuilder();
						
						if (task.equalsIgnoreCase("permission") || task.equalsIgnoreCase("config"))
						{
							postData.append(URLEncoder.encode("task", "UTF-8"));
							postData.append('=');
							postData.append(URLEncoder.encode(task, "UTF-8"));
							postData.append('&');
							if (task.equalsIgnoreCase("permission"))
							{
								postData.append(URLEncoder.encode("player", "UTF-8"));
							}
							else if (task.equalsIgnoreCase("config"))
							{
								postData.append(URLEncoder.encode("configlink", "UTF-8"));
							}
							postData.append('=');
							postData.append(URLEncoder.encode(secondArg, "UTF-8"));
						}
						else if (task.equalsIgnoreCase("phrases"))
						{
							postData.append(URLEncoder.encode("task", "UTF-8"));
							postData.append('=');
							postData.append(URLEncoder.encode(task, "UTF-8"));
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
							
							if (status.equalsIgnoreCase("success"))
							{
								if (task.equalsIgnoreCase("permission"))
								{
									boolean data = resp.get("data").getAsBoolean();
									responseReceived(data, src, cType, cTarget);
								}
								else if (task.equalsIgnoreCase("phrases"))
								{
									String data = resp.get("data").getAsString();
									responseReceived(status, data, src, cType, cTarget);
								}
								else if (task.equalsIgnoreCase("config"))
								{
									if (secondArg.equalsIgnoreCase("discord"))
									{
										String data = resp.get("data").getAsString();
										String[] links = new String[] { data };
										responseReceived(secondArg, src, links);
									}
									else if (secondArg.equalsIgnoreCase("both"))
									{
										JsonArray data = resp.get("data").getAsJsonArray();
										String discord = data.get(0).getAsString();
										String admin = data.get(1).getAsString();
										String[] links = new String[] { discord, admin };
										responseReceived(secondArg, src, links);
									}
								}
							}
							else
							{
								if (task.equalsIgnoreCase("permission"))
								{
									boolean data = false;
									responseReceived(data, src, cType, cTarget);
								}
								else if (task.equalsIgnoreCase("phrases"))
								{
									String data = "";
									responseReceived(status, data, src, cType, cTarget);
								}
								else if (task.equalsIgnoreCase("config"))
								{
									String[] links = new String[]{};
									responseReceived(secondArg, src, links);
								}
							}
						}
						else
						{
							if (task.equalsIgnoreCase("permission"))
							{
								responseReceived(false, src, cType, cTarget);
							}
							else if (task.equalsIgnoreCase("phrases"))
							{
								String data = "";
								responseReceived("failure", data, src, cType, cTarget);
							}
							else if (task.equalsIgnoreCase("config"))
							{
								String[] links = new String[]{};
								responseReceived(secondArg, src, links);
							}
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
				if (task.equalsIgnoreCase("permission"))
				{
					responseReceived(false, src, cType, cTarget);
				}
				else if (task.equalsIgnoreCase("phrases"))
				{
					String data = "";
					responseReceived("failure", data, src, cType, cTarget);
				}
				else if (task.equalsIgnoreCase("config"))
				{
					String[] links = new String[]{};
					responseReceived(secondArg, src, links);
				}
			}
		}
		else
		{
			if (task.equalsIgnoreCase("permission"))
			{
				responseReceived(false, src, cType, cTarget);
			}
			else if (task.equalsIgnoreCase("phrases"))
			{
				String data = "";
				responseReceived("failure", data, src, cType, cTarget);
			}
			else if (task.equalsIgnoreCase("config"))
			{
				String[] links = new String[]{};
				responseReceived(secondArg, src, links);
			}
		}
	}
	
	// This is the admin postRequestAsync method.
	// There are 3 http requests that each include different data that could be sent through this method.
	// 1. It will send the in-game name of a selected clan member along with their clan join date to the clan's web app.
	// 2. It will send the in-game name of a selected clan member along with their clan join date, 
	// along with the in-game name of another selected clan member, to the clan's web app.
	// 3. It will send the current in-game name, the previous in-game name of a selected clan member, and a string value to the clan's web app.
	// Although the string value is related to the player, it's for a value defined by the clan 
	// and not a personally identifying value linked to the player's data. The string value will either be 'main' or 'alt'.
	protected void postRequestAsync(String task, String firstArg, String secondArg, Optional<String> thirdArg)
	{
		if (executorService != null)
		{
			// On the off-chance someone stupidly changes the value for the URL to something invalid, we'll check again here before executing.
			if (spectralClanMgmtPlugin.checkURL("admin"))
			{
				executorService.execute(() ->
				{
					try
					{
						// URL of the web app for the script.
						String url = config.adminScriptURL();
						
						String optionalThirdArg = thirdArg.orElse("");
						
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
							postData.append(URLEncoder.encode(optionalThirdArg, "UTF-8"));
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
							postData.append(URLEncoder.encode(optionalThirdArg, "UTF-8"));
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
							
							responseReceived(task, status, data);
						}
						else
						{
							String data = "Something went wrong. Contact the developer about this issue.";
							responseReceived(task, "failure", data);
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
				String data = "The URL for Spectral's web app is either missing or not valid.";
				responseReceived("", "failure", data);
			}
		}
		else
		{
			String data = "The executor wasn't initialized. Contact the developer about this issue.";
			responseReceived("", "failure", data);
		}
	}
	
	
	// This method is for handling the returned value from the postRequestAsync method for Discord-related commands.
	private void responseReceived(String status, int cType, int cTarget, String data, String commandUsed)
	{
		// If the GameState isn't "LOGGED_IN" when the response is received, just shut down. Otherwise, call finishDiscordCommands.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			spectralClanMgmtPlugin.finishDiscordCommands(status, data, commandUsed, cType, cTarget);
		}
		else
		{
			shutdown();
		}
	}
	
	// This method is for handling the returned value from the admin postRequestAsync method.
	private void responseReceived(String task, String status, String data)
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
	
	// This method is for handling the returned value from the non-admin postRequestAsync method. The result of the config links check is passed here.
	private void responseReceived(String configLink, String src, String[] links)
	{
		
		spectralClanMgmtPlugin.setConfigLinks(configLink, links, Optional.of(src));
	}
	
	// This method is for handling the returned value from the non-admin postRequestAsync method. The result of the permissions check is passed here.
	private void responseReceived(boolean data, String src, int cType, int cTarget)
	{
		// If the GameState isn't "LOGGED_IN" when the response is received, just shut down. Otherwise, call getCommandPermission to pass back the result of the permission check.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			spectralClanMgmtPlugin.getCommandPermission(data, src, cType, cTarget);
		}
		else
		{
			shutdown();
		}
	}
	
	// This method is for handling the returned value from the non-admin postRequestAsync method. The result of the phrases check is passed here.
	private void responseReceived(String status, String data, String src, int cType, int cTarget)
	{
		// If the GameState isn't "LOGGED_IN" when the response is received, just shut down. Otherwise, call getPhrases to populate the spectralPhrases array with values.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			spectralClanMgmtPlugin.getPhrases(status, data, src, cType, cTarget);
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