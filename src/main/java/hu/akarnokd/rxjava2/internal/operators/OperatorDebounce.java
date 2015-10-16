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
import hu.akarnokd.rxjava2.disposables.Disposable;
import hu.akarnokd.rxjava2.functions.Function;
import hu.akarnokd.rxjava2.internal.subscribers.DisposableSubscriber;
import hu.akarnokd.rxjava2.internal.subscriptions.SubscriptionHelper;
import hu.akarnokd.rxjava2.internal.util.BackpressureHelper;
import hu.akarnokd.rxjava2.plugins.RxJavaPlugins;
import hu.akarnokd.rxjava2.subscribers.SerializedSubscriber;

public final class OperatorDebounce<T, U> implements Operator<T, T> {
    final Function<? super T, ? extends Publisher<U>> debounceSelector;

    public OperatorDebounce(Function<? super T, ? extends Publisher<U>> debounceSelector) {
        this.debounceSelector = debounceSelector;
    }
    
    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> t) {
        return new DebounceSubscriber<T, U>(new SerializedSubscriber<T>(t), debounceSelector);
    }
    
    static final class DebounceSubscriber<T, U> extends AtomicLong 
    implements Subscriber<T>, Subscription {
        /** */
        private static final long serialVersionUID = 6725975399620862591L;
        final Subscriber<? super T> actual;
        final Function<? super T, ? extends Publisher<U>> debounceSelector;
        
        volatile boolean gate;

        Subscription s;
        
        volatile Disposable debouncer;
        @SuppressWarnings("rawtypes")
        static final AtomicReferenceFieldUpdater<DebounceSubscriber, Disposable> DEBOUNCER =
                AtomicReferenceFieldUpdater.newUpdater(DebounceSubscriber.class, Disposable.class, "debouncer");
        
        static final Disposable CANCELLED = new Disposable() {
            @Override
            public void dispose() { }
        };

        volatile long index;
        
        boolean done;

        public DebounceSubscriber(Subscriber<? super T> actual,
                Function<? super T, ? extends Publisher<U>> debounceSelector) {
            this.actual = actual;
            this.debounceSelector = debounceSelector;
        }
        
        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validateSubscription(this.s, s)) {
                return;
            }
            
            this.s = s;
            actual.onSubscribe(this);
            s.request(Long.MAX_VALUE);
        }
        
        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }
            
            long idx = index + 1;
            index = idx;
            
            Disposable d = debouncer;
            if (d != null) {
                d.dispose();
            }
            
            Publisher<U> p;
            
            try {
                p = debounceSelector.apply(t);
            } catch (Throwable e) {
                cancel();
                actual.onError(e);
                return;
            }
            
            if (p == null) {
                cancel();
                actual.onError(new NullPointerException("The publisher supplied is null"));
                return;
            }
            
            DebounceInnerSubscriber<T, U> dis = new DebounceInnerSubscriber<T, U>(this, idx, t);
            
            if (DEBOUNCER.compareAndSet(this, d, dis)) {
                p.subscribe(dis);
            }
        }
        
        @Override
        public void onError(Throwable t) {
            disposeDebouncer();
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            Disposable d = debouncer;
            if (d != CANCELLED) {
                @SuppressWarnings("unchecked")
                DebounceInnerSubscriber<T, U> dis = (DebounceInnerSubscriber<T, U>)d;
                dis.emit();
                disposeDebouncer();
                actual.onComplete();
            }
        }
        
        @Override
        public void request(long n) {
            if (SubscriptionHelper.validateRequest(n)) {
                return;
            }
            
            BackpressureHelper.add(this, n);
        }
        
        @Override
        public void cancel() {
            s.cancel();
            disposeDebouncer();
        }
        
        public void disposeDebouncer() {
            Disposable d = debouncer;
            if (d != CANCELLED) {
                d = DEBOUNCER.getAndSet(this, CANCELLED);
                if (d != CANCELLED && d != null) {
                    d.dispose();
                }
            }
        }
        
        void emit(long idx, T value) {
            if (idx == index) {
                long r = get();
                if (r != 0L) {
                    actual.onNext(value);
                    if (r != Long.MAX_VALUE) {
                        decrementAndGet();
                    }
                } else {
                    cancel();
                    actual.onError(new IllegalStateException("Could not deliver value due to lack of requests"));
                }
            }
        }
        
        static final class DebounceInnerSubscriber<T, U> extends DisposableSubscriber<U> {
            final DebounceSubscriber<T, U> parent;
            final long index;
            final T value;
            
            boolean done;
            
            volatile int once;
            @SuppressWarnings("rawtypes")
            static final AtomicIntegerFieldUpdater<DebounceInnerSubscriber> ONCE =
                    AtomicIntegerFieldUpdater.newUpdater(DebounceInnerSubscriber.class, "once");
            
            public DebounceInnerSubscriber(DebounceSubscriber<T, U> parent, long index, T value) {
                this.parent = parent;
                this.index = index;
                this.value = value;
            }
            
            @Override
            public void onNext(U t) {
                if (done) {
                    return;
                }
                done = true;
                cancel();
                emit();
            }
            
            void emit() {
                if (ONCE.compareAndSet(this, 0, 1)) {
                    parent.emit(index, value);
                }
            }
            
            @Override
            public void onError(Throwable t) {
                if (done) {
                    RxJavaPlugins.onError(t);
                    return;
                }
                done = true;
                parent.onError(t);
            }
            
            @Override
            public void onComplete() {
                if (done) {
                    return;
                }
                done = true;
                emit();
            }
        }
    }
}
