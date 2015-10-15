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

package io.reactivex.internal.operators;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import io.reactivex.functions.*;

import org.junit.Test;
import org.mockito.InOrder;
import org.reactivestreams.Subscriber;

import io.reactivex.*;

public class OperatorSkipWhileTest {

    Subscriber<Integer> w = TestHelper.mockSubscriber();

    private static final Predicate<Integer> LESS_THAN_FIVE = new Predicate<Integer>() {
        @Override
        public boolean test(Integer v) {
            if (v == 42)
                throw new RuntimeException("that's not the answer to everything!");
            return v < 5;
        }
    };

    private static final Predicate<Integer> INDEX_LESS_THAN_THREE = new Predicate<Integer>() {
        int index = 0;
        @Override
        public boolean test(Integer value) {
            return index++ < 3;
        }
    };

    @Test
    public void testSkipWithIndex() {
        Observable<Integer> src = Observable.just(1, 2, 3, 4, 5);
        src.skipWhile(INDEX_LESS_THAN_THREE).subscribe(w);

        InOrder inOrder = inOrder(w);
        inOrder.verify(w, times(1)).onNext(4);
        inOrder.verify(w, times(1)).onNext(5);
        inOrder.verify(w, times(1)).onComplete();
        inOrder.verify(w, never()).onError(any(Throwable.class));
    }

    @Test
    public void testSkipEmpty() {
        Observable<Integer> src = Observable.empty();
        src.skipWhile(LESS_THAN_FIVE).subscribe(w);
        verify(w, never()).onNext(anyInt());
        verify(w, never()).onError(any(Throwable.class));
        verify(w, times(1)).onComplete();
    }

    @Test
    public void testSkipEverything() {
        Observable<Integer> src = Observable.just(1, 2, 3, 4, 3, 2, 1);
        src.skipWhile(LESS_THAN_FIVE).subscribe(w);
        verify(w, never()).onNext(anyInt());
        verify(w, never()).onError(any(Throwable.class));
        verify(w, times(1)).onComplete();
    }

    @Test
    public void testSkipNothing() {
        Observable<Integer> src = Observable.just(5, 3, 1);
        src.skipWhile(LESS_THAN_FIVE).subscribe(w);

        InOrder inOrder = inOrder(w);
        inOrder.verify(w, times(1)).onNext(5);
        inOrder.verify(w, times(1)).onNext(3);
        inOrder.verify(w, times(1)).onNext(1);
        inOrder.verify(w, times(1)).onComplete();
        inOrder.verify(w, never()).onError(any(Throwable.class));
    }

    @Test
    public void testSkipSome() {
        Observable<Integer> src = Observable.just(1, 2, 3, 4, 5, 3, 1, 5);
        src.skipWhile(LESS_THAN_FIVE).subscribe(w);

        InOrder inOrder = inOrder(w);
        inOrder.verify(w, times(1)).onNext(5);
        inOrder.verify(w, times(1)).onNext(3);
        inOrder.verify(w, times(1)).onNext(1);
        inOrder.verify(w, times(1)).onNext(5);
        inOrder.verify(w, times(1)).onComplete();
        inOrder.verify(w, never()).onError(any(Throwable.class));
    }

    @Test
    public void testSkipError() {
        Observable<Integer> src = Observable.just(1, 2, 42, 5, 3, 1);
        src.skipWhile(LESS_THAN_FIVE).subscribe(w);

        InOrder inOrder = inOrder(w);
        inOrder.verify(w, never()).onNext(anyInt());
        inOrder.verify(w, never()).onComplete();
        inOrder.verify(w, times(1)).onError(any(RuntimeException.class));
    }
    
    @Test
    public void testSkipManySubscribers() {
        Observable<Integer> src = Observable.range(1, 10).skipWhile(LESS_THAN_FIVE);
        int n = 5;
        for (int i = 0; i < n; i++) {
            Subscriber<Object> o = TestHelper.mockSubscriber();
            InOrder inOrder = inOrder(o);
            
            src.subscribe(o);
            
            for (int j = 5; j < 10; j++) {
                inOrder.verify(o).onNext(j);
            } 
            inOrder.verify(o).onComplete();
            verify(o, never()).onError(any(Throwable.class));
        }
    }
}