package io.reactivex.internal.operators;

import org.reactivestreams.*;

import io.reactivex.internal.subscriptions.SubscriptionArbiter;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * Delays the subscription to the main source until the other
 * observable fires an event or completes.
 * @param <T> the main type
 * @param <U> the other value type, ignored
 */
public final class PublisherDelaySubscriptionOther<T, U> implements Publisher<T> {
    final Publisher<? extends T> main;
    final Publisher<U> other;
    
    public PublisherDelaySubscriptionOther(Publisher<? extends T> main, Publisher<U> other) {
        this.main = main;
        this.other = other;
    }
    
    @Override
    public void subscribe(final Subscriber<? super T> child) {
        final SubscriptionArbiter serial = new SubscriptionArbiter();
        child.onSubscribe(serial);
        
        Subscriber<U> otherSubscriber = new Subscriber<U>() {
            boolean done;
            
            @Override
            public void onSubscribe(final Subscription s) {
                serial.setSubscription(new Subscription() {
                    @Override
                    public void request(long n) {
                        // ignored
                    }
                    
                    @Override
                    public void cancel() {
                        s.cancel();
                    }
                });
                s.request(Long.MAX_VALUE);
            }
            
            @Override
            public void onNext(U t) {
                onComplete();
            }
            
            @Override
            public void onError(Throwable e) {
                if (done) {
                    RxJavaPlugins.onError(e);
                    return;
                }
                done = true;
                child.onError(e);
            }
            
            @Override
            public void onComplete() {
                if (done) {
                    return;
                }
                done = true;
                
                main.subscribe(new Subscriber<T>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        serial.setSubscription(s);
                    }
                    
                    @Override
                    public void onNext(T t) {
                        child.onNext(t);
                    }
                    
                    @Override
                    public void onError(Throwable t) {
                        child.onError(t);
                    }
                    
                    @Override
                    public void onComplete() {
                        child.onComplete();
                    }
                });
            }
        };
        
        other.subscribe(otherSubscriber);
    }
}