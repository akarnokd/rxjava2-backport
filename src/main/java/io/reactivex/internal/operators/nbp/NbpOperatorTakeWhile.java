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

import io.reactivex.functions.*;

import io.reactivex.NbpObservable.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.subscriptions.SubscriptionHelper;

public final class NbpOperatorTakeWhile<T> implements NbpOperator<T, T> {
    final Predicate<? super T> predicate;
    public NbpOperatorTakeWhile(Predicate<? super T> predicate) {
        this.predicate = predicate;
    }
    
    @Override
    public NbpSubscriber<? super T> apply(NbpSubscriber<? super T> t) {
        return new TakeWhileSubscriber<T>(t, predicate);
    }
    
    static final class TakeWhileSubscriber<T> implements NbpSubscriber<T> {
        final NbpSubscriber<? super T> actual;
        final Predicate<? super T> predicate;
        
        Disposable s;
        
        boolean done;
        
        public TakeWhileSubscriber(NbpSubscriber<? super T> actual, Predicate<? super T> predicate) {
            this.actual = actual;
            this.predicate = predicate;
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
            boolean b;
            try {
                b = predicate.test(t);
            } catch (Throwable e) {
                done = true;
                s.dispose();
                actual.onError(e);
                return;
            }
            
            if (!b) {
                done = true;
                s.dispose();
                actual.onComplete();
                return;
            }
            
            actual.onNext(t);
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
            if (done) {
                return;
            }
            done = true;
            actual.onComplete();
        }
    }
}
