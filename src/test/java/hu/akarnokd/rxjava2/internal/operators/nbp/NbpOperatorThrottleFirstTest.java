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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import org.junit.*;
import org.mockito.InOrder;

import hu.akarnokd.rxjava2.*;
import hu.akarnokd.rxjava2.NbpObservable.*;
import hu.akarnokd.rxjava2.exceptions.TestException;
import hu.akarnokd.rxjava2.internal.disposables.EmptyDisposable;
import hu.akarnokd.rxjava2.schedulers.TestScheduler;
import hu.akarnokd.rxjava2.subjects.nbp.NbpPublishSubject;

public class NbpOperatorThrottleFirstTest {

    private TestScheduler scheduler;
    private Scheduler.Worker innerScheduler;
    private NbpSubscriber<String> NbpObserver;

    @Before
    public void before() {
        scheduler = new TestScheduler();
        innerScheduler = scheduler.createWorker();
        NbpObserver = TestHelper.mockNbpSubscriber();
    }

    @Test
    public void testThrottlingWithCompleted() {
        NbpObservable<String> source = NbpObservable.create(new NbpOnSubscribe<String>() {
            @Override
            public void accept(NbpSubscriber<? super String> NbpObserver) {
                NbpObserver.onSubscribe(EmptyDisposable.INSTANCE);
                publishNext(NbpObserver, 100, "one");    // publish as it's first
                publishNext(NbpObserver, 300, "two");    // skip as it's last within the first 400
                publishNext(NbpObserver, 900, "three");   // publish
                publishNext(NbpObserver, 905, "four");   // skip
                publishCompleted(NbpObserver, 1000);     // Should be published as soon as the timeout expires.
            }
        });

        NbpObservable<String> sampled = source.throttleFirst(400, TimeUnit.MILLISECONDS, scheduler);
        sampled.subscribe(NbpObserver);

        InOrder inOrder = inOrder(NbpObserver);

        scheduler.advanceTimeTo(1000, TimeUnit.MILLISECONDS);
        inOrder.verify(NbpObserver, times(1)).onNext("one");
        inOrder.verify(NbpObserver, times(0)).onNext("two");
        inOrder.verify(NbpObserver, times(1)).onNext("three");
        inOrder.verify(NbpObserver, times(0)).onNext("four");
        inOrder.verify(NbpObserver, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testThrottlingWithError() {
        NbpObservable<String> source = NbpObservable.create(new NbpOnSubscribe<String>() {
            @Override
            public void accept(NbpSubscriber<? super String> NbpObserver) {
                NbpObserver.onSubscribe(EmptyDisposable.INSTANCE);
                Exception error = new TestException();
                publishNext(NbpObserver, 100, "one");    // Should be published since it is first
                publishNext(NbpObserver, 200, "two");    // Should be skipped since onError will arrive before the timeout expires
                publishError(NbpObserver, 300, error);   // Should be published as soon as the timeout expires.
            }
        });

        NbpObservable<String> sampled = source.throttleFirst(400, TimeUnit.MILLISECONDS, scheduler);
        sampled.subscribe(NbpObserver);

        InOrder inOrder = inOrder(NbpObserver);

        scheduler.advanceTimeTo(400, TimeUnit.MILLISECONDS);
        inOrder.verify(NbpObserver).onNext("one");
        inOrder.verify(NbpObserver).onError(any(TestException.class));
        inOrder.verifyNoMoreInteractions();
    }

    private <T> void publishCompleted(final NbpSubscriber<T> NbpObserver, long delay) {
        innerScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                NbpObserver.onComplete();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private <T> void publishError(final NbpSubscriber<T> NbpObserver, long delay, final Exception error) {
        innerScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                NbpObserver.onError(error);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private <T> void publishNext(final NbpSubscriber<T> NbpObserver, long delay, final T value) {
        innerScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                NbpObserver.onNext(value);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testThrottle() {
        NbpSubscriber<Integer> NbpObserver = TestHelper.mockNbpSubscriber();
        TestScheduler s = new TestScheduler();
        NbpPublishSubject<Integer> o = NbpPublishSubject.create();
        o.throttleFirst(500, TimeUnit.MILLISECONDS, s).subscribe(NbpObserver);

        // send events with simulated time increments
        s.advanceTimeTo(0, TimeUnit.MILLISECONDS);
        o.onNext(1); // deliver
        o.onNext(2); // skip
        s.advanceTimeTo(501, TimeUnit.MILLISECONDS);
        o.onNext(3); // deliver
        s.advanceTimeTo(600, TimeUnit.MILLISECONDS);
        o.onNext(4); // skip
        s.advanceTimeTo(700, TimeUnit.MILLISECONDS);
        o.onNext(5); // skip
        o.onNext(6); // skip
        s.advanceTimeTo(1001, TimeUnit.MILLISECONDS);
        o.onNext(7); // deliver
        s.advanceTimeTo(1501, TimeUnit.MILLISECONDS);
        o.onComplete();

        InOrder inOrder = inOrder(NbpObserver);
        inOrder.verify(NbpObserver).onNext(1);
        inOrder.verify(NbpObserver).onNext(3);
        inOrder.verify(NbpObserver).onNext(7);
        inOrder.verify(NbpObserver).onComplete();
        inOrder.verifyNoMoreInteractions();
    }
}