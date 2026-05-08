package org.dofus.network.server;

public abstract class ServerClientHandler {

    protected Server server;
    protected ServerClient client;

    protected ServerClientHandler(Server server, ServerClient client) {
        this.server = server;
        this.client = client;
    }

    public abstract void parse(String packet) throws Exception;
    public abstract void onClosed();
}
