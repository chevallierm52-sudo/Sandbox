package org.dofus.network.game;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.dofus.network.game.handlers.GameScreenHandler;
import org.dofus.utils.PacketLogger;
import org.dofus.utils.RateLimiter;
import org.dofus.utils.ServerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Game implements IoHandler {

	private static final Logger logger = LoggerFactory.getLogger(Game.class);

	private final IoAcceptor acceptor;
	private boolean started;
	
	public Game() {
		this.acceptor = new NioSocketAcceptor();
        this.acceptor.getFilterChain().addLast("codec",
                new ProtocolCodecFilter(new TextLineCodecFactory(
                        Charset.forName("UTF8"),
                        LineDelimiter.NUL,
                        new LineDelimiter("\n\0")
                )));
        this.acceptor.setHandler(this);
        this.acceptor.getSessionConfig().setReadBufferSize(1024);
        this.acceptor.getSessionConfig().setIdleTime(IdleStatus.BOTH_IDLE, 10);
	}
	
	@Override
	public void exceptionCaught(IoSession session, Throwable object) throws Exception {
		logger.error("[Game-{}] exception: {}", session.getId(), object.getMessage());
		session.write("BN");
	}

	@Override
	public void messageReceived(IoSession session, Object object) throws Exception {
		String packet = (String) object;

		// ── Anti-flood : rate limiting par session ────────────────────────────
		if(!RateLimiter.allow(session.getId())) {
			// Si 3 bans consécutifs, l'état a été purgé → fermeture forcée
			if(!RateLimiter.isTracked(session.getId())) {
				logger.warn("[Game-{}] flood excessif — session fermée", session.getId());
				session.close(false);
			}
			return;
		}

		ServerMetrics.onPacketReceived();
		PacketLogger.recv("Game", session.getId(), packet);

		if(packet.equals("ping"))
			session.write("pong");
        else if(packet.equals("qping"))
        	session.write("qpong");
        else
        	((GameClient) session.getAttribute("client")).getHandler().parse(packet);
	}

	@Override
	public void messageSent(IoSession session, Object object) throws Exception {
		ServerMetrics.onPacketSent();
		PacketLogger.sent("Game", session.getId(), object);
	}

	@Override
	public void sessionClosed(IoSession session) throws Exception {
		logger.debug("[Game-{}] closed", session.getId());
		RateLimiter.remove(session.getId());
		((GameClient) session.getAttribute("client")).getHandler().onClosed();
	}

	@Override
	public void sessionCreated(IoSession session) throws Exception {
		logger.debug("[Game-{}] created", session.getId());
		
		GameClient client = new GameClient(this, session);
		client.setHandler(new GameScreenHandler(this, client));
		
		session.setAttribute("client", client);
	}

	@Override
	public void sessionIdle(IoSession session, IdleStatus object) throws Exception {
		logger.debug("[Game-{}] idle", session.getId());
	}

	@Override
	public void sessionOpened(IoSession session) throws Exception {
		logger.debug("[Game-{}] opened", session.getId());
	}

	public void start(short port) throws IOException {
		if(started)
			return;
		
		acceptor.bind(new InetSocketAddress(port));
        started = true;
        
        logger.info("Game listening on port {}", port);
	}
	
	public void stop() {
		if(!started)
			return;

        acceptor.unbind();
        
        for(IoSession session : acceptor.getManagedSessions().values())
            if(session.isConnected() || !session.isClosing())
                session.close(false);
            
        acceptor.dispose();
        started = false;
        
        logger.info("Game stopped");
	}
	
}
