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

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.*;

import hu.akarnokd.rxjava2.*;
import hu.akarnokd.rxjava2.NbpObservable.*;
import hu.akarnokd.rxjava2.functions.*;
import hu.akarnokd.rxjava2.internal.disposables.EmptyDisposable;
import hu.akarnokd.rxjava2.schedulers.TestScheduler;
import hu.akarnokd.rxjava2.subscribers.nbp.NbpTestSubscriber;


public class NbpOperatorWindowWithTimeTest {

    private TestScheduler scheduler;
    private Scheduler.Worker innerScheduler;

    @Before
    public void before() {
        scheduler = new TestScheduler();
        innerScheduler = scheduler.createWorker();
    }

    @Test
    public void testTimedAndCount() {
        final List<String> list = new ArrayList<String>();
        final List<List<String>> lists = new ArrayList<List<String>>();

        NbpObservable<String> source = NbpObservable.create(new NbpOnSubscribe<String>() {
            @Override
            public void accept(NbpSubscriber<? super String> NbpObserver) {
                NbpObserver.onSubscribe(EmptyDisposable.INSTANCE);
                push(NbpObserver, "one", 10);
                push(NbpObserver, "two", 90);
                push(NbpObserver, "three", 110);
                push(NbpObserver, "four", 190);
                push(NbpObserver, "five", 210);
                complete(NbpObserver, 250);
            }
        });

        NbpObservable<NbpObservable<String>> windowed = source.window(100, TimeUnit.MILLISECONDS, scheduler, 2);
        windowed.subscribe(observeWindow(list, lists));

        scheduler.advanceTimeTo(100, TimeUnit.MILLISECONDS);
        assertEquals(1, lists.size());
        assertEquals(lists.get(0), list("one", "two"));

        scheduler.advanceTimeTo(200, TimeUnit.MILLISECONDS);
        assertEquals(2, lists.size());
        assertEquals(lists.get(1), list("three", "four"));

        scheduler.advanceTimeTo(300, TimeUnit.MILLISECONDS);
        assertEquals(3, lists.size());
        assertEquals(lists.get(2), list("five"));
    }

    @Test
    public void testTimed() {
        final List<String> list = new ArrayList<String>();
        final List<List<String>> lists = new ArrayList<List<String>>();

        NbpObservable<String> source = NbpObservable.create(new NbpOnSubscribe<String>() {
            @Override
            public void accept(NbpSubscriber<? super String> NbpObserver) {
                NbpObserver.onSubscribe(EmptyDisposable.INSTANCE);
                push(NbpObserver, "one", 98);
                push(NbpObserver, "two", 99);
                push(NbpObserver, "three", 99); // FIXME happens after the window is open
                push(NbpObserver, "four", 101);
                push(NbpObserver, "five", 102);
                complete(NbpObserver, 150);
            }
        });

        NbpObservable<NbpObservable<String>> windowed = source.window(100, TimeUnit.MILLISECONDS, scheduler);
        windowed.subscribe(observeWindow(list, lists));

        scheduler.advanceTimeTo(101, TimeUnit.MILLISECONDS);
        assertEquals(1, lists.size());
        assertEquals(lists.get(0), list("one", "two", "three"));

        scheduler.advanceTimeTo(201, TimeUnit.MILLISECONDS);
        assertEquals(2, lists.size());
        assertEquals(lists.get(1), list("four", "five"));
    }

    private List<String> list(String... args) {
        List<String> list = new ArrayList<String>();
        for (String arg : args) {
            list.add(arg);
        }
        return list;
    }

    private <T> void push(final NbpSubscriber<T> NbpObserver, final T value, int delay) {
        innerScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                NbpObserver.onNext(value);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void complete(final NbpSubscriber<?> NbpObserver, int delay) {
        innerScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                NbpObserver.onComplete();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private <T> Consumer<NbpObservable<T>> observeWindow(final List<T> list, final List<List<T>> lists) {
        return new Consumer<NbpObservable<T>>() {
            @Override
            public void accept(NbpObservable<T> stringObservable) {
                stringObservable.subscribe(new NbpObserver<T>() {
                    @Override
                    public void onComplete() {
                        lists.add(new ArrayList<T>(list));
                        list.clear();
                    }

                    @Override
                    public void onError(Throwable e) {
                        fail(e.getMessage());
                    }

                    @Override
                    public void onNext(T args) {
                        list.add(args);
                    }
                });
            }
        };
    }
    @Test
    public void testExactWindowSize() {
        NbpObservable<NbpObservable<Integer>> source = NbpObservable.range(1, 10)
                .window(1, TimeUnit.MINUTES, scheduler, 3);
        
        final List<Integer> list = new ArrayList<Integer>();
        final List<List<Integer>> lists = new ArrayList<List<Integer>>();
        
        source.subscribe(observeWindow(list, lists));
        
        assertEquals(4, lists.size());
        assertEquals(3, lists.get(0).size());
        assertEquals(Arrays.asList(1, 2, 3), lists.get(0));
        assertEquals(3, lists.get(1).size());
        assertEquals(Arrays.asList(4, 5, 6), lists.get(1));
        assertEquals(3, lists.get(2).size());
        assertEquals(Arrays.asList(7, 8, 9), lists.get(2));
        assertEquals(1, lists.get(3).size());
        assertEquals(Arrays.asList(10), lists.get(3));
    }
    
    @Test
    public void testTakeFlatMapCompletes() {
        NbpTestSubscriber<Integer> ts = new NbpTestSubscriber<Integer>();
        
        final AtomicInteger wip = new AtomicInteger();
        
        final int indicator = 999999999;
        
        NbpOperatorWindowWithSizeTest.hotStream()
        .window(300, TimeUnit.MILLISECONDS)
        .take(10)
        .doOnComplete(new Runnable() {
            @Override
            public void run() {
                System.out.println("Main done!");
            }
        })
        .flatMap(new Function<NbpObservable<Integer>, NbpObservable<Integer>>() {
            @Override
            public NbpObservable<Integer> apply(NbpObservable<Integer> w) {
                return w.startWith(indicator)
                        .doOnComplete(new Runnable() {
                            @Override
                            public void run() {
                                System.out.println("inner done: " + wip.incrementAndGet());
                            }
                        })
                        ;
            }
        })
        .doOnNext(new Consumer<Integer>() {
            @Override
            public void accept(Integer pv) {
                System.out.println(pv);
            }
        })
        .subscribe(ts);
        
        ts.awaitTerminalEvent(5, TimeUnit.SECONDS);
        ts.assertComplete();
        Assert.assertTrue(ts.valueCount() != 0);
    }
    
}