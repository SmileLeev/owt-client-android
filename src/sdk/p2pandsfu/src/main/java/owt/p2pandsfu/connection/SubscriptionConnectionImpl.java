package owt.p2pandsfu.connection;

import owt.conference.Subscription;

class SubscriptionConnectionImpl implements Connection{
    private final Subscription subscription;

    SubscriptionConnectionImpl(Subscription subscription) {
        this.subscription = subscription;
    }

    @Override
    public String id() {
        return subscription.id;
    }

    @Override
    public void stop() {
        subscription.stop();
    }

    @Override
    public boolean isPublish() {
        return false;
    }
}
