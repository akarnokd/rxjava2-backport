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

import hu.akarnokd.rxjava2.NbpObservable.*;
import hu.akarnokd.rxjava2.disposables.Disposable;
import hu.akarnokd.rxjava2.functions.*;
import hu.akarnokd.rxjava2.internal.disposables.EmptyDisposable;
import hu.akarnokd.rxjava2.internal.subscribers.nbp.NbpCancelledSubscriber;
import hu.akarnokd.rxjava2.internal.subscriptions.SubscriptionHelper;

public final class NbpOperatorCollect<T, U> implements NbpOperator<U, T> {
    final Supplier<? extends U> initialSupplier;
    final BiConsumer<? super U, ? super T> collector;
    
    public NbpOperatorCollect(Supplier<? extends U> initialSupplier, BiConsumer<? super U, ? super T> collector) {
        this.initialSupplier = initialSupplier;
        this.collector = collector;
    }

    @Override
    public NbpSubscriber<? super T> apply(NbpSubscriber<? super U> t) {
        
        U u;
        try {
            u = initialSupplier.get();
        } catch (Throwable e) {
            EmptyDisposable.error(e, t);
            return NbpCancelledSubscriber.INSTANCE;
        }
        
        if (u == null) {
            EmptyDisposable.error(new NullPointerException("The inital supplier returned a null value"), t);
            return NbpCancelledSubscriber.INSTANCE;
        }
        
        return new CollectSubscriber<T, U>(t, u, collector);
    }
    
    static final class CollectSubscriber<T, U> implements NbpSubscriber<T> {
        final NbpSubscriber<? super U> actual;
        final BiConsumer<? super U, ? super T> collector;
        final U u;
        
        Disposable s;
        
        public CollectSubscriber(NbpSubscriber<? super U> actual, U u, BiConsumer<? super U, ? super T> collector) {
            this.actual = actual;
            this.collector = collector;
            this.u = u;
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
            try {
                collector.accept(u, t);
            } catch (Throwable e) {
                s.dispose();
                actual.onError(e);
            }
        }
        
        @Override
        public void onError(Throwable t) {
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            actual.onNext(u);
            actual.onComplete();
        }
    }
}