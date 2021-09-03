package owt.p2pandsfu.connection;

import owt.conference.Publication;
import owt.conference.Subscription;
import owt.p2pandsfu.p2p.P2PPublication;

public interface Connection {
    void stop();
    String id();

    static Connection getInstance(Publication publication) {
        return new PublicationConnectionImpl(publication);
    }

    static Connection getInstance(P2PPublication publication) {
        return new P2PPublicationConnectionImpl(publication);
    }

    static Connection getInstance(Subscription subscription) {
        return new SubscriptionConnectionImpl(subscription);
    }
}
