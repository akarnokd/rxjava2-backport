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

import io.reactivex.NbpObservable.*;
import io.reactivex.disposables.BooleanDisposable;

/**
 * 
 */
public final class NbpOnSubscribeArraySource<T> implements NbpOnSubscribe<T> {
    final T[] array;
    public NbpOnSubscribeArraySource(T[] array) {
        this.array = array;
    }
    public T[] array() {
        return array;
    }
    @Override
    public void accept(NbpSubscriber<? super T> s) {
        BooleanDisposable bd = new BooleanDisposable();
        
        s.onSubscribe(bd);
        
        T[] a = array;
        int n = a.length;
        
        for (int i = 0; i < n && !bd.isDisposed(); i++) {
            T value = a[i];
            if (value == null) {
                s.onError(new NullPointerException("The " + i + "th element is null"));
                return;
            }
            s.onNext(value);
        }
        if (!bd.isDisposed()) {
            s.onComplete();
        }
    }
}