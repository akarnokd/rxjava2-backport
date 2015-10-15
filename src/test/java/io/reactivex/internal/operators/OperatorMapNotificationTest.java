/**
 * Copyright 2015 David Karnok
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

package io.reactivex.internal.operators;

import io.reactivex.functions.*;

import org.junit.Test;

import io.reactivex.Observable;
import io.reactivex.subscribers.TestSubscriber;

public class OperatorMapNotificationTest {
    @Test
    public void testJust() {
        TestSubscriber<Object> ts = new TestSubscriber<>();
        Observable.just(1)
        .flatMap(
                new Function<Integer, Observable<Object>>() {
                    @Override
                    public Observable<Object> apply(Integer item) {
                        return Observable.just((Object)(item + 1));
                    }
                },
                new Function<Throwable, Observable<Object>>() {
                    @Override
                    public Observable<Object> apply(Throwable e) {
                        return Observable.error(e);
                    }
                },
                new Supplier<Observable<Object>>() {
                    @Override
                    public Observable<Object> get() {
                        return Observable.never();
                    }
                }
        ).subscribe(ts);
        
        ts.assertNoErrors();
        ts.assertNotComplete();
        ts.assertValue(2);
    }
}