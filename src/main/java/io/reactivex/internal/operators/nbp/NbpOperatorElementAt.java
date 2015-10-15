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

import io.reactivex.NbpObservable.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.subscriptions.SubscriptionHelper;

public final class NbpOperatorElementAt<T> implements NbpOperator<T, T> {
    final long index;
    final T defaultValue;
    public NbpOperatorElementAt(long index, T defaultValue) {
        this.index = index;
        this.defaultValue = defaultValue;
    }
    @Override
    public NbpSubscriber<? super T> apply(NbpSubscriber<? super T> t) {
        return new ElementAtSubscriber<>(t, index, defaultValue);
    }
    
    static final class ElementAtSubscriber<T> implements NbpSubscriber<T> {
        final NbpSubscriber<? super T> actual;
        final long index;
        final T defaultValue;
        
        Disposable s;
        
        long count;
        
        boolean done;
        
        public ElementAtSubscriber(NbpSubscriber<? super T> actual, long index, T defaultValue) {
            this.actual = actual;
            this.index = index;
            this.defaultValue = defaultValue;
        }
        
        @Override
        public void onSubscribe(Disposable s) {
            if (SubscriptionHelper.validateDisposable(this.s, s)) {
                return;
            }
            this.s = s;
            actual.onSubscribe(s);
        }
        
        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }
            long c = count;
            if (c == index) {
                done = true;
                s.dispose();
                actual.onNext(t);
                actual.onComplete();
                return;
            }
            count = c + 1;
        }
        
        @Override
        public void onError(Throwable t) {
            if (done) {
                return;
            }
            done = true;
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            if (index <= count && !done) {
                done = true;
                T v = defaultValue;
                if (v == null) {
                    actual.onError(new IndexOutOfBoundsException());
                } else {
                    actual.onNext(v);
                    actual.onComplete();
                }
            }
        }
    }
}
