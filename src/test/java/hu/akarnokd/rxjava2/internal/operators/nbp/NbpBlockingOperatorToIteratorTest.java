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

import static org.junit.Assert.assertEquals;

import java.util.Iterator;

import org.junit.*;

import hu.akarnokd.rxjava2.NbpObservable;
import hu.akarnokd.rxjava2.NbpObservable.*;
import hu.akarnokd.rxjava2.exceptions.TestException;
import hu.akarnokd.rxjava2.internal.disposables.EmptyDisposable;

public class NbpBlockingOperatorToIteratorTest {

    @Test
    public void testToIterator() {
        NbpObservable<String> obs = NbpObservable.just("one", "two", "three");

        Iterator<String> it = obs.toBlocking().iterator();

        assertEquals(true, it.hasNext());
        assertEquals("one", it.next());

        assertEquals(true, it.hasNext());
        assertEquals("two", it.next());

        assertEquals(true, it.hasNext());
        assertEquals("three", it.next());

        assertEquals(false, it.hasNext());

    }

    @Test(expected = TestException.class)
    public void testToIteratorWithException() {
        NbpObservable<String> obs = NbpObservable.create(new NbpOnSubscribe<String>() {

            @Override
            public void accept(NbpSubscriber<? super String> NbpObserver) {
                NbpObserver.onSubscribe(EmptyDisposable.INSTANCE);
                NbpObserver.onNext("one");
                NbpObserver.onError(new TestException());
            }
        });

        Iterator<String> it = obs.toBlocking().iterator();

        assertEquals(true, it.hasNext());
        assertEquals("one", it.next());

        assertEquals(true, it.hasNext());
        it.next();
    }

    @Ignore("subscribe() should not throw")
    @Test(expected = TestException.class)
    public void testExceptionThrownFromOnSubscribe() {
        Iterable<String> strings = NbpObservable.create(new NbpOnSubscribe<String>() {
            @Override
            public void accept(NbpSubscriber<? super String> NbpSubscriber) {
                throw new TestException("intentional");
            }
        }).toBlocking();
        for (String string : strings) {
            // never reaches here
            System.out.println(string);
        }
    }
}