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

import java.util.Queue;
import java.util.concurrent.atomic.*;

import hu.akarnokd.rxjava2.NbpObservable;
import hu.akarnokd.rxjava2.NbpObservable.*;
import hu.akarnokd.rxjava2.disposables.Disposable;
import hu.akarnokd.rxjava2.functions.Supplier;
import hu.akarnokd.rxjava2.internal.queue.MpscLinkedQueue;
import hu.akarnokd.rxjava2.internal.subscribers.nbp.*;
import hu.akarnokd.rxjava2.internal.subscriptions.SubscriptionHelper;
import hu.akarnokd.rxjava2.internal.util.NotificationLite;
import hu.akarnokd.rxjava2.plugins.RxJavaPlugins;
import hu.akarnokd.rxjava2.subjects.nbp.NbpUnicastSubject;
import hu.akarnokd.rxjava2.subscribers.nbp.NbpSerializedSubscriber;

public final class NbpOperatorWindowBoundarySupplier<T, B> implements NbpOperator<NbpObservable<T>, T> {
    final Supplier<? extends NbpObservable<B>> other;
    final int bufferSize;
    
    public NbpOperatorWindowBoundarySupplier(Supplier<? extends NbpObservable<B>> other, int bufferSize) {
        this.other = other;
        this.bufferSize = bufferSize;
    }
    
    @Override
    public NbpSubscriber<? super T> apply(NbpSubscriber<? super NbpObservable<T>> t) {
        return new WindowBoundaryMainSubscriber<T, B>(new NbpSerializedSubscriber<NbpObservable<T>>(t), other, bufferSize);
    }
    
    static final class WindowBoundaryMainSubscriber<T, B> 
    extends NbpQueueDrainSubscriber<T, Object, NbpObservable<T>> 
    implements Disposable {
        
        final Supplier<? extends NbpObservable<B>> other;
        final int bufferSize;
        
        Disposable s;
        
        volatile Disposable boundary;
        @SuppressWarnings("rawtypes")
        static final AtomicReferenceFieldUpdater<WindowBoundaryMainSubscriber, Disposable> BOUNDARY =
                AtomicReferenceFieldUpdater.newUpdater(WindowBoundaryMainSubscriber.class, Disposable.class, "boundary");
        
        static final Disposable CANCELLED = new Disposable() {
            @Override
            public void dispose() { }
        };
        
        NbpUnicastSubject<T> window;
        
        static final Object NEXT = new Object();
        
        volatile long windows;
        @SuppressWarnings("rawtypes")
        static final AtomicLongFieldUpdater<WindowBoundaryMainSubscriber> WINDOWS =
                AtomicLongFieldUpdater.newUpdater(WindowBoundaryMainSubscriber.class, "windows");

        public WindowBoundaryMainSubscriber(NbpSubscriber<? super NbpObservable<T>> actual, Supplier<? extends NbpObservable<B>> other,
                int bufferSize) {
            super(actual, new MpscLinkedQueue<Object>());
            this.other = other;
            this.bufferSize = bufferSize;
            WINDOWS.lazySet(this, 1);
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
            
            NbpObservable<B> p;
            
            try {
                p = other.get();
            } catch (Throwable e) {
                s.dispose();
                a.onError(e);
                return;
            }
            
            if (p == null) {
                s.dispose();
                a.onError(new NullPointerException("The first window NbpObservable supplied is null"));
                return;
            }
            
            NbpUnicastSubject<T> w = NbpUnicastSubject.create(bufferSize);

            window = w;

            a.onNext(w);
            
            WindowBoundaryInnerSubscriber<T, B> inner = new WindowBoundaryInnerSubscriber<T, B>(this);
            
            if (BOUNDARY.compareAndSet(this, null, inner)) {
                WINDOWS.getAndIncrement(this);
                p.subscribe(inner);
                return;
            }
        }
        
        @Override
        public void onNext(T t) {
            if (fastEnter()) {
                NbpUnicastSubject<T> w = window;
                
                w.onNext(t);
                
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
            if (done) {
                RxJavaPlugins.onError(error);
                return;
            }
            error = t;
            done = true;
            if (enter()) {
                drainLoop();
            }
            
            if (WINDOWS.decrementAndGet(this) == 0) {
                disposeBoundary();
            }
            
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            if (enter()) {
                drainLoop();
            }
            
            if (WINDOWS.decrementAndGet(this) == 0) {
                disposeBoundary();
            }

            actual.onComplete();
            
        }
        
        @Override
        public void dispose() {
            if (!cancelled) {
                cancelled = true;
            }
        }
        
        void disposeBoundary() {
            Disposable d = boundary;
            if (d != CANCELLED) {
                d = BOUNDARY.getAndSet(this, CANCELLED);
                if (d != CANCELLED && d != null) {
                    d.dispose();
                }
            }
        }
        
        void drainLoop() {
            final Queue<Object> q = queue;
            final NbpSubscriber<? super NbpObservable<T>> a = actual;
            int missed = 1;
            NbpUnicastSubject<T> w = window;
            for (;;) {
                
                for (;;) {
                    boolean d = done;
                    
                    Object o = q.poll();
                    boolean empty = o == null;
                    
                    if (d && empty) {
                        disposeBoundary();
                        Throwable e = error;
                        if (e != null) {
                            w.onError(e);
                        } else {
                            w.onComplete();
                        }
                        return;
                    }
                    
                    if (empty) {
                        break;
                    }
                    
                    if (o == NEXT) {
                        w.onComplete();

                        if (WINDOWS.decrementAndGet(this) == 0) {
                            disposeBoundary();
                            return;
                        }

                        if (cancelled) {
                            continue;
                        }
                        
                        NbpObservable<B> p;

                        try {
                            p = other.get();
                        } catch (Throwable e) {
                            disposeBoundary();
                            a.onError(e);
                            return;
                        }
                        
                        if (p == null) {
                            disposeBoundary();
                            a.onError(new NullPointerException("The NbpObservable supplied is null"));
                            return;
                        }
                        
                        w = NbpUnicastSubject.create(bufferSize);
                        
                        WINDOWS.getAndIncrement(this);

                        window = w;

                        a.onNext(w);
                        
                        WindowBoundaryInnerSubscriber<T, B> b = new WindowBoundaryInnerSubscriber<T, B>(this);
                        
                        if (BOUNDARY.compareAndSet(this, boundary, b)) {
                            p.subscribe(b);
                        }
                        
                        continue;
                    }
                    
                    w.onNext(NotificationLite.<T>getValue(o));
                }
                
                missed = leave(-missed);
                if (missed == 0) {
                    return;
                }
            }
        }
        
        void next() {
            queue.offer(NEXT);
            if (enter()) {
                drainLoop();
            }
        }
        
        @Override
        public void accept(NbpSubscriber<? super NbpObservable<T>> a, Object v) {
            // not used by this operator
        }
    }
    
    static final class WindowBoundaryInnerSubscriber<T, B> extends NbpDisposableSubscriber<B> {
        final WindowBoundaryMainSubscriber<T, B> parent;
        
        boolean done;
        
        public WindowBoundaryInnerSubscriber(WindowBoundaryMainSubscriber<T, B> parent) {
            this.parent = parent;
        }
        
        @Override
        public void onNext(B t) {
            if (done) {
                return;
            }
            done = true;
            dispose();
            parent.next();
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
            parent.onComplete();
//            parent.next();
        }
    }
}
