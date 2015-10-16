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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;

import org.reactivestreams.*;

import hu.akarnokd.rxjava2.Observable.Operator;
import hu.akarnokd.rxjava2.functions.Function;
import hu.akarnokd.rxjava2.internal.queue.SpscLinkedArrayQueue;
import hu.akarnokd.rxjava2.internal.subscriptions.*;
import hu.akarnokd.rxjava2.internal.util.BackpressureHelper;
import hu.akarnokd.rxjava2.observables.GroupedObservable;
import hu.akarnokd.rxjava2.plugins.RxJavaPlugins;

public final class OperatorGroupBy<T, K, V> implements Operator<GroupedObservable<K, V>, T>{
    final Function<? super T, ? extends K> keySelector;
    final Function<? super T, ? extends V> valueSelector;
    final int bufferSize;
    final boolean delayError;
    
    public OperatorGroupBy(Function<? super T, ? extends K> keySelector, Function<? super T, ? extends V> valueSelector, int bufferSize, boolean delayError) {
        this.keySelector = keySelector;
        this.valueSelector = valueSelector;
        this.bufferSize = bufferSize;
        this.delayError = delayError;
    }
    
    @Override
    public Subscriber<? super T> apply(Subscriber<? super GroupedObservable<K, V>> t) {
        return new GroupBySubscriber<T, K, V>(t, keySelector, valueSelector, bufferSize, delayError);
    }
    
    public static final class GroupBySubscriber<T, K, V> 
    extends AtomicInteger
    implements Subscriber<T>, Subscription {
        /** */
        private static final long serialVersionUID = -3688291656102519502L;
        
        final Subscriber<? super GroupedObservable<K, V>> actual;
        final Function<? super T, ? extends K> keySelector;
        final Function<? super T, ? extends V> valueSelector;
        final int bufferSize;
        final boolean delayError;
        final Map<Object, GroupedUnicast<K, V>> groups;
        final Queue<GroupedObservable<K, V>> queue;
        
        static final Object NULL_KEY = new Object();
        
        Subscription s;
        
        volatile int cancelled;
        @SuppressWarnings("rawtypes")
        static final AtomicIntegerFieldUpdater<GroupBySubscriber> CANCELLED =
                AtomicIntegerFieldUpdater.newUpdater(GroupBySubscriber.class, "cancelled");

        volatile long requested;
        @SuppressWarnings("rawtypes")
        static final AtomicLongFieldUpdater<GroupBySubscriber> REQUESTED =
                AtomicLongFieldUpdater.newUpdater(GroupBySubscriber.class, "requested");
        
        volatile int groupCount;
        @SuppressWarnings("rawtypes")
        static final AtomicIntegerFieldUpdater<GroupBySubscriber> GROUP_COUNT =
                AtomicIntegerFieldUpdater.newUpdater(GroupBySubscriber.class, "groupCount");
        
        Throwable error;
        volatile boolean done;
        
        public GroupBySubscriber(Subscriber<? super GroupedObservable<K, V>> actual, Function<? super T, ? extends K> keySelector, Function<? super T, ? extends V> valueSelector, int bufferSize, boolean delayError) {
            this.actual = actual;
            this.keySelector = keySelector;
            this.valueSelector = valueSelector;
            this.bufferSize = bufferSize;
            this.delayError = delayError;
            this.groups = new ConcurrentHashMap<Object, GroupedUnicast<K, V>>();
            this.queue = new SpscLinkedArrayQueue<GroupedObservable<K, V>>(bufferSize);
            GROUP_COUNT.lazySet(this, 1);
        }
        
        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validateSubscription(this.s, s)) {
                return;
            }
            
