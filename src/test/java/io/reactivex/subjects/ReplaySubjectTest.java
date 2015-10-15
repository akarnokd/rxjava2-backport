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

package io.reactivex.subjects;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import io.reactivex.functions.*;

import org.junit.Test;
import org.mockito.*;
import org.reactivestreams.*;

import io.reactivex.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.TestException;
import io.reactivex.schedulers.*;
import io.reactivex.subscribers.TestSubscriber;

public class ReplaySubjectTest {

    private final Throwable testException = new Throwable();

    @Test
    public void testCompleted() {
        ReplaySubject<String> subject = ReplaySubject.create();

        Subscriber<String> o1 = TestHelper.mockSubscriber();
        subject.subscribe(o1);

        subject.onNext("one");
        subject.onNext("two");
        subject.onNext("three");
        subject.onComplete();

        subject.onNext("four");
        subject.onComplete();
        subject.onError(new Throwable());

        assertCompletedSubscriber(o1);

        // assert that subscribing a 2nd time gets the same data
        Subscriber<String> o2 = TestHelper.mockSubscriber();
        subject.subscribe(o2);
        assertCompletedSubscriber(o2);
    }

    @Test
    public void testCompletedStopsEmittingData() {
        ReplaySubject<Integer> channel = ReplaySubject.create();
        Subscriber<Object> observerA = TestHelper.mockSubscriber();
        Subscriber<Object> observerB = TestHelper.mockSubscriber();
        Subscriber<Object> observerC = TestHelper.mockSubscriber();
        Subscriber<Object> observerD = TestHelper.mockSubscriber();
        TestSubscriber<Object> ts = new TestSubscriber<T>(observerA);

        channel.subscribe(ts);
        channel.subscribe(observerB);

        InOrder inOrderA = inOrder(observerA);
        InOrder inOrderB = inOrder(observerB);
        InOrder inOrderC = inOrder(observerC);
        InOrder inOrderD = inOrder(observerD);

        channel.onNext(42);

        // both A and B should have received 42 from before subscription
        inOrderA.verify(observerA).onNext(42);
        inOrderB.verify(observerB).onNext(42);

        ts.dispose();

        // a should receive no more
        inOrderA.verifyNoMoreInteractions();

        channel.onNext(4711);

        // only be should receive 4711 at this point
        inOrderB.verify(observerB).onNext(4711);

        channel.onComplete();

        // B is subscribed so should receive onCompleted
        inOrderB.verify(observerB).onComplete();

        channel.subscribe(observerC);

        // when C subscribes it should receive 42, 4711, onCompleted
        inOrderC.verify(observerC).onNext(42);
        inOrderC.verify(observerC).onNext(4711);
        inOrderC.verify(observerC).onComplete();

        // if further events are propagated they should be ignored
        channel.onNext(13);
        channel.onNext(14);
        channel.onNext(15);
        channel.onError(new RuntimeException());

        // a new subscription should only receive what was emitted prior to terminal state onCompleted
        channel.subscribe(observerD);

        inOrderD.verify(observerD).onNext(42);
        inOrderD.verify(observerD).onNext(4711);
        inOrderD.verify(observerD).onComplete();

        verify(observerA).onSubscribe((Subscription)notNull());
        verify(observerB).onSubscribe((Subscription)notNull());
        verify(observerC).onSubscribe((Subscription)notNull());
        verify(observerD).onSubscribe((Subscription)notNull());
        Mockito.verifyNoMoreInteractions(observerA);
        Mockito.verifyNoMoreInteractions(observerB);
        Mockito.verifyNoMoreInteractions(observerC);
        Mockito.verifyNoMoreInteractions(observerD);

    }

    @Test
    public void testCompletedAfterError() {
        ReplaySubject<String> subject = ReplaySubject.create();

        Subscriber<String> observer = TestHelper.mockSubscriber();

        subject.onNext("one");
        subject.onError(testException);
        subject.onNext("two");
        subject.onComplete();
        subject.onError(new RuntimeException());

        subject.subscribe(observer);
        verify(observer).onSubscribe((Subscription)notNull());
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onError(testException);
        verifyNoMoreInteractions(observer);
    }

