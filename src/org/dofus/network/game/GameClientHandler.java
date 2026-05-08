package org.dofus.network.game;

public abstract class GameClientHandler {

    protected Game game;
    protected GameClient client;

    protected GameClientHandler(Game game, GameClient client) {
        this.game = game;
        this.client = client;
    }

    public abstract void parse(String packet) throws Exception;
    public abstract void onClosed();
}
