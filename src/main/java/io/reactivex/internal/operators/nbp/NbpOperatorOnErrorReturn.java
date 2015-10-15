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

public final class NbpOperatorOnErrorReturn<T> implements NbpOperator<T, T> {
    final Function<? super Throwable, ? extends T> valueSupplier;
    public NbpOperatorOnErrorReturn(Function<? super Throwable, ? extends T> valueSupplier) {
        this.valueSupplier = valueSupplier;
    }
    
    @Override
    public NbpSubscriber<? super T> apply(NbpSubscriber<? super T> t) {
        return new OnErrorReturnSubscriber<>(t, valueSupplier);
    }
    
    static final class OnErrorReturnSubscriber<T> implements NbpSubscriber<T> {
        final NbpSubscriber<? super T> actual;
        final Function<? super Throwable, ? extends T> valueSupplier;
        
        Disposable s;
        
        volatile boolean done;
        
        public OnErrorReturnSubscriber(NbpSubscriber<? super T> actual, Function<? super Throwable, ? extends T> valueSupplier) {
            this.actual = actual;
            this.valueSupplier = valueSupplier;
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
            actual.onNext(t);
        }
        
        @Override
        public void onError(Throwable t) {
            done = true;
            T v;
            try {
                v = valueSupplier.apply(t);
            } catch (Throwable e) {
                e.addSuppressed(t);
                actual.onError(e);
                return;
            }
            
            if (v == null) {
                NullPointerException e = new NullPointerException("The supplied value is null");
                e.addSuppressed(t);
                actual.onError(e);
                return;
            }
            
            actual.onNext(v);
            actual.onComplete();
        }
        
        @Override
        public void onComplete() {
            actual.onComplete();
        }
    }
}