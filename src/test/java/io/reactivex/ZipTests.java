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

import static org.junit.Assert.*;

import java.util.*;
import io.reactivex.functions.*;
import io.reactivex.observables.GroupedObservable;

import org.junit.Test;
import org.reactivestreams.Publisher;

import io.reactivex.CovarianceTest.*;
import io.reactivex.EventStream.Event;

public class ZipTests {

    @Test
    public void testZipObservableOfObservables() {
        EventStream.getEventStream("HTTP-ClusterB", 20)
                .groupBy(new Function<Event, String>() {
                    @Override
                    public String apply(Event e) {
                        return e.instanceId;
                    }
                })
                // now we have streams of cluster+instanceId
                .flatMap(new Function<GroupedObservable<String, Event>, Publisher<HashMap<String, String>>>() {
                    @Override
                    public Publisher<HashMap<String, String>> apply(final GroupedObservable<String, Event> ge) {
                            return ge.scan(new HashMap<String, String>(), new BiFunction<HashMap<String, String>, Event, HashMap<String, String>>() {
                                @Override
                                public HashMap<String, String> apply(HashMap<String, String> accum,
                                        Event perInstanceEvent) {
                                            accum.put("instance", ge.key());
                                            return accum;
                                        }
                            });
                    }
                })
                .take(10)
                .toBlocking().forEach(new Consumer<HashMap<String, String>>() {
                    @Override
                    public void accept(HashMap<String, String> v) {
                        System.out.println(v);
                    }
                });

        System.out.println("**** finished");
    }

    /**
     * This won't compile if super/extends isn't done correctly on generics
     */
    @Test
    public void testCovarianceOfZip() {
        Observable<HorrorMovie> horrors = Observable.just(new HorrorMovie());
        Observable<CoolRating> ratings = Observable.just(new CoolRating());

        Observable.<Movie, CoolRating, Result> zip(horrors, ratings, combine).toBlocking().forEach(action);
        Observable.<Movie, CoolRating, Result> zip(horrors, ratings, combine).toBlocking().forEach(action);
        Observable.<Media, Rating, ExtendedResult> zip(horrors, ratings, combine).toBlocking().forEach(extendedAction);
        Observable.<Media, Rating, Result> zip(horrors, ratings, combine).toBlocking().forEach(action);
        Observable.<Media, Rating, ExtendedResult> zip(horrors, ratings, combine).toBlocking().forEach(action);

        Observable.<Movie, CoolRating, Result> zip(horrors, ratings, combine);
    }

    /**
     * Occasionally zip may be invoked with 0 observables. Test that we don't block indefinitely instead
     * of immediately invoking zip with 0 argument.
     * 
     * We now expect an NoSuchElementException since last() requires at least one value and nothing will be emitted.
     */
    @Test(expected = NoSuchElementException.class)
    public void nonBlockingObservable() {

        final Object invoked = new Object();

        Collection<Observable<Object>> observables = Collections.emptyList();

        Observable<Object> result = Observable.zip(observables, new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] args) {
                System.out.println("received: " + args);
                assertEquals("No argument should have been passed", 0, args.length);
                return invoked;
            }
        });

        assertSame(invoked, result.toBlocking().last());
    }

    BiFunction<Media, Rating, ExtendedResult> combine = new BiFunction<Media, Rating, ExtendedResult>() {
        @Override
        public ExtendedResult apply(Media m, Rating r) {
                return new ExtendedResult();
        }
    };

    Consumer<Result> action = new Consumer<Result>() {
        @Override
        public void accept(Result t1) {
            System.out.println("Result: " + t1);
        }
    };

    Consumer<ExtendedResult> extendedAction = new Consumer<ExtendedResult>() {
        @Override
        public void accept(ExtendedResult t1) {
            System.out.println("Result: " + t1);
        }
    };
}