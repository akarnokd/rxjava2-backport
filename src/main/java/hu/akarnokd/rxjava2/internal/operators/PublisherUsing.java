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

import java.util.concurrent.atomic.AtomicBoolean;

import org.reactivestreams.*;

import hu.akarnokd.rxjava2.exceptions.CompositeException;
import hu.akarnokd.rxjava2.functions.*;
import hu.akarnokd.rxjava2.internal.subscriptions.*;
import hu.akarnokd.rxjava2.plugins.RxJavaPlugins;

public final class PublisherUsing<T, D> implements Publisher<T> {
    final Supplier<? extends D> resourceSupplier;
    final Function<? super D, ? extends Publisher<? extends T>> sourceSupplier;
    final Consumer<? super D> disposer;
    final boolean eager;
    
    public PublisherUsing(Supplier<? extends D> resourceSupplier,
            Function<? super D, ? extends Publisher<? extends T>> sourceSupplier, 
            Consumer<? super D> disposer,
            boolean eager) {
        this.resourceSupplier = resourceSupplier;
        this.sourceSupplier = sourceSupplier;
        this.disposer = disposer;
        this.eager = eager;
    }
    
    @Override
    public void subscribe(Subscriber<? super T> s) {
        D resource;
        
        try {
            resource = resourceSupplier.get();
        } catch (Throwable e) {
            EmptySubscription.error(e, s);
            return;
        }
        
        Publisher<? extends T> source;
        try {
            source = sourceSupplier.apply(resource);
        } catch (Throwable e) {
            try {
                disposer.accept(resource);
            } catch (Throwable ex) {
                EmptySubscription.error(new CompositeException(ex, e), s);
                return;
            }
            EmptySubscription.error(e, s);
            return;
        }
        
        UsingSubscriber<T, D> us = new UsingSubscriber<T, D>(s, resource, disposer, eager);
        
        source.subscribe(us);
    }
    
    static final class UsingSubscriber<T, D> extends AtomicBoolean implements Subscriber<T>, Subscription {
        /** */
        private static final long serialVersionUID = 5904473792286235046L;
        
        final Subscriber<? super T> actual;
        final D resource;
        final Consumer<? super D> disposer;
        final boolean eager;
        
        Subscription s;

        public UsingSubscriber(Subscriber<? super T> actual, D resource, Consumer<? super D> disposer, boolean eager) {
            this.actual = actual;
            this.resource = resource;
            this.disposer = disposer;
            this.eager = eager;
        }
        
        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validateSubscription(this.s, s)) {
                return;
            }
            this.s = s;
            actual.onSubscribe(this);
        }
        
        @Override
        public void onNext(T t) {
            actual.onNext(t);
        }
        
        @Override
        public void onError(Throwable t) {
            if (eager) {
                Throwable innerError = null;
                if (compareAndSet(false, true)) {
                    try {
                        disposer.accept(resource);
                    } catch (Throwable e) {
                        innerError = e;
                    }
                }
                
                s.cancel();
                if (innerError != null) {
                    actual.onError(new CompositeException(innerError, t));
                } else {
                    actual.onError(t);
                }
            } else {
                actual.onError(t);
                s.cancel();
                disposeAfter();
            }
        }
        
        @Override
        public void onComplete() {
            if (eager) {
                if (compareAndSet(false, true)) {
                    try {
                        disposer.accept(resource);
                    } catch (Throwable e) {
                        actual.onError(e);
                        return;
                    }
                }
                
                s.cancel();
                actual.onComplete();
            } else {
                actual.onComplete();
                s.cancel();
                disposeAfter();
            }
        }
        
        @Override
        public void request(long n) {
            s.request(n);
        }
        
        @Override
        public void cancel() {
            disposeAfter();
            s.cancel();
        }
        
        void disposeAfter() {
            if (compareAndSet(false, true)) {
                try {
                    disposer.accept(resource);
                } catch (Throwable e) {
                    // can't call actual.onError unless it is serialized, which is expensive
                    RxJavaPlugins.onError(e);
                }
            }
        }
    }
}
