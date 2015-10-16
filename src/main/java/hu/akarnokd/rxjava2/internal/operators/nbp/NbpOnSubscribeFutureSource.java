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

import java.util.concurrent.*;

import hu.akarnokd.rxjava2.NbpObservable.*;
import hu.akarnokd.rxjava2.disposables.BooleanDisposable;

public final class NbpOnSubscribeFutureSource<T> implements NbpOnSubscribe<T> {
    final Future<? extends T> future;
    final long timeout;
    final TimeUnit unit;

    public NbpOnSubscribeFutureSource(Future<? extends T> future, long timeout, TimeUnit unit) {
        this.future = future;
        this.timeout = timeout;
        this.unit = unit;
    }
    
    @Override
    public void accept(NbpSubscriber<? super T> s) {
        BooleanDisposable bd = new BooleanDisposable();
        s.onSubscribe(bd);
        if (!bd.isDisposed()) {
            T v;
            try {
                v = unit != null ? future.get(timeout, unit) : future.get();
            } catch (Throwable ex) {
                if (!bd.isDisposed()) {
                    s.onError(ex);
                }
                return;
            } finally {
                future.cancel(true); // TODO ?? not sure about this
            }
            if (!bd.isDisposed()) {
                if (v != null) {
                    s.onNext(v);
                    s.onComplete();
                } else {
                    s.onError(new NullPointerException("Future returned null"));
                }
            }
        }
    }
}
