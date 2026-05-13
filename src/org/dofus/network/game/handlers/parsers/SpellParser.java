package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.SpellsData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.characters.Statistic;
import org.dofus.objects.spells.KnownSpell;
import org.dofus.utils.DeferredSaveService;

public class SpellParser {

    public static void parse(Characters character, IoSession session, String packet) {
        if(packet == null || packet.length() < 2) return;
        switch(packet.charAt(1)) {
            case 'B':
                boost(character, session, packet.substring(2));
                break;
            case 'M':
                move(character, session, packet.substring(2));
                break;
            case 'F':
                session.write("SFE");
                break;
            default:
                session.write("BN");
                break;
        }
    }

    private static void boost(Characters character, IoSession session, String rawSpellId) {
        int spellId;
        try { spellId = Integer.parseInt(rawSpellId); }
        catch(NumberFormatException e) { session.write("SUE"); return; }

        KnownSpell spell = character.getKnownSpell(spellId);
        if(spell == null || spell.getLevel() >= 6) {
            session.write("SUE");
            return;
        }

        int cost = spell.getBoostCost();
        if(character.getSpellPoint() < cost) {
            session.write("SUE");
            return;
        }

        spell.setLevel(spell.getLevel() + 1);
        character.setSpellPoint((short) (character.getSpellPoint() - cost));
        SpellsData.saveKnownSpell(character, spell);
        DeferredSaveService.schedule(character);

        session.write(SpellsData.buildSUPacket(spell));
        session.write(Statistic.getStatisticsMessage(character));
    }

    private static void move(Characters character, IoSession session, String raw) {
        String[] parts = raw.split("\\|", 2);
        if(parts.length != 2) {
            session.write("BN");
            return;
        }
        int spellId;
        try { spellId = Integer.parseInt(parts[0]); }
        catch(NumberFormatException e) { session.write("BN"); return; }

        if(!SpellsData.moveSpell(character, spellId, parts[1])) {
            session.write("BN");
            return;
        }
        session.write("BN");
    }
}
