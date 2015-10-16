/**
 * Copyright 2015 David Karnok and Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package hu.akarnokd.rxjava2.internal.operators;

import java.util.concurrent.atomic.*;

import org.reactivestreams.*;

import hu.akarnokd.rxjava2.Observable.Operator;
import hu.akarnokd.rxjava2.functions.BiFunction;
import hu.akarnokd.rxjava2.internal.subscriptions.*;
import hu.akarnokd.rxjava2.plugins.RxJavaPlugins;
import hu.akarnokd.rxjava2.subscribers.SerializedSubscriber;

public final class OperatorWithLatestFrom<T, U, R> implements Operator<R, T> {
    final BiFunction<? super T, ? super U, ? extends R> combiner;
    final Publisher<? extends U> other;
    public OperatorWithLatestFrom(BiFunction<? super T, ? super U, ? extends R> combiner, Publisher<? extends U> other) {
        this.combiner = combiner;
        this.other = other;
    }
    
    @Override
    public Subscriber<? super T> apply(Subscriber<? super R> t) {
        final SerializedSubscriber<R> serial = new SerializedSubscriber<R>(t);
        final WithLatestFromSubscriber<T, U, R> wlf = new WithLatestFromSubscriber<T, U, R>(serial, combiner);
        
        other.subscribe(new Subscriber<U>() {
            @Override
            public void onSubscribe(Subscription s) {
                if (wlf.setOther(s)) {
                    s.request(Long.MAX_VALUE);
                }
            }
            
            @Override
            public void onNext(U t) {
                wlf.lazySet(t);
            }
            
            @Override
            public void onError(Throwable t) {
                wlf.otherError(t);
            }
            
            @Override
            public void onComplete() {
                // nothing to do, the wlf will complete on its own pace
            }
        });
        
        return wlf;
    }
    
    static final class WithLatestFromSubscriber<T, U, R> extends AtomicReference<U> implements Subscriber<T>, Subscription {
        /** */
        private static final long serialVersionUID = -312246233408980075L;
        
        final Subscriber<? super R> actual;
        final BiFunction<? super T, ? super U, ? extends R> combiner;
        
        volatile Subscription s;
        @SuppressWarnings("rawtypes")
        static final AtomicReferenceFieldUpdater<WithLatestFromSubscriber, Subscription> S =
                AtomicReferenceFieldUpdater.newUpdater(WithLatestFromSubscriber.class, Subscription.class, "s");
        
        volatile Subscription other;
        @SuppressWarnings("rawtypes")
        static final AtomicReferenceFieldUpdater<WithLatestFromSubscriber, Subscription> OTHER =
                AtomicReferenceFieldUpdater.newUpdater(WithLatestFromSubscriber.class, Subscription.class, "other");
        
        static final Subscription CANCELLED = new Subscription() {
            @Override
            public void request(long n) {
                SubscriptionHelper.validateRequest(n);
            }
            
            @Override
            public void cancel() {
                
            }
        };
        
        public WithLatestFromSubscriber(Subscriber<? super R> actual, BiFunction<? super T, ? super U, ? extends R> combiner) {
            this.actual = actual;
            this.combiner = combiner;
        }
        @Override
        public void onSubscribe(Subscription s) {
            if (S.compareAndSet(this, null, s)) {
                actual.onSubscribe(this);
            } else {
                s.cancel();
                if (s != CANCELLED) {
                    SubscriptionHelper.reportSubscriptionSet();
                }
            }
        }
        
        @Override
        public void onNext(T t) {
            U u = get();
            if (u != null) {
                R r;
                try {
                    r = combiner.apply(t, u);
                } catch (Throwable e) {
                    cancel();
                    actual.onError(e);
                    return;
                }
                actual.onNext(r);
            }
        }
        
        @Override
        public void onError(Throwable t) {
            cancelOther();
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            cancelOther();
            actual.onComplete();
        }
        
        @Override
        public void request(long n) {
            s.request(n);
        }
        
        @Override
        public void cancel() {
            s.cancel();
            cancelOther();
        }
        
        void cancelOther() {
            Subscription o = other;
            if (o != CANCELLED) {
                o = OTHER.getAndSet(this, CANCELLED);
                if (o != CANCELLED && o != null) {
                    o.cancel();
                }
            }
        }
        
        public boolean setOther(Subscription o) {
            for (;;) {
                Subscription current = other;
                if (current == CANCELLED) {
                    o.cancel();
                    return false;
                }
                if (current != null) {
                    RxJavaPlugins.onError(new IllegalStateException("Other subscription already set!"));
                    o.cancel();
                    return false;
                }
                if (OTHER.compareAndSet(this, null, o)) {
                    return true;
                }
            }
        }
        
        public void otherError(Throwable e) {
            if (S.compareAndSet(this, null, CANCELLED)) {
                EmptySubscription.error(e, actual);
            } else {
                if (s != CANCELLED) {
                    cancel();
                    actual.onError(e);
                } else {
                    RxJavaPlugins.onError(e);
                }
            }
        }
    }
}
