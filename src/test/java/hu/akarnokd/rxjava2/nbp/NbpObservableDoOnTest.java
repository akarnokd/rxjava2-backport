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

import static org.junit.Assert.*;

import java.util.concurrent.atomic.*;

import org.junit.Test;

import hu.akarnokd.rxjava2.NbpObservable;
import hu.akarnokd.rxjava2.functions.Consumer;

public class NbpObservableDoOnTest {

    @Test
    public void testDoOnEach() {
        final AtomicReference<String> r = new AtomicReference<String>();
        String output = NbpObservable.just("one").doOnNext(new Consumer<String>() {
            @Override
            public void accept(String v) {
                r.set(v);
            }
        }).toBlocking().single();

        assertEquals("one", output);
        assertEquals("one", r.get());
    }

    @Test
    public void testDoOnError() {
        final AtomicReference<Throwable> r = new AtomicReference<Throwable>();
        Throwable t = null;
        try {
            NbpObservable.<String> error(new RuntimeException("an error"))
            .doOnError(new Consumer<Throwable>() {
                @Override
                public void accept(Throwable v) {
                    r.set(v);
                }
            }).toBlocking().single();
            fail("expected exception, not a return value");
        } catch (Throwable e) {
            t = e;
        }

        assertNotNull(t);
        assertEquals(t, r.get());
    }

    @Test
    public void testDoOnCompleted() {
        final AtomicBoolean r = new AtomicBoolean();
        String output = NbpObservable.just("one").doOnComplete(new Runnable() {
            @Override
            public void run() {
                r.set(true);
            }
        }).toBlocking().single();

        assertEquals("one", output);
        assertTrue(r.get());
    }
}