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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;
import io.reactivex.functions.*;

import io.reactivex.NbpObservable.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.disposables.EmptyDisposable;
import io.reactivex.internal.queue.SpscLinkedArrayQueue;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.observables.nbp.NbpGroupedObservable;

public final class NbpOperatorGroupBy<T, K, V> implements NbpOperator<NbpGroupedObservable<K, V>, T>{
    final Function<? super T, ? extends K> keySelector;
    final Function<? super T, ? extends V> valueSelector;
    final int bufferSize;
    final boolean delayError;
    
    public NbpOperatorGroupBy(Function<? super T, ? extends K> keySelector, Function<? super T, ? extends V> valueSelector, int bufferSize, boolean delayError) {
        this.keySelector = keySelector;
        this.valueSelector = valueSelector;
        this.bufferSize = bufferSize;
        this.delayError = delayError;
    }
    
    @Override
    public NbpSubscriber<? super T> apply(NbpSubscriber<? super NbpGroupedObservable<K, V>> t) {
        return new GroupBySubscriber<T, K, V>(t, keySelector, valueSelector, bufferSize, delayError);
    }
    
    public static final class GroupBySubscriber<T, K, V> extends AtomicInteger implements NbpSubscriber<T>, Disposable {
        /** */
        private static final long serialVersionUID = -3688291656102519502L;
        
        final NbpSubscriber<? super NbpGroupedObservable<K, V>> actual;
        final Function<? super T, ? extends K> keySelector;
        final Function<? super T, ? extends V> valueSelector;
        final int bufferSize;
        final boolean delayError;
        final Map<Object, GroupedUnicast<K, V>> groups;
        
        static final Object NULL_KEY = new Object();
        
        Disposable s;
        
        volatile int cancelled;
        @SuppressWarnings("rawtypes")
        static final AtomicIntegerFieldUpdater<GroupBySubscriber> CANCELLED =
                AtomicIntegerFieldUpdater.newUpdater(GroupBySubscriber.class, "cancelled");

        public GroupBySubscriber(NbpSubscriber<? super NbpGroupedObservable<K, V>> actual, Function<? super T, ? extends K> keySelector, Function<? super T, ? extends V> valueSelector, int bufferSize, boolean delayError) {
            this.actual = actual;
            this.keySelector = keySelector;
            this.valueSelector = valueSelector;
            this.bufferSize = bufferSize;
            this.delayError = delayError;
            this.groups = new ConcurrentHashMap<Object, GroupedUnicast<K, V>>();
            this.lazySet(1);
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
            K key;
            try {
                key = keySelector.apply(t);
            } catch (Throwable e) {
                s.dispose();
                onError(e);
                return;
            }
            
            Object mapKey = key != null ? key : NULL_KEY;
            GroupedUnicast<K, V> group = groups.get(mapKey);
            if (group == null) {
                // if the main has been cancelled, stop creating groups
                // and skip this value
                if (cancelled != 0) {
                    return;
                }
                
                group = GroupedUnicast.createWith(key, bufferSize, this, delayError);
                groups.put(mapKey, group);
                
                getAndIncrement();
                
                actual.onNext(group);
            }
            
            V v;
            try {
                v = valueSelector.apply(t);
            } catch (Throwable e) {
                s.dispose();
                onError(e);
                return;
            }
            
            if (v == null) {
                s.dispose();
                onError(new NullPointerException("The value supplied is null"));
                return;
            }

            group.onNext(v);
        }
        
        @Override
        public void onError(Throwable t) {
            List<GroupedUnicast<K, V>> list = new ArrayList<GroupedUnicast<K, V>>(groups.values());
            groups.clear();
            
            for (GroupedUnicast<K, V> e : list) {
                e.onError(t);
            }
            
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            List<GroupedUnicast<K, V>> list = new ArrayList<GroupedUnicast<K, V>>(groups.values());
            groups.clear();
            
            for (GroupedUnicast<K, V> e : list) {
                e.onComplete();
            }
            
            actual.onComplete();
        }

        @Override
        public void dispose() {
            // cancelling the main source means we don't want any more groups
            // but running groups still require new values
            if (CANCELLED.compareAndSet(this, 0, 1)) {
                if (decrementAndGet() == 0) {
                    s.dispose();
                }
            }
        }
        
        public void cancel(K key) {
            Object mapKey = key != null ? key : NULL_KEY;
            groups.remove(mapKey);
            if (decrementAndGet() == 0) {
                s.dispose();
            }
        }
    }
    
    static final class GroupedUnicast<K, T> extends NbpGroupedObservable<K, T> {
        
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
    
    static final class State<T, K> extends AtomicInteger implements Disposable, NbpOnSubscribe<T> {
        /** */
        private static final long serialVersionUID = -3852313036005250360L;

        final K key;
        final Queue<T> queue;
        final GroupBySubscriber<?, K, T> parent;
        final boolean delayError;
        
        volatile boolean done;
        Throwable error;
        
        volatile int cancelled;
        @SuppressWarnings("rawtypes")
        static final AtomicIntegerFieldUpdater<State> CANCELLED =
                AtomicIntegerFieldUpdater.newUpdater(State.class, "cancelled");
        
        volatile NbpSubscriber<? super T> actual;
        @SuppressWarnings("rawtypes")
        static final AtomicReferenceFieldUpdater<State, NbpSubscriber> ACTUAL =
                AtomicReferenceFieldUpdater.newUpdater(State.class, NbpSubscriber.class, "actual");
        
        public State(int bufferSize, GroupBySubscriber<?, K, T> parent, K key, boolean delayError) {
            this.queue = new SpscLinkedArrayQueue<T>(bufferSize);
            this.parent = parent;
            this.key = key;
            this.delayError = delayError;
        }
        
        @Override
        public void dispose() {
            if (CANCELLED.compareAndSet(this, 0, 1)) {
                if (getAndIncrement() == 0) {
                    parent.cancel(key);
                }
            }
        }
        
        @Override
        public void accept(NbpSubscriber<? super T> s) {
            if (ACTUAL.compareAndSet(this, null, s)) {
                s.onSubscribe(this);
                drain();
            } else {
                EmptyDisposable.error(new IllegalStateException("Only one Subscriber allowed!"), s);
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
            NbpSubscriber<? super T> a = actual;
            for (;;) {
                if (a != null) {
                    if (checkTerminated(done, q.isEmpty(), a, delayError)) {
                        return;
                    }
                    
                    for (;;) {
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
        
        boolean checkTerminated(boolean d, boolean empty, NbpSubscriber<? super T> a, boolean delayError) {
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