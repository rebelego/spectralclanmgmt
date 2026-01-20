package com.spectralclanmgmt;

import net.runelite.api.Client;
import java.io.*;
import java.util.concurrent.*;
import okhttp3.*;
import javax.inject.Inject;

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
		this.httpclient = okHttpClient.newBuilder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(1, TimeUnit.MINUTES).build();
		this.button = null;
	}
	
	protected void setButton(SpectralClanMgmtButton button)
	{
		this.button = button;
	}
	
	protected String getRequestAsyncPluginData(String player, String acctHash, int rank)
	{
		CompletableFuture<String> respBody = new CompletableFuture<>();
		
		HttpUrl url = HttpUrl.parse(config.scriptURL()).newBuilder()
		.addQueryParameter("task", "getData")
		.addQueryParameter("player", player)
		.addQueryParameter("acctHash", acctHash)
		.addQueryParameter("accessKey", config.memberKey())
		.addQueryParameter("rank", String.valueOf(rank))
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
	protected CompletableFuture<String> postRequestAsyncAdmin(String task, String firstArg, String secondArg, String thirdArg, String adminPlayer, String adminRank, String acctHash)
	{
		CompletableFuture<String> respBody = new CompletableFuture<>();
		
		String arg1 = "task";
		String arg2 = "";
		String arg3 = "";
		String arg4 = "";
		String arg5 = "adminPlayer";
		String arg6 = "accessKey";
		String arg7 = "acctHash";
		String arg8 = "adminRank";
		
		if (task.equalsIgnoreCase("add-new"))
		{
			arg2 = "joinDate";
			arg3 = "mainPlayer";
			arg4 = "rank";
		}
		else if (task.equalsIgnoreCase("add-alt"))
		{
			arg2 = "joinDate";
			arg3 = "mainAndAlt";
			arg4 = "altPlayerRank";
		}
		else if (task.equalsIgnoreCase("name-change"))
		{
			arg2 = "currentName";
			arg3 = "oldName";
			arg4 = "memberType";
		}
		else if (task.equalsIgnoreCase("rank-swap"))
		{
			arg2 = "oldMain";
			arg3 = "newMain";
			arg4 = "ranks";
		}
		else if (task.equalsIgnoreCase("discord-deserter") || task.equalsIgnoreCase("discord-returnee"))
		{
			arg2 = "mainPlayer";
		}
		
		HttpUrl admin = HttpUrl.parse(plugin.getAdminURL());
		
		String payload = "{\"" + arg1 + "\":\"" + task + "\",\"" + arg2 + "\":\"" + firstArg + "\",\"" + arg3 + "\":\"" + secondArg + "\",\"" + arg4 + "\":\"" + thirdArg + "\",\"" + arg5 + "\":\"" + adminPlayer + "\",\"" + arg6 + "\":\"" + config.memberKey() + "\",\"" + arg7 + "\":\"" + acctHash + "\",\"" + arg8 + "\":\"" + adminRank + "\"}";
		
		RequestBody body = RequestBody.create(MediaType.parse("application/json"), payload);
		
		Request request = new Request.Builder()
		.url(admin)
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
	protected CompletableFuture<String> postRequestAsyncRecruitMod(String task, String spectralCommand, String player, String acctHash, int rank)
	{
		CompletableFuture<String> respBody = new CompletableFuture<>();
		
		// URL of the web app for the script.
		HttpUrl url = HttpUrl.parse(config.scriptURL());
		String command = spectralCommand.substring(1);
		String payload = "{\"task\":\"" + task + "\",\"command\":\"" + command + "\",\"player\":\"" + player + "\",\"accessKey\":\"" + config.memberKey() + "\",\"acctHash\":\"" + acctHash + "\",\"rank\":\"" + String.valueOf(rank) + "\"}";
		
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
	protected CompletableFuture<String> postRequestAsyncAccessKey(String task, String player, String acctHash, int rank)
	{
		CompletableFuture<String> respBody = new CompletableFuture<>();
		
		HttpUrl url = HttpUrl.parse(config.scriptURL());
		String payload = "{\"task\":\"" + task + "\",\"player\":\"" + player + "\",\"acctHash\":\"" + acctHash + "\",\"rank\":\"" + String.valueOf(rank) + "\"}";
		
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
	
	protected CompletableFuture<String> postRequestAsyncRegisterPlayerID(String task, String player, String acctHash, int rank)
	{
		CompletableFuture<String> respBody = new CompletableFuture<>();
		
		HttpUrl url = HttpUrl.parse(config.scriptURL());
		String payload = "{\"task\":\"" + task + "\",\"player\":\"" + player + "\",\"acctHash\":\"" + acctHash + "\",\"rank\":\"" + String.valueOf(rank) + "\"}";
		
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
						respBody.complete(plugin.updateRegistered(response));
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