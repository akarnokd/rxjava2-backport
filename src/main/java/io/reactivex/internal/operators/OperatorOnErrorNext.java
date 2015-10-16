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

package io.reactivex.internal.operators;

import org.reactivestreams.*;

import io.reactivex.Observable.Operator;
import io.reactivex.exceptions.CompositeException;
import io.reactivex.functions.Function;
import io.reactivex.internal.subscriptions.SubscriptionArbiter;
import io.reactivex.plugins.RxJavaPlugins;

public final class OperatorOnErrorNext<T> implements Operator<T, T> {
    final Function<? super Throwable, ? extends Publisher<? extends T>> nextSupplier;
    final boolean allowFatal;
    
    public OperatorOnErrorNext(Function<? super Throwable, ? extends Publisher<? extends T>> nextSupplier, boolean allowFatal) {
        this.nextSupplier = nextSupplier;
        this.allowFatal = allowFatal;
    }
    
    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> t) {
        OnErrorNextSubscriber<T> parent = new OnErrorNextSubscriber<T>(t, nextSupplier, allowFatal);
        t.onSubscribe(parent.arbiter);
        return parent;
    }
    
    static final class OnErrorNextSubscriber<T> implements Subscriber<T> {
        final Subscriber<? super T> actual;
        final Function<? super Throwable, ? extends Publisher<? extends T>> nextSupplier;
        final boolean allowFatal;
        final SubscriptionArbiter arbiter;
        
        boolean once;
        
        boolean done;
        
        public OnErrorNextSubscriber(Subscriber<? super T> actual, Function<? super Throwable, ? extends Publisher<? extends T>> nextSupplier, boolean allowFatal) {
            this.actual = actual;
            this.nextSupplier = nextSupplier;
            this.allowFatal = allowFatal;
            this.arbiter = new SubscriptionArbiter();
        }
        
        @Override
        public void onSubscribe(Subscription s) {
            arbiter.setSubscription(s);
        }
        
        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }
            actual.onNext(t);
            if (!once) {
                arbiter.produced(1L);
            }
        }
        
        @Override
        public void onError(Throwable t) {
            if (once) {
                if (done) {
                    RxJavaPlugins.onError(t);
                    return;
                }
                actual.onError(t);
                return;
            }
            once = true;

            if (allowFatal && !(t instanceof Exception)) {
                actual.onError(t);
                return;
            }
            
            Publisher<? extends T> p;
            
            try {
                p = nextSupplier.apply(t);
            } catch (Throwable e) {
                actual.onError(new CompositeException(e, t));
                return;
            }
            
            if (p == null) {
                NullPointerException npe = new NullPointerException("Publisher is null");
                npe.initCause(t);
                actual.onError(npe);
                return;
            }
            
            p.subscribe(this);
        }
        
        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            once = true;
            actual.onComplete();
        }
    }
}