    private void assertCompletedSubscriber(Subscriber<String> observer) {
        InOrder inOrder = inOrder(observer);

        inOrder.verify(observer, times(1)).onNext("one");
        inOrder.verify(observer, times(1)).onNext("two");
        inOrder.verify(observer, times(1)).onNext("three");
        inOrder.verify(observer, Mockito.never()).onError(any(Throwable.class));
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testError() {
        ReplaySubject<String> subject = ReplaySubject.create();

        Subscriber<String> observer = TestHelper.mockSubscriber();
        subject.subscribe(observer);

        subject.onNext("one");
        subject.onNext("two");
        subject.onNext("three");
        subject.onError(testException);

        subject.onNext("four");
        subject.onError(new Throwable());
        subject.onComplete();

        assertErrorSubscriber(observer);

        observer = TestHelper.mockSubscriber();
        subject.subscribe(observer);
        assertErrorSubscriber(observer);
    }

    private void assertErrorSubscriber(Subscriber<String> observer) {
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, times(1)).onError(testException);
        verify(observer, Mockito.never()).onComplete();
    }

    @Test
    public void testSubscribeMidSequence() {
        ReplaySubject<String> subject = ReplaySubject.create();

        Subscriber<String> observer = TestHelper.mockSubscriber();
        subject.subscribe(observer);

        subject.onNext("one");
        subject.onNext("two");

        assertObservedUntilTwo(observer);

        Subscriber<String> anotherSubscriber = TestHelper.mockSubscriber();
        subject.subscribe(anotherSubscriber);
        assertObservedUntilTwo(anotherSubscriber);

        subject.onNext("three");
        subject.onComplete();

        assertCompletedSubscriber(observer);
        assertCompletedSubscriber(anotherSubscriber);
    }

    @Test
    public void testUnsubscribeFirstSubscriber() {
        ReplaySubject<String> subject = ReplaySubject.create();

        Subscriber<String> observer = TestHelper.mockSubscriber();
        TestSubscriber<String> ts = new TestSubscriber<T>(observer);
        subject.subscribe(ts);

        subject.onNext("one");
        subject.onNext("two");

        ts.dispose();
        assertObservedUntilTwo(observer);

        Subscriber<String> anotherSubscriber = TestHelper.mockSubscriber();
        subject.subscribe(anotherSubscriber);
        assertObservedUntilTwo(anotherSubscriber);

        subject.onNext("three");
        subject.onComplete();

        assertObservedUntilTwo(observer);
        assertCompletedSubscriber(anotherSubscriber);
    }

