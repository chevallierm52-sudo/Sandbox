package org.dofus.objects.characters;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.mina.core.session.IoSession;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;

public class Party {

	private Characters chief;
	private ConcurrentMap<Integer, Characters> members = new ConcurrentHashMap<Integer, Characters>();

	public Party(Characters chief, Characters member) {
		setChief(chief);
		members.put(chief.getId(), chief);
		members.put(member.getId(), member);
	}

	public Characters getChief() { return chief; }
	public void setChief(Characters chief) { this.chief = chief; }
	public boolean isChief(Characters character) { return chief == character; }
	public ConcurrentMap<Integer, Characters> getMembers() { return members; }
	public void setMembers(ConcurrentMap<Integer, Characters> members) { this.members = members; }
	public void addMember(Characters character) { members.put(character.getId(), character); }
	public int getMembersSize() { return members.size(); }

	public int getMembersLevel() {
		int level = 0;
		for(Characters character : members.values())
			level += character.getExperience().getLevel();
		return level;
	}

	public void leave(Characters leaver) {
		leaver.setParty(null);
		members.remove(leaver.getId());

		IoSession leaverSession = WorldData.getSessionByAccount().get(leaver.getOwner());

		if(getMembersSize() == 1) {
			chief.setParty(null);

			if(leaverSession != null && leaverSession.isConnected())
				leaverSession.write("PV");

			IoSession chiefSession = WorldData.getSessionByAccount().get(chief.getOwner());
			if(chiefSession != null && chiefSession.isConnected())
				chiefSession.write("PV");
		} else {
			for(Characters member : members.values()) {
				IoSession memberSession = WorldData.getSessionByAccount().get(member.getOwner());
				if(memberSession != null && memberSession.isConnected())
					memberSession.write("PM-" + leaver.getId());
			}

			if(leaverSession != null && leaverSession.isConnected()) {
				leaverSession.write("PM-" + leaver.getId());
				leaverSession.write("PV");
			}
		}
	}
}
