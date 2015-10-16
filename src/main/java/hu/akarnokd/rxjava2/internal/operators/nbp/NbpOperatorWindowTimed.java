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

import java.nio.channels.CancelledKeyException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import hu.akarnokd.rxjava2.*;
import hu.akarnokd.rxjava2.NbpObservable.*;
import hu.akarnokd.rxjava2.Scheduler.Worker;
import hu.akarnokd.rxjava2.disposables.Disposable;
import hu.akarnokd.rxjava2.internal.queue.MpscLinkedQueue;
import hu.akarnokd.rxjava2.internal.subscribers.nbp.NbpQueueDrainSubscriber;
import hu.akarnokd.rxjava2.internal.subscriptions.SubscriptionHelper;
import hu.akarnokd.rxjava2.internal.util.NotificationLite;
import hu.akarnokd.rxjava2.subjects.nbp.NbpUnicastSubject;
import hu.akarnokd.rxjava2.subscribers.nbp.NbpSerializedSubscriber;

public final class NbpOperatorWindowTimed<T> implements NbpOperator<NbpObservable<T>, T> {
    final long timespan;
    final long timeskip;
    final TimeUnit unit;
    final Scheduler scheduler;
    final long maxSize;
    final int bufferSize;
    final boolean restartTimerOnMaxSize;

    public NbpOperatorWindowTimed(long timespan, long timeskip, TimeUnit unit, Scheduler scheduler, long maxSize,
            int bufferSize, boolean restartTimerOnMaxSize) {
        this.timespan = timespan;
        this.timeskip = timeskip;
        this.unit = unit;
        this.scheduler = scheduler;
        this.maxSize = maxSize;
        this.bufferSize = bufferSize;
        this.restartTimerOnMaxSize = restartTimerOnMaxSize;
    }
    
    @Override
    public NbpSubscriber<? super T> apply(NbpSubscriber<? super NbpObservable<T>> t) {
        NbpSerializedSubscriber<NbpObservable<T>> actual = new NbpSerializedSubscriber<NbpObservable<T>>(t);
        
        if (timespan == timeskip) {
            if (maxSize == Long.MAX_VALUE) {
                return new WindowExactUnboundedSubscriber<T>(
                        actual, 
                        timespan, unit, scheduler, bufferSize);
            }
            return new WindowExactBoundedSubscriber<T>(
                        actual,
                        timespan, unit, scheduler, 
                        bufferSize, maxSize, restartTimerOnMaxSize);
        }
        return new WindowSkipSubscriber<T>(actual,
                timespan, timeskip, unit, scheduler.createWorker(), bufferSize);
    }
    
