package org.dofus.network.game.protocols;

import java.util.Map;

import org.dofus.objects.actors.Characters;
import org.dofus.objects.actors.NPC;
import org.dofus.objects.actors.NpcTemplate;
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

	/**
	 * Entrée GM d'un PNJ (format Dofus 1.29, confirmé Shivas + AncestraRemake) :
	 *   cell;dir;0;actorId;templateId;-4;gfxId^scale;sex;color1;color2;color3;accessories;extraClip;customArtWork
	 *
	 * Le client Flash distingue un PNJ d'un personnage via le champ 6 :
	 *   - Personnage : field6 = classe (1-12, positif)
	 *   - PNJ        : field6 = -4 (négatif = discriminant NPC)
	 * Sans templateId et -4, le client tente de parser le PNJ comme un
	 * personnage → exception interne → écran noir.
	 *
	 * Scale : gfxId^size si scaleX==scaleY, sinon gfxId^scaleXxscaleY.
	 * extraClip : toujours -1 (valeurs DB non nulles peuvent crasher Flash).
	 */
	public static void getNpcPattern(StringBuilder sb, NPC npc) {
		NpcTemplate tpl = npc.getTemplate();
		sb.append(npc.getCellId()).append(';')
		  .append(npc.getOrientation().ordinal()).append(';')
		  .append("0;")
		  .append(npc.getActorId()).append(';')
		  .append(tpl.getId()).append(';')   // templateId (champ 5)
		  .append("-4;")                     // type NPC   (champ 6)
		  .append(tpl.getGfxID()).append('^');
		if (tpl.getScaleX() == tpl.getScaleY()) {
		    sb.append(tpl.getScaleX());
		} else {
		    sb.append(tpl.getScaleX()).append('x').append(tpl.getScaleY());
		}
		sb.append(';')
		  .append(tpl.getSex()).append(';')
		  .append(StringUtils.toHexOrNegative(tpl.getColor1())).append(';')
		  .append(StringUtils.toHexOrNegative(tpl.getColor2())).append(';')
		  .append(StringUtils.toHexOrNegative(tpl.getColor3())).append(';')
		  .append(tpl.buildAccessories()).append(';')
		  .append(-1).append(';')   // extraClip : toujours -1
		  .append(0);               // customArtWork : toujours 0
	}

}
