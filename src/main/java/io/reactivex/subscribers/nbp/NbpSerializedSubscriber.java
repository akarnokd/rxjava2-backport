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
package io.reactivex.subscribers.nbp;

import io.reactivex.NbpObservable.NbpSubscriber;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.internal.util.*;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * Serializes access to the onNext, onError and onComplete methods of another Subscriber.
 * 
 * <p>Note that onSubscribe is not serialized in respect of the other methods so
 * make sure the Subscription is set before any of the other methods are called.
 * 
 * <p>The implementation assumes that the actual Subscriber's methods don't throw.
 * 
 * @param <T> the value type
 */
public final class NbpSerializedSubscriber<T> implements NbpSubscriber<T> {
    final NbpSubscriber<? super T> actual;
    final boolean delayError;
    
    static final int QUEUE_LINK_SIZE = 4;
    
    Disposable subscription;
    
    boolean emitting;
    AppendOnlyLinkedArrayList<Object> queue;
    
    volatile boolean done;
    
    public NbpSerializedSubscriber(NbpSubscriber<? super T> actual) {
        this(actual, false);
    }
    
    public NbpSerializedSubscriber(NbpSubscriber<? super T> actual, boolean delayError) {
        this.actual = actual;
        this.delayError = delayError;
    }
    @Override
    public void onSubscribe(Disposable s) {
        if (SubscriptionHelper.validateDisposable(this.subscription, s)) {
            return;
        }
        this.subscription = s;
        
        actual.onSubscribe(s);
    }
    
    @Override
    public void onNext(T t) {
        if (done) {
            return;
        }
        if (t == null) {
            subscription.dispose();
            onError(new NullPointerException());
            return;
        }
        synchronized (this) {
            if (done) {
                return;
            }
            if (emitting) {
                AppendOnlyLinkedArrayList<Object> q = queue;
                if (q == null) {
                    q = new AppendOnlyLinkedArrayList<T>(QUEUE_LINK_SIZE);
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
            } else
            if (emitting) {
                done = true;
                AppendOnlyLinkedArrayList<Object> q = queue;
                if (q == null) {
                    q = new AppendOnlyLinkedArrayList<T>(QUEUE_LINK_SIZE);
                    queue = q;
                }
                Object err = NotificationLite.error(t);
                if (delayError) {
                    q.add(err);
                } else {
                    q.setFirst(err);
                }
                return;
            } else {
                done = true;
                emitting = true;
                reportError = false;
            }
        }
        
        if (reportError) {
            RxJavaPlugins.onError(t);
            return;
        }
        
        actual.onError(t);
        // no need to loop because this onError is the last event
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
            if (emitting) {
                AppendOnlyLinkedArrayList<Object> q = queue;
                if (q == null) {
                    q = new AppendOnlyLinkedArrayList<T>(QUEUE_LINK_SIZE);
                    queue = q;
                }
                q.add(NotificationLite.complete());
                return;
            }
            done = true;
            emitting = true;
        }
        
        actual.onComplete();
        // no need to loop because this onComplete is the last event
    }
    
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
            
            q.forEachWhile(this::accept);
        }
    }
    
    boolean accept(Object value) {
        if (NotificationLite.isComplete(value)) {
            actual.onComplete();
            return true;
        } else
        if (NotificationLite.isError(value)) {
            actual.onError(NotificationLite.getError(value));
            return true;
        }
        actual.onNext(NotificationLite.getValue(value));
        return false;
    }
}
