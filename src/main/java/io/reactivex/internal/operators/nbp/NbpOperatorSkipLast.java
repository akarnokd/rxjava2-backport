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

public final class NbpOperatorSkipLast<T> implements NbpOperator<T, T> {
    final int skip;
    
    public NbpOperatorSkipLast(int skip) {
        this.skip = skip;
    }

    @Override
    public NbpSubscriber<? super T> apply(NbpSubscriber<? super T> s) {
        return new SkipLastSubscriber<T>(s, skip);
    }
    
    static final class SkipLastSubscriber<T> extends ArrayDeque<T> implements NbpSubscriber<T> {
        /** */
        private static final long serialVersionUID = -3807491841935125653L;
        final NbpSubscriber<? super T> actual;
        final int skip;
        
        public SkipLastSubscriber(NbpSubscriber<? super T> actual, int skip) {
            super(skip);
            this.actual = actual;
            this.skip = skip;
        }

        @Override
        public void onSubscribe(Disposable s) {
            actual.onSubscribe(s);
        }
        
        @Override
        public void onNext(T t) {
            if (skip == size()) {
                actual.onNext(poll());
            }
            offer(t);
        }
        
        @Override
        public void onError(Throwable t) {
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            actual.onComplete();
        }
    }
}
