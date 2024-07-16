package com.spectralclanmgmt;

/*
Explanation behind this class:

This is to hold the values for the random phrases that could be said when the !spectral command is issued.
This command, like the other Spectral commands, won't work outside of the clan chat channel. 
If it's used in any other channel, or by anyone that's not a ranked member of Spectral, 
nothing will be displayed in the chat, as the command is always consumed during processing
and there are checks to determine if output is allowed to be sent to the chat in place of the command.

Also, the script that supplies the phrases from Spectral's spreadsheet has a flag set that controls if the clan is
allowed to use the command, and a function to determine if a specific member is allowed to use the command.
We wanted to include this check in case our members ever use the command to be a nuisance in the clan chat.
While we want them to have a command that can be used just for fun, we'll take it away if they abuse it.
Permission checks are done via asynchronous post requests to the web app.
If the command isn't able to be used by a Spectral clan member for any reason, 
a game message will be sent to the game chat explaining why.

Since we want our clan members to remain informed of what could be sent to the clan chat under their name 
before they decide to use the command, all of the phrases available to the command are viewable in a private channel 
on Spectral's Discord server by server members with the in-game clan member role 
(b/c there are roles for people affiliated with the clan who aren't in it).
*/

public class SpectralClanMgmtCommandPhrases
{
	/* 
	Values for this string array only come from Spectral's private spreadsheet that only the clan's moderators can edit.
	All values on the phrases spreadsheet are verified by the clan's mods and leaders to adhere to OSRS's rules and Jagex's T&C.
	All values on the phrases spreadsheet are verified to not exceed the chat box's input limit before being added to the sheet. 
	The mods and leaders verify that the phrases don't contain links (or directions to a link), or anything that qualifies 
	as illegal or rule-breaking content as outlined in the Safety and Abuse section and the User Content section of Jagex's T&C.
	 */
	private String[] phrases;
	
	protected SpectralClanMgmtCommandPhrases()
	{
		this.phrases = null;
	}
	
	protected String[] getPhrases()
	{
		return this.phrases;
	}
	
	protected void setPhrases(String[] phrases)
	{
		// This will be set to either a string array or null. 
		this.phrases = phrases;
	}
}
