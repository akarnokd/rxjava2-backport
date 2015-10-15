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

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;
import io.reactivex.functions.*;

import org.junit.*;
import org.mockito.InOrder;

import io.reactivex.*;
import io.reactivex.NbpObservable.*;
import io.reactivex.exceptions.TestException;
import io.reactivex.internal.disposables.EmptyDisposable;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.nbp.NbpPublishSubject;
import io.reactivex.subscribers.nbp.NbpTestSubscriber;

public class NbpOperatorDebounceTest {

    private TestScheduler scheduler;
    private NbpSubscriber<String> NbpObserver;
    private Scheduler.Worker innerScheduler;

    @Before
    public void before() {
        scheduler = new TestScheduler();
        NbpObserver = TestHelper.mockNbpSubscriber();
        innerScheduler = scheduler.createWorker();
    }

    @Test
    public void testDebounceWithCompleted() {
        NbpObservable<String> source = NbpObservable.create(new NbpOnSubscribe<String>() {
            @Override
            public void accept(NbpSubscriber<? super String> NbpObserver) {
                NbpObserver.onSubscribe(EmptyDisposable.INSTANCE);
                publishNext(NbpObserver, 100, "one");    // Should be skipped since "two" will arrive before the timeout expires.
                publishNext(NbpObserver, 400, "two");    // Should be published since "three" will arrive after the timeout expires.
                publishNext(NbpObserver, 900, "three");   // Should be skipped since onCompleted will arrive before the timeout expires.
                publishCompleted(NbpObserver, 1000);     // Should be published as soon as the timeout expires.
            }
        });

        NbpObservable<String> sampled = source.debounce(400, TimeUnit.MILLISECONDS, scheduler);
        sampled.subscribe(NbpObserver);

        scheduler.advanceTimeTo(0, TimeUnit.MILLISECONDS);
        InOrder inOrder = inOrder(NbpObserver);
        // must go to 800 since it must be 400 after when two is sent, which is at 400
        scheduler.advanceTimeTo(800, TimeUnit.MILLISECONDS);
        inOrder.verify(NbpObserver, times(1)).onNext("two");
        scheduler.advanceTimeTo(1000, TimeUnit.MILLISECONDS);
        inOrder.verify(NbpObserver, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testDebounceNeverEmits() {
        NbpObservable<String> source = NbpObservable.create(new NbpOnSubscribe<String>() {
            @Override
            public void accept(NbpSubscriber<? super String> NbpObserver) {
                NbpObserver.onSubscribe(EmptyDisposable.INSTANCE);
                // all should be skipped since they are happening faster than the 200ms timeout
                publishNext(NbpObserver, 100, "a");    // Should be skipped
                publishNext(NbpObserver, 200, "b");    // Should be skipped
                publishNext(NbpObserver, 300, "c");    // Should be skipped
                publishNext(NbpObserver, 400, "d");    // Should be skipped
                publishNext(NbpObserver, 500, "e");    // Should be skipped
                publishNext(NbpObserver, 600, "f");    // Should be skipped
                publishNext(NbpObserver, 700, "g");    // Should be skipped
                publishNext(NbpObserver, 800, "h");    // Should be skipped
                publishCompleted(NbpObserver, 900);     // Should be published as soon as the timeout expires.
            }
        });

        NbpObservable<String> sampled = source.debounce(200, TimeUnit.MILLISECONDS, scheduler);
        sampled.subscribe(NbpObserver);

        scheduler.advanceTimeTo(0, TimeUnit.MILLISECONDS);
        InOrder inOrder = inOrder(NbpObserver);
        inOrder.verify(NbpObserver, times(0)).onNext(anyString());
        scheduler.advanceTimeTo(1000, TimeUnit.MILLISECONDS);
        inOrder.verify(NbpObserver, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testDebounceWithError() {
        NbpObservable<String> source = NbpObservable.create(new NbpOnSubscribe<String>() {
            @Override
            public void accept(NbpSubscriber<? super String> NbpObserver) {
                NbpObserver.onSubscribe(EmptyDisposable.INSTANCE);
                Exception error = new TestException();
                publishNext(NbpObserver, 100, "one");    // Should be published since "two" will arrive after the timeout expires.
                publishNext(NbpObserver, 600, "two");    // Should be skipped since onError will arrive before the timeout expires.
                publishError(NbpObserver, 700, error);   // Should be published as soon as the timeout expires.
            }
        });

        NbpObservable<String> sampled = source.debounce(400, TimeUnit.MILLISECONDS, scheduler);
        sampled.subscribe(NbpObserver);

        scheduler.advanceTimeTo(0, TimeUnit.MILLISECONDS);
        InOrder inOrder = inOrder(NbpObserver);
        // 100 + 400 means it triggers at 500
        scheduler.advanceTimeTo(500, TimeUnit.MILLISECONDS);
        inOrder.verify(NbpObserver).onNext("one");
        scheduler.advanceTimeTo(701, TimeUnit.MILLISECONDS);
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

    private <T> void publishNext(final NbpSubscriber<T> NbpObserver, final long delay, final T value) {
        innerScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                NbpObserver.onNext(value);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    @Test
    public void debounceSelectorNormal1() {
        NbpPublishSubject<Integer> source = NbpPublishSubject.create();
        final NbpPublishSubject<Integer> debouncer = NbpPublishSubject.create();
        Function<Integer, NbpObservable<Integer>> debounceSel = new Function<Integer, NbpObservable<Integer>>() {

            @Override
            public NbpObservable<Integer> apply(Integer t1) {
                return debouncer;
            }
        };

        NbpSubscriber<Object> o = TestHelper.mockNbpSubscriber();
        InOrder inOrder = inOrder(o);

        source.debounce(debounceSel).subscribe(o);

        source.onNext(1);
        debouncer.onNext(1);

        source.onNext(2);
        source.onNext(3);
        source.onNext(4);

        debouncer.onNext(2);

        source.onNext(5);
        source.onComplete();

        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onNext(4);
        inOrder.verify(o).onNext(5);
        inOrder.verify(o).onComplete();

        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void debounceSelectorFuncThrows() {
        NbpPublishSubject<Integer> source = NbpPublishSubject.create();
        Function<Integer, NbpObservable<Integer>> debounceSel = new Function<Integer, NbpObservable<Integer>>() {

            @Override
            public NbpObservable<Integer> apply(Integer t1) {
                throw new TestException();
            }
        };

        NbpSubscriber<Object> o = TestHelper.mockNbpSubscriber();
        
        source.debounce(debounceSel).subscribe(o);

        source.onNext(1);

        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();
        verify(o).onError(any(TestException.class));
    }

    @Test
    public void debounceSelectorObservableThrows() {
        NbpPublishSubject<Integer> source = NbpPublishSubject.create();
        Function<Integer, NbpObservable<Integer>> debounceSel = new Function<Integer, NbpObservable<Integer>>() {

            @Override
            public NbpObservable<Integer> apply(Integer t1) {
                return NbpObservable.error(new TestException());
            }
        };

        NbpSubscriber<Object> o = TestHelper.mockNbpSubscriber();
        
        source.debounce(debounceSel).subscribe(o);

        source.onNext(1);

        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();
        verify(o).onError(any(TestException.class));
    }
    @Test
    public void debounceTimedLastIsNotLost() {
        NbpPublishSubject<Integer> source = NbpPublishSubject.create();
        
        NbpSubscriber<Object> o = TestHelper.mockNbpSubscriber();
        
        source.debounce(100, TimeUnit.MILLISECONDS, scheduler).subscribe(o);
        
        source.onNext(1);
        source.onComplete();
        
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        
        verify(o).onNext(1);
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }
    @Test
    public void debounceSelectorLastIsNotLost() {
        NbpPublishSubject<Integer> source = NbpPublishSubject.create();
        final NbpPublishSubject<Integer> debouncer = NbpPublishSubject.create();

        Function<Integer, NbpObservable<Integer>> debounceSel = new Function<Integer, NbpObservable<Integer>>() {

            @Override
            public NbpObservable<Integer> apply(Integer t1) {
                return debouncer;
            }
        };

        NbpSubscriber<Object> o = TestHelper.mockNbpSubscriber();
        
        source.debounce(debounceSel).subscribe(o);
        
        source.onNext(1);
        source.onComplete();

        debouncer.onComplete();

        verify(o).onNext(1);
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void debounceWithTimeBackpressure() throws InterruptedException {
        TestScheduler scheduler = new TestScheduler();
        NbpTestSubscriber<Integer> NbpSubscriber = new NbpTestSubscriber<T>();
        NbpObservable.merge(
                NbpObservable.just(1),
                NbpObservable.just(2).delay(10, TimeUnit.MILLISECONDS, scheduler)
        ).debounce(20, TimeUnit.MILLISECONDS, scheduler).take(1).subscribe(NbpSubscriber);

        scheduler.advanceTimeBy(30, TimeUnit.MILLISECONDS);

        NbpSubscriber.assertValue(2);
        NbpSubscriber.assertTerminated();
        NbpSubscriber.assertNoErrors();
    }
}