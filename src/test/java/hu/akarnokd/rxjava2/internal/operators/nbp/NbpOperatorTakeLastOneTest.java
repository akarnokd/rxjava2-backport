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

import java.util.concurrent.atomic.*;

import org.junit.Test;

import hu.akarnokd.rxjava2.NbpObservable;
import hu.akarnokd.rxjava2.functions.Consumer;
import hu.akarnokd.rxjava2.subscribers.nbp.NbpTestSubscriber;

public class NbpOperatorTakeLastOneTest {

    @Test
    public void testLastOfManyReturnsLast() {
        NbpTestSubscriber<Integer> s = new NbpTestSubscriber<Integer>();
        NbpObservable.range(1, 10).takeLast(1).subscribe(s);
        s.assertValue(10);
        s.assertNoErrors();
        s.assertTerminated();
        // NO longer assertable
//        s.assertUnsubscribed();
    }

    @Test
    public void testLastOfEmptyReturnsEmpty() {
        NbpTestSubscriber<Object> s = new NbpTestSubscriber<Object>();
        NbpObservable.empty().takeLast(1).subscribe(s);
        s.assertNoValues();
        s.assertNoErrors();
        s.assertTerminated();
        // NO longer assertable
//      s.assertUnsubscribed();
    }

    @Test
    public void testLastOfOneReturnsLast() {
        NbpTestSubscriber<Integer> s = new NbpTestSubscriber<Integer>();
        NbpObservable.just(1).takeLast(1).subscribe(s);
        s.assertValue(1);
        s.assertNoErrors();
        s.assertTerminated();
        // NO longer assertable
//      s.assertUnsubscribed();
    }

    @Test
    public void testUnsubscribesFromUpstream() {
        final AtomicBoolean unsubscribed = new AtomicBoolean(false);
        Runnable unsubscribeAction = new Runnable() {
            @Override
            public void run() {
                unsubscribed.set(true);
            }
        };
        NbpObservable.just(1).doOnCancel(unsubscribeAction)
                .takeLast(1).subscribe();
        assertTrue(unsubscribed.get());
    }

    @Test
    public void testTakeLastZeroProcessesAllItemsButIgnoresThem() {
        final AtomicInteger upstreamCount = new AtomicInteger();
        final int num = 10;
        long count = NbpObservable.range(1,num).doOnNext(new Consumer<Integer>() {

            @Override
            public void accept(Integer t) {
                upstreamCount.incrementAndGet();
            }})
            .takeLast(0).count().toBlocking().single();
        assertEquals(num, upstreamCount.get());
        assertEquals(0L, count);
    }
}