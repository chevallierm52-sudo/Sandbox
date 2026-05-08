package org.dofus.utils;

public class Cipher {

    public final static char[] HASH = {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's',
            't', 'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U',
            'V', 'W', 'X', 'Y', 'Z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_' };
    
    public static String hash(String algorithm, String value) {
    	try {
    		byte[] hash = java.security.MessageDigest.getInstance(algorithm).digest(value.getBytes());
    		return byteArrayToHex(hash);
    	} catch(Exception e) {
    		return "";
    	}
    }
     
    private static String byteArrayToHex(byte[] hash) {
    	StringBuilder hexStr = new StringBuilder();
    	for(int i = 0; i < hash.length; i++) {
    		int n = hash[i];
    		if(n < 0)
    			n += 256;
    		
    		StringBuilder hex = new StringBuilder().append(Integer.toHexString(n));
    		if(hex.length() == 1)
    			hexStr.append('0');
    		hexStr.append(hex);
    	}
    	return hexStr.toString();
    }
    
	public static String encode(String packet, String key) {
        StringBuilder encode = new StringBuilder().append("#1");
        
        for(int i = 0; i < packet.length(); i++) {
            int current = (int) packet.charAt(i);
            int k = (int) key.charAt(i % key.length());

            int encode_c1 = current / 16 + k;
            int encode_c2 = current % 16 + k;

            encode.append(HASH[encode_c1 % HASH.length]);
            encode.append(HASH[encode_c2 % HASH.length]);
        }
        return encode.toString();
    }
}
