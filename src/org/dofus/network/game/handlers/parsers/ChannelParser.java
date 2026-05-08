package org.dofus.network.game.handlers.parsers;

import org.dofus.objects.actors.Characters;

public class ChannelParser {

	public static void change(Characters character, String packet) {
		String channel = packet.substring(3);
		switch(packet.charAt(2)) {
		case '+': //Add
			character.getChannel().addChannel(channel);
			break;
		case '-': //Remove
			character.getChannel().removeChannel(channel);
			break;
			//TODO: Make chr update, set timer save after 2min
		}
	}

}
