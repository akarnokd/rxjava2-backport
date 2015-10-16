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

import java.util.*;
import java.util.concurrent.atomic.*;

import hu.akarnokd.rxjava2.NbpObservable;
import hu.akarnokd.rxjava2.NbpObservable.*;
import hu.akarnokd.rxjava2.disposables.Disposable;
import hu.akarnokd.rxjava2.exceptions.CompositeException;
import hu.akarnokd.rxjava2.functions.Function;
import hu.akarnokd.rxjava2.internal.disposables.EmptyDisposable;
import hu.akarnokd.rxjava2.internal.queue.SpscLinkedArrayQueue;
import hu.akarnokd.rxjava2.internal.subscriptions.SubscriptionHelper;
import hu.akarnokd.rxjava2.plugins.RxJavaPlugins;

public final class NbpOnSubscribeCombineLatest<T, R> implements NbpOnSubscribe<R> {
    final NbpObservable<? extends T>[] sources;
    final Iterable<? extends NbpObservable<? extends T>> sourcesIterable;
    final Function<? super Object[], ? extends R> combiner;
    final int bufferSize;
    final boolean delayError;
    
    public NbpOnSubscribeCombineLatest(NbpObservable<? extends T>[] sources,
            Iterable<? extends NbpObservable<? extends T>> sourcesIterable,
            Function<? super Object[], ? extends R> combiner, int bufferSize,
            boolean delayError) {
        this.sources = sources;
        this.sourcesIterable = sourcesIterable;
        this.combiner = combiner;
        this.bufferSize = bufferSize;
        this.delayError = delayError;
    }

    
    @Override
    @SuppressWarnings("unchecked")
    public void accept(NbpSubscriber<? super R> s) {
        NbpObservable<? extends T>[] sources = this.sources;
        int count = 0;
        if (sources == null) {
            sources = new NbpObservable[8];
            for (NbpObservable<? extends T> p : sourcesIterable) {
                if (count == sources.length) {
                    NbpObservable<? extends T>[] b = new NbpObservable[count + (count >> 2)];
                    System.arraycopy(sources, 0, b, 0, count);
                    sources = b;
                }
                sources[count++] = p;
            }
        } else {
            count = sources.length;
        }
        
        if (count == 0) {
            EmptyDisposable.complete(s);
            return;
        }
        
        LatestCoordinator<T, R> lc = new LatestCoordinator<T, R>(s, combiner, count, bufferSize, delayError);
        lc.subscribe(sources);
    }
    
    static final class LatestCoordinator<T, R> extends AtomicInteger implements Disposable {
        /** */
        private static final long serialVersionUID = 8567835998786448817L;
        final NbpSubscriber<? super R> actual;
        final Function<? super Object[], ? extends R> combiner;
        final int count;
        final CombinerSubscriber<T, R>[] subscribers;
        final int bufferSize;
        final Object[] latest;
        final SpscLinkedArrayQueue<Object> queue;
        final boolean delayError;
        
        volatile boolean cancelled;
        
        volatile boolean done;
        
        volatile Throwable error;
        @SuppressWarnings("rawtypes")
        static final AtomicReferenceFieldUpdater<LatestCoordinator, Throwable> ERROR =
                AtomicReferenceFieldUpdater.newUpdater(LatestCoordinator.class, Throwable.class, "error");
        
        
        int active;
        int complete;
        
        @SuppressWarnings("unchecked")
        public LatestCoordinator(NbpSubscriber<? super R> actual, 
                Function<? super Object[], ? extends R> combiner, 
                int count, int bufferSize, boolean delayError) {
            this.actual = actual;
            this.combiner = combiner;
            this.count = count;
            this.bufferSize = bufferSize;
            this.delayError = delayError;
            this.latest = new Object[count];
            this.subscribers = new CombinerSubscriber[count];
            this.queue = new SpscLinkedArrayQueue<Object>(bufferSize);
        }
        
        public void subscribe(NbpObservable<? extends T>[] sources) {
            NbpSubscriber<T>[] as = subscribers;
            int len = as.length;
            for (int i = 0; i < len; i++) {
                as[i] = new CombinerSubscriber<T, R>(this, i);
            }
            lazySet(0); // release array contents
            actual.onSubscribe(this);
            for (int i = 0; i < len; i++) {
                if (cancelled) {
                    return;
                }
                sources[i].subscribe(as[i]);
            }
        }
        
        @Override
        public void dispose() {
            if (!cancelled) {
                cancelled = true;
                
                if (getAndIncrement() == 0) {
                    cancel(queue);
                }
            }
        }
        
        void cancel(Queue<?> q) {
            clear(q);
            for (CombinerSubscriber<T, R> s : subscribers) {
                s.dispose();
            }
        }
        
