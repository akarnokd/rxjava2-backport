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

import hu.akarnokd.rxjava2.*;
import hu.akarnokd.rxjava2.NbpObservable.*;
import hu.akarnokd.rxjava2.disposables.Disposable;
import hu.akarnokd.rxjava2.internal.subscriptions.SubscriptionHelper;
import hu.akarnokd.rxjava2.plugins.RxJavaPlugins;

public enum NbpOperatorDematerialize implements NbpOperator<Object, Try<Optional<Object>>> {
    INSTANCE;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T> NbpOperator<T, Try<Optional<T>>> instance() {
        return (NbpOperator)INSTANCE;
    }
    
    @Override
    public NbpSubscriber<? super Try<Optional<Object>>> apply(NbpSubscriber<? super Object> t) {
        return new DematerializeSubscriber<Object>(t);
    }
    
    static final class DematerializeSubscriber<T> implements NbpSubscriber<Try<Optional<T>>> {
        final NbpSubscriber<? super T> actual;
        
        boolean done;

        Disposable s;
        
        public DematerializeSubscriber(NbpSubscriber<? super T> actual) {
            this.actual = actual;
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
        public void onNext(Try<Optional<T>> t) {
            if (done) {
                return;
            }
            if (t.hasError()) {
                s.dispose();
                onError(t.error());
            } else {
                Optional<T> o = t.value();
                if (o.isPresent()) {
                    actual.onNext(o.get());
                } else {
                    s.dispose();
                    onComplete();
                }
            }
        }
        
        @Override
        public void onError(Throwable t) {
            if (done) {
                RxJavaPlugins.onError(t);
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
