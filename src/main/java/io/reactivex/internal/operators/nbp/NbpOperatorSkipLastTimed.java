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
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.NbpObservable.*;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.queue.SpscLinkedArrayQueue;
import io.reactivex.internal.subscriptions.SubscriptionHelper;

public final class NbpOperatorSkipLastTimed<T> implements NbpOperator<T, T> {
    final long time;
    final TimeUnit unit;
    final Scheduler scheduler;
    final int bufferSize;
    final boolean delayError;

    public NbpOperatorSkipLastTimed(long time, TimeUnit unit, Scheduler scheduler, int bufferSize, boolean delayError) {
        this.time = time;
        this.unit = unit;
        this.scheduler = scheduler;
        this.bufferSize = bufferSize;
        this.delayError = delayError;
    }
    
    @Override
    public NbpSubscriber<? super T> apply(NbpSubscriber<? super T> t) {
        return new SkipLastTimedSubscriber<>(t, time, unit, scheduler, bufferSize, delayError);
    }
    
    static final class SkipLastTimedSubscriber<T> extends AtomicInteger implements NbpSubscriber<T>, Disposable {
        /** */
        private static final long serialVersionUID = -5677354903406201275L;
        final NbpSubscriber<? super T> actual;
        final long time;
        final TimeUnit unit;
        final Scheduler scheduler;
        final SpscLinkedArrayQueue<Object> queue;
        final boolean delayError;
        
        Disposable s;
        
        volatile boolean cancelled;
        
        volatile boolean done;
        Throwable error;

        public SkipLastTimedSubscriber(NbpSubscriber<? super T> actual, long time, TimeUnit unit, Scheduler scheduler, int bufferSize, boolean delayError) {
            this.actual = actual;
            this.time = time;
            this.unit = unit;
            this.scheduler = scheduler;
            this.queue = new SpscLinkedArrayQueue<>(bufferSize);
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
            final SpscLinkedArrayQueue<Object> q = queue;

            long now = scheduler.now(unit);
            
            q.offer(now, t);

            drain();
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
            final SpscLinkedArrayQueue<Object> q = queue;
            final boolean delayError = this.delayError;
            final TimeUnit unit = this.unit;
            final Scheduler scheduler = this.scheduler;
            final long time = this.time;
            
            for (;;) {
                
                if (checkTerminated(done, q.isEmpty(), a, delayError)) {
                    return;
                }
                
                for (;;) {
                    boolean d = done;
                    
                    Long ts = (Long)q.peek();
                    
                    boolean empty = ts == null;

                    long now = scheduler.now(unit);
                    
                    if (!empty && ts > now - time) {
                        empty = true;
                    }
                    
                    if (checkTerminated(d, empty, a, delayError)) {
                        return;
                    }
                    
                    if (empty) {
                        break;
                    }
                    
                    
                    if (ts > now - time) {
                        // not old enough
                        break;
                    }
                    
                    // wait unit the second value arrives
                    if (q.size() == 1L) {
                        continue;
                    }
                    
                    q.poll();
                    
                    @SuppressWarnings("unchecked")
                    T v = (T)q.poll();
                    
                    a.onNext(v);
                }
                
                missed = addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }
        
        boolean checkTerminated(boolean d, boolean empty, NbpSubscriber<? super T> a, boolean delayError) {
            if (cancelled) {
                queue.clear();
                s.dispose();
                return true;
            }
            if (d) {
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
            }
            return false;
        }
    }
}
