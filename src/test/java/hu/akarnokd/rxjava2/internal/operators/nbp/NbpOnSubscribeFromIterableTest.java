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

import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.mockito.Mockito;

import hu.akarnokd.rxjava2.*;
import hu.akarnokd.rxjava2.NbpObservable.NbpSubscriber;
import hu.akarnokd.rxjava2.subscribers.nbp.NbpTestSubscriber;

public class NbpOnSubscribeFromIterableTest {

    @Test(expected = NullPointerException.class)
    public void testNull() {
        NbpObservable.fromIterable(null);
    }
    
    @Test
    public void testListIterable() {
        NbpObservable<String> o = NbpObservable.fromIterable(Arrays.<String> asList("one", "two", "three"));

        NbpSubscriber<String> NbpObserver = TestHelper.mockNbpSubscriber();
        
        o.subscribe(NbpObserver);
        
        verify(NbpObserver, times(1)).onNext("one");
        verify(NbpObserver, times(1)).onNext("two");
        verify(NbpObserver, times(1)).onNext("three");
        verify(NbpObserver, Mockito.never()).onError(any(Throwable.class));
        verify(NbpObserver, times(1)).onComplete();
    }

    /**
     * This tests the path that can not optimize based on size so must use setProducer.
     */
    @Test
    public void testRawIterable() {
        Iterable<String> it = new Iterable<String>() {

            @Override
            public Iterator<String> iterator() {
                return new Iterator<String>() {

                    int i = 0;

                    @Override
                    public boolean hasNext() {
                        return i < 3;
                    }

                    @Override
                    public String next() {
                        return String.valueOf(++i);
                    }

                    @Override
                    public void remove() {
                    }

                };
            }

        };
        NbpObservable<String> o = NbpObservable.fromIterable(it);

        NbpSubscriber<String> NbpObserver = TestHelper.mockNbpSubscriber();

        o.subscribe(NbpObserver);
        
        verify(NbpObserver, times(1)).onNext("1");
        verify(NbpObserver, times(1)).onNext("2");
        verify(NbpObserver, times(1)).onNext("3");
        verify(NbpObserver, Mockito.never()).onError(any(Throwable.class));
        verify(NbpObserver, times(1)).onComplete();
    }

    @Test
    public void testObservableFromIterable() {
        NbpObservable<String> o = NbpObservable.fromIterable(Arrays.<String> asList("one", "two", "three"));

        NbpSubscriber<String> NbpObserver = TestHelper.mockNbpSubscriber();

        o.subscribe(NbpObserver);
        
        verify(NbpObserver, times(1)).onNext("one");
        verify(NbpObserver, times(1)).onNext("two");
        verify(NbpObserver, times(1)).onNext("three");
        verify(NbpObserver, Mockito.never()).onError(any(Throwable.class));
        verify(NbpObserver, times(1)).onComplete();
    }

    @Test
    public void testNoBackpressure() {
        NbpObservable<Integer> o = NbpObservable.fromIterable(Arrays.asList(1, 2, 3, 4, 5));
        
        NbpTestSubscriber<Integer> ts = new NbpTestSubscriber<Integer>();
        
        o.subscribe(ts);
        
        ts.assertValues(1, 2, 3, 4, 5);
        ts.assertTerminated();
    }

    @Test
    public void testSubscribeMultipleTimes() {
        NbpObservable<Integer> o = NbpObservable.fromIterable(Arrays.asList(1, 2, 3));
        
        for (int i = 0; i < 10; i++) {
            NbpTestSubscriber<Integer> ts = new NbpTestSubscriber<Integer>();
            
            o.subscribe(ts);
            
            ts.assertValues(1, 2, 3);
            ts.assertNoErrors();
            ts.assertComplete();
        }    
    }
    
    @Test
    public void testDoesNotCallIteratorHasNextMoreThanRequiredWithBackpressure() {
        final AtomicBoolean called = new AtomicBoolean(false);
        Iterable<Integer> iterable = new Iterable<Integer>() {

            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {

                    int count = 1;
                    
                    @Override
                    public void remove() {
                        // ignore
                    }

                    @Override
                    public boolean hasNext() {
                        if (count > 1) {
                            called.set(true);
                            return false;
                        } else
                            return true;
                    }

                    @Override
                    public Integer next() {
                        return count++;
                    }

                };
            }
        };
        NbpObservable.fromIterable(iterable).take(1).subscribe();
        assertFalse(called.get());
    }

    @Test
    public void testDoesNotCallIteratorHasNextMoreThanRequiredFastPath() {
        final AtomicBoolean called = new AtomicBoolean(false);
        Iterable<Integer> iterable = new Iterable<Integer>() {

            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {

                    @Override
                    public void remove() {
                        // ignore
                    }

                    int count = 1;

                    @Override
                    public boolean hasNext() {
                        if (count > 1) {
                            called.set(true);
                            return false;
                        } else
                            return true;
                    }

                    @Override
                    public Integer next() {
                        return count++;
                    }

                };
            }
        };
        NbpObservable.fromIterable(iterable).subscribe(new NbpObserver<Integer>() {

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Integer t) {
                // unsubscribe on first emission
                cancel();
            }
        });
        assertFalse(called.get());
    }
    
}