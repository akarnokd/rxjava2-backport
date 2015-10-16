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

package io.reactivex;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import org.junit.*;
import org.reactivestreams.*;

import io.reactivex.Observable.Operator;
import io.reactivex.exceptions.TestException;
import io.reactivex.functions.*;
import io.reactivex.internal.functions.Functions;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.*;
import io.reactivex.subscribers.TestSubscriber;

/**
 * Verifies the operators handle null values properly by emitting/throwing NullPointerExceptions
 */
public class ObservableNullTests {

    Observable<Integer> just1 = Observable.just(1);
    
    //***********************************************************
    // Static methods
    //***********************************************************
    
    @Test(expected = NullPointerException.class)
    public void ambVarargsNull() {
        Observable.amb((Publisher<Object>[])null);
    }
    
    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void ambVarargsOneIsNull() {
        Observable.amb(Observable.never(), null).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void ambIterableNull() {
        Observable.amb((Iterable<Publisher<Object>>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void ambIterableIteratorNull() {
        Observable.amb(new Iterable<Publisher<Object>>() {
            @Override
            public Iterator<Publisher<Object>> iterator() {
                return null;
            }
        }).toBlocking().lastOption();
    }
    
    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void ambIterableOneIsNull() {
        Observable.amb(Arrays.asList(Observable.never(), null)).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void combineLatestVarargsNull() {
        Observable.combineLatest(new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] v) {
                return 1;
            }
        }, true, 128, (Publisher<Object>[])null);
    }
    
    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void combineLatestVarargsOneIsNull() {
        Observable.combineLatest(new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] v) {
                return 1;
            }
        }, true, 128, Observable.never(), null).toBlocking().lastOption();
    }

    @Test(expected = NullPointerException.class)
    public void combineLatestIterableNull() {
        Observable.combineLatest((Iterable<Publisher<Object>>)null, new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] v) {
                return 1;
            }
        }, true, 128);
    }
    
    @Test(expected = NullPointerException.class)
    public void combineLatestIterableIteratorNull() {
        Observable.combineLatest(new Iterable<Publisher<Object>>() {
            @Override
            public Iterator<Publisher<Object>> iterator() {
                return null;
            }
        }, new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] v) {
                return 1;
            }
        }, true, 128).toBlocking().lastOption();
    }
    
    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void combineLatestIterableOneIsNull() {
        Observable.combineLatest(Arrays.asList(Observable.never(), null), new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] v) {
                return 1;
            }
        }, true, 128).toBlocking().lastOption();
    }

    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void combineLatestVarargsFunctionNull() {
        Observable.combineLatest(null, true, 128, Observable.never());
    }

    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void combineLatestVarargsFunctionReturnsNull() {
        Observable.combineLatest(new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] v) {
                return null;
            }
        }, true, 128, just1).toBlocking().lastOption();
    }

    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void combineLatestIterableFunctionNull() {
        Observable.combineLatest(Arrays.asList(just1), null, true, 128);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void combineLatestIterableFunctionReturnsNull() {
        Observable.combineLatest(Arrays.asList(just1), new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] v) {
                return null;
            }
        }, true, 128).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void concatIterableNull() {
        Observable.concat((Iterable<Publisher<Object>>)null);
    }

    @Test(expected = NullPointerException.class)
    public void concatIterableIteratorNull() {
        Observable.concat(new Iterable<Publisher<Object>>() {
            @Override
            public Iterator<Publisher<Object>> iterator() {
                return null;
            }
        }).toBlocking().lastOption();
    }
    
    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void concatIterableOneIsNull() {
        Observable.concat(Arrays.asList(just1, null)).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void concatPublisherNull() {
        Observable.concat((Publisher<Publisher<Object>>)null);

    }

    @Test(expected = NullPointerException.class)
    public void concatArrayNull() {
        Observable.concatArray((Publisher<Object>[])null);
    }
    
    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void concatArrayOneIsNull() {
        Observable.concatArray(just1, null).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void createNull() {
        Observable.create(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void deferFunctionNull() {
        Observable.defer(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void deferFunctionReturnsNull() {
        Observable.defer(new Supplier<Publisher<Object>>() {
            @Override
            public Publisher<Object> get() {
                return null;
            }
        }).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void errorFunctionNull() {
        Observable.error((Supplier<Throwable>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void errorFunctionReturnsNull() {
        Observable.error(new Supplier<Throwable>() {
            @Override
            public Throwable get() {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void errorThrowableNull() {
        Observable.error((Throwable)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void fromArrayNull() {
        Observable.fromArray((Object[])null);
    }
    
    @Test(expected = NullPointerException.class)
    public void fromArrayOneIsNull() {
        Observable.fromArray(1, null).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void fromCallableNull() {
        Observable.fromCallable(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void fromCallableReturnsNull() {
        Observable.fromCallable(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return null;
            }
        }).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void fromFutureNull() {
        Observable.fromFuture(null);
    }
    
    @Test
    public void fromFutureReturnsNull() {
        FutureTask<Object> f = new FutureTask<Object>(Functions.emptyRunnable(), null);
        f.run();
        
        TestSubscriber<Object> ts = new TestSubscriber<Object>();
        Observable.fromFuture(f).subscribe(ts);
        ts.assertNoValues();
        ts.assertNotComplete();
        ts.assertError(NullPointerException.class);
    }
    
    @Test(expected = NullPointerException.class)
    public void fromFutureTimedFutureNull() {
        Observable.fromFuture(null, 1, TimeUnit.SECONDS);
    }
    
    @Test(expected = NullPointerException.class)
    public void fromFutureTimedUnitNull() {
        Observable.fromFuture(new FutureTask<Object>(Functions.emptyRunnable(), null), 1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void fromFutureTimedSchedulerNull() {
        Observable.fromFuture(new FutureTask<Object>(Functions.emptyRunnable(), null), 1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void fromFutureTimedReturnsNull() {
        FutureTask<Object> f = new FutureTask<Object>(Functions.emptyRunnable(), null);
        f.run();
        Observable.fromFuture(f, 1, TimeUnit.SECONDS).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void fromFutureSchedulerNull() {
        Observable.fromFuture(new FutureTask<Object>(Functions.emptyRunnable(), null), null);
    }
    
    @Test(expected = NullPointerException.class)
    public void fromIterableNull() {
        Observable.fromIterable(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void fromIterableIteratorNull() {
        Observable.fromIterable(new Iterable<Object>() {
            @Override
            public Iterator<Object> iterator() {
                return null;
            }
        }).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void fromIterableValueNull() {
        Observable.fromIterable(Arrays.asList(1, null)).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void fromPublisherNull() {
        Observable.fromPublisher(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void generateConsumerNull() {
        Observable.generate(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void generateConsumerEmitsNull() {
        Observable.generate(new Consumer<Subscriber<Object>>() {
            @Override
            public void accept(Subscriber<Object> s) {
                s.onNext(null);
            }
        }).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void generateStateConsumerInitialStateNull() {
        BiConsumer<Integer, Subscriber<Integer>> generator = new BiConsumer<Integer, Subscriber<Integer>>() {
            @Override
            public void accept(Integer s, Subscriber<Integer> o) {
                o.onNext(1);
            }
        };
        Observable.generate(null, generator);
    }

    @Test(expected = NullPointerException.class)
    public void generateStateFunctionInitialStateNull() {
        Observable.generate(null, new BiFunction<Object, Subscriber<Object>, Object>() {
            @Override
            public Object apply(Object s, Subscriber<Object> o) { o.onNext(1); return s; }
        });
    }

    @Test(expected = NullPointerException.class)
    public void generateStateConsumerNull() {
        Observable.generate(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return 1;
            }
        }, (BiConsumer<Integer, Subscriber<Object>>)null);
    }
    
    @Test
    public void generateConsumerStateNullAllowed() {
        BiConsumer<Integer, Subscriber<Integer>> generator = new BiConsumer<Integer, Subscriber<Integer>>() {
            @Override
            public void accept(Integer s, Subscriber<Integer> o) {
                o.onComplete();
            }
        };
        Observable.generate(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return null;
            }
        }, generator).toBlocking().lastOption();
    }

    @Test
    public void generateFunctionStateNullAllowed() {
        Observable.generate(new Supplier<Object>() {
            @Override
            public Object get() {
                return null;
            }
        }, new BiFunction<Object, Subscriber<Object>, Object>() {
            @Override
            public Object apply(Object s, Subscriber<Object> o) { o.onComplete(); return s; }
        }).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void generateConsumerDisposeNull() {
        BiConsumer<Integer, Subscriber<Integer>> generator = new BiConsumer<Integer, Subscriber<Integer>>() {
            @Override
            public void accept(Integer s, Subscriber<Integer> o) {
                o.onNext(1);
            }
        };
        Observable.generate(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return 1;
            }
        }, generator, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void generateFunctionDisposeNull() {
        Observable.generate(new Supplier<Object>() {
            @Override
            public Object get() {
                return 1;
            }
        }, new BiFunction<Object, Subscriber<Object>, Object>() {
            @Override
            public Object apply(Object s, Subscriber<Object> o) { o.onNext(1); return s; }
        }, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void intervalUnitNull() {
        Observable.interval(1, null);
    }
    
    public void intervalSchedulerNull() {
        Observable.interval(1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void intervalPeriodUnitNull() {
        Observable.interval(1, 1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void intervalPeriodSchedulerNull() {
        Observable.interval(1, 1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void intervalRangeUnitNull() {
        Observable.intervalRange(1,1, 1, 1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void intervalRangeSchedulerNull() {
        Observable.intervalRange(1, 1, 1, 1, TimeUnit.SECONDS, null);
    }
    
    @Test
    public void justNull() throws Exception {
        @SuppressWarnings("rawtypes")
        Class<Observable> clazz = Observable.class;
        for (int argCount = 1; argCount < 10; argCount++) {
            for (int argNull = 1; argNull <= argCount; argNull++) {
                Class<?>[] params = new Class[argCount];
                Arrays.fill(params, Object.class);

                Object[] values = new Object[argCount];
                Arrays.fill(values, 1);
                values[argNull - 1] = null;
                
                Method m = clazz.getMethod("just", params);
                
                try {
                    m.invoke(null, values);
                    Assert.fail("No exception for argCount " + argCount + " / argNull " + argNull);
                } catch (InvocationTargetException ex) {
                    if (!(ex.getCause() instanceof NullPointerException)) {
                        Assert.fail("Unexpected exception for argCount " + argCount + " / argNull " + argNull + ": " + ex);
                    }
                }
            }
        }
    }

    @Test(expected = NullPointerException.class)
    public void mergeIterableNull() {
        Observable.merge(128, 128, (Iterable<Publisher<Object>>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void mergeIterableIteratorNull() {
        Observable.merge(128, 128, new Iterable<Publisher<Object>>() {
            @Override
            public Iterator<Publisher<Object>> iterator() {
                return null;
            }
        }).toBlocking().lastOption();
    }

    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void mergeIterableOneIsNull() {
        Observable.merge(128, 128, Arrays.asList(just1, null)).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void mergeArrayNull() {
        Observable.merge(128, 128, (Publisher<Object>[])null);
    }
    
    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void mergeArrayOneIsNull() {
        Observable.merge(128, 128, just1, null).toBlocking().lastOption();
    }

    @Test(expected = NullPointerException.class)
    public void mergeDelayErrorIterableNull() {
        Observable.mergeDelayError(128, 128, (Iterable<Publisher<Object>>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void mergeDelayErrorIterableIteratorNull() {
        Observable.mergeDelayError(128, 128, new Iterable<Publisher<Object>>() {
            @Override
            public Iterator<Publisher<Object>> iterator() {
                return null;
            }
        }).toBlocking().lastOption();
    }
    
    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void mergeDelayErrorIterableOneIsNull() {
        Observable.mergeDelayError(128, 128, Arrays.asList(just1, null)).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void mergeDelayErrorArrayNull() {
        Observable.mergeDelayError(128, 128, (Publisher<Object>[])null);
    }
    
    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void mergeDelayErrorArrayOneIsNull() {
        Observable.mergeDelayError(128, 128, just1, null).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void sequenceEqualFirstNull() {
        Observable.sequenceEqual(null, just1);
    }
    
    @Test(expected = NullPointerException.class)
    public void sequenceEqualSecondNull() {
        Observable.sequenceEqual(just1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void sequenceEqualComparatorNull() {
        Observable.sequenceEqual(just1, just1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void switchOnNextNull() {
        Observable.switchOnNext(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void timerUnitNull() {
        Observable.timer(1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void timerSchedulerNull() {
        Observable.timer(1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void usingResourceSupplierNull() {
        Observable.using(null, new Function<Object, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Object d) {
                return just1;
            }
        }, Functions.emptyConsumer());
    }
    
    @Test(expected = NullPointerException.class)
    public void usingObservableSupplierNull() {
        Observable.using(new Supplier<Object>() {
            @Override
            public Object get() {
                return 1;
            }
        }, null, Functions.emptyConsumer());
    }
    
    @Test(expected = NullPointerException.class)
    public void usingObservableSupplierReturnsNull() {
        Observable.using(new Supplier<Object>() {
            @Override
            public Object get() {
                return 1;
            }
        }, new Function<Object, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Object d) {
                return null;
            }
        }, Functions.emptyConsumer()).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void usingDisposeNull() {
        Observable.using(new Supplier<Object>() {
            @Override
            public Object get() {
                return 1;
            }
        }, new Function<Object, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Object d) {
                return just1;
            }
        }, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void zipIterableNull() {
        Observable.zip((Iterable<Publisher<Object>>)null, new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] v) {
                return 1;
            }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void zipIterableIteratorNull() {
        Observable.zip(new Iterable<Publisher<Object>>() {
            @Override
            public Iterator<Publisher<Object>> iterator() {
                return null;
            }
        }, new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] v) {
                return 1;
            }
        }).toBlocking().lastOption();
    }
    
    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void zipIterableFunctionNull() {
        Observable.zip(Arrays.asList(just1, just1), null);
    }
    
    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void zipIterableFunctionReturnsNull() {
        Observable.zip(Arrays.asList(just1, just1), new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] a) {
                return null;
            }
        }).toBlocking().lastOption();
    }
    
    @Test(expected = NullPointerException.class)
    public void zipPublisherNull() {
        Observable.zip((Publisher<Publisher<Object>>)null, new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] a) {
                return 1;
            }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void zipPublisherFunctionNull() {
        Observable.zip((Observable.just(just1)), null);
    }

    @Test(expected = NullPointerException.class)
    public void zipPublisherFunctionReturnsNull() {
        Observable.zip((Observable.just(just1)), new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] a) {
                return null;
            }
        }).toBlocking().lastOption();
    }

    @Test(expected = NullPointerException.class)
    public void zipIterable2Null() {
        Observable.zipIterable(new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] a) {
                return 1;
            }
        }, true, 128, (Iterable<Publisher<Object>>)null);
    }

    @Test(expected = NullPointerException.class)
    public void zipIterable2IteratorNull() {
        Observable.zipIterable(new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] a) {
                return 1;
            }
        }, true, 128, new Iterable<Publisher<Object>>() {
            @Override
            public Iterator<Publisher<Object>> iterator() {
                return null;
            }
        }).toBlocking().lastOption();
    }
    
    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void zipIterable2FunctionNull() {
        Observable.zipIterable(null, true, 128, Arrays.asList(just1, just1));
    }

    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void zipIterable2FunctionReturnsNull() {
        Observable.zipIterable(new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] a) {
                return null;
            }
        }, true, 128, Arrays.asList(just1, just1)).toBlocking().lastOption();
    }

    //*************************************************************
    // Instance methods
    //*************************************************************

    @Test(expected = NullPointerException.class)
    public void allPredicateNull() {
        just1.all(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void ambWithNull() {
        just1.ambWith(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void anyPredicateNull() {
        just1.any(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferSupplierNull() {
        just1.buffer(1, 1, (Supplier<List<Integer>>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferSupplierReturnsNull() {
        just1.buffer(1, 1, new Supplier<Collection<Integer>>() {
            @Override
            public Collection<Integer> get() {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferTimedUnitNull() {
        just1.buffer(1L, 1L, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferTimedSchedulerNull() {
        just1.buffer(1L, 1L, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferTimedSupplierNull() {
        just1.buffer(1L, 1L, TimeUnit.SECONDS, Schedulers.single(), null);
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferTimedSupplierReturnsNull() {
        just1.buffer(1L, 1L, TimeUnit.SECONDS, Schedulers.single(), new Supplier<Collection<Integer>>() {
            @Override
            public Collection<Integer> get() {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferOpenCloseOpenNull() {
        just1.buffer(null, new Function<Object, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Object o) {
                return just1;
            }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferOpenCloseCloseNull() {
        just1.buffer(just1, (Function<Integer, Publisher<Object>>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferOpenCloseCloseReturnsNull() {
        just1.buffer(just1, new Function<Integer, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferBoundaryNull() {
        just1.buffer((Publisher<Object>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferBoundarySupplierNull() {
        just1.buffer(just1, (Supplier<List<Integer>>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferBoundarySupplierReturnsNull() {
        just1.buffer(just1, new Supplier<Collection<Integer>>() {
            @Override
            public Collection<Integer> get() {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferBoundarySupplier2Null() {
        just1.buffer((Supplier<Publisher<Integer>>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferBoundarySupplier2ReturnsNull() {
        just1.buffer(new Supplier<Publisher<Object>>() {
            @Override
            public Publisher<Object> get() {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferBoundarySupplier2SupplierNull() {
        just1.buffer(new Supplier<Observable<Integer>>() {
            @Override
            public Observable<Integer> get() {
                return just1;
            }
        }, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void bufferBoundarySupplier2SupplierReturnsNull() {
        just1.buffer(new Supplier<Observable<Integer>>() {
            @Override
            public Observable<Integer> get() {
                return just1;
            }
        }, new Supplier<Collection<Integer>>() {
            @Override
            public Collection<Integer> get() {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void castNull() {
        just1.cast(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void collectInitialSupplierNull() {
        just1.collect((Supplier<Integer>)null, new BiConsumer<Integer, Integer>() {
            @Override
            public void accept(Integer a, Integer b) { }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void collectInitialSupplierReturnsNull() {
        just1.collect(new Supplier<Object>() {
            @Override
            public Object get() {
                return null;
            }
        }, new BiConsumer<Object, Integer>() {
            @Override
            public void accept(Object a, Integer b) { }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void collectInitialCollectorNull() {
        just1.collect(new Supplier<Object>() {
            @Override
            public Object get() {
                return 1;
            }
        }, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void collectIntoInitialNull() {
        just1.collectInto(null, new BiConsumer<Object, Integer>() {
            @Override
            public void accept(Object a, Integer b) { }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void collectIntoCollectorNull() {
        just1.collectInto(1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void composeNull() {
        just1.compose(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void concatMapNull() {
        just1.concatMap(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void concatMapReturnsNull() {
        just1.concatMap(new Function<Integer, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void concatMapIterableNull() {
        just1.concatMapIterable(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void concatMapIterableReturnNull() {
        just1.concatMapIterable(new Function<Integer, Iterable<Object>>() {
            @Override
            public Iterable<Object> apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void concatMapIterableIteratorNull() {
        just1.concatMapIterable(new Function<Integer, Iterable<Object>>() {
            @Override
            public Iterable<Object> apply(Integer v) {
                return new Iterable<Object>() {
                    @Override
                    public Iterator<Object> iterator() {
                        return null;
                    }
                };
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void concatWithNull() {
        just1.concatWith(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void containsNull() {
        just1.contains(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void debounceFunctionNull() {
        just1.debounce(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void debounceFunctionReturnsNull() {
        just1.debounce(new Function<Integer, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void debounceTimedUnitNull() {
        just1.debounce(1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void debounceTimedSchedulerNull() {
        just1.debounce(1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void defaultIfEmptyNull() {
        just1.defaultIfEmpty(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void delayWithFunctionNull() {
        just1.delay(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void delayWithFunctionReturnsNull() {
        just1.delay(new Function<Integer, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void delayTimedUnitNull() {
        just1.delay(1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void delayTimedSchedulerNull() {
        just1.delay(1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void delaySubscriptionTimedUnitNull() {
        just1.delaySubscription(1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void delaySubscriptionTimedSchedulerNull() {
        just1.delaySubscription(1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void delaySubscriptionSupplierNull() {
        just1.delaySubscription((Supplier<Publisher<Object>>)null);
    }

    @Test(expected = NullPointerException.class)
    public void delaySubscriptionFunctionNull() {
        just1.delaySubscription((Publisher<Object>)null);
    }

    @Test(expected = NullPointerException.class)
    public void delayBothInitialSupplierNull() {
        just1.delay(null, new Function<Integer, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Integer v) {
                return just1;
            }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void delayBothInitialSupplierReturnsNull() {
        just1.delay(new Supplier<Publisher<Object>>() {
            @Override
            public Publisher<Object> get() {
                return null;
            }
        }, new Function<Integer, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Integer v) {
                return just1;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void delayBothItemSupplierNull() {
        just1.delay(new Supplier<Publisher<Integer>>() {
            @Override
            public Publisher<Integer> get() {
                return just1;
            }
        }, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void delayBothItemSupplierReturnsNull() {
        just1.delay(new Supplier<Publisher<Integer>>() {
            @Override
            public Publisher<Integer> get() {
                return just1;
            }
        }, new Function<Integer, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void distinctFunctionNull() {
        just1.distinct(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void distinctSupplierNull() {
        just1.distinct(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void distinctSupplierReturnsNull() {
        just1.distinct(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, new Supplier<Collection<Object>>() {
            @Override
            public Collection<Object> get() {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void distinctFunctionReturnsNull() {
        just1.distinct(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void distinctUntilChangedFunctionNull() {
        just1.distinctUntilChanged(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void distinctUntilChangedFunctionReturnsNull() {
        just1.distinctUntilChanged(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnCancelNull() {
        just1.doOnCancel(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnCompleteNull() {
        just1.doOnComplete(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnEachSupplierNull() {
        just1.doOnEach((Consumer<Try<Optional<Integer>>>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnEachSubscriberNull() {
        just1.doOnEach((Subscriber<Integer>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnErrorNull() {
        just1.doOnError(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnLifecycleOnSubscribeNull() {
        just1.doOnLifecycle(null, new LongConsumer() {
            @Override
            public void accept(long v) { }
        }, new Runnable() {
            @Override
            public void run() { }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnLifecycleOnRequestNull() {
        just1.doOnLifecycle(new Consumer<Subscription>() {
            @Override
            public void accept(Subscription s) { }
        }, null, new Runnable() {
            @Override
            public void run() { }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnLifecycleOnCancelNull() {
        just1.doOnLifecycle(new Consumer<Subscription>() {
            @Override
            public void accept(Subscription s) { }
        }, new LongConsumer() {
            @Override
            public void accept(long v) { }
        }, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnNextNull() {
        just1.doOnNext(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnRequestNull() {
        just1.doOnRequest(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnSubscribeNull() {
        just1.doOnSubscribe(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnTerminatedNull() {
        just1.doOnTerminate(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void elementAtNull() {
        just1.elementAt(1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void endWithIterableNull() {
        just1.endWith((Iterable<Integer>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void endWithIterableIteratorNull() {
        just1.endWith(new Iterable<Integer>() {
            @Override
            public Iterator<Integer> iterator() {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void endWithIterableOneIsNull() {
        just1.endWith(Arrays.asList(1, null)).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void endWithPublisherNull() {
        just1.endWith((Publisher<Integer>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void endWithNull() {
        just1.endWith((Integer)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void endWithArrayNull() {
        just1.endWithArray((Integer[])null);
    }
    
    @Test(expected = NullPointerException.class)
    public void endWithArrayOneIsNull() {
        just1.endWithArray(1, null).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void filterNull() {
        just1.filter(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void finallyDoNull() {
        just1.finallyDo(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void firstNull() {
        just1.first(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void flatMapNull() {
        just1.flatMap(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void flatMapFunctionReturnsNull() {
        just1.flatMap(new Function<Integer, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void flatMapNotificationOnNextNull() {
        just1.flatMap(null, new Function<Throwable, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Throwable e) {
                return just1;
            }
        }, new Supplier<Publisher<Integer>>() {
            @Override
            public Publisher<Integer> get() {
                return just1;
            }
        });
    }

    @Test(expected = NullPointerException.class)
    public void flatMapNotificationOnNextReturnsNull() {
        just1.flatMap(new Function<Integer, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Integer v) {
                return null;
            }
        }, new Function<Throwable, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Throwable e) {
                return just1;
            }
        }, new Supplier<Publisher<Integer>>() {
            @Override
            public Publisher<Integer> get() {
                return just1;
            }
        }).toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void flatMapNotificationOnErrorNull() {
        just1.flatMap(new Function<Integer, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Integer v) {
                return just1;
            }
        }, null, new Supplier<Publisher<Integer>>() {
            @Override
            public Publisher<Integer> get() {
                return just1;
            }
        });
    }

    @Test(expected = NullPointerException.class)
    public void flatMapNotificationOnErrorReturnsNull() {
        Observable.error(new TestException()).flatMap(new Function<Object, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Object v) {
                return just1;
            }
        }, new Function<Throwable, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Throwable e) {
                return null;
            }
        }, new Supplier<Publisher<Integer>>() {
            @Override
            public Publisher<Integer> get() {
                return just1;
            }
        }).toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void flatMapNotificationOnCompleteNull() {
        just1.flatMap(new Function<Integer, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Integer v) {
                return just1;
            }
        }, new Function<Throwable, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Throwable e) {
                return just1;
            }
        }, null);
    }

    @Test(expected = NullPointerException.class)
    public void flatMapNotificationOnCompleteReturnsNull() {
        just1.flatMap(new Function<Integer, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Integer v) {
                return just1;
            }
        }, new Function<Throwable, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Throwable e) {
                return just1;
            }
        }, new Supplier<Publisher<Integer>>() {
            @Override
            public Publisher<Integer> get() {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void flatMapCombinerMapperNull() {
        just1.flatMap(null, new BiFunction<Integer, Object, Object>() {
            @Override
            public Object apply(Integer a, Object b) {
                return 1;
            }
        });
    }

    @Test(expected = NullPointerException.class)
    public void flatMapCombinerMapperReturnsNull() {
        just1.flatMap(new Function<Integer, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Integer v) {
                return null;
            }
        }, new BiFunction<Integer, Object, Object>() {
            @Override
            public Object apply(Integer a, Object b) {
                return 1;
            }
        }).toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void flatMapCombinerCombinerNull() {
        just1.flatMap(new Function<Integer, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Integer v) {
                return just1;
            }
        }, null);
    }

    @Test(expected = NullPointerException.class)
    public void flatMapCombinerCombinerReturnsNull() {
        just1.flatMap(new Function<Integer, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Integer v) {
                return just1;
            }
        }, new BiFunction<Integer, Integer, Object>() {
            @Override
            public Object apply(Integer a, Integer b) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void flatMapIterableMapperNull() {
        just1.flatMapIterable(null);
    }

    @Test(expected = NullPointerException.class)
    public void flatMapIterableMapperReturnsNull() {
        just1.flatMapIterable(new Function<Integer, Iterable<Object>>() {
            @Override
            public Iterable<Object> apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void flatMapIterableMapperIteratorNull() {
        just1.flatMapIterable(new Function<Integer, Iterable<Object>>() {
            @Override
            public Iterable<Object> apply(Integer v) {
                return new Iterable<Object>() {
                    @Override
                    public Iterator<Object> iterator() {
                        return null;
                    }
                };
            }
        }).toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void flatMapIterableMapperIterableOneNull() {
        just1.flatMapIterable(new Function<Integer, Iterable<Integer>>() {
            @Override
            public Iterable<Integer> apply(Integer v) {
                return Arrays.asList(1, null);
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void flatMapIterableCombinerNull() {
        just1.flatMapIterable(new Function<Integer, Iterable<Integer>>() {
            @Override
            public Iterable<Integer> apply(Integer v) {
                return Arrays.asList(1);
            }
        }, null);
    }

    @Test(expected = NullPointerException.class)
    public void flatMapIterableCombinerReturnsNull() {
        just1.flatMapIterable(new Function<Integer, Iterable<Integer>>() {
            @Override
            public Iterable<Integer> apply(Integer v) {
                return Arrays.asList(1);
            }
        }, new BiFunction<Integer, Integer, Object>() {
            @Override
            public Object apply(Integer a, Integer b) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void forEachNull() {
        just1.forEach(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void forEachWhileNull() {
        just1.forEachWhile(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void forEachWhileOnErrorNull() {
        just1.forEachWhile(new Predicate<Integer>() {
            @Override
            public boolean test(Integer v) {
                return true;
            }
        }, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void forEachWhileOnCompleteNull() {
        just1.forEachWhile(new Predicate<Integer>() {
            @Override
            public boolean test(Integer v) {
                return true;
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) { }
        }, null);
    }

    @Test(expected = NullPointerException.class)
    public void groupByNull() {
        just1.groupBy(null);
    }
    
    public void groupByKeyNull() {
        just1.groupBy(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void groupByValueNull() {
        just1.groupBy(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void groupByValueReturnsNull() {
        just1.groupBy(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void lastNull() {
        just1.last(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void liftNull() {
        just1.lift(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void liftReturnsNull() {
        just1.lift(new Operator<Object, Integer>() {
            @Override
            public Subscriber<? super Integer> apply(Subscriber<? super Object> s) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void mapNull() {
        just1.map(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void mapReturnsNull() {
        just1.map(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void mergeWithNull() {
        just1.mergeWith(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void observeOnNull() {
        just1.observeOn(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void ofTypeNull() {
        just1.ofType(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void onBackpressureBufferOverflowNull() {
        just1.onBackpressureBuffer(10, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void onBackpressureDropActionNull() {
        just1.onBackpressureDrop(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void onErrorResumeNextFunctionNull() {
        just1.onErrorResumeNext((Function<Throwable, Publisher<Integer>>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void onErrorResumeNextFunctionReturnsNull() {
        Observable.error(new TestException()).onErrorResumeNext(new Function<Throwable, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Throwable e) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void onErrorResumeNextPublisherNull() {
        just1.onErrorResumeNext((Publisher<Integer>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void onErrorReturnFunctionNull() {
        just1.onErrorReturn(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void onErrorReturnValueNull() {
        just1.onErrorReturnValue(null);
    }

    @Test(expected = NullPointerException.class)
    public void onErrorReturnFunctionReturnsNull() {
        Observable.error(new TestException()).onErrorReturn(new Function<Throwable, Object>() {
            @Override
            public Object apply(Throwable e) {
                return null;
            }
        }).toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void onExceptionResumeNext() {
        just1.onExceptionResumeNext(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void publishFunctionNull() {
        just1.publish(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void publishFunctionReturnsNull() {
        just1.publish(new Function<Observable<Integer>, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Observable<Integer> v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void reduceFunctionNull() {
        just1.reduce(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void reduceFunctionReturnsNull() {
        Observable.just(1, 1).reduce(new BiFunction<Integer, Integer, Integer>() {
            @Override
            public Integer apply(Integer a, Integer b) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void reduceSeedNull() {
        just1.reduce(null, new BiFunction<Object, Integer, Object>() {
            @Override
            public Object apply(Object a, Integer b) {
                return 1;
            }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void reduceSeedFunctionNull() {
        just1.reduce(1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void reduceSeedFunctionReturnsNull() {
        just1.reduce(1, new BiFunction<Integer, Integer, Integer>() {
            @Override
            public Integer apply(Integer a, Integer b) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void reduceWithSeedNull() {
        just1.reduceWith(null, new BiFunction<Object, Integer, Object>() {
            @Override
            public Object apply(Object a, Integer b) {
                return 1;
            }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void reduceWithSeedReturnsNull() {
        just1.reduceWith(new Supplier<Object>() {
            @Override
            public Object get() {
                return null;
            }
        }, new BiFunction<Object, Integer, Object>() {
            @Override
            public Object apply(Object a, Integer b) {
                return 1;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void repeatUntilNull() {
        just1.repeatUntil(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void repeatWhenNull() {
        just1.repeatWhen(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void repeatWhenFunctionReturnsNull() {
        just1.repeatWhen(new Function<Observable<Object>, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Observable<Object> v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void replaySelectorNull() {
        just1.replay((Function<Observable<Integer>, Observable<Integer>>)null);
    }

    @Test(expected = NullPointerException.class)
    public void replaySelectorReturnsNull() {
        just1.replay(new Function<Observable<Integer>, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Observable<Integer> o) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void replayBoundedSelectorNull() {
        just1.replay((Function<Observable<Integer>, Observable<Integer>>)null, 1, 1, TimeUnit.SECONDS);
    }
    
    @Test(expected = NullPointerException.class)
    public void replayBoundedSelectorReturnsNull() {
        just1.replay(new Function<Observable<Integer>, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Observable<Integer> v) {
                return null;
            }
        }, 1, 1, TimeUnit.SECONDS).toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void replaySchedulerNull() {
        just1.replay((Scheduler)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void replayBoundedUnitNull() {
        just1.replay(new Function<Observable<Integer>, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Observable<Integer> v) {
                return v;
            }
        }, 1, 1, null).toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void replayBoundedSchedulerNull() {
        just1.replay(new Function<Observable<Integer>, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Observable<Integer> v) {
                return v;
            }
        }, 1, 1, TimeUnit.SECONDS, null).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void replayTimeBoundedSelectorNull() {
        just1.replay(null, 1, TimeUnit.SECONDS, Schedulers.single());
    }

    @Test(expected = NullPointerException.class)
    public void replayTimeBoundedSelectorReturnsNull() {
        just1.replay(new Function<Observable<Integer>, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Observable<Integer> v) {
                return null;
            }
        }, 1, TimeUnit.SECONDS, Schedulers.single()).toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void replaySelectorTimeBoundedUnitNull() {
        just1.replay(new Function<Observable<Integer>, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Observable<Integer> v) {
                return v;
            }
        }, 1, null, Schedulers.single());
    }

    @Test(expected = NullPointerException.class)
    public void replaySelectorTimeBoundedSchedulerNull() {
        just1.replay(new Function<Observable<Integer>, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Observable<Integer> v) {
                return v;
            }
        }, 1, TimeUnit.SECONDS, null);
    }

    @Test(expected = NullPointerException.class)
    public void replayTimeSizeBoundedUnitNull() {
        just1.replay(1, 1, null, Schedulers.single());
    }

    @Test(expected = NullPointerException.class)
    public void replayTimeSizeBoundedSchedulerNull() {
        just1.replay(1, 1, TimeUnit.SECONDS, null);
    }

    @Test(expected = NullPointerException.class)
    public void replayBufferSchedulerNull() {
        just1.replay(1, (Scheduler)null);
    }

    @Test(expected = NullPointerException.class)
    public void replayTimeBoundedUnitNull() {
        just1.replay(1, null, Schedulers.single());
    }

    @Test(expected = NullPointerException.class)
    public void replayTimeBoundedSchedulerNull() {
        just1.replay(1, TimeUnit.SECONDS, null);
    }

    @Test(expected = NullPointerException.class)
    public void retryFunctionNull() {
        just1.retry((BiPredicate<Integer, Throwable>)null);
    }

    @Test(expected = NullPointerException.class)
    public void retryCountFunctionNull() {
        just1.retry(1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void retryPredicateNull() {
        just1.retry((Predicate<Throwable>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void retryWhenFunctionNull() {
        just1.retryWhen(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void retryWhenFunctionReturnsNull() {
        Observable.error(new TestException()).retryWhen(new Function<Observable<? extends Throwable>, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Observable<? extends Throwable> f) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void retryUntil() {
        just1.retryUntil(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void safeSubscribeNull() {
        just1.safeSubscribe(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void sampleUnitNull() {
        just1.sample(1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void sampleSchedulerNull() {
        just1.sample(1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void samplePublisherNull() {
        just1.sample(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void scanFunctionNull() {
        just1.scan(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void scanFunctionReturnsNull() {
        Observable.just(1, 1).scan(new BiFunction<Integer, Integer, Integer>() {
            @Override
            public Integer apply(Integer a, Integer b) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void scanSeedNull() {
        just1.scan(null, new BiFunction<Object, Integer, Object>() {
            @Override
            public Object apply(Object a, Integer b) {
                return 1;
            }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void scanSeedFunctionNull() {
        just1.scan(1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void scanSeedFunctionReturnsNull() {
        just1.scan(1, new BiFunction<Integer, Integer, Integer>() {
            @Override
            public Integer apply(Integer a, Integer b) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void scanSeedSupplierNull() {
        just1.scanWith(null, new BiFunction<Object, Integer, Object>() {
            @Override
            public Object apply(Object a, Integer b) {
                return 1;
            }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void scanSeedSupplierReturnsNull() {
        just1.scanWith(new Supplier<Object>() {
            @Override
            public Object get() {
                return null;
            }
        }, new BiFunction<Object, Integer, Object>() {
            @Override
            public Object apply(Object a, Integer b) {
                return 1;
            }
        }).toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void scanSeedSupplierFunctionNull() {
        just1.scanWith(new Supplier<Object>() {
            @Override
            public Object get() {
                return 1;
            }
        }, null);
    }

    @Test(expected = NullPointerException.class)
    public void scanSeedSupplierFunctionReturnsNull() {
        just1.scanWith(new Supplier<Object>() {
            @Override
            public Object get() {
                return 1;
            }
        }, new BiFunction<Object, Integer, Object>() {
            @Override
            public Object apply(Object a, Integer b) {
                return null;
            }
        }).toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void singleNull() {
        just1.single(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void skipTimedUnitNull() {
        just1.skip(1, null, Schedulers.single());
    }
    
    @Test(expected = NullPointerException.class)
    public void skipTimedSchedulerNull() {
        just1.skip(1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void skipLastTimedUnitNull() {
        just1.skipLast(1, null, Schedulers.single());
    }
    
    @Test(expected = NullPointerException.class)
    public void skipLastTimedSchedulerNull() {
        just1.skipLast(1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void skipUntilNull() {
        just1.skipUntil(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void skipWhileNull() {
        just1.skipWhile(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void startWithIterableNull() {
        just1.startWith((Iterable<Integer>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void startWithIterableIteratorNull() {
        just1.startWith(new Iterable<Integer>() {
            @Override
            public Iterator<Integer> iterator() {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void startWithIterableOneNull() {
        just1.startWith(Arrays.asList(1, null)).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void startWithSingleNull() {
        just1.startWith((Integer)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void startWithPublisherNull() {
        just1.startWith((Publisher<Integer>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void startWithArrayNull() {
        just1.startWithArray((Integer[])null);
    }
    
    @Test(expected = NullPointerException.class)
    public void startWithArrayOneNull() {
        just1.startWithArray(1, null).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void subscribeOnNextNull() {
        just1.subscribe((Consumer<Integer>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void subscribeOnErrorNull() {
        just1.subscribe(Functions.emptyConsumer(), null);
    }
    
    @Test(expected = NullPointerException.class)
    public void subscribeOnCompleteNull() {
        just1.subscribe(Functions.emptyConsumer(), Functions.emptyConsumer(), null);
    }
    
    @Test(expected = NullPointerException.class)
    public void subscribeOnSubscribeNull() {
        just1.subscribe(Functions.emptyConsumer(), Functions.emptyConsumer(), Functions.emptyRunnable(), null);
    }
    
    @Test(expected = NullPointerException.class)
    public void subscribeNull() {
        just1.subscribe((Subscriber<Integer>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void subscribeOnNull() {
        just1.subscribeOn(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void switchIfEmptyNull() {
        just1.switchIfEmpty(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void switchMapNull() {
        just1.switchMap(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void switchMapFunctionReturnsNull() {
        just1.switchMap(new Function<Integer, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void takeTimedUnitNull() {
        just1.take(1, null, Schedulers.single());
    }
    
    @Test(expected = NullPointerException.class)
    public void takeTimedSchedulerNull() {
        just1.take(1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void takeFirstNull() {
        just1.takeFirst(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void takeLastTimedUnitNull() {
        just1.takeLast(1, null, Schedulers.single());
    }

    @Test(expected = NullPointerException.class)
    public void takeLastSizeTimedUnitNull() {
        just1.takeLast(1, 1, null, Schedulers.single());
    }

    @Test(expected = NullPointerException.class)
    public void takeLastTimedSchedulerNull() {
        just1.takeLast(1, TimeUnit.SECONDS, null);
    }

    @Test(expected = NullPointerException.class)
    public void takeLastSizeTimedSchedulerNull() {
        just1.takeLast(1, 1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void takeLastBufferTimedUnitNull() {
        just1.takeLastBuffer(1, null, Schedulers.single());
    }

    @Test(expected = NullPointerException.class)
    public void takeLastBufferTimedSchedulerNull() {
        just1.takeLastBuffer(1, TimeUnit.SECONDS, null);
    }

    @Test(expected = NullPointerException.class)
    public void takeLastBufferSizeTimedUnitNull() {
        just1.takeLastBuffer(1, 1, null, Schedulers.single());
    }

    @Test(expected = NullPointerException.class)
    public void takeLastBufferSizeTimedSchedulerNull() {
        just1.takeLastBuffer(1, 1, TimeUnit.SECONDS, null);
    }

    @Test(expected = NullPointerException.class)
    public void takeUntilPredicateNull() {
        just1.takeUntil((Predicate<Integer>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void takeUntilPublisherNull() {
        just1.takeUntil((Publisher<Integer>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void takeWhileNull() {
        just1.takeWhile(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void throttleFirstUnitNull() {
        just1.throttleFirst(1, null, Schedulers.single());
    }
    
    @Test(expected = NullPointerException.class)
    public void throttleFirstSchedulerNull() {
        just1.throttleFirst(1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void throttleLastUnitNull() {
        just1.throttleLast(1, null, Schedulers.single());
    }
    
    @Test(expected = NullPointerException.class)
    public void throttleLastSchedulerNull() {
        just1.throttleLast(1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void throttleWithTimeoutUnitNull() {
        just1.throttleWithTimeout(1, null, Schedulers.single());
    }
    
    @Test(expected = NullPointerException.class)
    public void throttleWithTimeoutSchedulerNull() {
        just1.throttleWithTimeout(1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void timeIntervalUnitNull() {
        just1.timeInterval(null, Schedulers.single());
    }
    
    @Test(expected = NullPointerException.class)
    public void timeIntervalSchedulerNull() {
        just1.timeInterval(TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void timeoutSelectorNull() {
        just1.timeout(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void timeoutSelectorReturnsNull() {
        just1.timeout(new Function<Integer, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void timeoutSelectorOtherNull() {
        just1.timeout(new Function<Integer, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Integer v) {
                return just1;
            }
        }, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void timeoutUnitNull() {
        just1.timeout(1, null, just1, Schedulers.single());
    }

    @Test(expected = NullPointerException.class)
    public void timeouOtherNull() {
        just1.timeout(1, TimeUnit.SECONDS, null, Schedulers.single());
    }

    @Test(expected = NullPointerException.class)
    public void timeouSchedulerNull() {
        just1.timeout(1, TimeUnit.SECONDS, just1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void timeoutFirstNull() {
        just1.timeout((Supplier<Publisher<Integer>>)null, new Function<Integer, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Integer v) {
                return just1;
            }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void timeoutFirstReturnsNull() {
        just1.timeout(new Supplier<Publisher<Object>>() {
            @Override
            public Publisher<Object> get() {
                return null;
            }
        }, new Function<Integer, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Integer v) {
                return just1;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void timeoutFirstItemNull() {
        just1.timeout(new Supplier<Publisher<Integer>>() {
            @Override
            public Publisher<Integer> get() {
                return just1;
            }
        }, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void timeoutFirstItemReturnsNull() {
        just1.timeout(new Supplier<Publisher<Integer>>() {
            @Override
            public Publisher<Integer> get() {
                return just1;
            }
        }, new Function<Integer, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void timestampUnitNull() {
        just1.timestamp(null, Schedulers.single());
    }
    
    @Test(expected = NullPointerException.class)
    public void timestampSchedulerNull() {
        just1.timestamp(TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void toNull() {
        just1.to(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void toListNull() {
        just1.toList(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void toListSupplierReturnsNull() {
        just1.toList(new Supplier<Collection<Integer>>() {
            @Override
            public Collection<Integer> get() {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void toSortedListNull() {
        just1.toSortedList(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void toMapKeyNullAllowed() {
        just1.toMap(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void toMapValueNull() {
        just1.toMap(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, null);
    }
    
    @Test
    public void toMapValueSelectorReturnsNull() {
        just1.toMap(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void toMapMapSupplierNull() {
        just1.toMap(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void toMapMapSupplierReturnsNull() {
        just1.toMap(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, new Supplier<Map<Object, Object>>() {
            @Override
            public Map<Object, Object> get() {
                return null;
            }
        }).toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void toMultimapKeyNull() {
        just1.toMultimap(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void toMultimapValueNull() {
        just1.toMultimap(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, null);
    }
    
    @Test
    public void toMultiMapValueSelectorReturnsNullAllowed() {
        just1.toMap(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void toMultimapMapMapSupplierNull() {
        just1.toMultimap(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void toMultimapMapSupplierReturnsNull() {
        just1.toMultimap(new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, new Function<Integer, Object>() {
            @Override
            public Object apply(Integer v) {
                return v;
            }
        }, new Supplier<Map<Object, Collection<Object>>>() {
            @Override
            public Map<Object, Collection<Object>> get() {
                return null;
            }
        }).toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void toMultimapMapMapCollectionSupplierNull() {
        just1.toMultimap(new Function<Integer, Integer>() {
            @Override
            public Integer apply(Integer v) {
                return v;
            }
        }, new Function<Integer, Integer>() {
            @Override
            public Integer apply(Integer v) {
                return v;
            }
        }, new Supplier<Map<Integer, Collection<Integer>>>() {
            @Override
            public Map<Integer, Collection<Integer>> get() {
                return new HashMap<Integer, Collection<Integer>>();
            }
        }, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void toMultimapMapCollectionSupplierReturnsNull() {
        just1.toMultimap(new Function<Integer, Integer>() {
            @Override
            public Integer apply(Integer v) {
                return v;
            }
        }, new Function<Integer, Integer>() {
            @Override
            public Integer apply(Integer v) {
                return v;
            }
        }, new Supplier<Map<Integer, Collection<Integer>>>() {
            @Override
            public Map<Integer, Collection<Integer>> get() {
                return new HashMap<Integer, Collection<Integer>>();
            }
        }, new Function<Integer, Collection<Integer>>() {
            @Override
            public Collection<Integer> apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void unsafeSubscribeNull() {
        just1.unsafeSubscribe(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void unsubscribeOnNull() {
        just1.unsubscribeOn(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void windowTimedUnitNull() {
        just1.window(1, null, Schedulers.single());
    }
    
    @Test(expected = NullPointerException.class)
    public void windowSizeTimedUnitNull() {
        just1.window(1, null, Schedulers.single(), 1);
    }

    @Test(expected = NullPointerException.class)
    public void windowTimedSchedulerNull() {
        just1.window(1, TimeUnit.SECONDS, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void windowSizeTimedSchedulerNull() {
        just1.window(1, TimeUnit.SECONDS, null, 1);
    }
    
    @Test(expected = NullPointerException.class)
    public void windowBoundaryNull() {
        just1.window((Publisher<Integer>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void windowOpenCloseOpenNull() {
        just1.window(null, new Function<Object, Publisher<Integer>>() {
            @Override
            public Publisher<Integer> apply(Object v) {
                return just1;
            }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void windowOpenCloseCloseNull() {
        just1.window(just1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void windowOpenCloseCloseReturnsNull() {
        Observable.never().window(just1, new Function<Integer, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(Integer v) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void windowBoundarySupplierNull() {
        just1.window((Supplier<Publisher<Integer>>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void windowBoundarySupplierReturnsNull() {
        just1.window(new Supplier<Publisher<Object>>() {
            @Override
            public Publisher<Object> get() {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void withLatestFromOtherNull() {
        just1.withLatestFrom(null, new BiFunction<Integer, Object, Object>() {
            @Override
            public Object apply(Integer a, Object b) {
                return 1;
            }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void withLatestFromCombinerNull() {
        just1.withLatestFrom(just1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void withLatestFromCombinerReturnsNull() {
        just1.withLatestFrom(just1, new BiFunction<Integer, Integer, Object>() {
            @Override
            public Object apply(Integer a, Integer b) {
                return null;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void zipWithIterableNull() {
        just1.zipWith((Iterable<Integer>)null, new BiFunction<Integer, Integer, Object>() {
            @Override
            public Object apply(Integer a, Integer b) {
                return 1;
            }
        });
    }

    @Test(expected = NullPointerException.class)
    public void zipWithIterableCombinerNull() {
        just1.zipWith(Arrays.asList(1), null);
    }

    @Test(expected = NullPointerException.class)
    public void zipWithIterableCombinerReturnsNull() {
        just1.zipWith(Arrays.asList(1), new BiFunction<Integer, Integer, Object>() {
            @Override
            public Object apply(Integer a, Integer b) {
                return null;
            }
        }).toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void zipWithIterableIteratorNull() {
        just1.zipWith(new Iterable<Object>() {
            @Override
            public Iterator<Object> iterator() {
                return null;
            }
        }, new BiFunction<Integer, Object, Object>() {
            @Override
            public Object apply(Integer a, Object b) {
                return 1;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void zipWithIterableOneIsNull() {
        Observable.just(1, 2).zipWith(Arrays.asList(1, null), new BiFunction<Integer, Integer, Object>() {
            @Override
            public Object apply(Integer a, Integer b) {
                return 1;
            }
        }).toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void zipWithPublisherNull() {
        just1.zipWith((Publisher<Integer>)null, new BiFunction<Integer, Integer, Object>() {
            @Override
            public Object apply(Integer a, Integer b) {
                return 1;
            }
        });
    }


    @Test(expected = NullPointerException.class)
    public void zipWithCombinerNull() {
        just1.zipWith(just1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void zipWithCombinerReturnsNull() {
        just1.zipWith(just1, new BiFunction<Integer, Integer, Object>() {
            @Override
            public Object apply(Integer a, Integer b) {
                return null;
            }
        }).toBlocking().run();
    }

    //*********************************************
    // Subject null tests
    //*********************************************
    
    @Test(expected = NullPointerException.class)
    public void asyncSubjectOnNextNull() {
        Subject<Integer, Integer> subject = AsyncSubject.create();
        subject.onNext(null);
        subject.toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void asyncSubjectOnErrorNull() {
        Subject<Integer, Integer> subject = AsyncSubject.create();
        subject.onError(null);
        subject.toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void behaviorSubjectOnNextNull() {
        Subject<Integer, Integer> subject = BehaviorSubject.create();
        subject.onNext(null);
        subject.toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void behaviorSubjectOnErrorNull() {
        Subject<Integer, Integer> subject = BehaviorSubject.create();
        subject.onError(null);
        subject.toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void publishSubjectOnNextNull() {
        Subject<Integer, Integer> subject = PublishSubject.create();
        subject.onNext(null);
        subject.toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void publishSubjectOnErrorNull() {
        Subject<Integer, Integer> subject = PublishSubject.create();
        subject.onError(null);
        subject.toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void replaycSubjectOnNextNull() {
        Subject<Integer, Integer> subject = ReplaySubject.create();
        subject.onNext(null);
        subject.toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void replaySubjectOnErrorNull() {
        Subject<Integer, Integer> subject = ReplaySubject.create();
        subject.onError(null);
        subject.toBlocking().run();
    }

    @Test(expected = NullPointerException.class)
    public void serializedcSubjectOnNextNull() {
        Subject<Integer, Integer> subject = PublishSubject.<Integer>create().toSerialized();
        subject.onNext(null);
        subject.toBlocking().run();
    }
    
    @Test(expected = NullPointerException.class)
    public void serializedSubjectOnErrorNull() {
        Subject<Integer, Integer> subject = PublishSubject.<Integer>create().toSerialized();
        subject.onError(null);
        subject.toBlocking().run();
    }

}