    private void assertObservedUntilTwo(Subscriber<String> observer) {
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, Mockito.never()).onNext("three");
        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, Mockito.never()).onComplete();
    }

    @Test(timeout = 2000)
    public void testNewSubscriberDoesntBlockExisting() throws InterruptedException {

        final AtomicReference<String> lastValueForSubscriber1 = new AtomicReference<T>();
        Subscriber<String> observer1 = new Observer<String>() {

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(String v) {
                System.out.println("observer1: " + v);
                lastValueForSubscriber1.set(v);
            }

        };

        final AtomicReference<String> lastValueForSubscriber2 = new AtomicReference<T>();
        final CountDownLatch oneReceived = new CountDownLatch(1);
        final CountDownLatch makeSlow = new CountDownLatch(1);
        final CountDownLatch completed = new CountDownLatch(1);
        Subscriber<String> observer2 = new Observer<String>() {

            @Override
            public void onComplete() {
                completed.countDown();
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(String v) {
                System.out.println("observer2: " + v);
                if (v.equals("one")) {
                    oneReceived.countDown();
                } else {
                    try {
                        makeSlow.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    lastValueForSubscriber2.set(v);
                }
            }

        };

        ReplaySubject<String> subject = ReplaySubject.create();
        subject.subscribe(observer1);
        subject.onNext("one");
        assertEquals("one", lastValueForSubscriber1.get());
        subject.onNext("two");
        assertEquals("two", lastValueForSubscriber1.get());

        // use subscribeOn to make this async otherwise we deadlock as we are using CountDownLatches
        subject.subscribeOn(Schedulers.newThread()).subscribe(observer2);

        System.out.println("before waiting for one");

        // wait until observer2 starts having replay occur
        oneReceived.await();

        System.out.println("after waiting for one");

        subject.onNext("three");
        
        System.out.println("sent three");
        
        // if subscription blocked existing subscribers then 'makeSlow' would cause this to not be there yet 
        assertEquals("three", lastValueForSubscriber1.get());
        
        System.out.println("about to send onCompleted");
        
        subject.onComplete();

        System.out.println("completed subject");
        
        // release 
        makeSlow.countDown();
        
        System.out.println("makeSlow released");
        
        completed.await();
        // all of them should be emitted with the last being "three"
        assertEquals("three", lastValueForSubscriber2.get());

    }
    @Test
    public void testSubscriptionLeak() {
        ReplaySubject<Object> replaySubject = ReplaySubject.create();
        
        Disposable s = replaySubject.subscribe();

        assertEquals(1, replaySubject.subscriberCount());

        s.dispose();
        
        assertEquals(0, replaySubject.subscriberCount());
    }
    @Test(timeout = 1000)
    public void testUnsubscriptionCase() {
        ReplaySubject<String> src = ReplaySubject.create();
        
        for (int i = 0; i < 10; i++) {
            final Subscriber<Object> o = TestHelper.mockSubscriber();
            InOrder inOrder = inOrder(o);
            String v = "" + i;
            src.onNext(v);
            System.out.printf("Turn: %d%n", i);
            src.first()
                .flatMap(new Function<String, Observable<String>>() {

                    @Override
                    public Observable<String> apply(String t1) {
                        return Observable.just(t1 + ", " + t1);
                    }
                })
                .subscribe(new Observer<String>() {
                    @Override
                    public void onNext(String t) {
                        System.out.println(t);
                        o.onNext(t);
                    }

                    @Override
                    public void onError(Throwable e) {
                        o.onError(e);
                    }

                    @Override
                    public void onComplete() {
                        o.onComplete();
                    }
                });
            inOrder.verify(o).onNext("0, 0");
            inOrder.verify(o).onComplete();
            verify(o, never()).onError(any(Throwable.class));
        }
    }
    @Test
    public void testTerminateOnce() {
        ReplaySubject<Integer> source = ReplaySubject.create();
        source.onNext(1);
        source.onNext(2);
        source.onComplete();
        
        final Subscriber<Integer> o = TestHelper.mockSubscriber();
        
        source.unsafeSubscribe(new Observer<Integer>() {

            @Override
            public void onNext(Integer t) {
                o.onNext(t);
            }

            @Override
            public void onError(Throwable e) {
                o.onError(e);
            }

            @Override
            public void onComplete() {
                o.onComplete();
            }
        });
        
        verify(o).onNext(1);
        verify(o).onNext(2);
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }
    
    @Test
    public void testReplay1AfterTermination() {
        ReplaySubject<Integer> source = ReplaySubject.createWithSize(1);
        
        source.onNext(1);
        source.onNext(2);
        source.onComplete();
        
        for (int i = 0; i < 1; i++) {
            Subscriber<Integer> o = TestHelper.mockSubscriber();

            source.subscribe(o);

            verify(o, never()).onNext(1);
            verify(o).onNext(2);
            verify(o).onComplete();
            verify(o, never()).onError(any(Throwable.class));
        }
    }
    @Test
    public void testReplay1Directly() {
        ReplaySubject<Integer> source = ReplaySubject.createWithSize(1);

        Subscriber<Integer> o = TestHelper.mockSubscriber();

        source.onNext(1);
        source.onNext(2);

        source.subscribe(o);

        source.onNext(3);
        source.onComplete();

        verify(o, never()).onNext(1);
        verify(o).onNext(2);
        verify(o).onNext(3);
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }
    
    @Test
    public void testReplayTimestampedAfterTermination() {
        TestScheduler scheduler = new TestScheduler();
        ReplaySubject<Integer> source = ReplaySubject.createWithTime(1, TimeUnit.SECONDS, scheduler);
        
        source.onNext(1);
        
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        
        source.onNext(2);

        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);

        source.onNext(3);
        source.onComplete();

        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);

        Subscriber<Integer> o = TestHelper.mockSubscriber();

        source.subscribe(o);
        
        verify(o, never()).onNext(1);
        verify(o, never()).onNext(2);
        verify(o).onNext(3);
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }
    
    @Test
    public void testReplayTimestampedDirectly() {
        TestScheduler scheduler = new TestScheduler();
        ReplaySubject<Integer> source = ReplaySubject.createWithTime(1, TimeUnit.SECONDS, scheduler);

        source.onNext(1);
        
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);

        Subscriber<Integer> o = TestHelper.mockSubscriber();

        source.subscribe(o);

        source.onNext(2);
        
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        
        source.onNext(3);
        
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        
        source.onComplete();
        
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        
        verify(o, never()).onError(any(Throwable.class));
        verify(o, never()).onNext(1);
        verify(o).onNext(2);
        verify(o).onNext(3);
        verify(o).onComplete();
    }
    
    // FIXME RS subscribers can't throw
//    @Test
//    public void testOnErrorThrowsDoesntPreventDelivery() {
//        ReplaySubject<String> ps = ReplaySubject.create();
//
//        ps.subscribe();
//        TestSubscriber<String> ts = new TestSubscriber<T>();
//        ps.subscribe(ts);
//
//        try {
//            ps.onError(new RuntimeException("an exception"));
//            fail("expect OnErrorNotImplementedException");
//        } catch (OnErrorNotImplementedException e) {
//            // ignore
//        }
//        // even though the onError above throws we should still receive it on the other subscriber 
//        assertEquals(1, ts.errors().size());
//    }
    
    // FIXME RS subscribers can't throw
