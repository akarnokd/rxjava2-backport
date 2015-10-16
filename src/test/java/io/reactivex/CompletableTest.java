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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.junit.*;
import org.reactivestreams.*;

import io.reactivex.Completable.*;
import io.reactivex.NbpObservable.NbpSubscriber;
import io.reactivex.disposables.*;
import io.reactivex.exceptions.*;
import io.reactivex.functions.*;
import io.reactivex.internal.disposables.EmptyDisposable;
import io.reactivex.internal.functions.Functions;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.*;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subscribers.TestSubscriber;
import io.reactivex.subscribers.nbp.NbpTestSubscriber;

/**
 * Test Completable methods and operators.
 */
public class CompletableTest {
    /**
     * Iterable that returns an Iterator that throws in its hasNext method.
     */
    static final class IterableIteratorNextThrows implements Iterable<Completable> {
        @Override
        public Iterator<Completable> iterator() {
            return new Iterator<Completable>() {
                @Override
                public boolean hasNext() {
                    return true;
                }
                
                @Override
                public Completable next() {
                    throw new TestException();
                }
                
                @Override
                public void remove() {
                    // TODO Auto-generated method stub
                    
                }
            };
        }
    }

    /**
     * Iterable that returns an Iterator that throws in its next method.
     */
    static final class IterableIteratorHasNextThrows implements Iterable<Completable> {
        @Override
        public Iterator<Completable> iterator() {
            return new Iterator<Completable>() {
                @Override
                public boolean hasNext() {
                    throw new TestException();
                }
                
                @Override
                public Completable next() {
                    return null;
                }
                
                @Override
                public void remove() {
                    // TODO Auto-generated method stub
                    
                }
            };
        }
    }

    /**
     * A class containing a completable instance and counts the number of subscribers.
     */
    static final class NormalCompletable extends AtomicInteger {
        /** */
        private static final long serialVersionUID = 7192337844700923752L;
        
        public final Completable completable = Completable.create(new CompletableOnSubscribe() {
            @Override
            public void accept(CompletableSubscriber s) {
                getAndIncrement();
                s.onSubscribe(EmptyDisposable.INSTANCE);
                s.onComplete();
            }
        });
        
        /**
         * Asserts the given number of subscriptions happened.
         * @param n the expected number of subscriptions
         */
        public void assertSubscriptions(int n) {
            Assert.assertEquals(n, get());
        }
    }

    /**
     * A class containing a completable instance that emits a TestException and counts
     * the number of subscribers.
     */
    static final class ErrorCompletable extends AtomicInteger {
        /** */
        private static final long serialVersionUID = 7192337844700923752L;
        
        public final Completable completable = Completable.create(new CompletableOnSubscribe() {
            @Override
            public void accept(CompletableSubscriber s) {
                getAndIncrement();
                s.onSubscribe(EmptyDisposable.INSTANCE);
                s.onError(new TestException());
            }
        });
        
        /**
         * Asserts the given number of subscriptions happened.
         * @param n the expected number of subscriptions
         */
        public void assertSubscriptions(int n) {
            Assert.assertEquals(n, get());
        }
    }

    /** A normal Completable object. */
    final NormalCompletable normal = new NormalCompletable();

    /** An error Completable object. */
    final ErrorCompletable error = new ErrorCompletable();

