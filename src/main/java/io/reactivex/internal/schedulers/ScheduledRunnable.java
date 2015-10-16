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

package io.reactivex.internal.schedulers;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReferenceArray;

import io.reactivex.disposables.Disposable;
import io.reactivex.internal.disposables.*;
import io.reactivex.plugins.RxJavaPlugins;

public final class ScheduledRunnable extends AtomicReferenceArray<Object> implements Runnable, Disposable {
    /** */
    private static final long serialVersionUID = -6120223772001106981L;
    final Runnable actual;
    
    static final Object DISPOSED = new Object();
    
    static final Object DONE = new Object();
    
    static final int PARENT_INDEX = 0;
    static final int FUTURE_INDEX = 1;
    
    /**
     * Creates a ScheduledRunnable by wrapping the given action and setting
     * up the optional parent.
     * @param actual the runnable to wrap, not-null (not verified)
     * @param parent the parent tracking container or null if none
     */
    public ScheduledRunnable(Runnable actual, CompositeResource<Disposable> parent) {
        super(2);
        this.actual = actual;
        this.lazySet(0, parent);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        try {
            actual.run();
        } catch (Throwable e) {
            RxJavaPlugins.onError(e);
        } finally {
            Object o = get(PARENT_INDEX);
            if (o != DISPOSED && o != null) {
                // done races with dispose here
                if (compareAndSet(PARENT_INDEX, o, DONE)) {
                    ((CompositeResource<Disposable>)o).delete(this);
                }
            }
            
            for (;;) {
                o = get(FUTURE_INDEX);
                if (o != DISPOSED) {
                    // o is either null or a future
                    if (compareAndSet(FUTURE_INDEX, o, DONE)) {
                        break;
                    }
                } else {
                    break;
                }
            }
        }
    }
    
    public void setFuture(Future<?> f) {
        for (;;) {
            Object o = get(FUTURE_INDEX);
            if (o == DONE) {
                return;
            }
            if (o == DISPOSED) {
                f.cancel(true);
                return;
            }
            if (compareAndSet(FUTURE_INDEX, o, f)) {
                return;
            }
        }
    }
    
    /**
     * Returns true if this ScheduledRunnable has been scheduled.
     * @return true if this ScheduledRunnable has been scheduled.
     */
    public boolean wasScheduled() {
        return get(FUTURE_INDEX) != null;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void dispose() {
        for (;;) {
            Object o = get(FUTURE_INDEX);
            if (o == DONE || o == DISPOSED) {
                break;
            }
            if (compareAndSet(FUTURE_INDEX, o, DISPOSED)) {
                if (o != null) {
                    ((Future<?>)o).cancel(true);
                }
                break;
            }
        }
        
        for (;;) {
            Object o = get(PARENT_INDEX);
            if (o == DONE || o == DISPOSED || o == null) {
                break;
            }
            if (compareAndSet(PARENT_INDEX, o, DISPOSED)) {
                ((CompositeResource<Disposable>)o).delete(this);
                return;
            }
        }
    }
}
