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

package hu.akarnokd.rxjava2.internal.operators;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.reactivestreams.*;

import hu.akarnokd.rxjava2.*;
import hu.akarnokd.rxjava2.subjects.PublishSubject;
import hu.akarnokd.rxjava2.subscribers.TestSubscriber;

public class OperatorTakeUntilTest {

    @Test
    public void testTakeUntil() {
        Subscription sSource = mock(Subscription.class);
        Subscription sOther = mock(Subscription.class);
        TestObservable source = new TestObservable(sSource);
        TestObservable other = new TestObservable(sOther);

        Subscriber<String> result = TestHelper.mockSubscriber();
        Observable<String> stringObservable = Observable.create(source)
                .takeUntil(Observable.create(other));
        stringObservable.subscribe(result);
        source.sendOnNext("one");
        source.sendOnNext("two");
        other.sendOnNext("three");
        source.sendOnNext("four");
        source.sendOnCompleted();
        other.sendOnCompleted();

        verify(result, times(1)).onNext("one");
        verify(result, times(1)).onNext("two");
        verify(result, times(0)).onNext("three");
        verify(result, times(0)).onNext("four");
        verify(sSource, times(1)).cancel();
        verify(sOther, times(1)).cancel();

    }

    @Test
    public void testTakeUntilSourceCompleted() {
        Subscription sSource = mock(Subscription.class);
        Subscription sOther = mock(Subscription.class);
        TestObservable source = new TestObservable(sSource);
        TestObservable other = new TestObservable(sOther);

        Subscriber<String> result = TestHelper.mockSubscriber();
        Observable<String> stringObservable = Observable.create(source).takeUntil(Observable.create(other));
        stringObservable.subscribe(result);
        source.sendOnNext("one");
        source.sendOnNext("two");
        source.sendOnCompleted();

        verify(result, times(1)).onNext("one");
        verify(result, times(1)).onNext("two");
        verify(sSource, times(1)).cancel();
        verify(sOther, times(1)).cancel();

    }

    @Test
    public void testTakeUntilSourceError() {
        Subscription sSource = mock(Subscription.class);
        Subscription sOther = mock(Subscription.class);
        TestObservable source = new TestObservable(sSource);
        TestObservable other = new TestObservable(sOther);
        Throwable error = new Throwable();

        Subscriber<String> result = TestHelper.mockSubscriber();
        Observable<String> stringObservable = Observable.create(source).takeUntil(Observable.create(other));
        stringObservable.subscribe(result);
        source.sendOnNext("one");
        source.sendOnNext("two");
        source.sendOnError(error);
        source.sendOnNext("three");

        verify(result, times(1)).onNext("one");
        verify(result, times(1)).onNext("two");
        verify(result, times(0)).onNext("three");
        verify(result, times(1)).onError(error);
        verify(sSource, times(1)).cancel();
        verify(sOther, times(1)).cancel();

    }

    @Test
    public void testTakeUntilOtherError() {
        Subscription sSource = mock(Subscription.class);
        Subscription sOther = mock(Subscription.class);
        TestObservable source = new TestObservable(sSource);
        TestObservable other = new TestObservable(sOther);
        Throwable error = new Throwable();

        Subscriber<String> result = TestHelper.mockSubscriber();
        Observable<String> stringObservable = Observable.create(source).takeUntil(Observable.create(other));
        stringObservable.subscribe(result);
        source.sendOnNext("one");
        source.sendOnNext("two");
        other.sendOnError(error);
        source.sendOnNext("three");

        verify(result, times(1)).onNext("one");
        verify(result, times(1)).onNext("two");
        verify(result, times(0)).onNext("three");
        verify(result, times(1)).onError(error);
        verify(result, times(0)).onComplete();
        verify(sSource, times(1)).cancel();
        verify(sOther, times(1)).cancel();

    }

