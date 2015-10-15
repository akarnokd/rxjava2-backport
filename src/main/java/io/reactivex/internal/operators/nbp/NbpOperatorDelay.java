/**
 * Copyright 2015 David Karnok
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

package io.reactivex.internal.operators.nbp;

import java.util.concurrent.TimeUnit;

import io.reactivex.NbpObservable.*;
import io.reactivex.Scheduler;
import io.reactivex.Scheduler.Worker;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.subscribers.nbp.NbpSerializedSubscriber;

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
            s = new NbpSerializedSubscriber<>(t);
        }
        
        Scheduler.Worker w = scheduler.createWorker();
        
        return new DelaySubscriber<>(s, delay, unit, w, delayError);
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
        public void onNext(T t) {
            w.schedule(() -> actual.onNext(t), delay, unit);
        }
        
        @Override
        public void onError(Throwable t) {
            if (delayError) {
                w.schedule(() -> {
                    try {
                        actual.onError(t);
                    } finally {
                        w.dispose();
                    }
                }, delay, unit);
            } else {
                actual.onError(t);
            }
        }
        
        @Override
        public void onComplete() {
            w.schedule(() -> {
                try {
                    actual.onComplete();
                } finally {
                    w.dispose();
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
