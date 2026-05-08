package org.dofus.network.server.handlers;

import org.dofus.constants.EApplication;
import org.dofus.constants.ServersInformation;
import org.dofus.constants.ServersInformation.Community;
import org.dofus.database.objects.AccountsData;
import org.dofus.database.objects.CharactersData;

import org.dofus.network.server.Server;
import org.dofus.network.server.ServerClient;
import org.dofus.network.server.ServerClientHandler;
import org.dofus.objects.actors.Characters;

public class ServerChoiceHandler extends ServerClientHandler {

	protected ServerChoiceHandler(Server server, ServerClient client) {
		super(server, client);

		AccountsData.addAccountByKey(client.getAccount(), client.getKey()); //Key for the game
		
		client.getSession().write("Ad" + client.getAccount().getNickname());
		client.getSession().write("Ac" + Community.FRENCH.ordinal()); //Community
		client.getSession().write(ServersInformation.get());
		client.getSession().write("AlK1");
		client.getSession().write("AQ" + client.getAccount().getQuestion().replace(" ", "+"));
		
		CharactersData.load(client.getAccount());
		
	}

	@Override
	public void parse(String packet) throws Exception {
        switch(packet.charAt(1)) {
	        case 'F':
	        	//TODO: Search a friend
	            break;
	        case 'X':
	        	client.getSession().write(new StringBuilder()
	    				.append("AYK")
	    				.append("127.0.0.1").append(":")
	    				.append(5555).append(";")
	    				.append(client.getKey()).toString());
	            break;
	        case 'x':
	        	//Count character(s) per server(s)
	        	int chrSize = 0;
	    		for(Characters chr : client.getAccount().getCharacters().values())
	    			if(client.getAccount().getId() == chr.getOwner().getId())
	    				chrSize++;
	    		
	    		client.getSession().write(new StringBuilder(
	    				10 + chrSize * 5)
	    				.append("AxK")
	    				.append(EApplication.SUBSCRIPTION_DURATION.getlValue())
	    		 		.append('|')
	    		 		.append(ServersInformation.getServerId()).append(',') //Server id
	    		 		.append(chrSize).toString());
	            break;
        }
	}

	@Override
	public void onClosed() {
		System.out.println("ServerChoiceHandler : onClosed()");
	}

}
