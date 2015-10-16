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

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.ExecutionException;
import io.reactivex.functions.*;

import org.junit.Test;
import org.reactivestreams.*;

import io.reactivex.*;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.internal.subscriptions.EmptySubscription;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.TestSubscriber;

public class OperatorMaterializeTest {

    @Test
    public void testMaterialize1() {
        // null will cause onError to be triggered before "three" can be
        // returned
        final TestAsyncErrorObservable o1 = new TestAsyncErrorObservable("one", "two", null,
                "three");

        TestObserver observer = new TestObserver();
        Observable<Try<Optional<String>>> m = Observable.create(o1).materialize();
        m.subscribe(observer);

        try {
            o1.t.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        assertFalse(observer.onError);
        assertTrue(observer.onCompleted);
        assertEquals(3, observer.notifications.size());
        assertEquals("one", observer.notifications.get(0).value().get());
        assertTrue(Notification.isNext(observer.notifications.get(0)));
        assertEquals("two", Notification.getValue(observer.notifications.get(1)));
        assertTrue(Notification.isNext(observer.notifications.get(1)));
        assertEquals(NullPointerException.class, observer.notifications.get(2).error().getClass());
        assertTrue(Notification.isError(observer.notifications.get(2)));
    }

    @Test
    public void testMaterialize2() {
        final TestAsyncErrorObservable o1 = new TestAsyncErrorObservable("one", "two", "three");

        TestObserver Observer = new TestObserver();
        Observable<Try<Optional<String>>> m = Observable.create(o1).materialize();
        m.subscribe(Observer);

        try {
            o1.t.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        assertFalse(Observer.onError);
        assertTrue(Observer.onCompleted);
        assertEquals(4, Observer.notifications.size());
        assertEquals("one", Notification.getValue(Observer.notifications.get(0)));
        assertTrue(Notification.isNext(Observer.notifications.get(0)));
        assertEquals("two", Notification.getValue(Observer.notifications.get(1)));
        assertTrue(Notification.isNext(Observer.notifications.get(1)));
        assertEquals("three", Notification.getValue(Observer.notifications.get(2)));
        assertTrue(Notification.isNext(Observer.notifications.get(2)));
        assertTrue(Notification.isComplete(Observer.notifications.get(3)));
    }

    @Test
    public void testMultipleSubscribes() throws InterruptedException, ExecutionException {
        final TestAsyncErrorObservable o = new TestAsyncErrorObservable("one", "two", null, "three");

        Observable<Try<Optional<String>>> m = Observable.create(o).materialize();

        assertEquals(3, m.toList().toBlocking().toFuture().get().size());
        assertEquals(3, m.toList().toBlocking().toFuture().get().size());
    }

    @Test
    public void testBackpressureOnEmptyStream() {
        TestSubscriber<Try<Optional<Integer>>> ts = new TestSubscriber<Try<Optional<Integer>>>((Long)null);
        Observable.<Integer> empty().materialize().subscribe(ts);
        ts.assertNoValues();
        ts.request(1);
        ts.assertValueCount(1);
        assertTrue(Notification.isComplete(ts.values().get(0)));
        ts.assertComplete();
    }

    @Test
    public void testBackpressureNoError() {
        TestSubscriber<Try<Optional<Integer>>> ts = new TestSubscriber<Try<Optional<Integer>>>((Long)null);
        Observable.just(1, 2, 3).materialize().subscribe(ts);
        ts.assertNoValues();
        ts.request(1);
        ts.assertValueCount(1);
        ts.request(2);
        ts.assertValueCount(3);
        ts.request(1);
        ts.assertValueCount(4);
        ts.assertComplete();
    }
    
    @Test
    public void testBackpressureNoErrorAsync() throws InterruptedException {
        TestSubscriber<Try<Optional<Integer>>> ts = new TestSubscriber<Try<Optional<Integer>>>((Long)null);
        Observable.just(1, 2, 3)
            .materialize()
            .subscribeOn(Schedulers.computation())
            .subscribe(ts);
        Thread.sleep(100);
        ts.assertNoValues();
        ts.request(1);
        Thread.sleep(100);
        ts.assertValueCount(1);
        ts.request(2);
        Thread.sleep(100);
        ts.assertValueCount(3);
        ts.request(1);
        Thread.sleep(100);
        ts.assertValueCount(4);
        ts.assertComplete();
    }

    @Test
    public void testBackpressureWithError() {
        TestSubscriber<Try<Optional<Integer>>> ts = new TestSubscriber<Try<Optional<Integer>>>((Long)null);
        Observable.<Integer> error(new IllegalArgumentException()).materialize().subscribe(ts);
        ts.assertNoValues();
        ts.request(1);
        ts.assertValueCount(1);
        ts.assertComplete();
    }

    @Test
    public void testBackpressureWithEmissionThenError() {
        TestSubscriber<Try<Optional<Integer>>> ts = new TestSubscriber<Try<Optional<Integer>>>((Long)null);
        IllegalArgumentException ex = new IllegalArgumentException();
        Observable.fromIterable(Arrays.asList(1)).concatWith(Observable.<Integer> error(ex)).materialize()
                .subscribe(ts);
        ts.assertNoValues();
        ts.request(1);
        ts.assertValueCount(1);
        assertTrue(Notification.isNext(ts.values().get(0)));
        ts.request(1);
        ts.assertValueCount(2);
        assertTrue(Notification.isError(ts.values().get(1)));
        assertTrue(ex == ts.values().get(1).error());
        ts.assertComplete();
    }

    @Test
    public void testWithCompletionCausingError() {
        TestSubscriber<Try<Optional<Integer>>> ts = new TestSubscriber<Try<Optional<Integer>>>();
        final RuntimeException ex = new RuntimeException("boo");
        Observable.<Integer>empty().materialize().doOnNext(new Consumer<Object>() {
            @Override
            public void accept(Object t) {
                throw ex;
            }
        }).subscribe(ts);
        ts.assertError(ex);
        ts.assertNoValues();
        ts.assertTerminated();
    }
    
    @Test
    public void testUnsubscribeJustBeforeCompletionNotificationShouldPreventThatNotificationArriving() {
        TestSubscriber<Try<Optional<Integer>>> ts = new TestSubscriber<Try<Optional<Integer>>>((Long)null);

        Observable.<Integer>empty().materialize()
                .subscribe(ts);
        ts.assertNoValues();
        ts.dispose();
        ts.request(1);
        ts.assertNoValues();
        // FIXME no longer assertable
//        ts.assertUnsubscribed();
    }

    private static class TestObserver extends Observer<Try<Optional<String>>> {

        boolean onCompleted = false;
        boolean onError = false;
        List<Try<Optional<String>>> notifications = new Vector<Try<Optional<String>>>();

        @Override
        public void onComplete() {
            this.onCompleted = true;
        }

        @Override
        public void onError(Throwable e) {
            this.onError = true;
        }

        @Override
        public void onNext(Try<Optional<String>> value) {
            this.notifications.add(value);
        }

    }

    private static class TestAsyncErrorObservable implements Publisher<String> {

        String[] valuesToReturn;

        TestAsyncErrorObservable(String... values) {
            valuesToReturn = values;
        }

        volatile Thread t;

        @Override
        public void subscribe(final Subscriber<? super String> observer) {
            observer.onSubscribe(EmptySubscription.INSTANCE);
            t = new Thread(new Runnable() {

                @Override
                public void run() {
                    for (String s : valuesToReturn) {
                        if (s == null) {
                            System.out.println("throwing exception");
                            try {
                                Thread.sleep(100);
                            } catch (Throwable e) {

                            }
                            observer.onError(new NullPointerException());
                            return;
                        } else {
                            observer.onNext(s);
                        }
                    }
                    System.out.println("subscription complete");
                    observer.onComplete();
                }

            });
            t.start();
        }
    }
}