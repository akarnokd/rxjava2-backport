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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import org.junit.*;
import org.mockito.InOrder;
import org.reactivestreams.*;

import io.reactivex.*;
import io.reactivex.exceptions.TestException;
import io.reactivex.internal.subscriptions.EmptySubscription;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.PublishSubject;

public class OperatorThrottleFirstTest {

    private TestScheduler scheduler;
    private Scheduler.Worker innerScheduler;
    private Subscriber<String> observer;

    @Before
    public void before() {
        scheduler = new TestScheduler();
        innerScheduler = scheduler.createWorker();
        observer = TestHelper.mockSubscriber();
    }

    @Test
    public void testThrottlingWithCompleted() {
        Observable<String> source = Observable.create(new Publisher<String>() {
            @Override
            public void subscribe(Subscriber<? super String> observer) {
                observer.onSubscribe(EmptySubscription.INSTANCE);
                publishNext(observer, 100, "one");    // publish as it's first
                publishNext(observer, 300, "two");    // skip as it's last within the first 400
                publishNext(observer, 900, "three");   // publish
                publishNext(observer, 905, "four");   // skip
                publishCompleted(observer, 1000);     // Should be published as soon as the timeout expires.
            }
        });

        Observable<String> sampled = source.throttleFirst(400, TimeUnit.MILLISECONDS, scheduler);
        sampled.subscribe(observer);

        InOrder inOrder = inOrder(observer);

        scheduler.advanceTimeTo(1000, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext("one");
        inOrder.verify(observer, times(0)).onNext("two");
        inOrder.verify(observer, times(1)).onNext("three");
        inOrder.verify(observer, times(0)).onNext("four");
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testThrottlingWithError() {
        Observable<String> source = Observable.create(new Publisher<String>() {
            @Override
            public void subscribe(Subscriber<? super String> observer) {
                observer.onSubscribe(EmptySubscription.INSTANCE);
                Exception error = new TestException();
                publishNext(observer, 100, "one");    // Should be published since it is first
                publishNext(observer, 200, "two");    // Should be skipped since onError will arrive before the timeout expires
                publishError(observer, 300, error);   // Should be published as soon as the timeout expires.
            }
        });

        Observable<String> sampled = source.throttleFirst(400, TimeUnit.MILLISECONDS, scheduler);
        sampled.subscribe(observer);

        InOrder inOrder = inOrder(observer);

        scheduler.advanceTimeTo(400, TimeUnit.MILLISECONDS);
        inOrder.verify(observer).onNext("one");
        inOrder.verify(observer).onError(any(TestException.class));
        inOrder.verifyNoMoreInteractions();
    }

    private <T> void publishCompleted(final Subscriber<T> observer, long delay) {
        innerScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                observer.onComplete();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private <T> void publishError(final Subscriber<T> observer, long delay, final Exception error) {
        innerScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                observer.onError(error);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private <T> void publishNext(final Subscriber<T> observer, long delay, final T value) {
        innerScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                observer.onNext(value);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testThrottle() {
        Subscriber<Integer> observer = TestHelper.mockSubscriber();
        TestScheduler s = new TestScheduler();
        PublishSubject<Integer> o = PublishSubject.create();
        o.throttleFirst(500, TimeUnit.MILLISECONDS, s).subscribe(observer);

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

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer).onNext(1);
        inOrder.verify(observer).onNext(3);
        inOrder.verify(observer).onNext(7);
        inOrder.verify(observer).onComplete();
        inOrder.verifyNoMoreInteractions();
    }
}