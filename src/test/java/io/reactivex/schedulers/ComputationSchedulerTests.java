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

package io.reactivex.schedulers;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import io.reactivex.functions.*;

import org.junit.*;

import io.reactivex.*;
import io.reactivex.Scheduler.Worker;

public class ComputationSchedulerTests extends AbstractSchedulerConcurrencyTests {

    @Override
    protected Scheduler getScheduler() {
        // this is an implementation of ExecutorScheduler
        return Schedulers.computation();
    }

    @Test
    public void testThreadSafetyWhenSchedulerIsHoppingBetweenThreads() {

        final int NUM = 1000000;
        final CountDownLatch latch = new CountDownLatch(1);
        final HashMap<String, Integer> map = new HashMap<String, Integer>();

        final Scheduler.Worker inner = Schedulers.computation().createWorker();
        
        try {
            inner.schedule(new Runnable() {
    
                private HashMap<String, Integer> statefulMap = map;
                int nonThreadSafeCounter = 0;
    
                @Override
                public void run() {
                    Integer i = statefulMap.get("a");
                    if (i == null) {
                        i = 1;
                        statefulMap.put("a", i);
                        statefulMap.put("b", i);
                    } else {
                        i++;
                        statefulMap.put("a", i);
                        statefulMap.put("b", i);
                    }
                    nonThreadSafeCounter++;
                    statefulMap.put("nonThreadSafeCounter", nonThreadSafeCounter);
                    if (i < NUM) {
                        inner.schedule(this);
                    } else {
                        latch.countDown();
                    }
                }
            });
    
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
    
            System.out.println("Count A: " + map.get("a"));
            System.out.println("Count B: " + map.get("b"));
            System.out.println("nonThreadSafeCounter: " + map.get("nonThreadSafeCounter"));
    
            assertEquals(NUM, map.get("a").intValue());
            assertEquals(NUM, map.get("b").intValue());
            assertEquals(NUM, map.get("nonThreadSafeCounter").intValue());
        } finally {
            inner.dispose();
        }
    }

    @Test
    public final void testComputationThreadPool1() {
        Observable<Integer> o1 = Observable.<Integer> just(1, 2, 3, 4, 5);
        Observable<Integer> o2 = Observable.<Integer> just(6, 7, 8, 9, 10);
        Observable<String> o = Observable.<Integer> merge(o1, o2).map(new Function<Integer, String>() {

            @Override
            public String apply(Integer t) {
                assertTrue(Thread.currentThread().getName().startsWith("RxComputationThreadPool"));
                return "Value_" + t + "_Thread_" + Thread.currentThread().getName();
            }
        });

        o.subscribeOn(Schedulers.computation()).toBlocking().forEach(new Consumer<String>() {

            @Override
            public void accept(String t) {
                System.out.println("t: " + t);
            }
        });
    }


    @Test
    public final void testMergeWithExecutorScheduler() {

        final String currentThreadName = Thread.currentThread().getName();

        Observable<Integer> o1 = Observable.<Integer> just(1, 2, 3, 4, 5);
        Observable<Integer> o2 = Observable.<Integer> just(6, 7, 8, 9, 10);
        Observable<String> o = Observable.<Integer> merge(o1, o2).subscribeOn(Schedulers.computation()).map(new Function<Integer, String>() {

            @Override
            public String apply(Integer t) {
                assertFalse(Thread.currentThread().getName().equals(currentThreadName));
                assertTrue(Thread.currentThread().getName().startsWith("RxComputationThreadPool"));
                return "Value_" + t + "_Thread_" + Thread.currentThread().getName();
            }
        });

        o.toBlocking().forEach(new Consumer<String>() {

            @Override
            public void accept(String t) {
                System.out.println("t: " + t);
            }
        });
    }

    @Test
    @Ignore("Unhandled errors are no longer thrown")
    public final void testUnhandledErrorIsDeliveredToThreadHandler() throws InterruptedException {
        SchedulerTests.testUnhandledErrorIsDeliveredToThreadHandler(getScheduler());
    }

    @Test
    public final void testHandledErrorIsNotDeliveredToThreadHandler() throws InterruptedException {
        SchedulerTests.testHandledErrorIsNotDeliveredToThreadHandler(getScheduler());
    }
    
    @Test(timeout = 60000)
    public void testCancelledTaskRetention() throws InterruptedException {
        Worker w = Schedulers.computation().createWorker();
        try {
            ExecutorSchedulerTest.testCancelledRetention(w, false);
        } finally {
            w.dispose();
        }
        w = Schedulers.computation().createWorker();
        try {
            ExecutorSchedulerTest.testCancelledRetention(w, true);
        } finally {
            w.dispose();
        }
    }
}