package org.dofus.objects.accounts;

public enum EPermission {
	
	NORMAL(0),
	BANNED(1),
	SERVER(2);
	
	private int value;
    
    EPermission(int value) {
        this.value = value;
    }
    
    public int value(){
        return value;
    }
    
    public boolean superior(EPermission permissions) {
        return this.value > permissions.value;
    }
    
    public boolean inferior(EPermission permissions) {
        return this.value < permissions.value;
    }
    
    public boolean equals(EPermission permissions) {
        return this.value == permissions.value;
    }
    
    public boolean superiorOrEquals(EPermission permissions) {
        return this.value >= permissions.value;
    }
    
    public boolean inferiorOrEquals(EPermission permissions) {
        return this.value <= permissions.value;
    }
}
