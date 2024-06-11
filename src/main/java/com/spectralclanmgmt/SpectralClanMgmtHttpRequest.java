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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpectralClanMgmtHttpRequest
{
	private ExecutorService executorService;
	private SpectralClanMgmtButton spectralClanMgmtButton;
	private SpectralClanMgmtConfig config;
	private SpectralClanMgmtPlugin spectralClanMgmtPlugin;
	private Client client;
	
	public SpectralClanMgmtHttpRequest(SpectralClanMgmtButton spectralClanMgmtButton, SpectralClanMgmtPlugin spectralClanMgmtPlugin, SpectralClanMgmtConfig config, Client client)
	{
		this.spectralClanMgmtButton = spectralClanMgmtButton;
		this.spectralClanMgmtPlugin = spectralClanMgmtPlugin;
		this.config = config;
		this.client = client;
		executorService = Executors.newSingleThreadExecutor();
	}
	
	public void postRequestAsync(String task, String joinDate, String mainPlayer)
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
					System.out.println("Response code: " + responseCode);
					
					BufferedReader incoming = new BufferedReader(new InputStreamReader(con.getInputStream()));
					String inputLine;
					StringBuffer response = new StringBuffer();
					
					while ((inputLine = incoming.readLine()) != null)
					{
						response.append(inputLine);
					}
					
					incoming.close();
					
					JsonObject resp = new JsonParser().parse(response.toString()).getAsJsonObject();
					String status = resp.get("status").getAsString();
					String data = resp.get("data").getAsString();
					
					if (spectralClanMgmtButton != null)
					{
						// If the player is no longer logged in when the response is received, just shut down. Otherwise, call exportDone.
						if (client.getGameState() == GameState.LOGGED_IN)
						{
							// This will initiate the end portion of the export process.
							spectralClanMgmtButton.exportDone(status, data);
						}
						else
						{
							shutdown();
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
			spectralClanMgmtButton.exportDone("failure", "The script URL is either missing or not valid.");
		}
	}
	
	public void postRequestAsync(String task, String joinDate, String mainPlayer, String altPlayer)
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
					System.out.println("Response code: " + responseCode);
					
					BufferedReader incoming = new BufferedReader(new InputStreamReader(con.getInputStream()));
					String inputLine;
					StringBuffer response = new StringBuffer();
					
					while ((inputLine = incoming.readLine()) != null)
					{
						response.append(inputLine);
					}
					
					incoming.close();
					
					JsonObject resp = new JsonParser().parse(response.toString()).getAsJsonObject();
					String status = resp.get("status").getAsString();
					String data = resp.get("data").getAsString();
					
					if (spectralClanMgmtButton != null)
					{
						// If the player is no longer logged in when the response is received, just shut down. Otherwise, call exportDone.
						if (client.getGameState() == GameState.LOGGED_IN)
						{
							// This will initiate the end portion of the export process.
							spectralClanMgmtButton.exportDone(status, data);
						}
						else
						{
							shutdown();
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
			spectralClanMgmtButton.exportDone("failure", "The script URL is either missing or not valid.");
		}
	}
	
	public void shutdown()
	{
		executorService.shutdown();
	}
}