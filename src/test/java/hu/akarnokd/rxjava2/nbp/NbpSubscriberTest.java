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

package hu.akarnokd.rxjava2.nbp;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import hu.akarnokd.rxjava2.*;
import hu.akarnokd.rxjava2.NbpObservable.*;

public class NbpSubscriberTest {
    @Test
    public void testOnStartCalledOnceViaSubscribe() {
        final AtomicInteger c = new AtomicInteger();
        NbpObservable.just(1, 2, 3, 4).take(2).subscribe(new NbpObserver<Integer>() {

            @Override
            public void onStart() {
                c.incrementAndGet();
            }

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Integer t) {
            }

        });

        assertEquals(1, c.get());
    }

    @Test
    public void testOnStartCalledOnceViaUnsafeSubscribe() {
        final AtomicInteger c = new AtomicInteger();
        NbpObservable.just(1, 2, 3, 4).take(2).unsafeSubscribe(new NbpObserver<Integer>() {

            @Override
            public void onStart() {
                c.incrementAndGet();
            }

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Integer t) {
            }

        });

        assertEquals(1, c.get());
    }

    @Test
    public void testOnStartCalledOnceViaLift() {
        final AtomicInteger c = new AtomicInteger();
        NbpObservable.just(1, 2, 3, 4).lift(new NbpOperator<Integer, Integer>() {

            @Override
            public NbpSubscriber<? super Integer> apply(final NbpSubscriber<? super Integer> child) {
                return new NbpObserver<Integer>() {

                    @Override
                    public void onStart() {
                        c.incrementAndGet();
                    }

                    @Override
                    public void onComplete() {
                        child.onComplete();
                    }

                    @Override
                    public void onError(Throwable e) {
                        child.onError(e);
                    }

                    @Override
                    public void onNext(Integer t) {
                        child.onNext(t);
                    }

                };
            }

        }).subscribe();

        assertEquals(1, c.get());
    }
}