    static final class WindowExactUnboundedSubscriber<T> 
            extends NbpQueueDrainSubscriber<T, Object, NbpObservable<T>> 
            implements NbpSubscriber<T>, Disposable, Runnable {
        final long timespan;
        final TimeUnit unit;
        final Scheduler scheduler;
        final int bufferSize;
        
        Disposable s;
        
        boolean selfCancel;
        
        NbpUnicastSubject<T> window;

        volatile Disposable timer;
        @SuppressWarnings("rawtypes")
        static final AtomicReferenceFieldUpdater<WindowExactUnboundedSubscriber, Disposable> TIMER =
                AtomicReferenceFieldUpdater.newUpdater(WindowExactUnboundedSubscriber.class, Disposable.class, "timer");

        static final Disposable CANCELLED = new Disposable() {
            @Override
            public void dispose() { }
        };
        
        static final Object NEXT = new Object();
        
        volatile boolean terminated;
        
        public WindowExactUnboundedSubscriber(NbpSubscriber<? super NbpObservable<T>> actual, long timespan, TimeUnit unit,
                Scheduler scheduler, int bufferSize) {
            super(actual, new MpscLinkedQueue<Object>());
            this.timespan = timespan;
            this.unit = unit;
            this.scheduler = scheduler;
            this.bufferSize = bufferSize;
        }
        
        @Override
        public void onSubscribe(Disposable s) {
            if (SubscriptionHelper.validateDisposable(this.s, s)) {
                return;
            }
            this.s = s;
            
            window = NbpUnicastSubject.<T>create(bufferSize);
            
            NbpSubscriber<? super NbpObservable<T>> a = actual;
            a.onSubscribe(this);
            
            a.onNext(window);
            
            if (!cancelled) {
                Disposable d = scheduler.schedulePeriodicallyDirect(this, timespan, timespan, unit);
                if (!TIMER.compareAndSet(this, null, d)) {
                    d.dispose();
                    return;
                }
                
            }
        }
        
        @Override
        public void onNext(T t) {
            if (terminated) {
                return;
            }
            if (fastEnter()) {
                window.onNext(t);
                if (leave(-1) == 0) {
                    return;
                }
            } else {
                queue.offer(NotificationLite.next(t));
                if (!enter()) {
                    return;
                }
            }
            drainLoop();
        }
        
        @Override
        public void onError(Throwable t) {
            error = t;
            done = true;
            if (enter()) {
                drainLoop();
            }
            
            disposeTimer();
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            done = true;
            if (enter()) {
                drainLoop();
            }
            
            disposeTimer();
            actual.onComplete();
        }
        
        @Override
        public void dispose() {
            cancelled = true;
        }
        
        void disposeTimer() {
            selfCancel = true;
            Disposable d = timer;
            if (d != CANCELLED) {
                d = TIMER.getAndSet(this, CANCELLED);
                if (d != CANCELLED && d != null) {
                    d.dispose();
                }
            }
        }
        
        @Override
        public void run() {

            if (selfCancel) {
                throw new CancelledKeyException();
            }

            if (cancelled) {
                terminated = true;
                disposeTimer();
            }
            queue.offer(NEXT);
            if (enter()) {
                drainLoop();
            }

        }
        
        void drainLoop() {
            
            final Queue<Object> q = queue;
            final NbpSubscriber<? super NbpObservable<T>> a = actual;
            NbpUnicastSubject<T> w = window;
            
            int missed = 1;
            for (;;) {
                
                for (;;) {
                    boolean term = terminated;
                    
                    boolean d = done;
                    
                    Object o = q.poll();
                    
                    if (d && (o == null || o == NEXT)) {
                        window = null;
                        q.clear();
                        disposeTimer();
                        Throwable err = error;
                        if (err != null) {
                            w.onError(err);
                        } else {
                            w.onComplete();
                        }
                        return;
                    }
                    
                    if (o == null) {
                        break;
                    }
                    
                    if (o == NEXT) {
                        w.onComplete();
                        if (!term) {
                            w = NbpUnicastSubject.create(bufferSize);
                            window = w;
                            
                            a.onNext(w);
                        } else {
                            s.dispose();
                        }
                        continue;
                    }
                    
                    w.onNext(NotificationLite.<T>getValue(o));
                }
                
                missed = leave(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }
        
        @Override
        public void accept(NbpSubscriber<? super NbpObservable<T>> a, Object v) {
            // not used in this operator
        }
    }
    
    static final class WindowExactBoundedSubscriber<T>
    extends NbpQueueDrainSubscriber<T, Object, NbpObservable<T>>
    implements Disposable {
        final long timespan;
        final TimeUnit unit;
        final Scheduler scheduler;
        final int bufferSize;
        final boolean restartTimerOnMaxSize;
        final long maxSize;
        
        boolean selfCancel;
        
        long count;
        
        long producerIndex;
        
        Disposable s;
        
        NbpUnicastSubject<T> window;
        
        Scheduler.Worker worker;
        
        volatile boolean terminated;
        
        volatile Disposable timer;
        @SuppressWarnings("rawtypes")
        static final AtomicReferenceFieldUpdater<WindowExactBoundedSubscriber, Disposable> TIMER =
                AtomicReferenceFieldUpdater.newUpdater(WindowExactBoundedSubscriber.class, Disposable.class, "timer");
        
        static final Disposable CANCELLED = new Disposable() {
            @Override
            public void dispose() { }
        };
        
        public WindowExactBoundedSubscriber(
                NbpSubscriber<? super NbpObservable<T>> actual, 
                long timespan, TimeUnit unit, Scheduler scheduler, 
                int bufferSize, long maxSize, boolean restartTimerOnMaxSize) {
            super(actual, new MpscLinkedQueue<Object>());
            this.timespan = timespan;
            this.unit = unit;
            this.scheduler = scheduler;
            this.bufferSize = bufferSize;
            this.maxSize = maxSize;
            this.restartTimerOnMaxSize = restartTimerOnMaxSize;
        }
        
        @Override
        public void onSubscribe(Disposable s) {
            if (SubscriptionHelper.validateDisposable(this.s, s)) {
                return;
            }
            
            this.s = s;
            
            NbpSubscriber<? super NbpObservable<T>> a = actual;
            
            a.onSubscribe(this);
            
            if (cancelled) {
                return;
            }
            
            NbpUnicastSubject<T> w = NbpUnicastSubject.create(bufferSize);
            window = w;
            
            a.onNext(w);
            
            Disposable d;
            ConsumerIndexHolder consumerIndexHolder = new ConsumerIndexHolder(producerIndex, this);
            if (restartTimerOnMaxSize) {
                Scheduler.Worker sw = scheduler.createWorker();
                sw.schedulePeriodically(consumerIndexHolder, timespan, timespan, unit);
                d = sw;
            } else {
                d = scheduler.schedulePeriodicallyDirect(consumerIndexHolder, timespan, timespan, unit);
            }
            
            if (!TIMER.compareAndSet(this, null, d)) {
                d.dispose();
                return;
            }
        }
        
        @Override
        public void onNext(T t) {
            if (terminated) {
                return;
            }

            if (fastEnter()) {
                NbpUnicastSubject<T> w = window;
                w.onNext(t);
                
                long c = count + 1;
                
                if (c >= maxSize) {
                    producerIndex++;
                    count = 0;
                    
                    w.onComplete();
                    
                    w = NbpUnicastSubject.create(bufferSize);
                    window = w;
                    actual.onNext(w);
                    if (restartTimerOnMaxSize) {
                        timer.dispose();
                        
                        Disposable tm = timer;
                        Disposable task = worker.schedulePeriodically(
                                new ConsumerIndexHolder(producerIndex, this), timespan, timespan, unit);
                        if (!TIMER.compareAndSet(this, tm, task)) {
                            task.dispose();
                        }
                    }
                } else {
                    count = c;
                }
                
                if (leave(-1) == 0) {
                    return;
                }
            } else {
                queue.offer(NotificationLite.next(t));
                if (!enter()) {
                    return;
                }
            }
            drainLoop();
        }
        
        @Override
        public void onError(Throwable t) {
            error = t;
            done = true;
            if (enter()) {
                drainLoop();
            }
            
            disposeTimer();
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            done = true;
            if (enter()) {
                drainLoop();
            }
            
            disposeTimer();
            actual.onComplete();
        }
        
        @Override
        public void dispose() {
            if (!cancelled) {
                cancelled = true;
            }
        }
        
        void disposeTimer() {
            selfCancel = true;
            Disposable d = timer;
            if (d != CANCELLED) {
                d = TIMER.getAndSet(this, CANCELLED);
                if (d != CANCELLED && d != null) {
                    d.dispose();
                }
            }
        }
        
        @Override
        public void accept(NbpSubscriber<? super NbpObservable<T>> a, Object v) {
            // not needed in this operator
        }
        
        void drainLoop() {
            final Queue<Object> q = queue;
            final NbpSubscriber<? super NbpObservable<T>> a = actual;
            NbpUnicastSubject<T> w = window;
            
            int missed = 1;
            for (;;) {
                
                for (;;) {
                    if (terminated) {
                        s.dispose();
                        q.clear();
                        disposeTimer();
                        return;
                    }
                    
                    boolean d = done;
                    
                    Object o = q.poll();
                    
                    boolean empty = o == null;
                    boolean isHolder = o instanceof ConsumerIndexHolder;
                    
                    if (d && (empty || isHolder)) {
                        window = null;
                        q.clear();
                        disposeTimer();
                        Throwable err = error;
                        if (err != null) {
                            w.onError(err);
                        } else {
                            w.onComplete();
                        }
                        return;
                    }
                    
                    if (empty) {
                        break;
                    }
                    
                    if (isHolder) {
                        ConsumerIndexHolder consumerIndexHolder = (ConsumerIndexHolder) o;
                        if (producerIndex == consumerIndexHolder.index) {
                            w = NbpUnicastSubject.create(bufferSize);
                            window = w;
                            
                            a.onNext(w);
                        }
                        continue;
                    }
                    
                    w.onNext(NotificationLite.<T>getValue(o));
                    long c = count + 1;
                    
                    if (c >= maxSize) {
                        producerIndex++;
                        count = 0;
                        
                        w.onComplete();
                        
                        w = NbpUnicastSubject.create(bufferSize);
                        window = w;
                        actual.onNext(w);
                        
                        if (restartTimerOnMaxSize) {
                            timer.dispose();
                            
                            Disposable tm = timer;
                            Disposable task = worker.schedulePeriodically(
                                    new ConsumerIndexHolder(producerIndex, this), timespan, timespan, unit);
                            if (!TIMER.compareAndSet(this, tm, task)) {
                                task.dispose();
                            }
                        }
                        
                    } else {
                        count = c;
                    }
                }
                
                missed = leave(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }
        
        static final class ConsumerIndexHolder implements Runnable {
            final long index;
            final WindowExactBoundedSubscriber<?> parent;
            public ConsumerIndexHolder(long index, WindowExactBoundedSubscriber<?> parent) {
                this.index = index;
                this.parent = parent;
            }
            
            @Override
            public void run() {
                WindowExactBoundedSubscriber<?> p = parent;
                if (p.selfCancel) {
                    throw new CancelledKeyException();
                }

                if (!p.cancelled) {
                    p.queue.offer(this);
                } else {
                    p.terminated = true;
                    p.disposeTimer();
                }
                if (p.enter()) {
                    p.drainLoop();
                }
            }
        }
    }
    
    static final class WindowSkipSubscriber<T>
    extends NbpQueueDrainSubscriber<T, Object, NbpObservable<T>>
    implements Disposable, Runnable {
        final long timespan;
        final long timeskip;
        final TimeUnit unit;
        final Scheduler.Worker worker;
        final int bufferSize;
        
        final List<NbpUnicastSubject<T>> windows;
        
        Disposable s;
        
        volatile boolean terminated;
        
        public WindowSkipSubscriber(NbpSubscriber<? super NbpObservable<T>> actual,
                long timespan, long timeskip, TimeUnit unit, 
                Worker worker, int bufferSize) {
            super(actual, new MpscLinkedQueue<Object>());
            this.timespan = timespan;
            this.timeskip = timeskip;
            this.unit = unit;
            this.worker = worker;
            this.bufferSize = bufferSize;
            this.windows = new LinkedList<NbpUnicastSubject<T>>();
        }
        
        @Override
        public void onSubscribe(Disposable s) {
            if (SubscriptionHelper.validateDisposable(this.s, s)) {
                return;
            }
            
            this.s = s;
            
            actual.onSubscribe(this);
            
            if (cancelled) {
                return;
            }
            
            final NbpUnicastSubject<T> w = NbpUnicastSubject.create(bufferSize);
            windows.add(w);
            
            actual.onNext(w);
            worker.schedule(new Runnable() {
                @Override
                public void run() {
                    complete(w);
                }
            }, timespan, unit);
            
            worker.schedulePeriodically(this, timeskip, timeskip, unit);
        }
        
        @Override
        public void onNext(T t) {
            if (fastEnter()) {
                for (NbpUnicastSubject<T> w : windows) {
                    w.onNext(t);
                }
                if (leave(-1) == 0) {
                    return;
                }
            } else {
                queue.offer(NotificationLite.next(t));
                if (!enter()) {
                    return;
                }
            }
            drainLoop();
        }
        
        @Override
        public void onError(Throwable t) {
            error = t;
            done = true;
            if (enter()) {
                drainLoop();
            }
            
            disposeWorker();
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            done = true;
            if (enter()) {
                drainLoop();
            }
            
            disposeWorker();
            actual.onComplete();
        }
        
        @Override
        public void dispose() {
            if (!cancelled) {
                cancelled = true;
            }
        }
        
        void disposeWorker() {
            worker.dispose();
        }
        
        @Override
        public void accept(NbpSubscriber<? super NbpObservable<T>> a, Object v) {
            // not used by this operator
        }
        
        void complete(NbpUnicastSubject<T> w) {
            queue.offer(new SubjectWork<T>(w, false));
            if (enter()) {
                drainLoop();
            }
        }
        
        void drainLoop() {
            final Queue<Object> q = queue;
            final NbpSubscriber<? super NbpObservable<T>> a = actual;
            final List<NbpUnicastSubject<T>> ws = windows;
            
            int missed = 1;
            
            for (;;) {
                
                for (;;) {
                    if (terminated) {
                        s.dispose();
                        disposeWorker();
                        q.clear();
                        ws.clear();
                        return;
                    }
                    
                    boolean d = done;
                    
                    Object v = q.poll();
                    
                    boolean empty = v == null;
                    boolean sw = v instanceof SubjectWork;
                    
                    if (d && (v == null || v instanceof SubjectWork)) {
                        q.clear();
                        disposeWorker();
                        Throwable e = error;
                        if (e != null) {
                            for (NbpUnicastSubject<T> w : ws) {
                                w.onError(e);
                            }
                        } else {
                            for (NbpUnicastSubject<T> w : ws) {
                                w.onError(e);
                            }
                        }
                        ws.clear();
                        return;
                    }
                    
                    if (empty) {
                        break;
                    }
                    
                    if (sw) {
                        @SuppressWarnings("unchecked")
                        SubjectWork<T> work = (SubjectWork<T>)v;
                        
                        if (work.open) {
                            if (cancelled) {
                                continue;
                            }
                            
                            final NbpUnicastSubject<T> w = NbpUnicastSubject.create(bufferSize);
                            ws.add(w);
                            a.onNext(w);
                                
                            worker.schedule(new Runnable() {
                                @Override
                                public void run() {
                                    complete(w);
                                }
                            }, timespan, unit);
                        } else {
                            ws.remove(work.w);
                            work.w.onComplete();
                            if (ws.isEmpty() && cancelled) {
                                terminated = true;
                            }
                            continue;
                        }
                    }
                    
                    for (NbpUnicastSubject<T> w : ws) {
                        w.onNext(NotificationLite.<T>getValue(v));
                    }
                }
                
                missed = leave(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }
        
        @Override
        public void run() {

            NbpUnicastSubject<T> w = NbpUnicastSubject.create(bufferSize);
            
            SubjectWork<T> sw = new SubjectWork<T>(w, true);
            if (!cancelled) {
                queue.offer(sw);
            }
            if (enter()) {
                drainLoop();
            }
        }
        
        static final class SubjectWork<T> {
            final NbpUnicastSubject<T> w;
            final boolean open;
            public SubjectWork(NbpUnicastSubject<T> w, boolean open) {
                this.w = w;
                this.open = open;
            }
        }
    }
    
}
