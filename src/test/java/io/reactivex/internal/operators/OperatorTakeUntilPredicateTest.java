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

package io.reactivex.internal.operators;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import io.reactivex.functions.*;

import org.junit.Test;
import org.reactivestreams.Subscriber;

import io.reactivex.*;
import io.reactivex.exceptions.TestException;
import io.reactivex.subscribers.TestSubscriber;
;

public class OperatorTakeUntilPredicateTest {
    @Test
    public void takeEmpty() {
        Subscriber<Object> o = TestHelper.mockSubscriber();
        
        Observable.empty().takeUntil(new Predicate<Object>() {
            @Override
            public boolean test(Object v) {
                return true;
            }
        }).subscribe(o);
        
        verify(o, never()).onNext(any());
        verify(o, never()).onError(any(Throwable.class));
        verify(o).onComplete();
    }
    @Test
    public void takeAll() {
        Subscriber<Object> o = TestHelper.mockSubscriber();
        
        Observable.just(1, 2).takeUntil(new Predicate<Integer>() {
            @Override
            public boolean test(Integer v) {
                return false;
            }
        }).subscribe(o);
        
        verify(o).onNext(1);
        verify(o).onNext(2);
        verify(o, never()).onError(any(Throwable.class));
        verify(o).onComplete();
    }
    @Test
    public void takeFirst() {
        Subscriber<Object> o = TestHelper.mockSubscriber();
        
        Observable.just(1, 2).takeUntil(new Predicate<Integer>() {
            @Override
            public boolean test(Integer v) {
                return true;
            }
        }).subscribe(o);
        
        verify(o).onNext(1);
        verify(o, never()).onNext(2);
        verify(o, never()).onError(any(Throwable.class));
        verify(o).onComplete();
    }
    @Test
    public void takeSome() {
        Subscriber<Object> o = TestHelper.mockSubscriber();
        
        Observable.just(1, 2, 3).takeUntil(new Predicate<Integer>() {
            @Override
            public boolean test(Integer t1) {
                return t1 == 2;
            }
        })
        .subscribe(o);
        
        verify(o).onNext(1);
        verify(o).onNext(2);
        verify(o, never()).onNext(3);
        verify(o, never()).onError(any(Throwable.class));
        verify(o).onComplete();
    }
    @Test
    public void functionThrows() {
        Subscriber<Object> o = TestHelper.mockSubscriber();
        
        Predicate<Integer> predicate = new Predicate<Integer>() {
            @Override
            public boolean test(Integer t1) {
                    throw new TestException("Forced failure");
            }
        };
        Observable.just(1, 2, 3).takeUntil(predicate).subscribe(o);
        
        verify(o).onNext(1);
        verify(o, never()).onNext(2);
        verify(o, never()).onNext(3);
        verify(o).onError(any(TestException.class));
        verify(o, never()).onComplete();
    }
    @Test
    public void sourceThrows() {
        Subscriber<Object> o = TestHelper.mockSubscriber();
        
        Observable.just(1)
        .concatWith(Observable.<Integer>error(new TestException()))
        .concatWith(Observable.just(2))
        .takeUntil(new Predicate<Integer>() {
            @Override
            public boolean test(Integer v) {
                return false;
            }
        }).subscribe(o);
        
        verify(o).onNext(1);
        verify(o, never()).onNext(2);
        verify(o).onError(any(TestException.class));
        verify(o, never()).onComplete();
    }
    @Test
    public void backpressure() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>(5L);
        
        Observable.range(1, 1000).takeUntil(new Predicate<Integer>() {
            @Override
            public boolean test(Integer v) {
                return false;
            }
        }).subscribe(ts);
        
        ts.assertNoErrors();
        ts.assertValues(1, 2, 3, 4, 5);
        ts.assertNotComplete();
    }
    
    @Test
    public void testErrorIncludesLastValueAsCause() {
        TestSubscriber<String> ts = new TestSubscriber<String>();
        final TestException e = new TestException("Forced failure");
        Predicate<String> predicate = new Predicate<String>() {
            @Override
            public boolean test(String t) {
                    throw e;
            }
        };
        Observable.just("abc").takeUntil(predicate).subscribe(ts);
        
        ts.assertTerminated();
        ts.assertNotComplete();
        ts.assertError(TestException.class);
        // FIXME last cause value is not saved
//        assertTrue(ts.errors().get(0).getCause().getMessage().contains("abc"));
    }
}