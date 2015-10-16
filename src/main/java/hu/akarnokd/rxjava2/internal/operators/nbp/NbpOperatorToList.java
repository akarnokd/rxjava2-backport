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

import java.util.*;

import hu.akarnokd.rxjava2.NbpObservable.*;
import hu.akarnokd.rxjava2.disposables.Disposable;
import hu.akarnokd.rxjava2.functions.Supplier;
import hu.akarnokd.rxjava2.internal.disposables.EmptyDisposable;
import hu.akarnokd.rxjava2.internal.subscribers.nbp.NbpCancelledSubscriber;
import hu.akarnokd.rxjava2.internal.subscriptions.SubscriptionHelper;
import hu.akarnokd.rxjava2.plugins.RxJavaPlugins;

public final class NbpOperatorToList<T, U extends Collection<? super T>> implements NbpOperator<U, T> {
    
    @SuppressWarnings({"rawtypes", "unchecked"})
    static final NbpOperatorToList DEFAULT = new NbpOperatorToList(new Supplier() {
        @Override
        public Object get() {
            return new ArrayList();
        }
    });
    
    @SuppressWarnings("unchecked")
    public static <T> NbpOperatorToList<T, List<T>> defaultInstance() {
        return DEFAULT;
    }
    
    final Supplier<U> collectionSupplier;
    
    public NbpOperatorToList(Supplier<U> collectionSupplier) {
        this.collectionSupplier = collectionSupplier;
    }
    
    @Override
    public NbpSubscriber<? super T> apply(NbpSubscriber<? super U> t) {
        U coll;
        try {
            coll = collectionSupplier.get();
        } catch (Throwable e) {
            EmptyDisposable.error(e, t);
            return NbpCancelledSubscriber.INSTANCE;
        }
        return new ToListSubscriber<T, U>(t, coll);
    }
    
    static final class ToListSubscriber<T, U extends Collection<? super T>> implements NbpSubscriber<T> {
        U collection;
        final NbpSubscriber<? super U> actual;
        
        Disposable s;
        
        public ToListSubscriber(NbpSubscriber<? super U> actual, U collection) {
            this.actual = actual;
            this.collection = collection;
        }
        
        @Override
        public void onSubscribe(Disposable s) {
            if (SubscriptionHelper.validateDisposable(this.s, s)) {
                s.dispose();
                RxJavaPlugins.onError(new IllegalStateException("Subscription already set!"));
                return;
            }
            this.s = s;
            actual.onSubscribe(s);
        }
        
        @Override
        public void onNext(T t) {
            collection.add(t);
        }
        
        @Override
        public void onError(Throwable t) {
            collection = null;
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            U c = collection;
            collection = null;
            actual.onNext(c);
            actual.onComplete();
        }
    }
}
