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

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.junit.*;

import io.reactivex.Observable;
import io.reactivex.exceptions.TestException;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subscribers.TestSubscriber;

public class OperatorOnBackpressureLatestTest {
    @Test
    public void testSimple() {
        TestSubscriber<Integer> ts = new TestSubscriber<T>();
        
        Observable.range(1, 5).onBackpressureLatest().subscribe(ts);
        
        ts.assertNoErrors();
        ts.assertTerminated();
        ts.assertValues(1, 2, 3, 4, 5);
    }
    @Test
    public void testSimpleError() {
        TestSubscriber<Integer> ts = new TestSubscriber<T>();
        
        Observable.range(1, 5).concatWith(Observable.<Integer>error(new TestException()))
        .onBackpressureLatest().subscribe(ts);
        
        ts.assertTerminated();
        ts.assertError(TestException.class);
        ts.assertValues(1, 2, 3, 4, 5);
    }
    @Test
    public void testSimpleBackpressure() {
        TestSubscriber<Integer> ts = new TestSubscriber<T>(2L);
        
        Observable.range(1, 5).onBackpressureLatest().subscribe(ts);
        
        ts.assertNoErrors();
        ts.assertValues(1, 2);
        ts.assertNotComplete();
    }
    
    @Test
    public void testSynchronousDrop() {
        PublishSubject<Integer> source = PublishSubject.create();
        TestSubscriber<Integer> ts = new TestSubscriber<T>((Long)null);
        
        source.onBackpressureLatest().subscribe(ts);

        ts.assertNoValues();

        source.onNext(1);
        ts.request(2);
        
        ts.assertValue(1);
        
        source.onNext(2);

        ts.assertValues(1, 2);

        source.onNext(3);
        source.onNext(4);
        source.onNext(5);
        source.onNext(6);

        ts.request(2);

        ts.assertValues(1, 2, 6);
        
        source.onNext(7);

        ts.assertValues(1, 2, 6, 7);
        
        source.onNext(8);
        source.onNext(9);
        source.onComplete();
        
        ts.request(1);
        
        ts.assertValues(1, 2, 6, 7, 9);
        ts.assertNoErrors();
        ts.assertTerminated();
    }
    @Test
    public void testAsynchronousDrop() throws InterruptedException {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>(1L) {
            final Random rnd = new Random();
            @Override
            public void onNext(Integer t) {
                super.onNext(t);
                if (rnd.nextDouble() < 0.001) {
                    try {
                        Thread.sleep(1);
                    } catch(InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
                request(1);
            }
        };
        int m = 100000;
        Observable.range(1, m)
        .subscribeOn(Schedulers.computation())
        .onBackpressureLatest()
        .observeOn(Schedulers.io())
        .subscribe(ts);
        
        ts.awaitTerminalEvent(2, TimeUnit.SECONDS);
        ts.assertTerminated();
        int n = ts.values().size();
        System.out.println("testAsynchronousDrop -> " + n);
        Assert.assertTrue("All events received?", n < m);
    }
}