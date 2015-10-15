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

package io.reactivex.internal.operators.completable;

import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Completable;
import io.reactivex.Completable.*;
import io.reactivex.disposables.*;

public final class CompletableOnSubscribeConcatArray implements CompletableOnSubscribe {
    final Completable[] sources;
    
    public CompletableOnSubscribeConcatArray(Completable[] sources) {
        this.sources = sources;
    }
    
    @Override
    public void accept(CompletableSubscriber s) {
        ConcatInnerSubscriber inner = new ConcatInnerSubscriber(s, sources);
        s.onSubscribe(inner.sd);
        inner.next();
    }
    
    static final class ConcatInnerSubscriber extends AtomicInteger implements CompletableSubscriber {
        /** */
        private static final long serialVersionUID = -7965400327305809232L;

        final CompletableSubscriber actual;
        final Completable[] sources;
        
        int index;
        
        final SerialDisposable sd;
        
        public ConcatInnerSubscriber(CompletableSubscriber actual, Completable[] sources) {
            this.actual = actual;
            this.sources = sources;
            this.sd = new SerialDisposable();
        }
        
        @Override
        public void onSubscribe(Disposable d) {
            sd.set(d);
        }
        
        @Override
        public void onError(Throwable e) {
            actual.onError(e);
        }
        
        @Override
        public void onComplete() {
            next();
        }
        
        void next() {
            if (sd.isDisposed()) {
                return;
            }
            
            if (getAndIncrement() != 0) {
                return;
            }

            Completable[] a = sources;
            do {
                if (sd.isDisposed()) {
                    return;
                }
                
                int idx = index++;
                if (idx == a.length) {
                    actual.onComplete();
                    return;
                }
                
                a[idx].subscribe(this);
            } while (decrementAndGet() != 0);
        }
    }
}