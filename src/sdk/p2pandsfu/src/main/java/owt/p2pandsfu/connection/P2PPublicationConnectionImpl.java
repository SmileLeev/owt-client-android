package owt.p2pandsfu.connection;

import owt.p2pandsfu.p2p.P2PPublication;

class P2PPublicationConnectionImpl implements Connection {
    private final P2PPublication publication;

    P2PPublicationConnectionImpl(P2PPublication publication) {
        this.publication = publication;
    }

    @Override
    public String id() {
        return publication.id();
    }

    @Override
    public void stop() {
        publication.stop();
    }
}
