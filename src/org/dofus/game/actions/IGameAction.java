package org.dofus.game.actions;

import java.util.HashMap;
import java.util.Map;

public interface IGameAction {
	
    GameActionType getActionType();

    void begin();
    void end();
    void cancel();

    public enum GameActionType {
        MOVEMENT,
        CHALLENGE_REQUEST_INVITATION,
    }

    public enum ActionTypeEnum {
        MOVEMENT(1),
        MAP_CHANGEMENT(2),
        CELL_CHANGEMENT(4),
        CELL_SLIDE(5),
        MP_CHANGEMENT(129),
        AP_CHANGEMENT(102),
        INFLICT_DAMAGE(100),
        KILL_UNIT(103),
        BLOCKED_DAMAGE(105),
        SUMMONED(181),
        LAUNCH_SPELL(300),
        SPELL_CRITICAL(301),
        SPELL_FAILURE(302),
        MELEE_ATACK(303),
        MAP_ACTION(500),
        ASK_FIGHT(900),
        ACCEPT_FIGHT(901),
        DECLINE_FIGHT(902),
        JOIN_FIGHT(903),
        FIGHTER_STATE(950),
        FIGHT_AGGRESSION(906),
        TURN_LIST(999);

        private int value;

        private ActionTypeEnum(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        private static final Map<Integer, ActionTypeEnum> values = new HashMap<>();

        static {
            for(ActionTypeEnum e : values())
                values.put(e.value(), e);
        }

        public static ActionTypeEnum valueOf(int ordinal) {
            return values.get(ordinal);
        }
    }
}