        void clear(Queue<?> q) {
            synchronized (this) {
                Arrays.fill(latest, null);
            }
            q.clear();
        }
        
        void combine(T value, int index) {
            CombinerSubscriber<T, R> cs = subscribers[index];
            
            int a;
            int c;
            int len;
            boolean empty;
            boolean f;
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                len = latest.length;
                Object o = latest[index];
                a = active;
                if (o == null) {
                    active = ++a;
                }
                c = complete;
                if (value == null) {
                    complete = ++c;
                } else {
                    latest[index] = value;
                }
                f = a == len;
                // see if either all sources completed
                empty = c == len 
                        || (value == null && o == null); // or this source completed without any value
                if (!empty) {
                    if (value != null && f) {
                        queue.offer(cs, latest.clone());
                    } else
                    if (value == null && error != null) {
                        done = true; // if this source completed without a value
                    }
                } else {
                    done = true;
                }
            }
            if (!f && value != null) {
                return;
            }
            drain();
        }
        void drain() {
            if (getAndIncrement() != 0) {
                return;
            }
            
            final Queue<Object> q = queue;
            final NbpSubscriber<? super R> a = actual;
            final boolean delayError = this.delayError;
            
            int missed = 1;
            for (;;) {
                
                if (checkTerminated(done, q.isEmpty(), a, q, delayError)) {
                    return;
                }
                
                for (;;) {
                    
                    boolean d = done;
                    @SuppressWarnings("unchecked")
                    CombinerSubscriber<T, R> cs = (CombinerSubscriber<T, R>)q.peek();
                    boolean empty = cs == null;
                    
                    if (checkTerminated(d, empty, a, q, delayError)) {
                        return;
                    }
                    
                    if (empty) {
                        break;
                    }

                    q.poll();
                    Object[] array = (Object[])q.poll();
                    
                    if (array == null) {
                        cancelled = true;
                        cancel(q);
                        a.onError(new IllegalStateException("Broken queue?! Sender received but not the array."));
                        return;
                    }
                    
                    R v;
                    try {
                        v = combiner.apply(array);
                    } catch (Throwable ex) {
                        cancelled = true;
                        cancel(q);
                        a.onError(ex);
                        return;
                    }
                    
                    if (v == null) {
                        cancelled = true;
                        cancel(q);
                        a.onError(new NullPointerException("The combiner returned a null"));
                        return;
                    }
                    
                    a.onNext(v);
                }
                
                missed = addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }
        
        
        boolean checkTerminated(boolean d, boolean empty, NbpSubscriber<?> a, Queue<?> q, boolean delayError) {
            if (cancelled) {
                cancel(q);
                return true;
            }
            if (d) {
                if (delayError) {
                    if (empty) {
                        clear(queue);
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
                        cancel(q);
                        a.onError(e);
                        return true;
                    } else
                    if (empty) {
                        clear(queue);
                        a.onComplete();
                        return true;
                    }
                }
            }
            return false;
        }
        
        void onError(Throwable e) {
            for (;;) {
                Throwable curr = error;
                if (curr instanceof CompositeException) {
                    CompositeException ce = new CompositeException((CompositeException)curr);
                    ce.suppress(e);
                    e = ce;
                }
                Throwable next = e;
                if (ERROR.compareAndSet(this, curr, next)) {
                    return;
                }
            }
        }
    }
    
    static final class CombinerSubscriber<T, R> implements NbpSubscriber<T>, Disposable {
        final LatestCoordinator<T, R> parent;
        final int index;
        
        boolean done;
        
        volatile Disposable s;
        @SuppressWarnings("rawtypes")
        static final AtomicReferenceFieldUpdater<CombinerSubscriber, Disposable> S =
                AtomicReferenceFieldUpdater.newUpdater(CombinerSubscriber.class, Disposable.class, "s");
        
        static final Disposable CANCELLED = new Disposable() {
            @Override
            public void dispose() { }
        };
        
        public CombinerSubscriber(LatestCoordinator<T, R> parent, int index) {
            this.parent = parent;
            this.index = index;
        }
        
        @Override
        public void onSubscribe(Disposable s) {
            if (!S.compareAndSet(this, null, s)) {
                s.dispose();
                if (s != CANCELLED) {
                    SubscriptionHelper.reportDisposableSet();
                }
                return;
            }
        }
        
        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }
            parent.combine(t, index);
        }
        
        @Override
        public void onError(Throwable t) {
            if (done) {
                RxJavaPlugins.onError(t);
                return;
            }
            parent.onError(t);
            done = true;
            parent.combine(null, index);
        }
        
        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            parent.combine(null, index);
        }
        
        @Override
        public void dispose() {
            Disposable a = s;
            if (a != CANCELLED) {
                a = S.getAndSet(this, CANCELLED);
                if (a != CANCELLED && a != null) {
                    a.dispose();
                }
            }
        }
    }
}