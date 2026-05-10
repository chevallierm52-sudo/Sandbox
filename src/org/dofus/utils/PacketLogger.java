package org.dofus.utils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class PacketLogger {

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	public static void recv(String server, long sessionId, Object packet) {
		System.out.println(LocalTime.now().format(FMT) + " [RECV] [" + server + "-" + sessionId + "] " + packet);
	}

	public static void sent(String server, long sessionId, Object packet) {
		System.out.println(LocalTime.now().format(FMT) + " [SENT] [" + server + "-" + sessionId + "] " + packet);
	}
}
