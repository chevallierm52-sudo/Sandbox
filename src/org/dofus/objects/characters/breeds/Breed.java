package org.dofus.objects.characters.breeds;

public class Breed {

	private byte id;
	private short life;
	private int ap;
	private int mp;
	private int prospecting;

	/*TODO private MapTemplate mapId;
	private int cellId;*/
	
	public Breed(byte id, short life, int ap, int mp, int prospecting) {
		this.setId(id);
		this.setLife(life);
		this.setAp(ap);
		this.setMp(mp);
		this.setProspecting(prospecting);
	}
 
	public byte getId() {
		return id;
	}

	public void setId(byte id) {
		this.id = id;
	}

	public short getLife() {
		return life;
	}

	public void setLife(short life) {
		this.life = life;
	}

	public int getAp() {
		return ap;
	}

	public void setAp(int ap) {
		this.ap = ap;
	}

	public int getMp() {
		return mp;
	}

	public void setMp(int mp) {
		this.mp = mp;
	}

	public int getProspecting() {
		return prospecting;
	}

	public void setProspecting(int prospecting) {
		this.prospecting = prospecting;
	}
	
	public enum BreedType {
		FECA(1),
		OSAMODAS(2),
		ENUTROF(3),
		SRAM(4),
		XELOR(5),
		ECAFLIP(6),
		ENIRIPSA(7),
		IOP(8),
		CRA(9),
		SADIDA(10),
		SACRIEUR(11),
		PANDAWA(12);
		
		public int value;
		
		BreedType(int value) { 
			this.value = value;
		}
		
		public int getValue() {
			return value;
		}
	}
}
