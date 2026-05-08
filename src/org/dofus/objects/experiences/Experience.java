package org.dofus.objects.experiences;

public class Experience {

    private short level;
    private long character;
    private int job;
    private int mount;
    private short alignment;
    
    public Experience(short level, long character, int job, int mount, short alignment) {
        this.level = level;
        this.character = character;
        this.job = job;
        this.mount = mount;
        this.alignment = alignment;
    }
    
    public short getLevel() {
        return level;
    }

    public long getCharacter() {
        return character;
    }
 
    public int getJob() {
        return job;
    }

    public int getMount() {
        return mount;
    }

    public short getAlignment() {
        return alignment;
    }
}
