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

package hu.akarnokd.rxjava2.internal.operators.nbp;

import java.util.concurrent.TimeUnit;

import hu.akarnokd.rxjava2.NbpObservable.*;
import hu.akarnokd.rxjava2.Scheduler;
import hu.akarnokd.rxjava2.Scheduler.Worker;
import hu.akarnokd.rxjava2.disposables.Disposable;
import hu.akarnokd.rxjava2.internal.subscriptions.SubscriptionHelper;
import hu.akarnokd.rxjava2.subscribers.nbp.NbpSerializedSubscriber;

public final class NbpOperatorDelay<T> implements NbpOperator<T, T> {
    final long delay;
    final TimeUnit unit;
    final Scheduler scheduler;
    final boolean delayError;
    
    public NbpOperatorDelay(long delay, TimeUnit unit, Scheduler scheduler, boolean delayError) {
        this.delay = delay;
        this.unit = unit;
        this.scheduler = scheduler;
        this.delayError = delayError;
    }



    @Override
    @SuppressWarnings("unchecked")
    public NbpSubscriber<? super T> apply(NbpSubscriber<? super T> t) {
        NbpSubscriber<T> s;
        if (delayError) {
            s = (NbpSubscriber<T>)t;
        } else {
            s = new NbpSerializedSubscriber<T>(t);
        }
        
        Scheduler.Worker w = scheduler.createWorker();
        
        return new DelaySubscriber<T>(s, delay, unit, w, delayError);
    }
    
    static final class DelaySubscriber<T> implements NbpSubscriber<T>, Disposable {
        final NbpSubscriber<? super T> actual;
        final long delay;
        final TimeUnit unit;
        final Scheduler.Worker w;
        final boolean delayError;
        
        Disposable s;
        
        public DelaySubscriber(NbpSubscriber<? super T> actual, long delay, TimeUnit unit, Worker w, boolean delayError) {
            super();
            this.actual = actual;
            this.delay = delay;
            this.unit = unit;
            this.w = w;
            this.delayError = delayError;
        }
        
        @Override
        public void onSubscribe(Disposable s) {
            if (SubscriptionHelper.validateDisposable(this.s, s)) {
                return;
            }
            this.s = s;
            actual.onSubscribe(this);
        }
        
        @Override
        public void onNext(final T t) {
            w.schedule(new Runnable() {
                @Override
                public void run() {
                    actual.onNext(t);
                }
            }, delay, unit);
        }
        
        @Override
        public void onError(final Throwable t) {
            if (delayError) {
                w.schedule(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            actual.onError(t);
                        } finally {
                            w.dispose();
                        }
                    }
                }, delay, unit);
            } else {
                actual.onError(t);
            }
        }
        
        @Override
        public void onComplete() {
            w.schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        actual.onComplete();
                    } finally {
                        w.dispose();
                    }
                }
            }, delay, unit);
        }
        
        @Override
        public void dispose() {
            w.dispose();
            s.dispose();
        }
        
    }
}
