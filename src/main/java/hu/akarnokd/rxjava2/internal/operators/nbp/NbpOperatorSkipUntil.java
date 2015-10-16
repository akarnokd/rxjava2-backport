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

import java.util.concurrent.atomic.AtomicBoolean;

import hu.akarnokd.rxjava2.NbpObservable;
import hu.akarnokd.rxjava2.NbpObservable.*;
import hu.akarnokd.rxjava2.disposables.*;
import hu.akarnokd.rxjava2.internal.disposables.*;
import hu.akarnokd.rxjava2.internal.subscriptions.SubscriptionHelper;
import hu.akarnokd.rxjava2.subscribers.nbp.NbpSerializedSubscriber;

public final class NbpOperatorSkipUntil<T, U> implements NbpOperator<T, T> {
    final NbpObservable<U> other;
    public NbpOperatorSkipUntil(NbpObservable<U> other) {
        this.other = other;
    }
    
    @Override
    public NbpSubscriber<? super T> apply(NbpSubscriber<? super T> child) {
        
        final NbpSerializedSubscriber<T> serial = new NbpSerializedSubscriber<T>(child);
        
        final ArrayCompositeResource<Disposable> frc = new ArrayCompositeResource<Disposable>(2, Disposables.consumeAndDispose());
        
        final SkipUntilSubscriber<T> sus = new SkipUntilSubscriber<T>(serial, frc);
        
        other.subscribe(new NbpSubscriber<U>() {
            Disposable s;
            @Override
            public void onSubscribe(Disposable s) {
                if (SubscriptionHelper.validateDisposable(this.s, s)) {
                    return;
                }
                this.s = s;
                frc.setResource(1, s);
            }
            
            @Override
            public void onNext(U t) {
                s.dispose();
                sus.notSkipping = true;
            }
            
            @Override
            public void onError(Throwable t) {
                frc.dispose();
                // in case the other emits an onError before the main even sets a subscription
                if (sus.compareAndSet(false, true)) {
                    EmptyDisposable.error(t, serial);
                } else {
                    serial.onError(t);
                }
            }
            
            @Override
            public void onComplete() {
                sus.notSkipping = true;
            }
        });
        
        return sus;
    }
    
    static final class SkipUntilSubscriber<T> extends AtomicBoolean implements NbpSubscriber<T>, Disposable {
        /** */
        private static final long serialVersionUID = -1113667257122396604L;
        final NbpSubscriber<? super T> actual;
        final ArrayCompositeResource<Disposable> frc;
        
        Disposable s;
        
        volatile boolean notSkipping;
        boolean notSkippingLocal;

        public SkipUntilSubscriber(NbpSubscriber<? super T> actual, ArrayCompositeResource<Disposable> frc) {
            this.actual = actual;
            this.frc = frc;
        }
        
        @Override
        public void onSubscribe(Disposable s) {
            if (SubscriptionHelper.validateDisposable(this.s, s)) {
                return;
            }
            this.s = s;
            if (frc.setResource(0, s)) {
                if (compareAndSet(false, true)) {
                    actual.onSubscribe(this);
                }
            }
        }
        
        @Override
        public void onNext(T t) {
            if (notSkippingLocal) {
                actual.onNext(t);
            } else
            if (notSkipping) {
                notSkippingLocal = true;
                actual.onNext(t);
            }
        }
        
        @Override
        public void onError(Throwable t) {
            frc.dispose();
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            frc.dispose();
            actual.onComplete();
        }
        
        @Override
        public void dispose() {
            frc.dispose();
        }
    }
}