            this.s = s;
            actual.onSubscribe(this);
            s.request(bufferSize);
        }
        
        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }

            final Queue<GroupedObservable<K, V>> q = this.queue;
            final Subscriber<? super GroupedObservable<K, V>> a = this.actual;

            K key;
            try {
                key = keySelector.apply(t);
            } catch (Throwable ex) {
                s.cancel();
                errorAll(a, q, ex);
                return;
            }
            
            boolean notNew = true;
            Object mapKey = key != null ? key : NULL_KEY;
            GroupedUnicast<K, V> group = groups.get(mapKey);
            if (group == null) {
                // if the main has been cancelled, stop creating groups
                // and skip this value
                if (cancelled == 0) {
                    group = GroupedUnicast.createWith(key, bufferSize, this, delayError);
                    groups.put(mapKey, group);
                    
                    GROUP_COUNT.getAndIncrement(this);
                    
                    notNew = false;
                    q.offer(group);
                    drain();
                } else {
                    return;
                }
            }
            
            V v;
            try {
                v = valueSelector.apply(t);
            } catch (Throwable ex) {
                s.cancel();
                errorAll(a, q, ex);
                return;
            }
            
            if (v == null) {
                s.cancel();
                errorAll(a, q, new NullPointerException("The valueSelector returned null"));
                return;
            }

            group.onNext(v);

            if (notNew) {
                s.request(1);
            }
        }
        
        @Override
        public void onError(Throwable t) {
            if (done) {
                RxJavaPlugins.onError(t);
                return;
            }
            error = t;
            done = true;
            GROUP_COUNT.decrementAndGet(this);
            drain();
        }
        
        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            GROUP_COUNT.decrementAndGet(this);
            drain();
        }

        @Override
        public void request(long n) {
            if (SubscriptionHelper.validateRequest(n)) {
                return;
            }
            
            BackpressureHelper.add(REQUESTED, this, n);
            drain();
        }
        
        @Override
        public void cancel() {
            // cancelling the main source means we don't want any more groups
            // but running groups still require new values
            if (CANCELLED.compareAndSet(this, 0, 1)) {
                if (GROUP_COUNT.decrementAndGet(this) == 0) {
                    s.cancel();
                }
            }
        }
        
        public void cancel(K key) {
            Object mapKey = key != null ? key : NULL_KEY;
            groups.remove(mapKey);
            if (GROUP_COUNT.decrementAndGet(this) == 0) {
                s.cancel();
            }
        }
        
        void drain() {
            if (getAndIncrement() != 0) {
                return;
            }
            
            int missed = 1;
            
            final Queue<GroupedObservable<K, V>> q = this.queue;
            final Subscriber<? super GroupedObservable<K, V>> a = this.actual;
            
            for (;;) {
                
                if (checkTerminated(done, q.isEmpty(), a, q)) {
                    return;
                }
                
                long r = requested;
                boolean unbounded = r == Long.MAX_VALUE;
                long e = 0L;
                
                while (r != 0) {
                    boolean d = done;
                    
                    GroupedObservable<K, V> t = q.poll();
                    
                    boolean empty = t == null;
                    
                    if (checkTerminated(d, empty, a, q)) {
                        return;
                    }
                    
                    if (empty) {
                        break;
                    }

                    a.onNext(t);
                    
                    r--;
                    e--;
                }
                
                if (e != 0L) {
                    if (!unbounded) {
                        REQUESTED.addAndGet(this, e);
                    }
                    s.request(-e);
                }
                
                missed = addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }
        
        void errorAll(Subscriber<? super GroupedObservable<K, V>> a, Queue<?> q, Throwable ex) {
            q.clear();
            List<GroupedUnicast<K, V>> list = new ArrayList<GroupedUnicast<K, V>>(groups.values());
            groups.clear();
            
            for (GroupedUnicast<K, V> e : list) {
                e.onError(ex);
            }
            
            a.onError(ex);
        }
        
        boolean checkTerminated(boolean d, boolean empty, 
                Subscriber<? super GroupedObservable<K, V>> a, Queue<?> q) {
            if (d) {
                Throwable err = error;
                if (err != null) {
                    errorAll(a, q, err);
                    return true;
                } else
                if (empty) {
                    List<GroupedUnicast<K, V>> list = new ArrayList<GroupedUnicast<K, V>>(groups.values());
                    groups.clear();
                    
                    for (GroupedUnicast<K, V> e : list) {
                        e.onComplete();
                    }
                    
                    actual.onComplete();
                    return true;
                }
            }
            return false;
        }
    }
    
    static final class GroupedUnicast<K, T> extends GroupedObservable<K, T> {
        
        public static <T, K> GroupedUnicast<K, T> createWith(K key, int bufferSize, GroupBySubscriber<?, K, T> parent, boolean delayError) {
            State<T, K> state = new State<T, K>(bufferSize, parent, key, delayError);
            return new GroupedUnicast<K, T>(key, state);
        }
        
        final State<T, K> state;
        
        protected GroupedUnicast(K key, State<T, K> state) {
            super(state, key);
            this.state = state;
        }
        
        public void onNext(T t) {
            state.onNext(t);
        }
        
        public void onError(Throwable e) {
            state.onError(e);
        }
        
        public void onComplete() {
            state.onComplete();
        }
    }
    
    static final class State<T, K> extends AtomicInteger implements Subscription, Publisher<T> {
        /** */
        private static final long serialVersionUID = -3852313036005250360L;

        final K key;
        final Queue<T> queue;
        final GroupBySubscriber<?, K, T> parent;
        final boolean delayError;
        
        volatile long requested;
        @SuppressWarnings("rawtypes")
        static final AtomicLongFieldUpdater<State> REQUESTED =
                AtomicLongFieldUpdater.newUpdater(State.class, "requested");
        
        volatile boolean done;
        Throwable error;
        
        volatile int cancelled;
        @SuppressWarnings("rawtypes")
        static final AtomicIntegerFieldUpdater<State> CANCELLED =
                AtomicIntegerFieldUpdater.newUpdater(State.class, "cancelled");
        
        volatile Subscriber<? super T> actual;
        @SuppressWarnings("rawtypes")
        static final AtomicReferenceFieldUpdater<State, Subscriber> ACTUAL =
                AtomicReferenceFieldUpdater.newUpdater(State.class, Subscriber.class, "actual");

        volatile int once;
        @SuppressWarnings("rawtypes")
        static final AtomicIntegerFieldUpdater<State> ONCE =
                AtomicIntegerFieldUpdater.newUpdater(State.class, "once");

        public State(int bufferSize, GroupBySubscriber<?, K, T> parent, K key, boolean delayError) {
            this.queue = new SpscLinkedArrayQueue<T>(bufferSize);
            this.parent = parent;
            this.key = key;
            this.delayError = delayError;
        }
        
        @Override
        public void request(long n) {
            if (SubscriptionHelper.validateRequest(n)) {
                return;
            }
            BackpressureHelper.add(REQUESTED, this, n);
            drain();
        }
        
        @Override
        public void cancel() {
            if (CANCELLED.compareAndSet(this, 0, 1)) {
                if (getAndIncrement() == 0) {
                    parent.cancel(key);
                }
            }
        }
        
        @Override
        public void subscribe(Subscriber<? super T> s) {
            if (ONCE.compareAndSet(this, 0, 1)) {
                s.onSubscribe(this);
                ACTUAL.lazySet(this, s);
                drain();
            } else {
                EmptySubscription.error(new IllegalStateException("Only one Subscriber allowed!"), s);
            }
        }

        public void onNext(T t) {
            if (t == null) {
                error = new NullPointerException();
                done = true;
            } else {
                queue.offer(t);
            }
            drain();
        }
        
        public void onError(Throwable e) {
            error = e;
            done = true;
            drain();
        }
        
        public void onComplete() {
            done = true;
            drain();
        }

        void drain() {
            if (getAndIncrement() != 0) {
                return;
            }
            int missed = 1;
            
            final Queue<T> q = queue;
            final boolean delayError = this.delayError;
            Subscriber<? super T> a = actual;
            for (;;) {
                if (a != null) {
                    if (checkTerminated(done, q.isEmpty(), a, delayError)) {
                        return;
                    }
                    
                    long r = requested;
                    boolean unbounded = r == Long.MAX_VALUE;
                    long e = 0;
                    
                    while (r != 0L) {
                        boolean d = done;
                        T v = q.poll();
                        boolean empty = v == null;
                        
                        if (checkTerminated(d, empty, a, delayError)) {
                            return;
                        }
                        
                        if (empty) {
                            break;
                        }
                        
                        a.onNext(v);
                        
                        r--;
                        e--;
                    }
                    
                    if (e != 0L) {
                        if (!unbounded) {
                            REQUESTED.addAndGet(this, e);
                        }
                        parent.s.request(-e);
                    }
                }
                
                missed = addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
                if (a == null) {
                    a = actual;
                }
            }
        }
        
        boolean checkTerminated(boolean d, boolean empty, Subscriber<? super T> a, boolean delayError) {
            if (cancelled != 0) {
                queue.clear();
                parent.cancel(key);
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