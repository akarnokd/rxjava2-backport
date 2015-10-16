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
import hu.akarnokd.rxjava2.NbpObservable.NbpSubscriber;
import hu.akarnokd.rxjava2.schedulers.*;
import hu.akarnokd.rxjava2.subjects.nbp.NbpPublishSubject;

public class NbpOperatorTimestampTest {
    NbpSubscriber<Object> NbpObserver;

    @Before
    public void before() {
        NbpObserver = TestHelper.mockNbpSubscriber();
    }

    @Test
    public void timestampWithScheduler() {
        TestScheduler scheduler = new TestScheduler();

        NbpPublishSubject<Integer> source = NbpPublishSubject.create();
        NbpObservable<Timed<Integer>> m = source.timestamp(scheduler);
        m.subscribe(NbpObserver);

        source.onNext(1);
        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        source.onNext(2);
        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        source.onNext(3);

        InOrder inOrder = inOrder(NbpObserver);

        inOrder.verify(NbpObserver, times(1)).onNext(new Timed<Integer>(1, 0, TimeUnit.MILLISECONDS));
        inOrder.verify(NbpObserver, times(1)).onNext(new Timed<Integer>(2, 100, TimeUnit.MILLISECONDS));
        inOrder.verify(NbpObserver, times(1)).onNext(new Timed<Integer>(3, 200, TimeUnit.MILLISECONDS));

        verify(NbpObserver, never()).onError(any(Throwable.class));
        verify(NbpObserver, never()).onComplete();
    }

    @Test
    public void timestampWithScheduler2() {
        TestScheduler scheduler = new TestScheduler();

        NbpPublishSubject<Integer> source = NbpPublishSubject.create();
        NbpObservable<Timed<Integer>> m = source.timestamp(scheduler);
        m.subscribe(NbpObserver);

        source.onNext(1);
        source.onNext(2);
        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        source.onNext(3);

        InOrder inOrder = inOrder(NbpObserver);

        inOrder.verify(NbpObserver, times(1)).onNext(new Timed<Integer>(1, 0, TimeUnit.MILLISECONDS));
        inOrder.verify(NbpObserver, times(1)).onNext(new Timed<Integer>(2, 0, TimeUnit.MILLISECONDS));
        inOrder.verify(NbpObserver, times(1)).onNext(new Timed<Integer>(3, 200, TimeUnit.MILLISECONDS));

        verify(NbpObserver, never()).onError(any(Throwable.class));
        verify(NbpObserver, never()).onComplete();
    }
}