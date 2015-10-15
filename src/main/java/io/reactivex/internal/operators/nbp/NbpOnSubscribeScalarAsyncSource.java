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

import java.util.concurrent.Callable;

import io.reactivex.NbpObservable.*;
import io.reactivex.disposables.BooleanDisposable;

/**
 *
 */
public final class NbpOnSubscribeScalarAsyncSource<T> implements NbpOnSubscribe<T> {
    final Callable<? extends T> callable;
    public NbpOnSubscribeScalarAsyncSource(Callable<? extends T> callable) {
        this.callable = callable;
    }
    @Override
    public void accept(NbpSubscriber<? super T> s) {
        BooleanDisposable bd = new BooleanDisposable();
        s.onSubscribe(bd);
        if (bd.isDisposed()) {
            return;
        }
        T value;
        try {
            value = callable.call();
        } catch (Throwable e) {
            if (!bd.isDisposed()) {
                s.onError(e);
            }
            return;
        }
        if (bd.isDisposed()) {
            return;
        }
        s.onNext(value);
        s.onComplete();
    }
}
