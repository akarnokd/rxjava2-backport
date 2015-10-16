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

public enum NbpOperatorIgnoreElements implements NbpOperator<Object, Object> {
    INSTANCE;
    
    @SuppressWarnings("unchecked")
    public static <T> NbpOperator<T, T> instance() {
        return (NbpOperator<T, T>)INSTANCE;
    }
    
    @Override
    public NbpSubscriber<? super Object> apply(final NbpSubscriber<? super Object> t) {
        return new NbpSubscriber<Object>() {
            @Override
            public void onSubscribe(Disposable s) {
                t.onSubscribe(s);
            }
            
            @Override
            public void onNext(Object v) {
                // deliberately ignored
            }
            
            @Override
            public void onError(Throwable e) {
                t.onError(e);
            }
            
            @Override
            public void onComplete() {
                t.onComplete();
            }
        };
    }
}