//    /**
//     * This one has multiple failures so should get a CompositeException
//     */
//    @Test
//    public void testOnErrorThrowsDoesntPreventDelivery2() {
//        ReplaySubject<String> ps = ReplaySubject.create();
//
//        ps.subscribe();
//        ps.subscribe();
//        TestSubscriber<String> ts = new TestSubscriber<String>();
//        ps.subscribe(ts);
//        ps.subscribe();
//        ps.subscribe();
//        ps.subscribe();
//
//        try {
//            ps.onError(new RuntimeException("an exception"));
//            fail("expect OnErrorNotImplementedException");
//        } catch (CompositeException e) {
//            // we should have 5 of them
//            assertEquals(5, e.getExceptions().size());
//        }
//        // even though the onError above throws we should still receive it on the other subscriber 
//        assertEquals(1, ts.getOnErrorEvents().size());
//    }
    
    @Test
    public void testCurrentStateMethodsNormal() {
        ReplaySubject<Object> as = ReplaySubject.create();
        
        assertFalse(as.hasThrowable());
        assertFalse(as.hasComplete());
        assertNull(as.getThrowable());
        
        as.onNext(1);
        
        assertFalse(as.hasThrowable());
        assertFalse(as.hasComplete());
        assertNull(as.getThrowable());
        
        as.onComplete();
        
        assertFalse(as.hasThrowable());
        assertTrue(as.hasComplete());
        assertNull(as.getThrowable());
    }
    
    @Test
    public void testCurrentStateMethodsEmpty() {
        ReplaySubject<Object> as = ReplaySubject.create();
        
        assertFalse(as.hasThrowable());
        assertFalse(as.hasComplete());
        assertNull(as.getThrowable());
        
        as.onComplete();
        
        assertFalse(as.hasThrowable());
        assertTrue(as.hasComplete());
        assertNull(as.getThrowable());
    }
    @Test
    public void testCurrentStateMethodsError() {
        ReplaySubject<Object> as = ReplaySubject.create();
        
        assertFalse(as.hasThrowable());
        assertFalse(as.hasComplete());
        assertNull(as.getThrowable());
        
        as.onError(new TestException());
        
        assertTrue(as.hasThrowable());
        assertFalse(as.hasComplete());
        assertTrue(as.getThrowable() instanceof TestException);
    }
    @Test
    public void testSizeAndHasAnyValueUnbounded() {
        ReplaySubject<Object> rs = ReplaySubject.create();
        
        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());
        
        rs.onNext(1);
        
        assertEquals(1, rs.size());
        assertTrue(rs.hasValue());
        
        rs.onNext(1);

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());
        
        rs.onComplete();

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());
    }
    @Test
    public void testSizeAndHasAnyValueEffectivelyUnbounded() {
        ReplaySubject<Object> rs = ReplaySubject.createUnbounded();
        
        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());
        
        rs.onNext(1);
        
        assertEquals(1, rs.size());
        assertTrue(rs.hasValue());
        
        rs.onNext(1);

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());
        
        rs.onComplete();

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());
    }
    
    @Test
    public void testSizeAndHasAnyValueUnboundedError() {
        ReplaySubject<Object> rs = ReplaySubject.create();
        
        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());
        
        rs.onNext(1);
        
        assertEquals(1, rs.size());
        assertTrue(rs.hasValue());
        
        rs.onNext(1);

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());
        
        rs.onError(new TestException());

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());
    }
    @Test
    public void testSizeAndHasAnyValueEffectivelyUnboundedError() {
        ReplaySubject<Object> rs = ReplaySubject.createUnbounded();
        
        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());
        
        rs.onNext(1);
        
        assertEquals(1, rs.size());
        assertTrue(rs.hasValue());
        
        rs.onNext(1);

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());
        
        rs.onError(new TestException());

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());
    }
    
    @Test
    public void testSizeAndHasAnyValueUnboundedEmptyError() {
        ReplaySubject<Object> rs = ReplaySubject.create();
        
        rs.onError(new TestException());

        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());
    }
    @Test
    public void testSizeAndHasAnyValueEffectivelyUnboundedEmptyError() {
        ReplaySubject<Object> rs = ReplaySubject.createUnbounded();
        
        rs.onError(new TestException());

        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());
    }
    
    @Test
    public void testSizeAndHasAnyValueUnboundedEmptyCompleted() {
        ReplaySubject<Object> rs = ReplaySubject.create();
        
        rs.onComplete();

        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());
    }
    @Test
    public void testSizeAndHasAnyValueEffectivelyUnboundedEmptyCompleted() {
        ReplaySubject<Object> rs = ReplaySubject.createUnbounded();
        
        rs.onComplete();

        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());
    }
    
    @Test
    public void testSizeAndHasAnyValueSizeBounded() {
        ReplaySubject<Object> rs = ReplaySubject.createWithSize(1);
        
        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());
        
        for (int i = 0; i < 1000; i++) {
            rs.onNext(i);

            assertEquals(1, rs.size());
            assertTrue(rs.hasValue());
        }
        
        rs.onComplete();

        assertEquals(1, rs.size());
        assertTrue(rs.hasValue());
    }
    
    @Test
    public void testSizeAndHasAnyValueTimeBounded() {
        TestScheduler ts = new TestScheduler();
        ReplaySubject<Object> rs = ReplaySubject.createWithTime(1, TimeUnit.SECONDS, ts);
        
        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());
        
        for (int i = 0; i < 1000; i++) {
            rs.onNext(i);
            ts.advanceTimeBy(2, TimeUnit.SECONDS);
            assertEquals(1, rs.size());
            assertTrue(rs.hasValue());
        }
        
        rs.onComplete();

        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());
    }
    @Test
    public void testGetValues() {
        ReplaySubject<Object> rs = ReplaySubject.create();
        Object[] expected = new Object[10];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = i;
            rs.onNext(i);
            assertArrayEquals(Arrays.copyOf(expected, i + 1), rs.getValues());
        }
        rs.onComplete();
        
        assertArrayEquals(expected, rs.getValues());
        
    }
    @Test
    public void testGetValuesUnbounded() {
        ReplaySubject<Object> rs = ReplaySubject.createUnbounded();
        Object[] expected = new Object[10];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = i;
            rs.onNext(i);
            assertArrayEquals(Arrays.copyOf(expected, i + 1), rs.getValues());
        }
        rs.onComplete();
        
        assertArrayEquals(expected, rs.getValues());
        
    }
    
    @Test
    public void testBackpressureHonored() {
        ReplaySubject<Integer> rs = ReplaySubject.create();
        rs.onNext(1);
        rs.onNext(2);
        rs.onNext(3);
        rs.onComplete();
        
        TestSubscriber<Integer> ts = new TestSubscriber<T>((Long)null);
        
        rs.subscribe(ts);
        
        ts.request(1);
        ts.assertValue(1);
        ts.assertNotComplete();
        ts.assertNoErrors();
        
        
        ts.request(1);
        ts.assertValues(1, 2);
        ts.assertNotComplete();
        ts.assertNoErrors();

        ts.request(1);
        ts.assertValues(1, 2, 3);
        ts.assertComplete();
        ts.assertNoErrors();
    }
    
    @Test
    public void testBackpressureHonoredSizeBound() {
        ReplaySubject<Integer> rs = ReplaySubject.createWithSize(100);
        rs.onNext(1);
        rs.onNext(2);
        rs.onNext(3);
        rs.onComplete();
        
        TestSubscriber<Integer> ts = new TestSubscriber<T>((Long)null);
        
        rs.subscribe(ts);
        
        ts.request(1);
        ts.assertValue(1);
        ts.assertNotComplete();
        ts.assertNoErrors();
        
        
        ts.request(1);
        ts.assertValues(1, 2);
        ts.assertNotComplete();
        ts.assertNoErrors();

        ts.request(1);
        ts.assertValues(1, 2, 3);
        ts.assertComplete();
        ts.assertNoErrors();
    }
    
    @Test
    public void testBackpressureHonoredTimeBound() {
        ReplaySubject<Integer> rs = ReplaySubject.createWithTime(1, TimeUnit.DAYS);
        rs.onNext(1);
        rs.onNext(2);
        rs.onNext(3);
        rs.onComplete();
        
        TestSubscriber<Integer> ts = new TestSubscriber<T>((Long)null);
        
        rs.subscribe(ts);
        
        ts.request(1);
        ts.assertValue(1);
        ts.assertNotComplete();
        ts.assertNoErrors();
        
        
        ts.request(1);
        ts.assertValues(1, 2);
        ts.assertNotComplete();
        ts.assertNoErrors();

        ts.request(1);
        ts.assertValues(1, 2, 3);
        ts.assertComplete();
        ts.assertNoErrors();
    }
}