package com.spectralclanmgmt;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.runelite.api.Client;
import net.runelite.api.GameState;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.util.concurrent.*;

public class SpectralClanMgmtHttpRequest
{
	public ExecutorService executorService;
	private SpectralClanMgmtButton spectralClanMgmtButton;
	private SpectralClanMgmtConfig config;
	private SpectralClanMgmtPlugin spectralClanMgmtPlugin;
	private Client client;
	
	public SpectralClanMgmtHttpRequest(SpectralClanMgmtPlugin spectralClanMgmtPlugin, SpectralClanMgmtConfig config, Client client)
	{
		this.spectralClanMgmtPlugin = spectralClanMgmtPlugin;
		this.config = config;
		this.client = client;
		executorService = null;
	}
	
	public void setSpectralClanMgmtButton(SpectralClanMgmtButton spectralClanMgmtButton)
	{
		this.spectralClanMgmtButton = spectralClanMgmtButton;
	}
	
	public void initializeExecutor()
	{
		if (executorService == null)
		{
			executorService = Executors.newSingleThreadExecutor();
		}
	}
	
	// The method for posting new Main clan member exports.
	public void postRequestAsync(String task, String joinDate, String mainPlayer)
	{
		if (executorService != null)
		{
			// On the off-chance someone stupidly changes the value for the URL to something invalid, we'll check again here before executing.
			if (spectralClanMgmtPlugin.checkURL() == true)
			{
				executorService.execute(() ->
				{
					try
					{
						// URL of the web app for the script.
						String url = config.scriptURL();
						
						StringBuilder postData = new StringBuilder();
						
						postData.append(URLEncoder.encode("task", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(task, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("joinDate", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(joinDate, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("mainPlayer", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(mainPlayer, "UTF-8"));
						
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
							
							responseReceived("add-new", status, data);
						}
						else
						{
							responseReceived("add-new", "failure", "Something went wrong.");
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
				responseReceived("", "failure", "The script URL is either missing or not valid.");
			}
		}
		else
		{
			responseReceived("", "failure", "You have to initialize the executor service first.");
		}
	}
	
	// The method for posting new Alt clan member exports.
	public void postRequestAsync(String task, String joinDate, String mainPlayer, String altPlayer)
	{
		if (executorService != null)
		{
			// On the off-chance someone stupidly changes the value for the URL to something invalid, 
			// it'll check again before executing.
			if (spectralClanMgmtPlugin.checkURL() == true)
			{
				executorService.execute(() ->
				{
					try
					{
						// URL of the web app for the script.
						String url = config.scriptURL();
						
						StringBuilder postData = new StringBuilder();
						
						postData.append(URLEncoder.encode("task", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(task, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("joinDate", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(joinDate, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("mainPlayer", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(mainPlayer, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("altPlayer", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(altPlayer, "UTF-8"));
						
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
							
							responseReceived("add-alt", status, data);
						}
						else
						{
							responseReceived("add-alt", "failure", "Something went wrong.");
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
				responseReceived("", "failure", "The script URL is either missing or not valid.");
			}
		}
		else
		{
			responseReceived("", "failure", "You have to initialize the executor service first.");
		}
	}
	
	// The method for posting clan member name change exports.
	// The nameChangeFlag is just there to make this method different from the other one that accepts four String arguments.
	public void postRequestAsync(String task, String currentName, String oldName, String memberType, boolean nameChangeFlag)
	{
		if (executorService != null)
		{
			// On the off-chance someone stupidly changes the value for the URL to something invalid, 
			// it'll check again before executing.
			if (spectralClanMgmtPlugin.checkURL() == true)
			{
				executorService.execute(() ->
				{
					try
					{
						// URL of the web app for the script.
						String url = config.scriptURL();
						
						StringBuilder postData = new StringBuilder();
						
						postData.append(URLEncoder.encode("task", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(task, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("currentName", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(currentName, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("oldName", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(oldName, "UTF-8"));
						postData.append('&');
						postData.append(URLEncoder.encode("memberType", "UTF-8"));
						postData.append('=');
						postData.append(URLEncoder.encode(memberType, "UTF-8"));
						
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
							
							responseReceived("name-change", status, data);
						}
						else
						{
							responseReceived("name-change", "failure", "Something went wrong.");
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
				responseReceived("", "failure", "The script URL is either missing or not valid.");
			}
		}
		else
		{
			responseReceived("", "failure", "You have to initialize the executor service first.");
		}
	}
	
	// It made more sense to just move this code to its own method rather than have the same code duplicated in both of the post methods.
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
	
	public void shutdown()
	{
		if (executorService != null)
		{
			executorService.shutdown();
			
			try 
			{
				executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
				executorService = null;
				// Once executorService is null, it's ready to be initialized again.
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
		}
	}
	
	// This is to make sure that the player won't be able to click the button to start another export
	// until the executorService is null again. It would just cause problems if I let multiple requests get queued.
	public boolean isReady()
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