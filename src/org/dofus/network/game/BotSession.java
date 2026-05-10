package org.dofus.network.game;

import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.ReadFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.TransportMetadata;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.core.write.WriteRequestQueue;

/**
 * IoSession factice pour les bots.
 * write() route les packets vers BotPacketHandler.
 * isConnected() retourne true tant que le bot est actif.
 * Tout le reste est stubé — MINA n'invoque jamais ces méthodes
 * puisque BotSession n'est pas géré par un IoProcessor.
 */
public class BotSession implements IoSession {

	private final long id;
	private volatile boolean closed = false;
	private BotPacketHandler packetHandler;

	private final ConcurrentMap<Object, Object> attributes = new ConcurrentHashMap<>();
	private final long creationTime = System.currentTimeMillis();

	public BotSession(long id) {
		this.id = id;
	}

	public void setPacketHandler(BotPacketHandler handler) {
		this.packetHandler = handler;
	}

	// ─── Méthodes importantes ─────────────────────────────────────────────────

	@Override
	public WriteFuture write(Object message) {
		if(!closed && packetHandler != null)
			packetHandler.handle(message.toString());
		return null; // retour ignoré par tout le code appelant
	}

	@Override public boolean isConnected() { return !closed; }
	@Override public boolean isClosing()   { return closed; }
	@Override public long   getId()        { return id; }
	@Override public long   getCreationTime() { return creationTime; }

	@Override
	public CloseFuture close(boolean immediately) {
		closed = true;
		return null;
	}

	// ─── Attributs (utilisés si session.setAttribute("client",...) est appelé) ─

	@Override public Object getAttribute(Object key)                          { return attributes.get(key); }
	@Override public Object getAttribute(Object key, Object defaultValue)     { return attributes.getOrDefault(key, defaultValue); }
	@Override public Object setAttribute(Object key, Object value)            { return attributes.put(key, value); }
	@Override public Object setAttribute(Object key)                          { return attributes.put(key, Boolean.TRUE); }
	@Override public Object setAttributeIfAbsent(Object key, Object value)    { return attributes.putIfAbsent(key, value); }
	@Override public Object setAttributeIfAbsent(Object key)                  { return attributes.putIfAbsent(key, Boolean.TRUE); }
	@Override public boolean replaceAttribute(Object key, Object old, Object n){ Object cur = attributes.get(key); if(cur != null && cur.equals(old)){ attributes.put(key, n); return true; } return false; }
	@Override public Object removeAttribute(Object key)                        { return attributes.remove(key); }
	@Override public boolean removeAttribute(Object key, Object value)         { return attributes.remove(key, value); }
	@Override public boolean containsAttribute(Object key)                     { return attributes.containsKey(key); }
	@Override public Set<Object> getAttributeKeys()                            { return attributes.keySet(); }

	// ─── Stubs réseau / stats (jamais appelés sur les bots) ──────────────────

	@Override public IoService        getService()            { return null; }
	@Override public IoHandler        getHandler()            { return null; }
	@Override public IoSessionConfig  getConfig()             { return null; }
	@Override public IoFilterChain    getFilterChain()        { return null; }
	@Override public SocketAddress    getRemoteAddress()      { return null; }
	@Override public SocketAddress    getLocalAddress()       { return null; }
	@Override public SocketAddress    getServiceAddress()     { return null; }
	@Override public CloseFuture      getCloseFuture()        { return null; }
	@Override public WriteFuture      write(Object msg, SocketAddress dest) { return write(msg); }

	@Override public void    suspendRead()          {}
	@Override public void    suspendWrite()         {}
	@Override public void    resumeRead()           {}
	@Override public void    resumeWrite()          {}
	@Override public boolean isReadSuspended()      { return false; }
	@Override public boolean isWriteSuspended()     { return false; }

	@Override public long   getReadBytes()                    { return 0; }
	@Override public long   getWrittenBytes()                 { return 0; }
	@Override public long   getReadMessages()                 { return 0; }
	@Override public long   getWrittenMessages()              { return 0; }
	@Override public double getReadBytesThroughput()          { return 0; }
	@Override public double getWrittenBytesThroughput()       { return 0; }
	@Override public double getReadMessagesThroughput()       { return 0; }
	@Override public double getWrittenMessagesThroughput()    { return 0; }
	@Override public int    getScheduledWriteMessages()       { return 0; }
	@Override public long   getScheduledWriteBytes()          { return 0; }
	@Override public long   getLastIoTime()                   { return creationTime; }
	@Override public long   getLastReadTime()                 { return creationTime; }
	@Override public long   getLastWriteTime()                { return creationTime; }

	@Override public boolean isIdle(IdleStatus status)        { return false; }
	@Override public boolean isBothIdle()                     { return false; }
	@Override public boolean isReaderIdle()                   { return false; }
	@Override public boolean isWriterIdle()                   { return false; }
	@Override public int     getIdleCount(IdleStatus status)  { return 0; }
	@Override public int     getBothIdleCount()               { return 0; }
	@Override public int     getReaderIdleCount()             { return 0; }
	@Override public int     getWriterIdleCount()             { return 0; }
	@Override public long    getLastBothIdleTime()            { return 0; }
	@Override public long    getLastReaderIdleTime()          { return 0; }
	@Override public long    getLastWriterIdleTime()          { return 0; }

	// ReadFuture / WriteRequestQueue — jamais utilisés sur les bots
	@Override public ReadFuture        read()                 { throw new UnsupportedOperationException(); }
	@Override public WriteRequestQueue getWriteRequestQueue() { throw new UnsupportedOperationException(); }
	@Override public WriteRequest      getCurrentWriteRequest(){ return null; }
	@Override public void setCurrentWriteRequest(WriteRequest req) {}

	@Override
	public CloseFuture close() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getAttachment() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getCurrentWriteMessage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getLastIdleTime(IdleStatus arg0) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Object setAttachment(Object arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void updateThroughput(long arg0, boolean arg1) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public TransportMetadata getTransportMetadata() {
		// TODO Auto-generated method stub
		return null;
	}
}
