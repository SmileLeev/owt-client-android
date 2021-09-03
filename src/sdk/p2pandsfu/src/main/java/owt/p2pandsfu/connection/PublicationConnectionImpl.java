package owt.p2pandsfu.connection;

import owt.conference.Publication;

class PublicationConnectionImpl implements Connection {
    private final Publication publication;

    PublicationConnectionImpl(Publication publication) {
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
