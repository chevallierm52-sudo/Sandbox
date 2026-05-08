package org.dofus.network.game.protocols;

import java.util.Map;

import org.dofus.objects.actors.Characters;
import org.dofus.utils.StringUtils;

public class GProtocol {

	public static String showActorsMessage(Map<Integer, Characters> character) {
	    StringBuilder sb = new StringBuilder(10 + 30 * character.size()).append("GM");
	    for(Characters actor : character.values()) {
	        sb.append("|+");
	        GProtocol.getCharacterPattern(sb, actor);
	    }
	    return sb.toString();
	}

	public static void getCharacterPattern(StringBuilder sb, Characters player){
	    sb.append(player.getCurrentCell()).append(';')
	      .append(player.getCurrentOrientation().ordinal()).append(';')
	      .append("0;");
	
	    sb.append(player.getId()).append(';')
	      .append(player.getName()).append(';')
	      .append(player.getBreed().getId()).append(';')
	      .append(player.getSkin()).append('^').append(player.getSize()).append(';')
	      .append(player.getGender()).append(';');
	    
	    sb.append(player.getAlignmentType()).append(",100,"); //0 = alignment dons /100 TODO
	    sb.append(player.getAlignment().getLevel()).append(",");
	    sb.append(0); //TODO?
	    sb.append(",").append(player.getAlignment().getDishonor() > 0 ? 1 : 0).append(';');
	
	    sb.append(StringUtils.toHexOrNegative(player.getColor1())).append(';')
	      .append(StringUtils.toHexOrNegative(player.getColor2())).append(';')
	      .append(StringUtils.toHexOrNegative(player.getColor3())).append(';');
	
	    /*boolean first = true;
	    for (int accessory : player.getAccessories()){
	        if (first) first = false;
	        else sb.append(',');
	
	        sb.append(accessory == 0 ? "" : StringUtil.toHex(accessory));
	    }*/
	    sb.append(';');
	
	    sb.append(player.getExperience().getLevel() >= 100 ? player.getExperience().getLevel() == 200 ? '2' : '1' : '0');
	
	    sb.append(';')
	      .append(';');
	
	    sb.append(';');//Guild name
	
	    sb.append(';')
	      .append("0;;");
	}

}
