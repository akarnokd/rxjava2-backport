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

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.ExecutionException;
import io.reactivex.functions.*;

import org.junit.Test;

import io.reactivex.*;
import io.reactivex.NbpObservable.*;
import io.reactivex.internal.disposables.EmptyDisposable;
import io.reactivex.subscribers.nbp.NbpTestSubscriber;

public class NbpOperatorMaterializeTest {

    @Test
    public void testMaterialize1() {
        // null will cause onError to be triggered before "three" can be
        // returned
        final TestAsyncErrorObservable o1 = new TestAsyncErrorObservable("one", "two", null,
                "three");

        TestObserver NbpObserver = new TestObserver();
        NbpObservable<Try<Optional<String>>> m = NbpObservable.create(o1).materialize();
        m.subscribe(NbpObserver);

        try {
            o1.t.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        assertFalse(NbpObserver.onError);
        assertTrue(NbpObserver.onCompleted);
        assertEquals(3, NbpObserver.notifications.size());
        assertEquals("one", NbpObserver.notifications.get(0).value().get());
        assertTrue(Notification.isNext(NbpObserver.notifications.get(0)));
        assertEquals("two", Notification.getValue(NbpObserver.notifications.get(1)));
        assertTrue(Notification.isNext(NbpObserver.notifications.get(1)));
        assertEquals(NullPointerException.class, NbpObserver.notifications.get(2).error().getClass());
        assertTrue(Notification.isError(NbpObserver.notifications.get(2)));
    }

    @Test
    public void testMaterialize2() {
        final TestAsyncErrorObservable o1 = new TestAsyncErrorObservable("one", "two", "three");

        TestObserver NbpObserver = new TestObserver();
        NbpObservable<Try<Optional<String>>> m = NbpObservable.create(o1).materialize();
        m.subscribe(NbpObserver);

        try {
            o1.t.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        assertFalse(NbpObserver.onError);
        assertTrue(NbpObserver.onCompleted);
        assertEquals(4, NbpObserver.notifications.size());
        assertEquals("one", Notification.getValue(NbpObserver.notifications.get(0)));
        assertTrue(Notification.isNext(NbpObserver.notifications.get(0)));
        assertEquals("two", Notification.getValue(NbpObserver.notifications.get(1)));
        assertTrue(Notification.isNext(NbpObserver.notifications.get(1)));
        assertEquals("three", Notification.getValue(NbpObserver.notifications.get(2)));
        assertTrue(Notification.isNext(NbpObserver.notifications.get(2)));
        assertTrue(Notification.isComplete(NbpObserver.notifications.get(3)));
    }

    @Test
    public void testMultipleSubscribes() throws InterruptedException, ExecutionException {
        final TestAsyncErrorObservable o = new TestAsyncErrorObservable("one", "two", null, "three");

        NbpObservable<Try<Optional<String>>> m = NbpObservable.create(o).materialize();

        assertEquals(3, m.toList().toBlocking().toFuture().get().size());
        assertEquals(3, m.toList().toBlocking().toFuture().get().size());
    }

    @Test
    public void testWithCompletionCausingError() {
        NbpTestSubscriber<Try<Optional<Integer>>> ts = new NbpTestSubscriber<>();
        final RuntimeException ex = new RuntimeException("boo");
        NbpObservable.<Integer>empty().materialize().doOnNext(new Consumer<Object>() {
            @Override
            public void accept(Object t) {
                throw ex;
            }
        }).subscribe(ts);
        ts.assertError(ex);
        ts.assertNoValues();
        ts.assertTerminated();
    }
    
    private static class TestObserver extends NbpObserver<Try<Optional<String>>> {

        boolean onCompleted = false;
        boolean onError = false;
        List<Try<Optional<String>>> notifications = new Vector<>();

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

    private static class TestAsyncErrorObservable implements NbpOnSubscribe<String> {

        String[] valuesToReturn;

        TestAsyncErrorObservable(String... values) {
            valuesToReturn = values;
        }

        volatile Thread t;

        @Override
        public void accept(final NbpSubscriber<? super String> NbpObserver) {
            NbpObserver.onSubscribe(EmptyDisposable.INSTANCE);
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
                            NbpObserver.onError(new NullPointerException());
                            return;
                        } else {
                            NbpObserver.onNext(s);
                        }
                    }
                    System.out.println("subscription complete");
                    NbpObserver.onComplete();
                }

            });
            t.start();
        }
    }
}