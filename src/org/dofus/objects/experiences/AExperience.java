package org.dofus.objects.experiences;

import org.dofus.objects.actors.Characters;

public abstract class AExperience {
	
    public abstract void add(long exp);
    public abstract void remove(long exp);
    public abstract void onLevelUp(long exp);
    public abstract void onLevelDown(long exp);
    public abstract long min();
    public abstract long max();
    
    public abstract short getLevel();
    public abstract long getExperience();
    public abstract Experience getTemplate();
    public abstract Characters getCharacter();
     
}
