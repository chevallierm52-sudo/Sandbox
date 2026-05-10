package org.dofus.utils;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.mina.core.buffer.IoBuffer;

/**
 * Thank you :) (From D2J)
 * User: Blackrush
 * Date: 29/10/11
 * Time: 19:38
 * IDE : IntelliJ IDEA
 */
public class StringUtils {
	
    private static int random(int min, int max) {
        return min + (int)(Math.random() * (max - min + 1));
    }

    public static char random(String str) {
        return str.charAt(random(0, str.length() - 1));
    }
 
    public static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
    public static final String VOWELS = "aeiouy";
    public static final String CONSONANTS = "bcdfghjklmnpqrstvwxz";
    public static final String HASH = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";

    public static final String MYSQL_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final SimpleDateFormat MYSQL_DATETIME_FORMATER = new SimpleDateFormat(MYSQL_DATETIME_PATTERN);

    public static final String CURRENT_ENCODING_NAME = "UTF8";
    public static final Charset CURRENT_CHARSET = Charset.forName(CURRENT_ENCODING_NAME);

    public static final SimpleDateFormat CURRENT_DATE_FORMATTER = new SimpleDateFormat(
            "yyyy|MM|dd"
    );
    
    public static String random(int length) {
        StringBuilder sb = new StringBuilder(length);
        for(int i = 0; i < length; ++i)
            sb.append(random(ALPHABET));
        return sb.toString();
    }

    public static String randomPseudo() {
        final int length = random(4, 8);
        StringBuilder sb = new StringBuilder(length);
        boolean flag = random(0, 1) == 1;
        for(int i = 0; i < length; ++i){
            sb.append(flag ? random(VOWELS) : random(CONSONANTS));
            flag = !flag;
        }
        return sb.toString();
    }

    public static String formatString(String str, Object[] objects) {
        for(int i = 0; i < objects.length; ++i)
            str = str.replace("{" + i + "}", objects[i].toString());
        return str;
    }

    public static String format(String str, Object... objects) {
        return formatString(str, objects);
    }

    public static short[] toShort(String[] arr) {
        short[] s = new short[arr.length];
        for(int i = 0; i < arr.length; ++i)
            s[i] = Short.parseShort(arr[i]);
        return s;
    }

    public static String toHexOrNegative(int i) {
        return toHex(i, "-1");
    }

    public static String toHex(int i, String $default) {
        return i != -1 ? Integer.toHexString(i) : $default;
    }

    public static String toHex(int i) {
        return Integer.toHexString(i);
    }

    public static String toHex(long n) {
        return Long.toHexString(n);
    }

    public static int fromHex(String s) {
        return Integer.parseInt(s, 16);
    }

    public static void putString(IoBuffer buf, String string) {
        buf.putInt(string.length());
        for(int i = 0; i < string.length(); ++i)
            buf.putChar(string.charAt(i));
    }

    public static String getString(IoBuffer buf) {
        int length = buf.getInt();
        StringBuilder sb = new StringBuilder(length);
        for(int i = 0; i < length; ++i)
            sb.append(buf.getChar());
        return sb.toString();
    }

    public static String join(Iterable<?> c, String delimeter) {
        StringBuilder sb = new StringBuilder();
        Iterator<?> it = c.iterator();
        while(it.hasNext()) {
            sb.append(it.next());
            if(!it.hasNext()) 
            	break;
            sb.append(delimeter);
        }
        return sb.toString();
    }

    public static <T> String join(T[] c, String delimeter) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for(T t : c) {
            if(first) 
            	first = false;
            else 
            	sb.append(delimeter);
            sb.append(t);
        }
        return sb.toString();
    }

    public static String makeSentence(String[] words) {
        return join(words, " ");
    }

    public static String makeSentence(String[] words, int start) {
        return join(Arrays.copyOfRange(words, start, words.length), " ");
    }

    public static String toBase36(long l) {
        return Long.toString(l, 36);
    }
}
