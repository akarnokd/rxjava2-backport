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

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.reactivestreams.*;

import hu.akarnokd.rxjava2.*;
import hu.akarnokd.rxjava2.functions.*;
import hu.akarnokd.rxjava2.subscribers.TestSubscriber;

public class OperatorAllTest {

    @Test
    public void testAll() {
        Observable<String> obs = Observable.just("one", "two", "six");

        Subscriber <Boolean> observer = TestHelper.mockSubscriber();
        
        obs.all(new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return s.length() == 3;
            }
        })
        .subscribe(observer);

        verify(observer).onSubscribe((Subscription)any());
        verify(observer).onNext(true);
        verify(observer).onComplete();
        verifyNoMoreInteractions(observer);
    }

    @Test
    public void testNotAll() {
        Observable<String> obs = Observable.just("one", "two", "three", "six");

        Subscriber <Boolean> observer = TestHelper.mockSubscriber();

        obs.all(new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return s.length() == 3;
            }
        })
        .subscribe(observer);

        verify(observer).onSubscribe((Subscription)any());
        verify(observer).onNext(false);
        verify(observer).onComplete();
        verifyNoMoreInteractions(observer);
    }

    @Test
    public void testEmpty() {
        Observable<String> obs = Observable.empty();

        Subscriber <Boolean> observer = TestHelper.mockSubscriber();

        obs.all(new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return s.length() == 3;
            }
        })
        .subscribe(observer);

        verify(observer).onSubscribe((Subscription)any());
        verify(observer).onNext(true);
        verify(observer).onComplete();
        verifyNoMoreInteractions(observer);
    }

    @Test
    public void testError() {
        Throwable error = new Throwable();
        Observable<String> obs = Observable.error(error);

        Subscriber <Boolean> observer = TestHelper.mockSubscriber();

        obs.all(new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return s.length() == 3;
            }
        })
        .subscribe(observer);

        verify(observer).onSubscribe((Subscription)any());
        verify(observer).onError(error);
        verifyNoMoreInteractions(observer);
    }

    @Test
    public void testFollowingFirst() {
        Observable<Integer> o = Observable.fromArray(1, 3, 5, 6);
        Observable<Boolean> allOdd = o.all(new Predicate<Integer>() {
            @Override
            public boolean test(Integer i) {
                return i % 2 == 1;
            }
        });
        
        assertFalse(allOdd.toBlocking().first());
    }
    @Test(timeout = 5000)
    public void testIssue1935NoUnsubscribeDownstream() {
        Observable<Integer> source = Observable.just(1)
            .all(new Predicate<Integer>() {
                @Override
                public boolean test(Integer t1) {
                    return false;
                }
            })
            .flatMap(new Function<Boolean, Publisher<Integer>>() {
                @Override
                public Publisher<Integer> apply(Boolean t1) {
                    return Observable.just(2).delay(500, TimeUnit.MILLISECONDS);
                }
            });
        
        assertEquals((Object)2, source.toBlocking().first());
    }
    
    @Test
    public void testBackpressureIfNoneRequestedNoneShouldBeDelivered() {
        TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>((Long)null);
        Observable.empty().all(new Predicate<Object>() {
            @Override
            public boolean test(Object t1) {
                return false;
            }
        }).subscribe(ts);
        
        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertNotComplete();
    }
    
    @Test
    public void testBackpressureIfOneRequestedOneShouldBeDelivered() {
        TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>(1L);
        
        Observable.empty().all(new Predicate<Object>() {
            @Override
            public boolean test(Object t) {
                return false;
            }
        }).subscribe(ts);
        
        ts.assertTerminated();
        ts.assertNoErrors();
        ts.assertComplete();
        
        ts.assertValue(true);
    }
    
    @Test
    public void testPredicateThrowsExceptionAndValueInCauseMessage() {
        TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>();
        
        final IllegalArgumentException ex = new IllegalArgumentException();
        
        Observable.just("Boo!").all(new Predicate<String>() {
            @Override
            public boolean test(String v) {
                throw ex;
            }
        })
        .subscribe(ts);
        
        ts.assertTerminated();
        ts.assertNoValues();
        ts.assertNotComplete();
        ts.assertError(ex);
        // FIXME need to decide about adding the value that probably caused the crash in some way
//        assertTrue(ex.getCause().getMessage().contains("Boo!"));
    }
}