    /**
     * If the 'other' onCompletes then we unsubscribe from the source and onComplete
     */
    @Test
    public void testTakeUntilOtherCompleted() {
        Subscription sSource = mock(Subscription.class);
        Subscription sOther = mock(Subscription.class);
        TestObservable source = new TestObservable(sSource);
        TestObservable other = new TestObservable(sOther);

        Subscriber<String> result = TestHelper.mockSubscriber();
        Observable<String> stringObservable = Observable.create(source).takeUntil(Observable.create(other));
        stringObservable.subscribe(result);
        source.sendOnNext("one");
        source.sendOnNext("two");
        other.sendOnCompleted();
        source.sendOnNext("three");

        verify(result, times(1)).onNext("one");
        verify(result, times(1)).onNext("two");
        verify(result, times(0)).onNext("three");
        verify(result, times(1)).onComplete();
        verify(sSource, times(1)).cancel();
        verify(sOther, times(1)).cancel(); // unsubscribed since SafeSubscriber unsubscribes after onComplete

    }

    private static class TestObservable implements Publisher<String> {

        Subscriber<? super String> observer;
        Subscription s;

        public TestObservable(Subscription s) {
            this.s = s;
        }

        /* used to simulate subscription */
        public void sendOnCompleted() {
            observer.onComplete();
        }

        /* used to simulate subscription */
        public void sendOnNext(String value) {
            observer.onNext(value);
        }

        /* used to simulate subscription */
        public void sendOnError(Throwable e) {
            observer.onError(e);
        }

        @Override
        public void subscribe(Subscriber<? super String> observer) {
            this.observer = observer;
            observer.onSubscribe(s);
        }
    }
    
    @Test
    public void testUntilFires() {
        PublishSubject<Integer> source = PublishSubject.create();
        PublishSubject<Integer> until = PublishSubject.create();
        
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        
        source.takeUntil(until).unsafeSubscribe(ts);

        assertTrue(source.hasSubscribers());
        assertTrue(until.hasSubscribers());

        source.onNext(1);
        
        ts.assertValue(1);
        until.onNext(1);
        
        ts.assertValue(1);
        ts.assertNoErrors();
        ts.assertTerminated();
        
        assertFalse("Source still has observers", source.hasSubscribers());
        assertFalse("Until still has observers", until.hasSubscribers());
        assertFalse("TestSubscriber is unsubscribed", ts.isCancelled());
    }
    @Test
    public void testMainCompletes() {
        PublishSubject<Integer> source = PublishSubject.create();
        PublishSubject<Integer> until = PublishSubject.create();
        
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        
        source.takeUntil(until).unsafeSubscribe(ts);

        assertTrue(source.hasSubscribers());
        assertTrue(until.hasSubscribers());

        source.onNext(1);
        source.onComplete();
        
        ts.assertValue(1);
        ts.assertNoErrors();
        ts.assertTerminated();
        
        assertFalse("Source still has observers", source.hasSubscribers());
        assertFalse("Until still has observers", until.hasSubscribers());
        assertFalse("TestSubscriber is unsubscribed", ts.isCancelled());
    }
    @Test
    public void testDownstreamUnsubscribes() {
        PublishSubject<Integer> source = PublishSubject.create();
        PublishSubject<Integer> until = PublishSubject.create();
        
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        
        source.takeUntil(until).take(1).unsafeSubscribe(ts);

        assertTrue(source.hasSubscribers());
        assertTrue(until.hasSubscribers());

        source.onNext(1);
        
        ts.assertValue(1);
        ts.assertNoErrors();
        ts.assertTerminated();
        
        assertFalse("Source still has observers", source.hasSubscribers());
        assertFalse("Until still has observers", until.hasSubscribers());
        assertFalse("TestSubscriber is unsubscribed", ts.isCancelled());
    }
    public void testBackpressure() {
        PublishSubject<Integer> until = PublishSubject.create();
        
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>((Long)null);
        
        Observable.range(1, 10).takeUntil(until).unsafeSubscribe(ts);

        assertTrue(until.hasSubscribers());

        ts.request(1);
        
        ts.assertValue(1);
        ts.assertNoErrors();
        ts.assertNotComplete();
        
        assertFalse("Until still has observers", until.hasSubscribers());
        assertFalse("TestSubscriber is unsubscribed", ts.isCancelled());
    }
}