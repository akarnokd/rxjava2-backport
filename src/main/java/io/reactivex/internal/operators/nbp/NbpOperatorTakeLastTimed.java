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

package io.reactivex.internal.operators.nbp;

import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.NbpObservable.*;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.queue.SpscLinkedArrayQueue;
import io.reactivex.internal.subscriptions.SubscriptionHelper;

public final class NbpOperatorTakeLastTimed<T> implements NbpOperator<T, T> {
    final long count;
    final long time;
    final TimeUnit unit;
    final Scheduler scheduler;
    final int bufferSize;
    final boolean delayError;

    public NbpOperatorTakeLastTimed(long count, long time, TimeUnit unit, Scheduler scheduler, int bufferSize, boolean delayError) {
        this.count = count;
        this.time = time;
        this.unit = unit;
        this.scheduler = scheduler;
        this.bufferSize = bufferSize;
        this.delayError = delayError;
    }
    
    @Override
    public NbpSubscriber<? super T> apply(NbpSubscriber<? super T> t) {
        return new TakeLastTimedSubscriber<T>(t, count, time, unit, scheduler, bufferSize, delayError);
    }
    
    static final class TakeLastTimedSubscriber<T> extends AtomicInteger implements NbpSubscriber<T>, Disposable {
        /** */
        private static final long serialVersionUID = -5677354903406201275L;
        final NbpSubscriber<? super T> actual;
        final long count;
        final long time;
        final TimeUnit unit;
        final Scheduler scheduler;
        final Queue<Object> queue;
        final boolean delayError;
        
        Disposable s;
        
        volatile boolean cancelled;
        
        volatile boolean done;
        Throwable error;

        public TakeLastTimedSubscriber(NbpSubscriber<? super T> actual, long count, long time, TimeUnit unit, Scheduler scheduler, int bufferSize, boolean delayError) {
            this.actual = actual;
            this.count = count;
            this.time = time;
            this.unit = unit;
            this.scheduler = scheduler;
            this.queue = new SpscLinkedArrayQueue<Object>(bufferSize);
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
            final Queue<Object> q = queue;

            long now = scheduler.now(unit);
            long time = this.time;
            long c = count;
            boolean unbounded = c == Long.MAX_VALUE;
            
            q.offer(now);
            q.offer(t);
            
            while (!q.isEmpty()) {
                long ts = (Long)q.peek();
                if (ts <= now - time || (!unbounded && (q.size() >> 1) > c)) {
                    q.poll();
                    q.poll();
                } else {
                    break;
                }
            }
        }
        
        @Override
        public void onError(Throwable t) {
            error = t;
            done = true;
            drain();
        }
        
        @Override
        public void onComplete() {
            done = true;
            drain();
        }
        
        @Override
        public void dispose() {
            if (cancelled) {
                cancelled = true;
                
                if (getAndIncrement() == 0) {
                    queue.clear();
                    s.dispose();
                }
            }
        }
        
        void drain() {
            if (getAndIncrement() != 0) {
                return;
            }
            
            int missed = 1;
            
            final NbpSubscriber<? super T> a = actual;
            final Queue<Object> q = queue;
            final boolean delayError = this.delayError;
            
            for (;;) {
                
                if (done) {
                    boolean empty = q.isEmpty();
                    
                    if (checkTerminated(empty, a, delayError)) {
                        return;
                    }
                    
                    for (;;) {
                        Object ts = q.poll(); // the timestamp long
                        empty = ts == null;
                        
                        if (checkTerminated(empty, a, delayError)) {
                            return;
                        }
                        
                        if (empty) {
                            break;
                        }
                        
                        @SuppressWarnings("unchecked")
                        T o = (T)q.poll();
                        if (o == null) {
                            s.dispose();
                            a.onError(new IllegalStateException("Queue empty?!"));
                            return;
                        }
                        
                        if ((Long)ts < scheduler.now(unit) - time) {
                            continue;
                        }
                        
                        a.onNext(o);
                    }
                }
                
                missed = addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }
        
        boolean checkTerminated(boolean empty, NbpSubscriber<? super T> a, boolean delayError) {
            if (cancelled) {
                queue.clear();
                s.dispose();
                return true;
            }
            if (delayError) {
                if (empty) {
                    Throwable e = error;
                    if (e != null) {
                        a.onError(e);
                    } else {
                        a.onComplete();
                    }
                    return true;
                }
            } else {
                Throwable e = error;
                if (e != null) {
                    queue.clear();
                    a.onError(e);
                    return true;
                } else
                if (empty) {
                    a.onComplete();
                    return true;
                }
            }
            return false;
        }
    }
}
