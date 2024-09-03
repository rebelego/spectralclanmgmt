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
	protected SpectralClanMgmtHttpRequest(SpectralClanMgmtPlugin plugin, SpectralClanMgmtConfig config, Client client, OkHttpClient okHttpClient)
	{
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		this.httpclient = okHttpClient.newBuilder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(6, TimeUnit.MINUTES).build();
		this.button = null;
	}
	
	protected void setButton(SpectralClanMgmtButton button)
	{
		this.button = button;
	}
	
	// For getting the permissions, config links, and phrases all at once.
	// This will be called after start up and when a command is used and it's been at least 5 minutes since the permissions were last checked.
	protected String getRequestAsyncPluginData(String configLink, String player, String acctHash)
	{
		if (!configLink.equalsIgnoreCase("discord") && !configLink.equalsIgnoreCase("both") && !configLink.equalsIgnoreCase("reg-fail"))
		{
			return configLink;
		}
		
		CompletableFuture<String> respBody = new CompletableFuture<>();
		
		HttpUrl url = HttpUrl.parse(config.scriptURL()).newBuilder()
		.addQueryParameter("configLink", configLink)
		.addQueryParameter("player", player)
		.addQueryParameter("acctHash", acctHash)
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
					respBody.completeExceptionally(new IOException("Something went wrong. Report this issue with this response code to the developer: " + response.toString()));
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
	protected CompletableFuture<String> postRequestAsyncAdmin(String task, String firstArg, String secondArg, String thirdArg, String adminPlayer, String acctHash)
	{
		CompletableFuture<String> respBody = new CompletableFuture<>();
		
		String arg1 = "task";
		String arg2 = "";
		String arg3 = "";
		String arg4 = "";
		String arg5 = "adminPlayer";
		String arg6 = "accessKey";
		String arg7 = "acctHash";
		
		if (task.equalsIgnoreCase("add-new"))
		{
			arg2 = "joinDate";
			arg3 = "mainPlayer";
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
		else if (task.equalsIgnoreCase("rank-swap"))
		{
			arg2 = "oldMain";
			arg3 = "newMain";
		}
		else if (task.equalsIgnoreCase("discord-deserter") || task.equalsIgnoreCase("discord-returnee"))
		{
			arg2 = "mainPlayer";
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
		
		if (task.equalsIgnoreCase("revoke-permission") || task.equalsIgnoreCase("restore-permission") || task.equalsIgnoreCase("add-new") || task.equalsIgnoreCase("rank-swap"))
		{
			payload = "{\"" + arg1 + "\":\"" + task + "\",\"" + arg2 + "\":\"" + firstArg + "\",\"" + arg3 + "\":\"" + secondArg + "\",\"" + arg5 + "\":\"" + adminPlayer + "\",\"" + arg6 + "\":\"" + config.memberKey() + "\",\"" + arg7 + "\":\"" + acctHash + "\"}";
		}
		else if (task.equalsIgnoreCase("discord-deserter"))
		{
			payload = "{\"" + arg1 + "\":\"" + task + "\",\"" + arg2 + "\":\"" + firstArg + "\",\"" + arg5 + "\":\"" + adminPlayer + "\",\"" + arg6 + "\":\"" + config.memberKey() + "\",\"" + arg7 + "\":\"" + acctHash + "\"}";
		}
		else
		{
			payload = "{\"" + arg1 + "\":\"" + task + "\",\"" + arg2 + "\":\"" + firstArg + "\",\"" + arg3 + "\":\"" + secondArg + "\",\"" + arg4 + "\":\"" + thirdArg + "\",\"" + arg5 + "\":\"" + adminPlayer + "\",\"" + arg6 + "\":\"" + config.memberKey() + "\",\"" + arg7 + "\":\"" + acctHash + "\"}";
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
					respBody.completeExceptionally(new IOException("Something went wrong. Report this issue with this response code to the developer: " + response.toString()));
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
	This is the postRequestAsync method for the Discord-related commands.
	*/
	protected CompletableFuture<String> postRequestAsyncRecruitMod(String task, String spectralCommand, String player, String acctHash)
	{
		CompletableFuture<String> respBody = new CompletableFuture<>();
		
		// URL of the web app for the script.
		HttpUrl url = HttpUrl.parse(config.scriptURL());
		String command = spectralCommand.substring(1);
		String payload = "{\"task\":\"" + task + "\",\"command\":\"" + command + "\",\"player\":\"" + player + "\",\"accessKey\":\"" + config.memberKey() + "\",\"acctHash\":\"" + acctHash + "\"}";
		
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
					respBody.completeExceptionally(new IOException("Something went wrong. Report this issue with this response code to the developer: " + response.toString()));
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
	
	/*
	This is the postRequestAsync method for retrieving a member's access key.
	*/
	protected CompletableFuture<String> postRequestAsyncAccessKey(String task, String player, String acctHash)
	{
		CompletableFuture<String> respBody = new CompletableFuture<>();
		
		HttpUrl url = HttpUrl.parse(config.scriptURL());
		String payload = "{\"task\":\"" + task + "\",\"player\":\"" + player + "\",\"acctHash\":\"" + acctHash + "\"}";
		
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
					respBody.completeExceptionally(new IOException("Something went wrong. Report this issue with this response code to the developer: " + response.toString()));
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
	
	/*
	This is the postRequestAsync method for processing and storing a player's account hash in a private data storage location.
	This will send an http request to Spectral's Discord application and trigger its Discord bot to ping the member who used the !addme command
	in Spectral's private Discord server to confirm that they initiated the request. Only the member can respond to the bot's message, 
	and the member has up to 5 minutes to respond to it in the server before the application will return a failed response. 
	Once the member confirms that they initiated the request, the application will process and store the account hash and return a response.
	Including the player's name and account hash, along with a private access key, was the best option I could come up with for 
	validating if any http requests sent to Spectral's web app came from this plugin.
	*/
	protected CompletableFuture<String> postRequestAsyncRegisterPlayerID(String task, String player, String acctHash)
	{
		CompletableFuture<String> respBody = new CompletableFuture<>();
		
		HttpUrl url = HttpUrl.parse(plugin.getDiscordURL());
		String payload = "{\"task\":\"" + task + "\",\"player\":\"" + player + "\",\"acctHash\":\"" + acctHash + "\"}";
		
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
					respBody.completeExceptionally(new IOException("Something went wrong. Report this issue with this response code to the developer: " + response.toString()));
				}
				else
				{
					try
					{
						respBody.complete(plugin.updateRegistered(player, response));
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