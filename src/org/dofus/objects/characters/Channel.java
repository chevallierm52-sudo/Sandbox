package org.dofus.objects.characters;

import java.util.ArrayList;

public class Channel {

	ArrayList<String> channels = new ArrayList<String>(9);
	
	public Channel(ArrayList<String> channel) {
		this.channels = channel; 
	}
	 
	public void addChannel(String c) {
		if(!channels.contains(c))
			channels.add(c);
	}
	
	public void removeChannel(String c) {
		for(int size = 0; size < channels.size(); size++)
			if(channels.contains(c))
				channels.remove(c);
	}

	public String get() {
		StringBuilder channel = new StringBuilder();
		
		if(channels.isEmpty() || channels == null) //Connection RolePlayHandler()
			channels.add(getBase());
		
		for(int size = 0; size < channels.size(); size++)
			channel.append(channels.get(size));
		
		return channel.toString();
	}
	
	public String getBase() {
		return ChannelType.TEAM.getChannel() +
			   ChannelType.PARTY.getChannel() +
			   ChannelType.DEFAULT.getChannel() +
			   ChannelType.INFORMATION.getChannel();
	}

	public enum ChannelType {
		ALIGNEMENT("!"),
	    TEAM("#"), //
	    PARTY("$"), 
	    GUILDE("%"), 
	    DEFAULT("*"),
	    TRADE(":"),
	    RECRUITMENT("?"),
	    ADMIN("@"),
	    INFORMATION("i"); //TODO ^ channel
		
		private String channel;
		
		private ChannelType(String channel) {
			this.channel = channel;
		}
		
		public String getChannel() {
			return channel;
		}
	}
}
