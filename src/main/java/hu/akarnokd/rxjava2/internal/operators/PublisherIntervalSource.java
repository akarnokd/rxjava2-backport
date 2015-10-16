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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

import org.reactivestreams.*;

import hu.akarnokd.rxjava2.Scheduler;
import hu.akarnokd.rxjava2.disposables.Disposable;
import hu.akarnokd.rxjava2.exceptions.MissingBackpressureException;
import hu.akarnokd.rxjava2.internal.subscriptions.SubscriptionHelper;
import hu.akarnokd.rxjava2.internal.util.BackpressureHelper;
import hu.akarnokd.rxjava2.plugins.RxJavaPlugins;

public final class PublisherIntervalSource implements Publisher<Long> {
    final Scheduler scheduler;
    final long initialDelay;
    final long period;
    final TimeUnit unit;
    
    public PublisherIntervalSource(long initialDelay, long period, TimeUnit unit, Scheduler scheduler) {
        this.initialDelay = initialDelay;
        this.period = period;
        this.unit = unit;
        this.scheduler = scheduler;
    }
    
    @Override
    public void subscribe(Subscriber<? super Long> s) {
        IntervalSubscriber is = new IntervalSubscriber(s);
        s.onSubscribe(is);
        
        Disposable d = scheduler.schedulePeriodicallyDirect(is, initialDelay, period, unit);
        
        is.setResource(d);
    }
    
    static final class IntervalSubscriber extends AtomicLong 
    implements Subscription, Runnable {
        /** */
        private static final long serialVersionUID = -2809475196591179431L;

        final Subscriber<? super Long> actual;
        
        long count;
        
        volatile boolean cancelled;
        
        static final Disposable DISPOSED = new Disposable() {
            @Override
            public void dispose() { }
        };
        
        volatile Disposable resource;
        static final AtomicReferenceFieldUpdater<IntervalSubscriber, Disposable> RESOURCE =
                AtomicReferenceFieldUpdater.newUpdater(IntervalSubscriber.class, Disposable.class, "resource");
        
        public IntervalSubscriber(Subscriber<? super Long> actual) {
            this.actual = actual;
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
            if (!cancelled) {
                cancelled = true;
                disposeResource();
            }
        }
        
        void disposeResource() {
            Disposable d = resource;
            if (d != DISPOSED) {
                d = RESOURCE.getAndSet(this, DISPOSED);
                if (d != DISPOSED && d != null) {
                    d.dispose();
                }
            }
        }
        
        @Override
        public void run() {
            if (!cancelled) {
                long r = get();
                
                if (r != 0L) {
                    actual.onNext(count++);
                    if (r != Long.MAX_VALUE) {
                        decrementAndGet();
                    }
                } else {
                    cancelled = true;
                    try {
                        actual.onError(new MissingBackpressureException("Can't deliver value " + count + " due to lack of requests"));
                    } finally {
                        disposeResource();
                    }
                }
            }
        }
        
        public void setResource(Disposable d) {
            for (;;) {
                Disposable current = resource;
                if (current == DISPOSED) {
                    d.dispose();
                    return;
                }
                if (current != null) {
                    RxJavaPlugins.onError(new IllegalStateException("Resource already set!"));
                    return;
                }
                if (RESOURCE.compareAndSet(this, null, d)) {
                    return;
                }
            }
        }
    }
}
