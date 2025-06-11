package com.spectralclanmgmt.competition;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.protobuf.InvalidProtocolBufferException;
import javax.inject.Inject;
import com.spectralclanmgmt.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.RuneLite;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import javax.inject.Singleton;
import java.util.Objects;

@Slf4j
@Singleton
public class SpectralCompetitionClient extends WebSocketListener implements AutoCloseable
{
	private SpectralCompetition competition;
	
	@Inject
	private OkHttpClient okHttpClient;
	
	@Inject
	private Gson gson;
	
	@Inject
	private SpectralClanMgmtConfig config;
	
	private WebSocket webSocket;
	
	private HttpUrl wsURL;
	
	private String playerName;
	
	private String acctHash;
	
	private String teamID;
	
	private String competitionID;
	
	private Boolean startingXPSent;
	
	@Inject
	public SpectralCompetitionClient(OkHttpClient okHttpClient, Gson gson, String playerName, String acctHash, String competitionID, String teamID, Boolean startingXPSent)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
		this.wsURL = HttpUrl.parse(config.scriptURL() + "/ws");
		this.playerName = playerName;
		this.acctHash = acctHash;
		this.competitionID = competitionID;
		this.teamID = teamID;
		this.startingXPSent = startingXPSent;
	}
	
	protected void connect()
	{
		if (playerName.equals("") || acctHash.equals("") || config.memberKey().equals("") || config.scriptURL().equals("") || competitionID.equals("") || teamID.equals(""))
		{
			throw new IllegalStateException("Can't connect, required connection values missing.");
		}
		
		Request request = new Request.Builder()
		.url(wsURL.newBuilder()
		.addQueryParameter("playerName", playerName)
		.addQueryParameter("acctHash", acctHash)
		.addQueryParameter("accessKey", config.memberKey())
		.addQueryParameter("competitionID", competitionID)
		.addQueryParameter("teamID", teamID)
		.build())
		.build();
		
		webSocket = okHttpClient.newWebSocket(request, this);
	}
	
	@Override
	public void close()
	{
		if (webSocket != null)
		{
			webSocket.close(1000, null);
		}
	}
	
	@Override
	public void onOpen(WebSocket webSocket, Response response)
	{
		log.info("Websocket {} opened", webSocket);
	}
	
	@Override
	public void onMessage(WebSocket webSocket, ByteString bytes)
	{
		
	}
	
	public void sendMessage()
	{
		
	}
	
	@Override
	public void onClosed(WebSocket webSocket, int code, String reason)
	{
		log.info("Websocket {} closed: {}/{}", webSocket, code, reason);
		this.webSocket = null;
	}
	
	@Override
	public void onFailure(WebSocket webSocket, Throwable t, Response response)
	{
		log.warn("Error in websocket: {}", response, t);
		this.webSocket = null;
	}
}
