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

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.junit.*;
import org.mockito.InOrder;
import org.reactivestreams.Subscriber;

import hu.akarnokd.rxjava2.*;
import hu.akarnokd.rxjava2.functions.Function;

public class OperatorDistinctUntilChangedTest {

    Subscriber<String> w;
    Subscriber<String> w2;

    // nulls lead to exceptions
    final Function<String, String> TO_UPPER_WITH_EXCEPTION = new Function<String, String>() {
        @Override
        public String apply(String s) {
            if (s.equals("x")) {
                return "xx";
            }
            return s.toUpperCase();
        }
    };

    @Before
    public void before() {
        w = TestHelper.mockSubscriber();
        w2 = TestHelper.mockSubscriber();
    }

    @Test
    public void testDistinctUntilChangedOfNone() {
        Observable<String> src = Observable.empty();
        src.distinctUntilChanged().subscribe(w);

        verify(w, never()).onNext(anyString());
        verify(w, never()).onError(any(Throwable.class));
        verify(w, times(1)).onComplete();
    }

    @Test
    public void testDistinctUntilChangedOfNoneWithKeySelector() {
        Observable<String> src = Observable.empty();
        src.distinctUntilChanged(TO_UPPER_WITH_EXCEPTION).subscribe(w);

        verify(w, never()).onNext(anyString());
        verify(w, never()).onError(any(Throwable.class));
        verify(w, times(1)).onComplete();
    }

    @Test
    public void testDistinctUntilChangedOfNormalSource() {
        Observable<String> src = Observable.just("a", "b", "c", "c", "c", "b", "b", "a", "e");
        src.distinctUntilChanged().subscribe(w);

        InOrder inOrder = inOrder(w);
        inOrder.verify(w, times(1)).onNext("a");
        inOrder.verify(w, times(1)).onNext("b");
        inOrder.verify(w, times(1)).onNext("c");
        inOrder.verify(w, times(1)).onNext("b");
        inOrder.verify(w, times(1)).onNext("a");
        inOrder.verify(w, times(1)).onNext("e");
        inOrder.verify(w, times(1)).onComplete();
        inOrder.verify(w, never()).onNext(anyString());
        verify(w, never()).onError(any(Throwable.class));
    }

    @Test
    public void testDistinctUntilChangedOfNormalSourceWithKeySelector() {
        Observable<String> src = Observable.just("a", "b", "c", "C", "c", "B", "b", "a", "e");
        src.distinctUntilChanged(TO_UPPER_WITH_EXCEPTION).subscribe(w);

        InOrder inOrder = inOrder(w);
        inOrder.verify(w, times(1)).onNext("a");
        inOrder.verify(w, times(1)).onNext("b");
        inOrder.verify(w, times(1)).onNext("c");
        inOrder.verify(w, times(1)).onNext("B");
        inOrder.verify(w, times(1)).onNext("a");
        inOrder.verify(w, times(1)).onNext("e");
        inOrder.verify(w, times(1)).onComplete();
        inOrder.verify(w, never()).onNext(anyString());
        verify(w, never()).onError(any(Throwable.class));
    }

    @Test
    @Ignore("Null values no longer allowed")
    public void testDistinctUntilChangedOfSourceWithNulls() {
        Observable<String> src = Observable.just(null, "a", "a", null, null, "b", null, null);
        src.distinctUntilChanged().subscribe(w);

        InOrder inOrder = inOrder(w);
        inOrder.verify(w, times(1)).onNext(null);
        inOrder.verify(w, times(1)).onNext("a");
        inOrder.verify(w, times(1)).onNext(null);
        inOrder.verify(w, times(1)).onNext("b");
        inOrder.verify(w, times(1)).onNext(null);
        inOrder.verify(w, times(1)).onComplete();
        inOrder.verify(w, never()).onNext(anyString());
        verify(w, never()).onError(any(Throwable.class));
    }

    @Test
    @Ignore("Null values no longer allowed")
    public void testDistinctUntilChangedOfSourceWithExceptionsFromKeySelector() {
        Observable<String> src = Observable.just("a", "b", null, "c");
        src.distinctUntilChanged(TO_UPPER_WITH_EXCEPTION).subscribe(w);

        InOrder inOrder = inOrder(w);
        inOrder.verify(w, times(1)).onNext("a");
        inOrder.verify(w, times(1)).onNext("b");
        verify(w, times(1)).onError(any(NullPointerException.class));
        inOrder.verify(w, never()).onNext(anyString());
        inOrder.verify(w, never()).onComplete();
    }
}