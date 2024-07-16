/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.spectralclanmgmt;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/*
An event called when input is typed into the chatbox and sent (modified for use by this plugin)
*/

@Getter
@EqualsAndHashCode(callSuper = true)
public class SpectralClanMgmtChatboxCommandInput extends SpectralClanMgmtChatCommandInput
{
	/*
	 the chatbox input
	 */
	private final String value;
	
	/*
	 * sent message type
	 *
	 * 0 = public
	 * 1 = cheat
	 * 2 = friends chat
	 * 3 = clan chat
	 * 4 = guest clan
	 */
	private final int chatType;
	
	private final int chatTarget;
	
	private final String spectralCommand;
	
	private final int rank;
	
	protected SpectralClanMgmtChatboxCommandInput(int rank, String spectralCommand, int chatTarget, String value, int chatType, Runnable resume)
	{
		super(resume);
		this.spectralCommand = spectralCommand;
		this.value = value;
		this.chatType = chatType;
		this.chatTarget = chatTarget;
		this.rank = rank;
	}
}