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

import java.util.ArrayDeque;

import io.reactivex.NbpObservable.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.subscriptions.SubscriptionHelper;

public final class NbpOperatorTakeLast<T> implements NbpOperator<T, T> {
    final int count;
    
    public NbpOperatorTakeLast(int count) {
        this.count = count;
    }

    @Override
    public NbpSubscriber<? super T> apply(NbpSubscriber<? super T> t) {
        return new TakeLastSubscriber<T>(t, count);
    }
    
    static final class TakeLastSubscriber<T> extends ArrayDeque<T> implements NbpSubscriber<T>, Disposable {
        /** */
        private static final long serialVersionUID = 7240042530241604978L;
        final NbpSubscriber<? super T> actual;
        final int count;
        
        Disposable s;
        
        volatile boolean done;
        volatile boolean cancelled;
        
        public TakeLastSubscriber(NbpSubscriber<? super T> actual, int count) {
            this.actual = actual;
            this.count = count;
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
            if (count == size()) {
                poll();
            }
            offer(t);
        }
        
        @Override
        public void onError(Throwable t) {
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            done = true;
            if (cancelled) {
                return;
            }
            NbpSubscriber<? super T> a = actual;
            for (;;) {
                if (cancelled) {
                    return;
                }
                T v = poll();
                if (v == null) {
                    if (!cancelled) {
                        a.onComplete();
                    }
                    return;
                }
                a.onNext(v);
            }
        }
        
        @Override
        public void dispose() {
            if (!cancelled) {
                cancelled = true;
                s.dispose();
            }
        }
    }
}
