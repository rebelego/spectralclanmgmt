package com.spectralclanmgmt;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import java.io.*;
import java.util.concurrent.*;

import okhttp3.*;
import javax.inject.Inject;

@Slf4j
public class SpectralClanMgmtHttpRequest
{
	@Inject
	private OkHttpClient httpclient;
	
	private SpectralClanMgmtConfig config;
	
	private SpectralClanMgmtPlugin plugin;
	
	private SpectralClanMgmtButton button;
	
	private Client client;
	
	private boolean isReady = true;
	
	@Inject
	protected SpectralClanMgmtHttpRequest(SpectralClanMgmtPlugin plugin, SpectralClanMgmtConfig config, Client client, OkHttpClient httpclient)
	{
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		this.httpclient = httpclient;
		this.button = null;
	}
	
	protected void setButton(SpectralClanMgmtButton button)
	{
		this.button = button;
	}
	
	// For getting the permissions, config links, and phrases all at once.
	// This will be called after start up and when a command is used and it's been at least 5 minutes since the permissions were last checked.
	protected String getRequestAsyncPluginData(String configLink, String player)
	{
		if (!configLink.equalsIgnoreCase("discord") && !configLink.equalsIgnoreCase("both"))
		{
			return configLink;
		}
		
		CompletableFuture<String> respBody = new CompletableFuture<>();
		
		HttpUrl url = HttpUrl.parse(config.scriptURL()).newBuilder()
		.addQueryParameter("configLink", configLink)
		.addQueryParameter("player", player)
		.addQueryParameter("accessKey", config.memberKey())
		.build();
		
		Request request = new Request.Builder()
									 .url(url.toString())
									 .get()
									 .build();
		
		httpclient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				respBody.completeExceptionally(e);
			}
			
			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				if (!response.isSuccessful())
				{
					respBody.completeExceptionally(new IOException("Something went wrong.<br>Report this issue with this response code to the developer: " + response.toString()));
				}
				else
				{
					try
					{
						respBody.complete(plugin.setPluginData(response));
					}
					finally
					{
						response.close();
					}
				}
			}
		});
		
		return respBody.join();
	}
	
	/* 
	This is for the Admin-related export tasks in the SpectralClanMgmtButton class (new member additions and name changes).
	 */
	protected CompletableFuture<String> postRequestAsyncAdmin(String task, String firstArg, String secondArg, String thirdArg)
	{
		CompletableFuture<String> respBody = new CompletableFuture<>();
		
		String arg1 = "task";
		String arg2 = "";
		String arg3 = "";
		String arg4 = "";
		
		if (task.equalsIgnoreCase("add-new"))
		{
			arg2 = "joinDate";
			arg3 = "mainPlayer";
			arg4 = "adminPlayer";
		}
		else if (task.equalsIgnoreCase("add-alt"))
		{
			arg2 = "joinDate";
			arg3 = "mainPlayer";
			arg4 = "altPlayer";
		}
		else if (task.equalsIgnoreCase("name-change"))
		{
			arg2 = "currentName";
			arg3 = "oldName";
			arg4 = "memberType";
		}
		else if (task.equalsIgnoreCase("revoke-permission") || task.equalsIgnoreCase("restore-permission"))
		{
			arg2 = "player";
			arg3 = "category";
		}
		
		HttpUrl adminURL;
		
		// URL of the web app for the script.
		if (task.equalsIgnoreCase("revoke-permission") || task.equalsIgnoreCase("restore-permission"))
		{
			adminURL = HttpUrl.parse(config.scriptURL());
		}
		else
		{
			adminURL = HttpUrl.parse(plugin.getAdminURL());
		}
		
		String payload = "";
		
		if (task.equalsIgnoreCase("revoke-permission") || task.equalsIgnoreCase("restore-permission"))
		{
			payload = "{\"" + arg1 + "\":\"" + task + "\",\"" + arg2 + "\":\"" + firstArg + "\",\"" + arg3 + "\":\"" + thirdArg + "\"}";
		}
		else
		{
			payload = "{\"" + arg1 + "\":\"" + task + "\",\"" + arg2 + "\":\"" + firstArg + "\",\"" + arg3 + "\":\"" + secondArg + "\",\"" + arg4 + "\":\"" + thirdArg + "\"}";
		}
		
		RequestBody body = RequestBody.create(MediaType.parse("application/json"), payload);
		
		Request request = new Request.Builder()
									 .url(adminURL)
									 .post(body)
									 .addHeader("Content-Type", "application/json")
									 .build();
		
		httpclient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				respBody.completeExceptionally(e);
			}
			
			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				if (!response.isSuccessful())
				{
					respBody.completeExceptionally(new IOException("Something went wrong.<br>Report this issue with this response code to the developer: " + response.toString()));
				}
				else
				{
					try
					{
						respBody.complete(button.exportDone(task, response));
					}
					finally
					{
						response.close();
					}
				}
			}
		});
		
		return respBody;
	}
	
	/*
	This is the postRequestAsync method for the Discord-related commands. The requests are sent to Spectral's Discord web api.
	The task argument is 'discord', it's used for routing the request in the web api.
	The spectralCommand object is used to retrieve the command the local player sent to the chat. The object itself won't be included in the request.
	The player argument is for the name of the local player.
	The task and player arguments, along with the command text, are included as parameters in the post request.
	*/
	protected CompletableFuture<String> postRequestAsyncRecruitMod(String task, String spectralCommand, String player)
	{
		CompletableFuture<String> respBody = new CompletableFuture<>();
		
		// URL of the web app for the script.
		HttpUrl url = HttpUrl.parse(plugin.getDiscordURL());
		String command = spectralCommand.substring(1);
		String payload = "{\"task\":\"" + task + "\",\"command\":\"" + command + "\",\"player\":\"" + player + "\"}";
		
		RequestBody body = RequestBody.create(MediaType.parse("application/json"), payload);
		
		Request request = new Request.Builder()
		.url(url)
		.post(body)
		.addHeader("Content-Type", "application/json")
		.build();
		
		httpclient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				respBody.completeExceptionally(e);
			}
			
			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				if (!response.isSuccessful())
				{
					respBody.completeExceptionally(new IOException("Something went wrong.<br>Report this issue with this response code to the developer: " + response.toString()));
				}
				else
				{
					try
					{
						respBody.complete(plugin.setModRecruit(response));
					}
					finally
					{
						response.close();
					}
				}
			}
		});
		
		return respBody;
	}
	
	protected CompletableFuture<String> postRequestAsyncAccessKey(String task, String player)
	{
		CompletableFuture<String> respBody = new CompletableFuture<>();
		
		// URL of the web app for the script.
		HttpUrl url = HttpUrl.parse(config.scriptURL());
		String payload = "{\"task\":\"" + task + "\",\"player\":\"" + player + "\"}";
		
		RequestBody body = RequestBody.create(MediaType.parse("application/json"), payload);
		
		Request request = new Request.Builder()
		.url(url)
		.post(body)
		.addHeader("Content-Type", "application/json")
		.build();
		
		httpclient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				respBody.completeExceptionally(e);
			}
			
			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				if (!response.isSuccessful())
				{
					respBody.completeExceptionally(new IOException("Something went wrong.<br>Report this issue with this response code to the developer: " + response.toString()));
				}
				else
				{
					try
					{
						respBody.complete(plugin.setAccessKey(response));
					}
					finally
					{
						response.close();
					}
				}
			}
		});
		
		return respBody;
	}
	
	protected boolean getIsReady()
	{
		return this.isReady;
	}
	
	protected void setIsReady(boolean value)
	{
		this.isReady = value;
	}
}