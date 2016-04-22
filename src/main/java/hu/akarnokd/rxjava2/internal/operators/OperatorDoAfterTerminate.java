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

package hu.akarnokd.rxjava2.internal.operators;

import org.reactivestreams.*;

import hu.akarnokd.rxjava2.Observable.Operator;
import hu.akarnokd.rxjava2.plugins.RxJavaPlugins;

public final class OperatorDoAfterTerminate<T> implements Operator<T, T> {
    final Runnable run;
    
    public OperatorDoAfterTerminate(Runnable run) {
        this.run = run;
    }
    
    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> t) {
        return new DoAfterTerminateSubscriber<T>(t, run);
    }
    
    static final class DoAfterTerminateSubscriber<T> implements Subscriber<T> {
        
        final Subscriber<? super T> actual;
        final Runnable run;

        public DoAfterTerminateSubscriber(Subscriber<? super T> actual, Runnable run) {
            this.actual = actual;
            this.run = run;
        }
        
        @Override
        public void onSubscribe(Subscription s) {
            actual.onSubscribe(s);
        }
        
        @Override
        public void onNext(T t) {
            actual.onNext(t);
        }
        
        @Override
        public void onError(Throwable t) {
            actual.onError(t);
            run();
        }
        
        @Override
        public void onComplete() {
            actual.onComplete();
            run();
        }
        
        void run() {
            try {
                run.run();
            } catch (Throwable ex) {
                RxJavaPlugins.onError(ex);
            }
        }
    }
}