    @Test(timeout = 1000)
    public void complete() {
        Completable c = Completable.complete();
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void concatNull() {
        Completable.concat((Completable[])null);
    }
    
    @Test(timeout = 1000)
    public void concatEmpty() {
        Completable c = Completable.concat();
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void concatSingleSource() {
        Completable c = Completable.concat(normal.completable);
        
        c.await();
        
        normal.assertSubscriptions(1);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void concatSingleSourceThrows() {
        Completable c = Completable.concat(error.completable);
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void concatMultipleSources() {
        Completable c = Completable.concat(normal.completable, normal.completable, normal.completable);
        
        c.await();
        
        normal.assertSubscriptions(3);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void concatMultipleOneThrows() {
        Completable c = Completable.concat(normal.completable, error.completable, normal.completable);
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void concatMultipleOneIsNull() {
        Completable c = Completable.concat(normal.completable, null);
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void concatIterableEmpty() {
        Completable c = Completable.concat(Collections.<Completable>emptyList());
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void concatIterableNull() {
        Completable.concat((Iterable<Completable>)null);
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void concatIterableIteratorNull() {
        Completable c = Completable.concat(new Iterable<Completable>() {
            @Override
            public Iterator<Completable> iterator() {
                return null;
            }
        });
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void concatIterableWithNull() {
        Completable c = Completable.concat(Arrays.asList(normal.completable, (Completable)null));
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void concatIterableSingle() {
        Completable c = Completable.concat(Collections.singleton(normal.completable));
        
        c.await();
        
        normal.assertSubscriptions(1);
    }
    
    @Test(timeout = 1000)
    public void concatIterableMany() {
        Completable c = Completable.concat(Arrays.asList(normal.completable, normal.completable, normal.completable));
        
        c.await();
        
        normal.assertSubscriptions(3);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void concatIterableOneThrows() {
        Completable c = Completable.concat(Collections.singleton(error.completable));
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void concatIterableManyOneThrows() {
        Completable c = Completable.concat(Arrays.asList(normal.completable, error.completable));
        
        c.await();
    }
    
    @Test(expected = TestException.class)
    public void concatIterableIterableThrows() {
        Completable c = Completable.concat(new Iterable<Completable>() {
            @Override
            public Iterator<Completable> iterator() {
                throw new TestException();
            }
        });
        
        c.await();
    }
    
    @Test(expected = TestException.class)
    public void concatIterableIteratorHasNextThrows() {
        Completable c = Completable.concat(new IterableIteratorHasNextThrows());
        
        c.await();
    }
    
    @Test(expected = TestException.class)
    public void concatIterableIteratorNextThrows() {
        Completable c = Completable.concat(new IterableIteratorNextThrows());
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void concatObservableEmpty() {
        Completable c = Completable.concat(Observable.<Completable>empty());
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void concatObservableError() {
        Completable c = Completable.concat(Observable.<Completable>error(new Supplier<Throwable>() {
            @Override
            public Throwable get() {
                return new TestException();
            }
        }));
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void concatObservableSingle() {
        Completable c = Completable.concat(Observable.just(normal.completable));
        
        c.await();
        
        normal.assertSubscriptions(1);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void concatObservableSingleThrows() {
        Completable c = Completable.concat(Observable.just(error.completable));
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void concatObservableMany() {
        Completable c = Completable.concat(Observable.just(normal.completable).repeat(3));
        
        c.await();
        
        normal.assertSubscriptions(3);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void concatObservableManyOneThrows() {
        Completable c = Completable.concat(Observable.just(normal.completable, error.completable));
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void concatObservablePrefetch() {
        final List<Long> requested = new ArrayList<Long>();
        Observable<Completable> cs = Observable
                .just(normal.completable)
                .repeat(10)
                .doOnRequest(new LongConsumer() {
                    @Override
                    public void accept(long v) {
                        requested.add(v);
                    }
                });
        
        Completable c = Completable.concat(cs, 5);
        
        c.await();
        
        // FIXME this request pattern looks odd because all 10 completions trigger 1 requests
        Assert.assertEquals(Arrays.asList(5L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L), requested);
    }
    
    @Test(expected = NullPointerException.class)
    public void createNull() {
        Completable.create(null);
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void createOnSubscribeThrowsNPE() {
        Completable c = Completable.create(new CompletableOnSubscribe() {
            @Override
            public void accept(CompletableSubscriber s) { throw new NullPointerException(); }
        });
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void createOnSubscribeThrowsRuntimeException() {
        try {
            Completable c = Completable.create(new CompletableOnSubscribe() {
                @Override
                public void accept(CompletableSubscriber s) {
                    throw new TestException();
                }
            });
            
            c.await();
            
            Assert.fail("Did not throw exception");
        } catch (NullPointerException ex) {
            if (!(ex.getCause() instanceof TestException)) {
                ex.printStackTrace();
                Assert.fail("Did not wrap the TestException but it returned: " + ex);
            }
        }
    }
    
    @Test(timeout = 1000)
    public void defer() {
        Completable c = Completable.defer(new Supplier<Completable>() {
            @Override
            public Completable get() {
                return normal.completable;
            }
        });
        
        normal.assertSubscriptions(0);
        
        c.await();
        
        normal.assertSubscriptions(1);
    }
    
    @Test(expected = NullPointerException.class)
    public void deferNull() {
        Completable.defer(null);
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void deferReturnsNull() {
        Completable c = Completable.defer(new Supplier<Completable>() {
            @Override
            public Completable get() {
                return null;
            }
        });
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void deferFunctionThrows() {
        Completable c = Completable.defer(new Supplier<Completable>() {
            @Override
            public Completable get() { throw new TestException(); }
        });
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void deferErrorSource() {
        Completable c = Completable.defer(new Supplier<Completable>() {
            @Override
            public Completable get() {
                return error.completable;
            }
        });
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void errorSupplierNull() {
        Completable.error((Supplier<Throwable>)null);
    }

    @Test(timeout = 1000, expected = TestException.class)
    public void errorSupplierNormal() {
        Completable c = Completable.error(new Supplier<Throwable>() {
            @Override
            public Throwable get() {
                return new TestException();
            }
        });
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void errorSupplierReturnsNull() {
        Completable c = Completable.error(new Supplier<Throwable>() {
            @Override
            public Throwable get() {
                return null;
            }
        });
        
        c.await();
    }

    @Test(timeout = 1000, expected = TestException.class)
    public void errorSupplierThrows() {
        Completable c = Completable.error(new Supplier<Throwable>() {
            @Override
            public Throwable get() { throw new TestException(); }
        });
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void errorNull() {
        Completable.error((Throwable)null);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void errorNormal() {
        Completable c = Completable.error(new TestException());
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void fromCallableNull() {
        Completable.fromCallable(null);
    }
    
    @Test(timeout = 1000)
    public void fromCallableNormal() {
        final AtomicInteger calls = new AtomicInteger();
        
        Completable c = Completable.fromCallable(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return calls.getAndIncrement();
            }
        });
        
        c.await();
        
        Assert.assertEquals(1, calls.get());
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void fromCallableThrows() {
        Completable c = Completable.fromCallable(new Callable<Object>() {
            @Override
            public Object call() throws Exception { throw new TestException(); }
        });
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void fromFlowableNull() {
        Completable.fromFlowable(null);
    }
    
    @Test(timeout = 1000)
    public void fromFlowableEmpty() {
        Completable c = Completable.fromFlowable(Observable.empty());
        
        c.await();
    }

    @Test(timeout = 5000)
    public void fromFlowableSome() {
        for (int n = 1; n < 10000; n *= 10) {
            Completable c = Completable.fromFlowable(Observable.range(1, n));
            
            c.await();
        }
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void fromFlowableError() {
        Completable c = Completable.fromFlowable(Observable.error(new Supplier<Throwable>() {
            @Override
            public Throwable get() {
                return new TestException();
            }
        }));
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void fromNbpObservableNull() {
        Completable.fromNbpObservable(null);
    }
    
    @Test(timeout = 1000)
    public void fromNbpObservableEmpty() {
        Completable c = Completable.fromNbpObservable(NbpObservable.empty());
        
        c.await();
    }

    @Test(timeout = 5000)
    public void fromNbpObservableSome() {
        for (int n = 1; n < 10000; n *= 10) {
            Completable c = Completable.fromNbpObservable(NbpObservable.range(1, n));
            
            c.await();
        }
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void fromNbpObservableError() {
        Completable c = Completable.fromNbpObservable(NbpObservable.error(new Supplier<Throwable>() {
            @Override
            public Throwable get() {
                return new TestException();
            }
        }));
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void fromRunnableNull() {
        Completable.fromRunnable(null);
    }
    
    @Test(timeout = 1000)
    public void fromRunnableNormal() {
        final AtomicInteger calls = new AtomicInteger();
        
        Completable c = Completable.fromRunnable(new Runnable() {
            @Override
            public void run() {
                calls.getAndIncrement();
            }
        });
        
        c.await();
        
        Assert.assertEquals(1, calls.get());
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void fromRunnableThrows() {
        Completable c = Completable.fromRunnable(new Runnable() {
            @Override
            public void run() { throw new TestException(); }
        });
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void fromSingleNull() {
        Completable.fromSingle(null);
    }
    
    @Test(timeout = 1000)
    public void fromSingleNormal() {
        Completable c = Completable.fromSingle(Single.just(1));
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void fromSingleThrows() {
        Completable c = Completable.fromSingle(Single.error(new Supplier<Throwable>() {
            @Override
            public Throwable get() {
                return new TestException();
            }
        }));
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void mergeNull() {
        Completable.merge((Completable[])null);
    }
    
    @Test(timeout = 1000)
    public void mergeEmpty() {
        Completable c = Completable.merge();
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeSingleSource() {
        Completable c = Completable.merge(normal.completable);
        
        c.await();
        
        normal.assertSubscriptions(1);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void mergeSingleSourceThrows() {
        Completable c = Completable.merge(error.completable);
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeMultipleSources() {
        Completable c = Completable.merge(normal.completable, normal.completable, normal.completable);
        
        c.await();
        
        normal.assertSubscriptions(3);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void mergeMultipleOneThrows() {
        Completable c = Completable.merge(normal.completable, error.completable, normal.completable);
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void mergeMultipleOneIsNull() {
        Completable c = Completable.merge(normal.completable, null);
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeIterableEmpty() {
        Completable c = Completable.merge(Collections.<Completable>emptyList());
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void mergeIterableNull() {
        Completable.merge((Iterable<Completable>)null);
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void mergeIterableIteratorNull() {
        Completable c = Completable.merge(new Iterable<Completable>() {
            @Override
            public Iterator<Completable> iterator() {
                return null;
            }
        });
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void mergeIterableWithNull() {
        Completable c = Completable.merge(Arrays.asList(normal.completable, (Completable)null));
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeIterableSingle() {
        Completable c = Completable.merge(Collections.singleton(normal.completable));
        
        c.await();
        
        normal.assertSubscriptions(1);
    }
    
    @Test(timeout = 1000)
    public void mergeIterableMany() {
        Completable c = Completable.merge(Arrays.asList(normal.completable, normal.completable, normal.completable));
        
        c.await();
        
        normal.assertSubscriptions(3);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void mergeIterableOneThrows() {
        Completable c = Completable.merge(Collections.singleton(error.completable));
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void mergeIterableManyOneThrows() {
        Completable c = Completable.merge(Arrays.asList(normal.completable, error.completable));
        
        c.await();
    }
    
    @Test(expected = TestException.class)
    public void mergeIterableIterableThrows() {
        Completable c = Completable.merge(new Iterable<Completable>() {
            @Override
            public Iterator<Completable> iterator() {
                throw new TestException();
            }
        });
        
        c.await();
    }
    
    @Test(expected = TestException.class)
    public void mergeIterableIteratorHasNextThrows() {
        Completable c = Completable.merge(new IterableIteratorHasNextThrows());
        
        c.await();
    }
    
    @Test(expected = TestException.class)
    public void mergeIterableIteratorNextThrows() {
        Completable c = Completable.merge(new IterableIteratorNextThrows());
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeObservableEmpty() {
        Completable c = Completable.merge(Observable.<Completable>empty());
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void mergeObservableError() {
        Completable c = Completable.merge(Observable.<Completable>error(new Supplier<Throwable>() {
            @Override
            public Throwable get() {
                return new TestException();
            }
        }));
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeObservableSingle() {
        Completable c = Completable.merge(Observable.just(normal.completable));
        
        c.await();
        
        normal.assertSubscriptions(1);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void mergeObservableSingleThrows() {
        Completable c = Completable.merge(Observable.just(error.completable));
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeObservableMany() {
        Completable c = Completable.merge(Observable.just(normal.completable).repeat(3));
        
        c.await();
        
        normal.assertSubscriptions(3);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void mergeObservableManyOneThrows() {
        Completable c = Completable.merge(Observable.just(normal.completable, error.completable));
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeObservableMaxConcurrent() {
        final List<Long> requested = new ArrayList<Long>();
        Observable<Completable> cs = Observable
                .just(normal.completable)
                .repeat(10)
                .doOnRequest(new LongConsumer() {
                    @Override
                    public void accept(long v) {
                        requested.add(v);
                    }
                });
        
        Completable c = Completable.merge(cs, 5);
        
        c.await();
        
        // FIXME this request pattern looks odd because all 10 completions trigger 1 requests
        Assert.assertEquals(Arrays.asList(5L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L), requested);
    }

    @Test(expected = NullPointerException.class)
    public void mergeDelayErrorNull() {
        Completable.mergeDelayError((Completable[])null);
    }
    
    @Test(timeout = 1000)
    public void mergeDelayErrorEmpty() {
        Completable c = Completable.mergeDelayError();
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeDelayErrorSingleSource() {
        Completable c = Completable.mergeDelayError(normal.completable);
        
        c.await();
        
        normal.assertSubscriptions(1);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void mergeDelayErrorSingleSourceThrows() {
        Completable c = Completable.mergeDelayError(error.completable);
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeDelayErrorMultipleSources() {
        Completable c = Completable.mergeDelayError(normal.completable, normal.completable, normal.completable);
        
        c.await();
        
        normal.assertSubscriptions(3);
    }
    
    @Test(timeout = 1000)
    public void mergeDelayErrorMultipleOneThrows() {
        Completable c = Completable.mergeDelayError(normal.completable, error.completable, normal.completable);
        
        try {
            c.await();
        } catch (TestException ex) {
            normal.assertSubscriptions(2);
        }
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void mergeDelayErrorMultipleOneIsNull() {
        Completable c = Completable.mergeDelayError(normal.completable, null);
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeDelayErrorIterableEmpty() {
        Completable c = Completable.mergeDelayError(Collections.<Completable>emptyList());
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void mergeDelayErrorIterableNull() {
        Completable.mergeDelayError((Iterable<Completable>)null);
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void mergeDelayErrorIterableIteratorNull() {
        Completable c = Completable.mergeDelayError(new Iterable<Completable>() {
            @Override
            public Iterator<Completable> iterator() {
                return null;
            }
        });
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void mergeDelayErrorIterableWithNull() {
        Completable c = Completable.mergeDelayError(Arrays.asList(normal.completable, (Completable)null));
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeDelayErrorIterableSingle() {
        Completable c = Completable.mergeDelayError(Collections.singleton(normal.completable));
        
        c.await();
        
        normal.assertSubscriptions(1);
    }
    
    @Test(timeout = 1000)
    public void mergeDelayErrorIterableMany() {
        Completable c = Completable.mergeDelayError(Arrays.asList(normal.completable, normal.completable, normal.completable));
        
        c.await();
        
        normal.assertSubscriptions(3);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void mergeDelayErrorIterableOneThrows() {
        Completable c = Completable.mergeDelayError(Collections.singleton(error.completable));
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeDelayErrorIterableManyOneThrows() {
        Completable c = Completable.mergeDelayError(Arrays.asList(normal.completable, error.completable, normal.completable));
        
        try {
            c.await();
        } catch (TestException ex) {
            normal.assertSubscriptions(2);
        }
    }
    
    @Test(expected = TestException.class)
    public void mergeDelayErrorIterableIterableThrows() {
        Completable c = Completable.mergeDelayError(new Iterable<Completable>() {
            @Override
            public Iterator<Completable> iterator() {
                throw new TestException();
            }
        });
        
        c.await();
    }
    
    @Test(expected = TestException.class)
    public void mergeDelayErrorIterableIteratorHasNextThrows() {
        Completable c = Completable.mergeDelayError(new IterableIteratorHasNextThrows());
        
        c.await();
    }
    
    @Test(expected = TestException.class)
    public void mergeDelayErrorIterableIteratorNextThrows() {
        Completable c = Completable.mergeDelayError(new IterableIteratorNextThrows());
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeDelayErrorObservableEmpty() {
        Completable c = Completable.mergeDelayError(Observable.<Completable>empty());
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void mergeDelayErrorObservableError() {
        Completable c = Completable.mergeDelayError(Observable.<Completable>error(new Supplier<Throwable>() {
            @Override
            public Throwable get() {
                return new TestException();
            }
        }));
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeDelayErrorObservableSingle() {
        Completable c = Completable.mergeDelayError(Observable.just(normal.completable));
        
        c.await();
        
        normal.assertSubscriptions(1);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void mergeDelayErrorObservableSingleThrows() {
        Completable c = Completable.mergeDelayError(Observable.just(error.completable));
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeDelayErrorObservableMany() {
        Completable c = Completable.mergeDelayError(Observable.just(normal.completable).repeat(3));
        
        c.await();
        
        normal.assertSubscriptions(3);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void mergeDelayErrorObservableManyOneThrows() {
        Completable c = Completable.mergeDelayError(Observable.just(normal.completable, error.completable));
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void mergeDelayErrorObservableMaxConcurrent() {
        final List<Long> requested = new ArrayList<Long>();
        Observable<Completable> cs = Observable
                .just(normal.completable)
                .repeat(10)
                .doOnRequest(new LongConsumer() {
                    @Override
                    public void accept(long v) {
                        requested.add(v);
                    }
                });
        
        Completable c = Completable.mergeDelayError(cs, 5);
        
        c.await();
        
        // FIXME this request pattern looks odd because all 10 completions trigger 1 requests
        Assert.assertEquals(Arrays.asList(5L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L), requested);
    }

    @Test(timeout = 1000)
    public void never() {
        final AtomicBoolean onSubscribeCalled = new AtomicBoolean();
        final AtomicInteger calls = new AtomicInteger();
        Completable.never().subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(Disposable d) {
                onSubscribeCalled.set(true);
            }
            
            @Override
            public void onError(Throwable e) {
                calls.getAndIncrement();
            }
            
            @Override
            public void onComplete() {
                calls.getAndIncrement();
            }
        });
        
        Assert.assertTrue("onSubscribe not called", onSubscribeCalled.get());
        Assert.assertEquals("There were calls to onXXX methods", 0, calls.get());
    }
    
    @Test(timeout = 1500)
    public void timer() {
        Completable c = Completable.timer(500, TimeUnit.MILLISECONDS);
        
        c.await();
    }
    
    @Test(timeout = 1500)
    public void timerNewThread() {
        Completable c = Completable.timer(500, TimeUnit.MILLISECONDS, Schedulers.newThread());
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void timerTestScheduler() {
        TestScheduler scheduler = Schedulers.test();
        
        Completable c = Completable.timer(250, TimeUnit.MILLISECONDS, scheduler);
        
        final AtomicInteger calls = new AtomicInteger();
        
        c.subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(Disposable d) {
                
            }
            
            @Override
            public void onComplete() {
                calls.getAndIncrement();
            }
            
            @Override
            public void onError(Throwable e) {
                RxJavaPlugins.onError(e);
            }
        });

        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        
        Assert.assertEquals(0, calls.get());
        
        scheduler.advanceTimeBy(200, TimeUnit.MILLISECONDS);
        
        Assert.assertEquals(1, calls.get());
    }
    
    @Test(timeout = 2000)
    public void timerCancel() throws InterruptedException {
        Completable c = Completable.timer(250, TimeUnit.MILLISECONDS);
        
        final MultipleAssignmentDisposable mad = new MultipleAssignmentDisposable();
        final AtomicInteger calls = new AtomicInteger();
        
        c.subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(Disposable d) {
                mad.set(d);
            }
            
            @Override
            public void onError(Throwable e) {
                calls.getAndIncrement();
            }
            
            @Override
            public void onComplete() {
                calls.getAndIncrement();
            }
        });
        
        Thread.sleep(100);
        
        mad.dispose();
        
        Thread.sleep(200);
        
        Assert.assertEquals(0, calls.get());
    }
    
    @Test(expected = NullPointerException.class)
    public void timerUnitNull() {
        Completable.timer(1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void timerSchedulerNull() {
        Completable.timer(1, TimeUnit.SECONDS, null);
    }
    
    @Test(timeout = 1000)
    public void usingNormalEager() {
        final AtomicInteger dispose = new AtomicInteger();
        
        Completable c = Completable.using(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return 1;
            }
        }, new Function<Object, Completable>() {
            @Override
            public Completable apply(Object v) {
                return normal.completable;
            }
        }, new Consumer<Integer>() {
            @Override
            public void accept(Integer d) {
                dispose.set(d);
            }
        });
        
        final AtomicBoolean disposedFirst = new AtomicBoolean();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        
        c.subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(Disposable d) {
                
            }
            
            @Override
            public void onError(Throwable e) {
                error.lazySet(e);
            }
            
            @Override
            public void onComplete() {
                disposedFirst.set(dispose.get() != 0);
            }
        });
        
        Assert.assertEquals(1, dispose.get());
        Assert.assertTrue("Not disposed first", disposedFirst.get());
        Assert.assertNull(error.get());
    }
    
    @Test(timeout = 1000)
    public void usingNormalLazy() {
        final AtomicInteger dispose = new AtomicInteger();
        
        Completable c = Completable.using(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return 1;
            }
        }, new Function<Integer, Completable>() {
            @Override
            public Completable apply(Integer v) {
                return normal.completable;
            }
        }, new Consumer<Integer>() {
            @Override
            public void accept(Integer d) {
                dispose.set(d);
            }
        }, false);
        
        final AtomicBoolean disposedFirst = new AtomicBoolean();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        
        c.subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(Disposable d) {
                
            }
            
            @Override
            public void onError(Throwable e) {
                error.lazySet(e);
            }
            
            @Override
            public void onComplete() {
                disposedFirst.set(dispose.get() != 0);
            }
        });
        
        Assert.assertEquals(1, dispose.get());
        Assert.assertFalse("Disposed first", disposedFirst.get());
        Assert.assertNull(error.get());
    }
    
    @Test(timeout = 1000)
    public void usingErrorEager() {
        final AtomicInteger dispose = new AtomicInteger();
        
        Completable c = Completable.using(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return 1;
            }
        }, new Function<Integer, Completable>() {
            @Override
            public Completable apply(Integer v) {
                return error.completable;
            }
        }, new Consumer<Integer>() {
            @Override
            public void accept(Integer d) {
                dispose.set(d);
            }
        });
        
        final AtomicBoolean disposedFirst = new AtomicBoolean();
        final AtomicBoolean complete = new AtomicBoolean();
        
        c.subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(Disposable d) {
                
            }
            
            @Override
            public void onError(Throwable e) {
                disposedFirst.set(dispose.get() != 0);
            }
            
            @Override
            public void onComplete() {
                complete.set(true);
            }
        });
        
        Assert.assertEquals(1, dispose.get());
        Assert.assertTrue("Not disposed first", disposedFirst.get());
        Assert.assertFalse(complete.get());
    }
    
    @Test(timeout = 1000)
    public void usingErrorLazy() {
        final AtomicInteger dispose = new AtomicInteger();
        
        Completable c = Completable.using(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return 1;
            }
        }, new Function<Integer, Completable>() {
            @Override
            public Completable apply(Integer v) {
                return error.completable;
            }
        }, new Consumer<Integer>() {
            @Override
            public void accept(Integer d) {
                dispose.set(d);
            }
        }, false);
        
        final AtomicBoolean disposedFirst = new AtomicBoolean();
        final AtomicBoolean complete = new AtomicBoolean();
        
        c.subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(Disposable d) {
                
            }
            
            @Override
            public void onError(Throwable e) {
                disposedFirst.set(dispose.get() != 0);
            }
            
            @Override
            public void onComplete() {
                complete.set(true);
            }
        });
        
        Assert.assertEquals(1, dispose.get());
        Assert.assertFalse("Disposed first", disposedFirst.get());
        Assert.assertFalse(complete.get());
    }
    
    @Test(expected = NullPointerException.class)
    public void usingResourceSupplierNull() {
        Completable.using(null, new Function<Object, Completable>() {
            @Override
            public Completable apply(Object v) {
                return normal.completable;
            }
        }, new Consumer<Object>() {
            @Override
            public void accept(Object v) { }
        });
    }

    @Test(expected = NullPointerException.class)
    public void usingMapperNull() {
        Completable.using(new Supplier<Object>() {
            @Override
            public Object get() {
                return 1;
            }
        }, null, new Consumer<Object>() {
            @Override
            public void accept(Object v) { }
        });
    }

    @Test(expected = NullPointerException.class)
    public void usingMapperReturnsNull() {
        Completable c = Completable.using(new Supplier<Object>() {
            @Override
            public Object get() {
                return 1;
            }
        }, new Function<Object, Completable>() {
            @Override
            public Completable apply(Object v) {
                return null;
            }
        }, new Consumer<Object>() {
            @Override
            public void accept(Object v) { }
        });
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void usingDisposeNull() {
        Completable.using(new Supplier<Object>() {
            @Override
            public Object get() {
                return 1;
            }
        }, new Function<Object, Completable>() {
            @Override
            public Completable apply(Object v) {
                return normal.completable;
            }
        }, null);
    }
    
    @Test(expected = TestException.class)
    public void usingResourceThrows() {
        Completable c = Completable.using(new Supplier<Object>() {
            @Override
            public Object get() { throw new TestException(); }
        }, 
                new Function<Object, Completable>() {
                    @Override
                    public Completable apply(Object v) {
                        return normal.completable;
                    }
                }, new Consumer<Object>() {
                    @Override
                    public void accept(Object v) { }
                });
        
        c.await();
    }
    
    @Test(expected = TestException.class)
    public void usingMapperThrows() {
        Completable c = Completable.using(new Supplier<Object>() {
            @Override
            public Object get() {
                return 1;
            }
        }, 
                new Function<Object, Completable>() {
                    @Override
                    public Completable apply(Object v) { throw new TestException(); }
                }, new Consumer<Object>() {
                    @Override
                    public void accept(Object v) { }
                });
        
        c.await();
    }
    
    @Test(expected = TestException.class)
    public void usingDisposerThrows() {
        Completable c = Completable.using(new Supplier<Object>() {
            @Override
            public Object get() {
                return 1;
            }
        }, 
                new Function<Object, Completable>() {
                    @Override
                    public Completable apply(Object v) {
                        return normal.completable;
                    }
                }, new Consumer<Object>() {
                    @Override
                    public void accept(Object v) { throw new TestException(); }
                });
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void composeNormal() {
        Completable c = error.completable.compose(new CompletableTransformer() {
            @Override
            public Completable apply(Completable n) {
                return n.onErrorComplete();
            }
        });
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void composeNull() {
        error.completable.compose(null);
    }
    
    @Test(timeout = 1000)
    public void concatWithNormal() {
        Completable c = normal.completable.concatWith(normal.completable);
        
        c.await();
        
        normal.assertSubscriptions(2);
    }

    @Test(timeout = 1000, expected = TestException.class)
    public void concatWithError() {
        Completable c = normal.completable.concatWith(error.completable);
        
        c.await();
    }

    @Test(expected = NullPointerException.class)
    public void concatWithNull() {
        normal.completable.concatWith(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void delayUnitNull() {
        normal.completable.delay(1, null);
    }

    @Test(expected = NullPointerException.class)
    public void delaySchedulerNull() {
        normal.completable.delay(1, TimeUnit.SECONDS, null);
    }
    
    @Test(timeout = 1000)
    public void delayNormal() throws InterruptedException {
        Completable c = normal.completable.delay(250, TimeUnit.MILLISECONDS);
        
        final AtomicBoolean done = new AtomicBoolean();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        
        c.subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(Disposable d) {
                
            }
            
            @Override
            public void onError(Throwable e) {
                error.set(e);
            }
            
            @Override
            public void onComplete() {
                done.set(true);
            }
        });
        
        Thread.sleep(100);
        
        Assert.assertFalse("Already done", done.get());
        
        Thread.sleep(200);
        
        Assert.assertTrue("Not done", done.get());
        
        Assert.assertNull(error.get());
    }
    
    @Test(timeout = 1000)
    public void delayErrorImmediately() throws InterruptedException {
        Completable c = error.completable.delay(250, TimeUnit.MILLISECONDS);
        
        final AtomicBoolean done = new AtomicBoolean();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        
        c.subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(Disposable d) {
                
            }
            
            @Override
            public void onError(Throwable e) {
                error.set(e);
            }
            
            @Override
            public void onComplete() {
                done.set(true);
            }
        });

        Assert.assertTrue(error.get().toString(), error.get() instanceof TestException);
        Assert.assertFalse("Already done", done.get());

        Thread.sleep(100);
        
        Assert.assertFalse("Already done", done.get());
        
        Thread.sleep(200);
    }
    
    @Test(timeout = 1000)
    public void delayErrorToo() throws InterruptedException {
        Completable c = error.completable.delay(250, TimeUnit.MILLISECONDS, Schedulers.computation(), true);
        
        final AtomicBoolean done = new AtomicBoolean();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        
        c.subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(Disposable d) {
                
            }
            
            @Override
            public void onError(Throwable e) {
                error.set(e);
            }
            
            @Override
            public void onComplete() {
                done.set(true);
            }
        });
        
        Thread.sleep(100);
        
        Assert.assertFalse("Already done", done.get());
        Assert.assertNull(error.get());
        
        Thread.sleep(200);
        
        Assert.assertFalse("Already done", done.get());
        Assert.assertTrue(error.get() instanceof TestException);
    }
    
    @Test(timeout = 1000)
    public void doOnCompleteNormal() {
        final AtomicInteger calls = new AtomicInteger();
        
        Completable c = normal.completable.doOnComplete(new Runnable() {
            @Override
            public void run() {
                calls.getAndIncrement();
            }
        });
        
        c.await();
        
        Assert.assertEquals(1, calls.get());
    }
    
    @Test(timeout = 1000)
    public void doOnCompleteError() {
        final AtomicInteger calls = new AtomicInteger();
        
        Completable c = error.completable.doOnComplete(new Runnable() {
            @Override
            public void run() {
                calls.getAndIncrement();
            }
        });
        
        try {
            c.await();
            Assert.fail("Failed to throw TestException");
        } catch (TestException ex) {
            // expected
        }
        
        Assert.assertEquals(0, calls.get());
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnCompleteNull() {
        normal.completable.doOnComplete(null);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void doOnCompleteThrows() {
        Completable c = normal.completable.doOnComplete(new Runnable() {
            @Override
            public void run() { throw new TestException(); }
        });
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void doOnDisposeNormalDoesntCall() {
        final AtomicInteger calls = new AtomicInteger();
        
        Completable c = normal.completable.doOnDispose(new Runnable() {
            @Override
            public void run() {
                calls.getAndIncrement();
            }
        });
        
        c.await();
        
        Assert.assertEquals(0, calls.get());
    }
    
    @Test(timeout = 1000)
    public void doOnDisposeErrorDoesntCall() {
        final AtomicInteger calls = new AtomicInteger();
        
        Completable c = error.completable.doOnDispose(new Runnable() {
            @Override
            public void run() {
                calls.getAndIncrement();
            }
        });
        
        try {
            c.await();
            Assert.fail("No exception thrown");
        } catch (TestException ex) {
            // expected
        }
        Assert.assertEquals(0, calls.get());
    }
    
    @Test(timeout = 1000)
    public void doOnDisposeChildCancels() {
        final AtomicInteger calls = new AtomicInteger();
        
        Completable c = normal.completable.doOnDispose(new Runnable() {
            @Override
            public void run() {
                calls.getAndIncrement();
            }
        });
        
        c.subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(Disposable d) {
                d.dispose();
            }
            
            @Override
            public void onError(Throwable e) {
                // ignored
            }
            
            @Override
            public void onComplete() {
                // ignored
            }
        });
        
        Assert.assertEquals(1, calls.get());
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnDisposeNull() {
        normal.completable.doOnDispose(null);
    }
    
    @Test(timeout = 1000)
    public void doOnDisposeThrows() {
        Completable c = normal.completable.doOnDispose(new Runnable() {
            @Override
            public void run() { throw new TestException(); }
        });
        
        c.subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(Disposable d) {
                d.dispose();
            }
            
            @Override
            public void onError(Throwable e) {
                // ignored
            }
            
            @Override
            public void onComplete() {
                // ignored
            }
        });
    }
    
    @Test(timeout = 1000)
    public void doOnErrorNoError() {
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        
        Completable c = normal.completable.doOnError(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) {
                error.set(e);
            }
        });
        
        c.await();
        
        Assert.assertNull(error.get());
    }
    
    @Test(timeout = 1000)
    public void doOnErrorHasError() {
        final AtomicReference<Throwable> err = new AtomicReference<Throwable>();
        
        Completable c = error.completable.doOnError(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) {
                err.set(e);
            }
        });
        
        try {
            c.await();
            Assert.fail("Did not throw exception");
        } catch (Throwable e) {
            // expected
        }
        
        Assert.assertTrue(err.get() instanceof TestException);
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnErrorNull() {
        normal.completable.doOnError(null);
    }
    
    @Test(timeout = 1000)
    public void doOnErrorThrows() {
        Completable c = error.completable.doOnError(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) { throw new IllegalStateException(); }
        });
        
        try {
            c.await();
        } catch (CompositeException ex) {
            List<Throwable> a = ex.getExceptions();
            Assert.assertEquals(2, a.size());
            Assert.assertTrue(a.get(0) instanceof IllegalStateException);
            Assert.assertTrue(a.get(1) instanceof TestException);
        }
    }
    
    @Test(timeout = 1000)
    public void doOnSubscribeNormal() {
        final AtomicInteger calls = new AtomicInteger();
        
        Completable c = normal.completable.doOnSubscribe(new Consumer<Disposable>() {
            @Override
            public void accept(Disposable s) {
                calls.getAndIncrement();
            }
        });
        
        for (int i = 0; i < 10; i++) {
            c.await();
        }
        
        Assert.assertEquals(10, calls.get());
    }
    
    @Test(expected = NullPointerException.class)
    public void doOnSubscribeNull() {
        normal.completable.doOnSubscribe(null);
    }
    
    @Test(expected = TestException.class)
    public void doOnSubscribeThrows() {
        Completable c = normal.completable.doOnSubscribe(new Consumer<Disposable>() {
            @Override
            public void accept(Disposable d) { throw new TestException(); }
        });
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void doOnTerminateNormal() {
        final AtomicInteger calls = new AtomicInteger();
        
        Completable c = normal.completable.doOnTerminate(new Runnable() {
            @Override
            public void run() {
                calls.getAndIncrement();
            }
        });
        
        c.await();
        
        Assert.assertEquals(1, calls.get());
    }
    
    @Test(timeout = 1000)
    public void doOnTerminateError() {
        final AtomicInteger calls = new AtomicInteger();
        
        Completable c = error.completable.doOnTerminate(new Runnable() {
            @Override
            public void run() {
                calls.getAndIncrement();
            }
        });
        
        try {
            c.await();
            Assert.fail("Did dot throw exception");
        } catch (TestException ex) {
            // expected
        }
        
        Assert.assertEquals(1, calls.get());
    }
    
    @Test(timeout = 1000)
    public void finallyDoNormal() {
        final AtomicBoolean doneAfter = new AtomicBoolean();
        final AtomicBoolean complete = new AtomicBoolean();
        
        Completable c = normal.completable.finallyDo(new Runnable() {
            @Override
            public void run() {
                doneAfter.set(complete.get());
            }
        });
        
        c.subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(Disposable d) {
                
            }

            @Override
            public void onError(Throwable e) {
                
            }
            
            @Override
            public void onComplete() {
                complete.set(true);
            }
        });
        
        c.await();
        
        Assert.assertTrue("Not completed", complete.get());
        Assert.assertTrue("Finally called before onComplete", doneAfter.get());
    }
    
    @Test(timeout = 1000)
    public void finallyDoWithError() {
        final AtomicBoolean doneAfter = new AtomicBoolean();
        
        Completable c = error.completable.finallyDo(new Runnable() {
            @Override
            public void run() {
                doneAfter.set(true);
            }
        });
        
        try {
            c.await();
            Assert.fail("Did not throw TestException");
        } catch (TestException ex) {
            // expected
        }
        
        Assert.assertFalse("FinallyDo called", doneAfter.get());
    }
    
    @Test(expected = NullPointerException.class)
    public void finallyDoNull() {
        normal.completable.finallyDo(null);
    }
    
    @Test(timeout = 1000)
    public void getNormal() {
        Assert.assertNull(normal.completable.get());
    }
    
    @Test(timeout = 1000)
    public void getError() {
        Assert.assertTrue(error.completable.get() instanceof TestException);
    }
    
    @Test(timeout = 1000)
    public void getTimeout() {
        try {
            Completable.never().get(100, TimeUnit.MILLISECONDS);
        } catch (RuntimeException ex) {
            if (!(ex.getCause() instanceof TimeoutException)) {
                Assert.fail("Wrong exception cause: " + ex.getCause());
            }
        }
    }
    
    @Test(expected = NullPointerException.class)
    public void getNullUnit() {
        normal.completable.get(1, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void liftNull() {
        normal.completable.lift(null);
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void liftReturnsNull() {
        Completable c = normal.completable.lift(new CompletableOperator() {
            @Override
            public CompletableSubscriber apply(CompletableSubscriber v) {
                return null;
            }
        });
        
        c.await();
    }

    final static class CompletableOperatorSwap implements CompletableOperator {
        @Override
        public CompletableSubscriber apply(final CompletableSubscriber v) {
            return new CompletableSubscriber() {

                @Override
                public void onComplete() {
                    v.onError(new TestException());
                }

                @Override
                public void onError(Throwable e) {
                    v.onComplete();
                }

                @Override
                public void onSubscribe(Disposable d) {
                    v.onSubscribe(d);
                }
                
            };
        }
    }
    @Test(timeout = 1000, expected = TestException.class)
    public void liftOnCompleteError() {
        Completable c = normal.completable.lift(new CompletableOperatorSwap());
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void liftOnErrorComplete() {
        Completable c = error.completable.lift(new CompletableOperatorSwap());
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void mergeWithNull() {
        normal.completable.mergeWith(null);
    }
    
    @Test(timeout = 1000)
    public void mergeWithNormal() {
        Completable c = normal.completable.mergeWith(normal.completable);
        
        c.await();
        
        normal.assertSubscriptions(2);
    }
    
    @Test(expected = NullPointerException.class)
    public void observeOnNull() {
        normal.completable.observeOn(null);
    }
    
    @Test(timeout = 1000)
    public void observeOnNormal() throws InterruptedException {
        final AtomicReference<String> name = new AtomicReference<String>();
        final AtomicReference<Throwable> err = new AtomicReference<Throwable>();
        final CountDownLatch cdl = new CountDownLatch(1);
        
        Completable c = normal.completable.observeOn(Schedulers.computation());
        
        c.subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(Disposable d) {
                
            }
            
            @Override
            public void onComplete() {
                name.set(Thread.currentThread().getName());
                cdl.countDown();
            }
            
            @Override
            public void onError(Throwable e) {
                err.set(e);
                cdl.countDown();
            }
        });
        
        cdl.await();
        
        Assert.assertNull(err.get());
        Assert.assertTrue(name.get().startsWith("RxComputation"));
    }
    
    @Test(timeout = 1000)
    public void observeOnError() throws InterruptedException {
        final AtomicReference<String> name = new AtomicReference<String>();
        final AtomicReference<Throwable> err = new AtomicReference<Throwable>();
        final CountDownLatch cdl = new CountDownLatch(1);
        
        Completable c = error.completable.observeOn(Schedulers.computation());
        
        c.subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(Disposable d) {
                
            }
            
            @Override
            public void onComplete() {
                name.set(Thread.currentThread().getName());
                cdl.countDown();
            }
            
            @Override
            public void onError(Throwable e) {
                name.set(Thread.currentThread().getName());
                err.set(e);
                cdl.countDown();
            }
        });
        
        cdl.await();
        
        Assert.assertTrue(err.get() instanceof TestException);
        Assert.assertTrue(name.get().startsWith("RxComputation"));
    }
    
    @Test(timeout = 1000)
    public void onErrorComplete() {
        Completable c = error.completable.onErrorComplete();
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void onErrorCompleteFalse() {
        Completable c = error.completable.onErrorComplete(new Predicate<Throwable>() {
            @Override
            public boolean test(Throwable e) {
                return e instanceof IllegalStateException;
            }
        });
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void onErrorCompleteNull() {
        error.completable.onErrorComplete(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void onErrorResumeNextNull() {
        error.completable.onErrorResumeNext(null);
    }
    
    @Test(timeout = 1000)
    public void onErrorResumeNextFunctionReturnsNull() {
        Completable c = error.completable.onErrorResumeNext(new Function<Throwable, Completable>() {
            @Override
            public Completable apply(Throwable e) {
                return null;
            }
        });
        
        try {
            c.await();
            Assert.fail("Did not throw an exception");
        } catch (NullPointerException ex) {
            Assert.assertTrue(ex.getCause() instanceof TestException);
        }
    }
    
    @Test(timeout = 1000)
    public void onErrorResumeNextFunctionThrows() {
        Completable c = error.completable.onErrorResumeNext(new Function<Throwable, Completable>() {
            @Override
            public Completable apply(Throwable e) { throw new TestException(); }
        });
        
        try {
            c.await();
            Assert.fail("Did not throw an exception");
        } catch (CompositeException ex) {
            List<Throwable> a = ex.getExceptions();
            
            Assert.assertEquals(2, a.size());
            Assert.assertTrue(a.get(0) instanceof TestException);
            Assert.assertTrue(a.get(1) instanceof TestException);
        }
    }
    
    @Test(timeout = 1000)
    public void onErrorResumeNextNormal() {
        Completable c = error.completable.onErrorResumeNext(new Function<Throwable, Completable>() {
            @Override
            public Completable apply(Throwable v) {
                return normal.completable;
            }
        });
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void onErrorResumeNextError() {
        Completable c = error.completable.onErrorResumeNext(new Function<Throwable, Completable>() {
            @Override
            public Completable apply(Throwable v) {
                return error.completable;
            }
        });
        
        c.await();
    }
    
    @Test(timeout = 2000)
    public void repeatNormal() {
        final AtomicReference<Throwable> err = new AtomicReference<Throwable>();
        final AtomicInteger calls = new AtomicInteger();
        
        Completable c = Completable.fromCallable(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                calls.getAndIncrement();
                Thread.sleep(100);
                return null;
            }
        }).repeat();
        
        c.subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(final Disposable d) {
                Schedulers.single().scheduleDirect(new Runnable() {
                    @Override
                    public void run() {
                        d.dispose();
                    }
                }, 550, TimeUnit.MILLISECONDS);
            }
            
            @Override
            public void onError(Throwable e) {
                err.set(e);
            }
            
            @Override
            public void onComplete() {
                
            }
        });
        
        Assert.assertEquals(6, calls.get());
        Assert.assertNull(err.get());
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void repeatError() {
        Completable c = error.completable.repeat();
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void repeat5Times() {
        final AtomicInteger calls = new AtomicInteger();
        
        Completable c = Completable.fromCallable(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                calls.getAndIncrement();
                return null;
            }
        }).repeat(5);
        
        c.await();
        
        Assert.assertEquals(5, calls.get());
    }
    
    @Test(timeout = 1000)
    public void repeat1Time() {
        final AtomicInteger calls = new AtomicInteger();
        
        Completable c = Completable.fromCallable(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                calls.getAndIncrement();
                return null;
            }
        }).repeat(1);
        
        c.await();
        
        Assert.assertEquals(1, calls.get());
    }
    
    @Test(timeout = 1000)
    public void repeat0Time() {
        final AtomicInteger calls = new AtomicInteger();
        
        Completable c = Completable.fromCallable(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                calls.getAndIncrement();
                return null;
            }
        }).repeat(0);
        
        c.await();
        
        Assert.assertEquals(0, calls.get());
    }
    
    @Test(timeout = 1000)
    public void repeatUntilNormal() {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger times = new AtomicInteger(5);
        
        Completable c = Completable.fromCallable(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                calls.getAndIncrement();
                return null;
            }
        }).repeatUntil(new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return times.decrementAndGet() == 0;
            }
        });
        
        c.await();
        
        Assert.assertEquals(5, calls.get());
    }
    
    @Test(expected = NullPointerException.class)
    public void repeatUntilNull() {
        normal.completable.repeatUntil(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void repeatWhenNull() {
        normal.completable.repeatWhen(null);
    }
    
    @Test(timeout = 1000)
    public void retryNormal() {
        Completable c = normal.completable.retry();
        
        c.await();
        
        normal.assertSubscriptions(1);
    }
    
    @Test(timeout = 1000)
    public void retry5Times() {
        final AtomicInteger calls = new AtomicInteger(5);
        Completable c = Completable.fromRunnable(new Runnable() {
            @Override
            public void run() {
                if (calls.decrementAndGet() != 0) {
                    throw new TestException();
                }
            }
        }).retry();
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void retryBiPredicate5Times() {
        Completable c = error.completable.retry(new BiPredicate<Integer, Throwable>() {
            @Override
            public boolean test(Integer n, Throwable e) {
                return n < 5;
            }
        });
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void retryTimes5Error() {
        Completable c = error.completable.retry(5);
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void retryTimes5Normal() {
        final AtomicInteger calls = new AtomicInteger(5);

        Completable c = Completable.fromRunnable(new Runnable() {
            @Override
            public void run() {
                if (calls.decrementAndGet() != 0) {
                    throw new TestException();
                }
            }
        }).retry(5);
        
        c.await();
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void retryNegativeTimes() {
        normal.completable.retry(-1);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void retryPredicateError() {
        Completable c = error.completable.retry(new Predicate<Throwable>() {
            @Override
            public boolean test(Throwable e) {
                return false;
            }
        });
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void retryPredicateNull() {
        error.completable.retry((Predicate<Throwable>)null);
    }

    @Test(timeout = 1000)
    public void retryPredicate5Times() {
        final AtomicInteger calls = new AtomicInteger(5);

        Completable c = Completable.fromRunnable(new Runnable() {
            @Override
            public void run() {
                if (calls.decrementAndGet() != 0) {
                    throw new TestException();
                }
            }
        }).retry(new Predicate<Throwable>() {
            @Override
            public boolean test(Throwable e) {
                return true;
            }
        });
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void retryWhen5Times() {
        final AtomicInteger calls = new AtomicInteger(5);

        Completable c = Completable.fromRunnable(new Runnable() {
            @Override
            public void run() {
                if (calls.decrementAndGet() != 0) {
                    throw new TestException();
                }
            }
        }).retryWhen(new Function<Observable<? extends Throwable>, Publisher<Object>>() {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            @Override
            public Publisher<Object> apply(Observable<? extends Throwable> o) {
                return (Publisher)o;
            }
        });
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void subscribe() throws InterruptedException {
        final AtomicBoolean complete = new AtomicBoolean();
        
        Completable c = normal.completable
                .delay(100, TimeUnit.MILLISECONDS)
                .doOnComplete(new Runnable() {
                    @Override
                    public void run() {
                        complete.set(true);
                    }
                });
        
        c.subscribe();
        
        Thread.sleep(150);
        
        Assert.assertTrue("Not completed", complete.get());
    }
    
    @Test(timeout = 1000)
    public void subscribeDispose() throws InterruptedException {
        final AtomicBoolean complete = new AtomicBoolean();
        
        Completable c = normal.completable
                .delay(200, TimeUnit.MILLISECONDS)
                .doOnComplete(new Runnable() {
                    @Override
                    public void run() {
                        complete.set(true);
                    }
                });
        
        Disposable d = c.subscribe();
        
        Thread.sleep(100);
        
        d.dispose();
        
        Thread.sleep(150);
        
        Assert.assertFalse("Completed", complete.get());
    }
    
    @Test(timeout = 1000)
    public void subscribeTwoCallbacksNormal() {
        final AtomicReference<Throwable> err = new AtomicReference<Throwable>();
        final AtomicBoolean complete = new AtomicBoolean();
        normal.completable.subscribe(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) {
                err.set(e);
            }
        }, new Runnable() {
            @Override
            public void run() {
                complete.set(true);
            }
        });
        
        Assert.assertNull(err.get());
        Assert.assertTrue("Not completed", complete.get());
    }
    
    @Test(timeout = 1000)
    public void subscribeTwoCallbacksError() {
        final AtomicReference<Throwable> err = new AtomicReference<Throwable>();
        final AtomicBoolean complete = new AtomicBoolean();
        error.completable.subscribe(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) {
                err.set(e);
            }
        }, new Runnable() {
            @Override
            public void run() {
                complete.set(true);
            }
        });
        
        Assert.assertTrue(err.get() instanceof TestException);
        Assert.assertFalse("Not completed", complete.get());
    }
    
    @Test(expected = NullPointerException.class)
    public void subscribeTwoCallbacksFirstNull() {
        normal.completable.subscribe(null, new Runnable() {
            @Override
            public void run() { }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void subscribeTwoCallbacksSecondNull() {
        normal.completable.subscribe(null, new Runnable() {
            @Override
            public void run() { }
        });
    }
    
    @Test(timeout = 1000)
    public void subscribeTwoCallbacksCompleteThrows() {
        final AtomicReference<Throwable> err = new AtomicReference<Throwable>();
        normal.completable.subscribe(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) {
                err.set(e);
            }
        }, new Runnable() {
            @Override
            public void run() { throw new TestException(); }
        });
        
        Assert.assertTrue(String.valueOf(err.get()), err.get() instanceof TestException);
    }
    
    @Test(timeout = 1000)
    public void subscribeTwoCallbacksOnErrorThrows() {
        error.completable.subscribe(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) { throw new TestException(); }
        }, new Runnable() {
            @Override
            public void run() { }
        });
    }
    
    @Test(timeout = 1000)
    public void subscribeNbpSubscriberNormal() {
        NbpTestSubscriber<Object> ts = new NbpTestSubscriber<Object>();
        
        normal.completable.subscribe(ts);
        
        ts.assertComplete();
        ts.assertNoValues();
        ts.assertNoErrors();
    }

    @Test(timeout = 1000)
    public void subscribeNbpSubscriberError() {
        NbpTestSubscriber<Object> ts = new NbpTestSubscriber<Object>();
        
        error.completable.subscribe(ts);
        
        ts.assertNotComplete();
        ts.assertNoValues();
        ts.assertError(TestException.class);
    }
    
    @Test(timeout = 1000)
    public void subscribeRunnableNormal() {
        final AtomicBoolean run = new AtomicBoolean();
        
        normal.completable.subscribe(new Runnable() {
            @Override
            public void run() {
                run.set(true);
            }
        });
        
        Assert.assertTrue("Not completed", run.get());
    }

    @Test(timeout = 1000)
    public void subscribeRunnableError() {
        final AtomicBoolean run = new AtomicBoolean();
        
        error.completable.subscribe(new Runnable() {
            @Override
            public void run() {
                run.set(true);
            }
        });
        
        Assert.assertFalse("Completed", run.get());
    }
    
    @Test(expected = NullPointerException.class)
    public void subscribeRunnableNull() {
        normal.completable.subscribe((Runnable)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void subscribeSubscriberNull() {
        normal.completable.subscribe((Subscriber<Object>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void subscribeNbpSubscriberNull() {
        normal.completable.subscribe((NbpSubscriber<Object>)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void subscribeCompletableSubscriberNull() {
        normal.completable.subscribe((CompletableSubscriber)null);
    }

    @Test(timeout = 1000)
    public void subscribeSubscriberNormal() {
        TestSubscriber<Object> ts = new TestSubscriber<Object>();
        
        normal.completable.subscribe(ts);
        
        ts.assertComplete();
        ts.assertNoValues();
        ts.assertNoErrors();
    }

    @Test(timeout = 1000)
    public void subscribeSubscriberError() {
        TestSubscriber<Object> ts = new TestSubscriber<Object>();
        
        error.completable.subscribe(ts);
        
        ts.assertNotComplete();
        ts.assertNoValues();
        ts.assertError(TestException.class);
    }
    
    @Test(expected = NullPointerException.class)
    public void subscribeOnNull() {
        normal.completable.subscribeOn(null);
    }
    
    @Test(timeout = 1000)
    public void subscribeOnNormal() {
        final AtomicReference<String> name = new  AtomicReference<String>();
        
        Completable c = Completable.create(new CompletableOnSubscribe() {
            @Override
            public void accept(CompletableSubscriber s) { 
                name.set(Thread.currentThread().getName());
                s.onSubscribe(EmptyDisposable.INSTANCE);
                s.onComplete();
            }
        }).subscribeOn(Schedulers.computation());
        
        c.await();
        
        Assert.assertTrue(name.get().startsWith("RxComputation"));
    }
    
    @Test(timeout = 1000)
    public void subscribeOnError() {
        final AtomicReference<String> name = new  AtomicReference<String>();
        
        Completable c = Completable.create(new CompletableOnSubscribe() {
            @Override
            public void accept(CompletableSubscriber s) { 
                name.set(Thread.currentThread().getName());
                s.onSubscribe(EmptyDisposable.INSTANCE);
                s.onError(new TestException());
            }
        }).subscribeOn(Schedulers.computation());
        
        try {
            c.await();
            Assert.fail("No exception thrown");
        } catch (TestException ex) {
            // expected
        }
        
        Assert.assertTrue(name.get().startsWith("RxComputation"));
    }
    
    @Test(timeout = 1000)
    public void timeoutEmitError() {
        Throwable e = Completable.never().timeout(100, TimeUnit.MILLISECONDS).get();
        
        Assert.assertTrue(e instanceof TimeoutException);
    }
    
    @Test(timeout = 1000)
    public void timeoutSwitchNormal() {
        Completable c = Completable.never().timeout(100, TimeUnit.MILLISECONDS, normal.completable);
        
        c.await();
        
        normal.assertSubscriptions(1);
    }
    
    @Test(timeout = 1000)
    public void timeoutTimerCancelled() throws InterruptedException {
        Completable c = Completable.fromCallable(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                Thread.sleep(50);
                return null;
            }
        }).timeout(100, TimeUnit.MILLISECONDS, normal.completable);
        
        c.await();
        
        Thread.sleep(100);
        
        normal.assertSubscriptions(0);
    }
    
    @Test(expected = NullPointerException.class)
    public void timeoutUnitNull() {
        normal.completable.timeout(1, null);
    }

    @Test(expected = NullPointerException.class)
    public void timeoutSchedulerNull() {
        normal.completable.timeout(1, TimeUnit.SECONDS, (Scheduler)null);
    }
    
    @Test(expected = NullPointerException.class)
    public void timeoutOtherNull() {
        normal.completable.timeout(1, TimeUnit.SECONDS, (Completable)null);
    }
    
    @Test(timeout = 1000)
    public void toNormal() {
        Observable<Object> flow = normal.completable.to(new Function<Completable, Observable<Object>>() {
            @Override
            public Observable<Object> apply(Completable c) {
                return c.toFlowable();
            }
        });
        
        flow.toBlocking().forEach(new Consumer<Object>() {
            @Override
            public void accept(Object e) { }
        });
    }
    
    @Test(expected = NullPointerException.class)
    public void toNull() {
        normal.completable.to(null);
    }
    
    @Test(timeout = 1000)
    public void toFlowableNormal() {
        normal.completable.toFlowable().toBlocking().forEach(Functions.emptyConsumer());
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void toFlowableError() {
        error.completable.toFlowable().toBlocking().forEach(Functions.emptyConsumer());
    }

    @Test(timeout = 1000)
    public void toNbpObservableNormal() {
        normal.completable.toNbpObservable().toBlocking().forEach(Functions.emptyConsumer());
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void toNbpObservableError() {
        error.completable.toNbpObservable().toBlocking().forEach(Functions.emptyConsumer());
    }
    
    @Test(timeout = 1000)
    public void toSingleSupplierNormal() {
        Assert.assertEquals(1, normal.completable.toSingle(new Supplier<Object>() {
            @Override
            public Object get() {
                return 1;
            }
        }).get());
    }

    @Test(timeout = 1000, expected = TestException.class)
    public void toSingleSupplierError() {
        error.completable.toSingle(new Supplier<Object>() {
            @Override
            public Object get() {
                return 1;
            }
        }).get();
    }

    @Test(expected = NullPointerException.class)
    public void toSingleSupplierNull() {
        normal.completable.toSingle(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void toSingleSupplierReturnsNull() {
        normal.completable.toSingle(new Supplier<Object>() {
            @Override
            public Object get() {
                return null;
            }
        }).get();
    }

    @Test(expected = TestException.class)
    public void toSingleSupplierThrows() {
        normal.completable.toSingle(new Supplier<Object>() {
            @Override
            public Object get() { throw new TestException(); }
        }).get();
    }

    @Test(timeout = 1000, expected = TestException.class)
    public void toSingleDefaultError() {
        error.completable.toSingleDefault(1).get();
    }
    
    @Test(timeout = 1000)
    public void toSingleDefaultNormal() {
        Assert.assertEquals((Integer)1, normal.completable.toSingleDefault(1).get());
    }
    
    @Test(expected = NullPointerException.class)
    public void toSingleDefaultNull() {
        normal.completable.toSingleDefault(null);
    }
    
    @Test(timeout = 1000)
    public void unsubscribeOnNormal() throws InterruptedException {
        final AtomicReference<String> name = new AtomicReference<String>();
        final CountDownLatch cdl = new CountDownLatch(1);
        
        normal.completable.delay(1, TimeUnit.SECONDS)
        .doOnDispose(new Runnable() {
            @Override
            public void run() {
                name.set(Thread.currentThread().getName());
                cdl.countDown();
            }
        })
        .unsubscribeOn(Schedulers.computation())
        .subscribe(new CompletableSubscriber() {
            @Override
            public void onSubscribe(final Disposable d) {
                Schedulers.single().scheduleDirect(new Runnable() {
                    @Override
                    public void run() {
                        d.dispose();
                    }
                }, 100, TimeUnit.MILLISECONDS);
            }
            
            @Override
            public void onError(Throwable e) {
                
            }
            
            @Override
            public void onComplete() {
                
            }
        });
        
        cdl.await();
        
        Assert.assertTrue(name.get().startsWith("RxComputation"));
    }
    
    @Test(expected = NullPointerException.class)
    public void ambArrayNull() {
        Completable.amb((Completable[])null);
    }

    @Test(timeout = 1000)
    public void ambArrayEmpty() {
        Completable c = Completable.amb();
                
        c.await();
    }

    @Test(timeout = 1000)
    public void ambArraySingleNormal() {
        Completable c = Completable.amb(normal.completable);
                
        c.await();
    }

    @Test(timeout = 1000, expected = TestException.class)
    public void ambArraySingleError() {
        Completable c = Completable.amb(error.completable);
                
        c.await();
    }
    
    @Test(timeout = 1000)
    public void ambArrayOneFires() {
        PublishSubject<Object> ps1 = PublishSubject.create();
        PublishSubject<Object> ps2 = PublishSubject.create();
        
        Completable c1 = Completable.fromFlowable(ps1);

        Completable c2 = Completable.fromFlowable(ps2);
        
        Completable c = Completable.amb(c1, c2);
        
        final AtomicBoolean complete = new AtomicBoolean();
        
        c.subscribe(new Runnable() {
            @Override
            public void run() {
                complete.set(true);
            }
        });
        
        Assert.assertTrue("First subject no subscribers", ps1.hasSubscribers());
        Assert.assertTrue("Second subject no subscribers", ps2.hasSubscribers());
        
        ps1.onComplete();
        
        Assert.assertFalse("First subject has subscribers", ps1.hasSubscribers());
        Assert.assertFalse("Second subject has subscribers", ps2.hasSubscribers());
        
        Assert.assertTrue("Not completed", complete.get());
    }

    @Test(timeout = 1000)
    public void ambArrayOneFiresError() {
        PublishSubject<Object> ps1 = PublishSubject.create();
        PublishSubject<Object> ps2 = PublishSubject.create();
        
        Completable c1 = Completable.fromFlowable(ps1);

        Completable c2 = Completable.fromFlowable(ps2);
        
        Completable c = Completable.amb(c1, c2);
        
        final AtomicReference<Throwable> complete = new AtomicReference<Throwable>();
        
        c.subscribe(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable v) {
                complete.set(v);
            }
        }, Functions.emptyRunnable());
        
        Assert.assertTrue("First subject no subscribers", ps1.hasSubscribers());
        Assert.assertTrue("Second subject no subscribers", ps2.hasSubscribers());
        
        ps1.onError(new TestException());
        
        Assert.assertFalse("First subject has subscribers", ps1.hasSubscribers());
        Assert.assertFalse("Second subject has subscribers", ps2.hasSubscribers());
        
        Assert.assertTrue("Not completed", complete.get() instanceof TestException);
    }
    
    @Test(timeout = 1000)
    public void ambArraySecondFires() {
        PublishSubject<Object> ps1 = PublishSubject.create();
        PublishSubject<Object> ps2 = PublishSubject.create();
        
        Completable c1 = Completable.fromFlowable(ps1);

        Completable c2 = Completable.fromFlowable(ps2);
        
        Completable c = Completable.amb(c1, c2);
        
        final AtomicBoolean complete = new AtomicBoolean();
        
        c.subscribe(new Runnable() {
            @Override
            public void run() {
                complete.set(true);
            }
        });
        
        Assert.assertTrue("First subject no subscribers", ps1.hasSubscribers());
        Assert.assertTrue("Second subject no subscribers", ps2.hasSubscribers());
        
        ps2.onComplete();
        
        Assert.assertFalse("First subject has subscribers", ps1.hasSubscribers());
        Assert.assertFalse("Second subject has subscribers", ps2.hasSubscribers());
        
        Assert.assertTrue("Not completed", complete.get());
    }

    @Test(timeout = 1000)
    public void ambArraySecondFiresError() {
        PublishSubject<Object> ps1 = PublishSubject.create();
        PublishSubject<Object> ps2 = PublishSubject.create();
        
        Completable c1 = Completable.fromFlowable(ps1);

        Completable c2 = Completable.fromFlowable(ps2);
        
        Completable c = Completable.amb(c1, c2);
        
        final AtomicReference<Throwable> complete = new AtomicReference<Throwable>();
        
        c.subscribe(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable v) {
                complete.set(v);
            }
        }, Functions.emptyRunnable());
        
        Assert.assertTrue("First subject no subscribers", ps1.hasSubscribers());
        Assert.assertTrue("Second subject no subscribers", ps2.hasSubscribers());
        
        ps2.onError(new TestException());
        
        Assert.assertFalse("First subject has subscribers", ps1.hasSubscribers());
        Assert.assertFalse("Second subject has subscribers", ps2.hasSubscribers());
        
        Assert.assertTrue("Not completed", complete.get() instanceof TestException);
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void ambMultipleOneIsNull() {
        Completable c = Completable.amb(null, normal.completable);
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void ambIterableEmpty() {
        Completable c = Completable.amb(Collections.<Completable>emptyList());
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void ambIterableNull() {
        Completable.amb((Iterable<Completable>)null);
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void ambIterableIteratorNull() {
        Completable c = Completable.amb(new Iterable<Completable>() {
            @Override
            public Iterator<Completable> iterator() {
                return null;
            }
        });
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = NullPointerException.class)
    public void ambIterableWithNull() {
        Completable c = Completable.amb(Arrays.asList(null, normal.completable));
        
        c.await();
    }
    
    @Test(timeout = 1000)
    public void ambIterableSingle() {
        Completable c = Completable.amb(Collections.singleton(normal.completable));
        
        c.await();
        
        normal.assertSubscriptions(1);
    }
    
    @Test(timeout = 1000)
    public void ambIterableMany() {
        Completable c = Completable.amb(Arrays.asList(normal.completable, normal.completable, normal.completable));
        
        c.await();
        
        normal.assertSubscriptions(1);
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void ambIterableOneThrows() {
        Completable c = Completable.amb(Collections.singleton(error.completable));
        
        c.await();
    }
    
    @Test(timeout = 1000, expected = TestException.class)
    public void ambIterableManyOneThrows() {
        Completable c = Completable.amb(Arrays.asList(error.completable, normal.completable));
        
        c.await();
    }
    
    @Test(expected = TestException.class)
    public void ambIterableIterableThrows() {
        Completable c = Completable.amb(new Iterable<Completable>() {
            @Override
            public Iterator<Completable> iterator() {
                throw new TestException();
            }
        });
        
        c.await();
    }
    
    @Test(expected = TestException.class)
    public void ambIterableIteratorHasNextThrows() {
        Completable c = Completable.amb(new IterableIteratorHasNextThrows());
        
        c.await();
    }
    
    @Test(expected = TestException.class)
    public void ambIterableIteratorNextThrows() {
        Completable c = Completable.amb(new IterableIteratorNextThrows());
        
        c.await();
    }
    
    @Test(expected = NullPointerException.class)
    public void ambWithNull() {
        normal.completable.ambWith(null);
    }
    
    @Test(timeout = 1000)
    public void ambWithArrayOneFires() {
        PublishSubject<Object> ps1 = PublishSubject.create();
        PublishSubject<Object> ps2 = PublishSubject.create();
        
        Completable c1 = Completable.fromFlowable(ps1);

        Completable c2 = Completable.fromFlowable(ps2);
        
        Completable c = c1.ambWith(c2);
        
        final AtomicBoolean complete = new AtomicBoolean();
        
        c.subscribe(new Runnable() {
            @Override
            public void run() {
                complete.set(true);
            }
        });
        
        Assert.assertTrue("First subject no subscribers", ps1.hasSubscribers());
        Assert.assertTrue("Second subject no subscribers", ps2.hasSubscribers());
        
        ps1.onComplete();
        
        Assert.assertFalse("First subject has subscribers", ps1.hasSubscribers());
        Assert.assertFalse("Second subject has subscribers", ps2.hasSubscribers());
        
        Assert.assertTrue("Not completed", complete.get());
    }

    @Test(timeout = 1000)
    public void ambWithArrayOneFiresError() {
        PublishSubject<Object> ps1 = PublishSubject.create();
        PublishSubject<Object> ps2 = PublishSubject.create();
        
        Completable c1 = Completable.fromFlowable(ps1);

        Completable c2 = Completable.fromFlowable(ps2);
        
        Completable c = c1.ambWith(c2);
        
        final AtomicReference<Throwable> complete = new AtomicReference<Throwable>();
        
        c.subscribe(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable v) {
                complete.set(v);
            }
        }, Functions.emptyRunnable());
        
        Assert.assertTrue("First subject no subscribers", ps1.hasSubscribers());
        Assert.assertTrue("Second subject no subscribers", ps2.hasSubscribers());
        
        ps1.onError(new TestException());
        
        Assert.assertFalse("First subject has subscribers", ps1.hasSubscribers());
        Assert.assertFalse("Second subject has subscribers", ps2.hasSubscribers());
        
        Assert.assertTrue("Not completed", complete.get() instanceof TestException);
    }
    
    @Test(timeout = 1000)
    public void ambWithArraySecondFires() {
        PublishSubject<Object> ps1 = PublishSubject.create();
        PublishSubject<Object> ps2 = PublishSubject.create();
        
        Completable c1 = Completable.fromFlowable(ps1);

        Completable c2 = Completable.fromFlowable(ps2);
        
        Completable c = c1.ambWith(c2);
        
        final AtomicBoolean complete = new AtomicBoolean();
        
        c.subscribe(new Runnable() {
            @Override
            public void run() {
                complete.set(true);
            }
        });
        
        Assert.assertTrue("First subject no subscribers", ps1.hasSubscribers());
        Assert.assertTrue("Second subject no subscribers", ps2.hasSubscribers());
        
        ps2.onComplete();
        
        Assert.assertFalse("First subject has subscribers", ps1.hasSubscribers());
        Assert.assertFalse("Second subject has subscribers", ps2.hasSubscribers());
        
        Assert.assertTrue("Not completed", complete.get());
    }

    @Test(timeout = 1000)
    public void ambWithArraySecondFiresError() {
        PublishSubject<Object> ps1 = PublishSubject.create();
        PublishSubject<Object> ps2 = PublishSubject.create();
        
        Completable c1 = Completable.fromFlowable(ps1);

        Completable c2 = Completable.fromFlowable(ps2);
        
        Completable c = c1.ambWith(c2);
        
        final AtomicReference<Throwable> complete = new AtomicReference<Throwable>();
        
        c.subscribe(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable v) {
                complete.set(v);
            }
        }, Functions.emptyRunnable());
        
        Assert.assertTrue("First subject no subscribers", ps1.hasSubscribers());
        Assert.assertTrue("Second subject no subscribers", ps2.hasSubscribers());
        
        ps2.onError(new TestException());
        
        Assert.assertFalse("First subject has subscribers", ps1.hasSubscribers());
        Assert.assertFalse("Second subject has subscribers", ps2.hasSubscribers());
        
        Assert.assertTrue("Not completed", complete.get() instanceof TestException);
    }
    
    @Test(timeout = 1000)
    public void startWithCompletableNormal() {
        final AtomicBoolean run = new AtomicBoolean();
        Completable c = normal.completable
                .startWith(Completable.fromCallable(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        run.set(normal.get() == 0);
                        return null;
                    }
                }));
        
        c.await();
        
        Assert.assertTrue("Did not start with other", run.get());
        normal.assertSubscriptions(1);
    }
    
    @Test(timeout = 1000)
    public void startWithCompletableError() {
        Completable c = normal.completable.startWith(error.completable);
        
        try {
            c.await();
            Assert.fail("Did not throw TestException");
        } catch (TestException ex) {
            normal.assertSubscriptions(0);
            error.assertSubscriptions(1);
        }
    }
    
    @Test(timeout = 1000)
    public void startWithFlowableNormal() {
        final AtomicBoolean run = new AtomicBoolean();
        Observable<Object> c = normal.completable
                .startWith(Observable.fromCallable(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        run.set(normal.get() == 0);
                        return 1;
                    }
                }));
        
        TestSubscriber<Object> ts = new TestSubscriber<Object>();
        
        c.subscribe(ts);
        
        Assert.assertTrue("Did not start with other", run.get());
        normal.assertSubscriptions(1);
        
        ts.assertValue(1);
        ts.assertComplete();
        ts.assertNoErrors();
    }
    
    @Test(timeout = 1000)
    public void startWithFlowableError() {
        Observable<Object> c = normal.completable
                .startWith(Observable.error(new TestException()));
        
        TestSubscriber<Object> ts = new TestSubscriber<Object>();
        
        c.subscribe(ts);
        
        normal.assertSubscriptions(0);
        
        ts.assertNoValues();
        ts.assertError(TestException.class);
        ts.assertNotComplete();
    }
    
    @Test(timeout = 1000)
    public void startWithNbpObservableNormal() {
        final AtomicBoolean run = new AtomicBoolean();
        NbpObservable<Object> c = normal.completable
                .startWith(NbpObservable.fromCallable(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        run.set(normal.get() == 0);
                        return 1;
                    }
                }));
        
        NbpTestSubscriber<Object> ts = new NbpTestSubscriber<Object>();
        
        c.subscribe(ts);
        
        Assert.assertTrue("Did not start with other", run.get());
        normal.assertSubscriptions(1);
        
        ts.assertValue(1);
        ts.assertComplete();
        ts.assertNoErrors();
    }
    
    @Test(timeout = 1000)
    public void startWithNbpObservableError() {
        NbpObservable<Object> c = normal.completable
                .startWith(NbpObservable.error(new TestException()));
        
        NbpTestSubscriber<Object> ts = new NbpTestSubscriber<Object>();
        
        c.subscribe(ts);
        
        normal.assertSubscriptions(0);
        
        ts.assertNoValues();
        ts.assertError(TestException.class);
        ts.assertNotComplete();
    }
    
    @Test(expected = NullPointerException.class)
    public void startWithCompletableNull() {
        normal.completable.startWith((Completable)null);
    }

    @Test(expected = NullPointerException.class)
    public void startWithFlowableNull() {
        normal.completable.startWith((Observable<Object>)null);
    }

    @Test(expected = NullPointerException.class)
    public void startWithNbpObservableNull() {
        normal.completable.startWith((NbpObservable<Object>)null);
    }

    @Test(expected = NullPointerException.class)
    public void endWithCompletableNull() {
        normal.completable.endWith((Completable)null);
    }

    @Test(expected = NullPointerException.class)
    public void endWithFlowableNull() {
        normal.completable.endWith((Observable<Object>)null);
    }

    @Test(expected = NullPointerException.class)
    public void endWithNbpObservableNull() {
        normal.completable.endWith((NbpObservable<Object>)null);
    }
    
    @Test(timeout = 1000)
    public void endWithCompletableNormal() {
        final AtomicBoolean run = new AtomicBoolean();
        Completable c = normal.completable
                .endWith(Completable.fromCallable(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        run.set(normal.get() == 0);
                        return null;
                    }
                }));
        
        c.await();
        
        Assert.assertFalse("Start with other", run.get());
        normal.assertSubscriptions(1);
    }
    
    @Test(timeout = 1000)
    public void endWithCompletableError() {
        Completable c = normal.completable.endWith(error.completable);
        
        try {
            c.await();
            Assert.fail("Did not throw TestException");
        } catch (TestException ex) {
            normal.assertSubscriptions(1);
            error.assertSubscriptions(1);
        }
    }
    
    @Test(timeout = 1000)
    public void endWithFlowableNormal() {
        final AtomicBoolean run = new AtomicBoolean();
        Observable<Object> c = normal.completable
                .endWith(Observable.fromCallable(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        run.set(normal.get() == 0);
                        return 1;
                    }
                }));
        
        TestSubscriber<Object> ts = new TestSubscriber<Object>();
        
        c.subscribe(ts);
        
        Assert.assertFalse("Start with other", run.get());
        normal.assertSubscriptions(1);
        
        ts.assertValue(1);
        ts.assertComplete();
        ts.assertNoErrors();
    }
    
    @Test(timeout = 1000)
    public void endWithFlowableError() {
        Observable<Object> c = normal.completable
                .endWith(Observable.error(new TestException()));
        
        TestSubscriber<Object> ts = new TestSubscriber<Object>();
        
        c.subscribe(ts);
        
        normal.assertSubscriptions(1);
        
        ts.assertNoValues();
        ts.assertError(TestException.class);
        ts.assertNotComplete();
    }
    
    @Test(timeout = 1000)
    public void endWithNbpObservableNormal() {
        final AtomicBoolean run = new AtomicBoolean();
        NbpObservable<Object> c = normal.completable
                .endWith(NbpObservable.fromCallable(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        run.set(normal.get() == 0);
                        return 1;
                    }
                }));
        
        NbpTestSubscriber<Object> ts = new NbpTestSubscriber<Object>();
        
        c.subscribe(ts);
        
        Assert.assertFalse("Start with other", run.get());
        normal.assertSubscriptions(1);
        
        ts.assertValue(1);
        ts.assertComplete();
        ts.assertNoErrors();
    }
    
    @Test(timeout = 1000)
    public void endWithNbpObservableError() {
        NbpObservable<Object> c = normal.completable
                .endWith(NbpObservable.error(new TestException()));
        
        NbpTestSubscriber<Object> ts = new NbpTestSubscriber<Object>();
        
        c.subscribe(ts);
        
        normal.assertSubscriptions(1);
        
        ts.assertNoValues();
        ts.assertError(TestException.class);
        ts.assertNotComplete();
    }
}