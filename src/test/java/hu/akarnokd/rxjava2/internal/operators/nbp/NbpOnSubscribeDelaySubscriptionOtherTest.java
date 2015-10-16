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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.*;

import hu.akarnokd.rxjava2.NbpObservable;
import hu.akarnokd.rxjava2.disposables.Disposable;
import hu.akarnokd.rxjava2.exceptions.TestException;
import hu.akarnokd.rxjava2.functions.Consumer;
import hu.akarnokd.rxjava2.subjects.nbp.NbpPublishSubject;
import hu.akarnokd.rxjava2.subscribers.nbp.NbpTestSubscriber;

public class NbpOnSubscribeDelaySubscriptionOtherTest {
    @Test
    public void testNoPrematureSubscription() {
        NbpPublishSubject<Object> other = NbpPublishSubject.create();
        
        NbpTestSubscriber<Integer> ts = new NbpTestSubscriber<Integer>();
        
        final AtomicInteger subscribed = new AtomicInteger();
        
        NbpObservable.just(1)
        .doOnSubscribe(new Consumer<Disposable>() {
            @Override
            public void accept(Disposable d) {
                subscribed.getAndIncrement();
            }
        })
        .delaySubscription(other)
        .subscribe(ts);
        
        ts.assertNotComplete();
        ts.assertNoErrors();
        ts.assertNoValues();
        
        Assert.assertEquals("Premature subscription", 0, subscribed.get());
        
        other.onNext(1);
        
        Assert.assertEquals("No subscription", 1, subscribed.get());
        
        ts.assertValue(1);
        ts.assertNoErrors();
        ts.assertComplete();
    }
    
    @Test
    public void testNoMultipleSubscriptions() {
        NbpPublishSubject<Object> other = NbpPublishSubject.create();
        
        NbpTestSubscriber<Integer> ts = new NbpTestSubscriber<Integer>();
        
        final AtomicInteger subscribed = new AtomicInteger();
        
        NbpObservable.just(1)
        .doOnSubscribe(new Consumer<Disposable>() {
            @Override
            public void accept(Disposable d) {
                subscribed.getAndIncrement();
            }
        })
        .delaySubscription(other)
        .subscribe(ts);
        
        ts.assertNotComplete();
        ts.assertNoErrors();
        ts.assertNoValues();
        
        Assert.assertEquals("Premature subscription", 0, subscribed.get());
        
        other.onNext(1);
        other.onNext(2);
        
        Assert.assertEquals("No subscription", 1, subscribed.get());
        
        ts.assertValue(1);
        ts.assertNoErrors();
        ts.assertComplete();
    }
    
    @Test
    public void testCompleteTriggersSubscription() {
        NbpPublishSubject<Object> other = NbpPublishSubject.create();
        
        NbpTestSubscriber<Integer> ts = new NbpTestSubscriber<Integer>();
        
        final AtomicInteger subscribed = new AtomicInteger();
        
        NbpObservable.just(1)
        .doOnSubscribe(new Consumer<Disposable>() {
            @Override
            public void accept(Disposable d) {
                subscribed.getAndIncrement();
            }
        })
        .delaySubscription(other)
        .subscribe(ts);
        
        ts.assertNotComplete();
        ts.assertNoErrors();
        ts.assertNoValues();
        
        Assert.assertEquals("Premature subscription", 0, subscribed.get());
        
        other.onComplete();
        
        Assert.assertEquals("No subscription", 1, subscribed.get());
        
        ts.assertValue(1);
        ts.assertNoErrors();
        ts.assertComplete();
    }
    
    @Test
    public void testNoPrematureSubscriptionToError() {
        NbpPublishSubject<Object> other = NbpPublishSubject.create();
        
        NbpTestSubscriber<Integer> ts = new NbpTestSubscriber<Integer>();
        
        final AtomicInteger subscribed = new AtomicInteger();
        
        NbpObservable.<Integer>error(new TestException())
        .doOnSubscribe(new Consumer<Disposable>() {
            @Override
            public void accept(Disposable d) {
                subscribed.getAndIncrement();
            }
        })
        .delaySubscription(other)
        .subscribe(ts);
        
        ts.assertNotComplete();
        ts.assertNoErrors();
        ts.assertNoValues();
        
        Assert.assertEquals("Premature subscription", 0, subscribed.get());
        
        other.onComplete();
        
        Assert.assertEquals("No subscription", 1, subscribed.get());
        
        ts.assertNoValues();
        ts.assertNotComplete();
        ts.assertError(TestException.class);
    }
    
    @Test
    public void testNoSubscriptionIfOtherErrors() {
        NbpPublishSubject<Object> other = NbpPublishSubject.create();
        
        NbpTestSubscriber<Integer> ts = new NbpTestSubscriber<Integer>();
        
        final AtomicInteger subscribed = new AtomicInteger();
        
        NbpObservable.<Integer>error(new TestException())
        .doOnSubscribe(new Consumer<Disposable>() {
            @Override
            public void accept(Disposable d) {
                subscribed.getAndIncrement();
            }
        })
        .delaySubscription(other)
        .subscribe(ts);
        
        ts.assertNotComplete();
        ts.assertNoErrors();
        ts.assertNoValues();
        
        Assert.assertEquals("Premature subscription", 0, subscribed.get());
        
        other.onError(new TestException());
        
        Assert.assertEquals("Premature subscription", 0, subscribed.get());
        
        ts.assertNoValues();
        ts.assertNotComplete();
        ts.assertError(TestException.class);
    }
}