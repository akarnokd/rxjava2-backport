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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.*;
import org.mockito.InOrder;

import hu.akarnokd.rxjava2.*;
import hu.akarnokd.rxjava2.NbpObservable.*;
import hu.akarnokd.rxjava2.disposables.*;
import hu.akarnokd.rxjava2.functions.Consumer;
import hu.akarnokd.rxjava2.schedulers.*;
import hu.akarnokd.rxjava2.subjects.nbp.NbpPublishSubject;
import hu.akarnokd.rxjava2.subscribers.nbp.NbpTestSubscriber;

public class NbpOnSubscribeAmbTest {

    private TestScheduler scheduler;
    private Scheduler.Worker innerScheduler;

    @Before
    public void setUp() {
        scheduler = new TestScheduler();
        innerScheduler = scheduler.createWorker();
    }

    private NbpObservable<String> createObservable(final String[] values,
            final long interval, final Throwable e) {
        return NbpObservable.create(new NbpOnSubscribe<String>() {

            @Override
            public void accept(final NbpSubscriber<? super String> NbpSubscriber) {
                CompositeDisposable parentSubscription = new CompositeDisposable();
                
                NbpSubscriber.onSubscribe(parentSubscription);
                
                long delay = interval;
                for (final String value : values) {
                    parentSubscription.add(innerScheduler.schedule(new Runnable() {
                        @Override
                        public void run() {
                            NbpSubscriber.onNext(value);
                        }
                    }
                    , delay, TimeUnit.MILLISECONDS));
                    delay += interval;
                }
                parentSubscription.add(innerScheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                            if (e == null) {
                                NbpSubscriber.onComplete();
                            } else {
                                NbpSubscriber.onError(e);
                            }
                    }
                }, delay, TimeUnit.MILLISECONDS));
            }
        });
    }

    @Test
    public void testAmb() {
        NbpObservable<String> observable1 = createObservable(new String[] {
                "1", "11", "111", "1111" }, 2000, null);
        NbpObservable<String> observable2 = createObservable(new String[] {
                "2", "22", "222", "2222" }, 1000, null);
        NbpObservable<String> observable3 = createObservable(new String[] {
                "3", "33", "333", "3333" }, 3000, null);

        @SuppressWarnings("unchecked")
        NbpObservable<String> o = NbpObservable.amb(observable1,
                observable2, observable3);

        NbpSubscriber<String> NbpObserver = TestHelper.mockNbpSubscriber();
        o.subscribe(NbpObserver);

        scheduler.advanceTimeBy(100000, TimeUnit.MILLISECONDS);

        InOrder inOrder = inOrder(NbpObserver);
        inOrder.verify(NbpObserver, times(1)).onNext("2");
        inOrder.verify(NbpObserver, times(1)).onNext("22");
        inOrder.verify(NbpObserver, times(1)).onNext("222");
        inOrder.verify(NbpObserver, times(1)).onNext("2222");
        inOrder.verify(NbpObserver, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testAmb2() {
        IOException expectedException = new IOException(
                "fake exception");
        NbpObservable<String> observable1 = createObservable(new String[] {},
                2000, new IOException("fake exception"));
        NbpObservable<String> observable2 = createObservable(new String[] {
                "2", "22", "222", "2222" }, 1000, expectedException);
        NbpObservable<String> observable3 = createObservable(new String[] {},
                3000, new IOException("fake exception"));

        @SuppressWarnings("unchecked")
        NbpObservable<String> o = NbpObservable.amb(observable1,
                observable2, observable3);

        NbpSubscriber<String> NbpObserver = TestHelper.mockNbpSubscriber();
        o.subscribe(NbpObserver);

        scheduler.advanceTimeBy(100000, TimeUnit.MILLISECONDS);

        InOrder inOrder = inOrder(NbpObserver);
        inOrder.verify(NbpObserver, times(1)).onNext("2");
        inOrder.verify(NbpObserver, times(1)).onNext("22");
        inOrder.verify(NbpObserver, times(1)).onNext("222");
        inOrder.verify(NbpObserver, times(1)).onNext("2222");
        inOrder.verify(NbpObserver, times(1)).onError(expectedException);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testAmb3() {
        NbpObservable<String> observable1 = createObservable(new String[] {
                "1" }, 2000, null);
        NbpObservable<String> observable2 = createObservable(new String[] {},
                1000, null);
        NbpObservable<String> observable3 = createObservable(new String[] {
                "3" }, 3000, null);

        @SuppressWarnings("unchecked")
        NbpObservable<String> o = NbpObservable.amb(observable1,
                observable2, observable3);

        NbpSubscriber<String> NbpObserver = TestHelper.mockNbpSubscriber();
        o.subscribe(NbpObserver);

        scheduler.advanceTimeBy(100000, TimeUnit.MILLISECONDS);
        InOrder inOrder = inOrder(NbpObserver);
        inOrder.verify(NbpObserver, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSubscriptionOnlyHappensOnce() throws InterruptedException {
        final AtomicLong count = new AtomicLong();
        Consumer<Disposable> incrementer = new Consumer<Disposable>() {
            @Override
            public void accept(Disposable s) {
                count.incrementAndGet();
            }
        };
        
        //this aync stream should emit first
        NbpObservable<Integer> o1 = NbpObservable.just(1).doOnSubscribe(incrementer)
                .delay(100, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.computation());
        //this stream emits second
        NbpObservable<Integer> o2 = NbpObservable.just(1).doOnSubscribe(incrementer)
                .delay(100, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.computation());
        NbpTestSubscriber<Integer> ts = new NbpTestSubscriber<Integer>();
        NbpObservable.amb(o1, o2).subscribe(ts);
        ts.awaitTerminalEvent(5, TimeUnit.SECONDS);
        ts.assertNoErrors();
        assertEquals(2, count.get());
    }
    
    @Test
    public void testSynchronousSources() {
        // under async subscription the second NbpObservable would complete before
        // the first but because this is a synchronous subscription to sources
        // then second NbpObservable does not get subscribed to before first
        // subscription completes hence first NbpObservable emits result through
        // amb
        int result = NbpObservable.just(1).doOnNext(new Consumer<Integer>() {
            @Override
            public void accept(Integer t) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        //
                    }
            }
        }).ambWith(NbpObservable.just(2)).toBlocking().single();
        assertEquals(1, result);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAmbCancelsOthers() {
        NbpPublishSubject<Integer> source1 = NbpPublishSubject.create();
        NbpPublishSubject<Integer> source2 = NbpPublishSubject.create();
        NbpPublishSubject<Integer> source3 = NbpPublishSubject.create();
        
        NbpTestSubscriber<Integer> ts = new NbpTestSubscriber<Integer>();
        
        NbpObservable.amb(source1, source2, source3).subscribe(ts);
        
        assertTrue("Source 1 doesn't have subscribers!", source1.hasSubscribers());
        assertTrue("Source 2 doesn't have subscribers!", source2.hasSubscribers());
        assertTrue("Source 3 doesn't have subscribers!", source3.hasSubscribers());
        
        source1.onNext(1);

        assertTrue("Source 1 doesn't have subscribers!", source1.hasSubscribers());
        assertFalse("Source 2 still has subscribers!", source2.hasSubscribers());
        assertFalse("Source 2 still has subscribers!", source3.hasSubscribers());
        
    }
}