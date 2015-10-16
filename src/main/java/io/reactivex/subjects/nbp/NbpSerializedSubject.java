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

package io.reactivex.subjects.nbp;

import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Predicate;
import io.reactivex.internal.util.*;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * Serializes calls to the Subscriber methods.
 * <p>All other Publisher and Subject methods are thread-safe by design.
 *
 * @param <T> the source value type
 * @param <R> the subject's result value type
 */
/* public */ final class NbpSerializedSubject<T, R> extends NbpSubject<T, R> {
    /** The actual subscriber to serialize Subscriber calls to. */
    final NbpSubject<T, R> actual;
    /** Indicates an emission is going on, guarted by this. */
    boolean emitting;
    /** If not null, it holds the missed NotificationLite events. */
    AppendOnlyLinkedArrayList<Object> queue;
    /** Indicates a terminal event has been received and all further events will be dropped. */
    volatile boolean done;
    
    /**
     * Constructor that wraps an actual subject.
     * @param actual the subject wrapped
     */
    public NbpSerializedSubject(final NbpSubject<T, R> actual) {
        super(new io.reactivex.NbpObservable.NbpOnSubscribe<R>() {
            @Override
            public void accept(io.reactivex.NbpObservable.NbpSubscriber<? super R> s) {
                actual.subscribe(s);
            }
        });
        this.actual = actual;
    }
    
    @Override
    public void onSubscribe(Disposable s) {
        // NO-OP
    }
    
    @Override
    public void onNext(T t) {
        if (done) {
            return;
        }
        synchronized (this) {
            if (done) {
                return;
            }
            if (emitting) {
                AppendOnlyLinkedArrayList<Object> q = queue;
                if (q == null) {
                    q = new AppendOnlyLinkedArrayList<Object>(4);
                    queue = q;
                }
                q.add(NotificationLite.next(t));
                return;
            }
            emitting = true;
        }
        actual.onNext(t);
        emitLoop();
    }
    
    @Override
    public void onError(Throwable t) {
        if (done) {
            RxJavaPlugins.onError(t);
            return;
        }
        boolean reportError;
        synchronized (this) {
            if (done) {
                reportError = true;
                return;
            } else {
                done = true;
                if (emitting) {
                    AppendOnlyLinkedArrayList<Object> q = queue;
                    if (q == null) {
                        q = new AppendOnlyLinkedArrayList<Object>(4);
                        queue = q;
                    }
                    q.setFirst(NotificationLite.error(t));
                    return;
                }
                reportError = false;
                emitting = true;
            }
        }
        if (reportError) {
            RxJavaPlugins.onError(t);
            return;
        }
        actual.onError(t);
    }
    
    @Override
    public void onComplete() {
        if (done) {
            return;
        }
        synchronized (this) {
            if (done) {
                return;
            }
            done = true;
            if (emitting) {
                AppendOnlyLinkedArrayList<Object> q = queue;
                if (q == null) {
                    q = new AppendOnlyLinkedArrayList<Object>(4);
                    queue = q;
                }
                q.add(NotificationLite.complete());
                return;
            }
            emitting = true;
        }
        actual.onComplete();
    }
    
    /** Loops until all notifications in the queue has been processed. */
    void emitLoop() {
        for (;;) {
            AppendOnlyLinkedArrayList<Object> q;
            synchronized (this) {
                q = queue;
                if (q == null) {
                    emitting = false;
                    return;
                }
                queue = null;
            }
            q.forEachWhile(consumer);
        }
    }

    final Predicate<Object> consumer = new Predicate<Object>() {
        @Override
        public boolean test(Object v) {
            return accept(v);
        }
    };
    
    /** Delivers the notification to the actual subscriber. */
    boolean accept(Object o) {
        return NotificationLite.accept(o, actual);
    }
    
    @Override
    public boolean hasSubscribers() {
        return actual.hasSubscribers();
    }
    
    @Override
    public boolean hasThrowable() {
        return actual.hasThrowable();
    }
    
    @Override
    public Throwable getThrowable() {
        return actual.getThrowable();
    }
    
    @Override
    public boolean hasValue() {
        return actual.hasValue();
    }
    
    @Override
    public R getValue() {
        return actual.getValue();
    }
    
    @Override
    public Object[] getValues() {
        return actual.getValues();
    }
    
    @Override
    public R[] getValues(R[] array) {
        return actual.getValues(array);
    }
    
    @Override
    public boolean hasComplete() {
        return actual.hasComplete();
    }
}
