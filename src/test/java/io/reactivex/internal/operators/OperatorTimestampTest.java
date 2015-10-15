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
import org.reactivestreams.Subscriber;

import io.reactivex.*;
import io.reactivex.schedulers.*;
import io.reactivex.subjects.PublishSubject;

public class OperatorTimestampTest {
    Subscriber<Object> observer;

    @Before
    public void before() {
        observer = TestHelper.mockSubscriber();
    }

    @Test
    public void timestampWithScheduler() {
        TestScheduler scheduler = new TestScheduler();

        PublishSubject<Integer> source = PublishSubject.create();
        Observable<Timed<Integer>> m = source.timestamp(scheduler);
        m.subscribe(observer);

        source.onNext(1);
        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        source.onNext(2);
        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        source.onNext(3);

        InOrder inOrder = inOrder(observer);

        inOrder.verify(observer, times(1)).onNext(new Timed<>(1, 0, TimeUnit.MILLISECONDS));
        inOrder.verify(observer, times(1)).onNext(new Timed<>(2, 100, TimeUnit.MILLISECONDS));
        inOrder.verify(observer, times(1)).onNext(new Timed<>(3, 200, TimeUnit.MILLISECONDS));

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
    }

    @Test
    public void timestampWithScheduler2() {
        TestScheduler scheduler = new TestScheduler();

        PublishSubject<Integer> source = PublishSubject.create();
        Observable<Timed<Integer>> m = source.timestamp(scheduler);
        m.subscribe(observer);

        source.onNext(1);
        source.onNext(2);
        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        source.onNext(3);

        InOrder inOrder = inOrder(observer);

        inOrder.verify(observer, times(1)).onNext(new Timed<>(1, 0, TimeUnit.MILLISECONDS));
        inOrder.verify(observer, times(1)).onNext(new Timed<>(2, 0, TimeUnit.MILLISECONDS));
        inOrder.verify(observer, times(1)).onNext(new Timed<>(3, 200, TimeUnit.MILLISECONDS));

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
    }
}