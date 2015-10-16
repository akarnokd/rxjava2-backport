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

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import org.junit.*;

import hu.akarnokd.rxjava2.*;
import hu.akarnokd.rxjava2.NbpObservable.NbpSubscriber;
import hu.akarnokd.rxjava2.exceptions.TestException;
import hu.akarnokd.rxjava2.functions.*;

public class NbpOperatorReduceTest {
    NbpSubscriber<Object> NbpObserver;

    @Before
    public void before() {
        NbpObserver = TestHelper.mockNbpSubscriber();
    }

    BiFunction<Integer, Integer, Integer> sum = new BiFunction<Integer, Integer, Integer>() {
        @Override
        public Integer apply(Integer t1, Integer t2) {
            return t1 + t2;
        }
    };

    @Test
    public void testAggregateAsIntSum() {

        NbpObservable<Integer> result = NbpObservable.just(1, 2, 3, 4, 5).reduce(0, sum)
                .map(new Function<Integer, Integer>() {
                    @Override
                    public Integer apply(Integer v) {
                        return v;
                    }
                });

        result.subscribe(NbpObserver);

        verify(NbpObserver).onNext(1 + 2 + 3 + 4 + 5);
        verify(NbpObserver).onComplete();
        verify(NbpObserver, never()).onError(any(Throwable.class));
    }

    @Test
    public void testAggregateAsIntSumSourceThrows() {
        NbpObservable<Integer> result = NbpObservable.concat(NbpObservable.just(1, 2, 3, 4, 5),
                NbpObservable.<Integer> error(new TestException()))
                .reduce(0, sum).map(new Function<Integer, Integer>() {
                    @Override
                    public Integer apply(Integer v) {
                        return v;
                    }
                });

        result.subscribe(NbpObserver);

        verify(NbpObserver, never()).onNext(any());
        verify(NbpObserver, never()).onComplete();
        verify(NbpObserver, times(1)).onError(any(TestException.class));
    }

    @Test
    public void testAggregateAsIntSumAccumulatorThrows() {
        BiFunction<Integer, Integer, Integer> sumErr = new BiFunction<Integer, Integer, Integer>() {
            @Override
            public Integer apply(Integer t1, Integer t2) {
                throw new TestException();
            }
        };

        NbpObservable<Integer> result = NbpObservable.just(1, 2, 3, 4, 5)
                .reduce(0, sumErr).map(new Function<Integer, Integer>() {
                    @Override
                    public Integer apply(Integer v) {
                        return v;
                    }
                });

        result.subscribe(NbpObserver);

        verify(NbpObserver, never()).onNext(any());
        verify(NbpObserver, never()).onComplete();
        verify(NbpObserver, times(1)).onError(any(TestException.class));
    }

    @Test
    public void testAggregateAsIntSumResultSelectorThrows() {

        Function<Integer, Integer> error = new Function<Integer, Integer>() {

            @Override
            public Integer apply(Integer t1) {
                throw new TestException();
            }
        };

        NbpObservable<Integer> result = NbpObservable.just(1, 2, 3, 4, 5)
                .reduce(0, sum).map(error);

        result.subscribe(NbpObserver);

        verify(NbpObserver, never()).onNext(any());
        verify(NbpObserver, never()).onComplete();
        verify(NbpObserver, times(1)).onError(any(TestException.class));
    }

    @Test
    public void testBackpressureWithNoInitialValue() throws InterruptedException {
        NbpObservable<Integer> source = NbpObservable.just(1, 2, 3, 4, 5, 6);
        NbpObservable<Integer> reduced = source.reduce(sum);

        Integer r = reduced.toBlocking().first();
        assertEquals(21, r.intValue());
    }

    @Test
    public void testBackpressureWithInitialValue() throws InterruptedException {
        NbpObservable<Integer> source = NbpObservable.just(1, 2, 3, 4, 5, 6);
        NbpObservable<Integer> reduced = source.reduce(0, sum);

        Integer r = reduced.toBlocking().first();
        assertEquals(21, r.intValue());
    }



}