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

package io.reactivex;

import org.junit.Test;
import org.reactivestreams.Publisher;

import io.reactivex.EventStream.Event;
import io.reactivex.functions.*;
import io.reactivex.observables.GroupedObservable;

public class GroupByTests {

    @SuppressWarnings("unchecked")
    @Test
    public void testTakeUnsubscribesOnGroupBy() {
        Observable.merge(
            EventStream.getEventStream("HTTP-ClusterA", 50),
            EventStream.getEventStream("HTTP-ClusterB", 20)
        )
        // group by type (2 clusters)
        .groupBy(new Function<Event, Object>() {
            @Override
            public Object apply(Event event) {
                return event.type;
            }
        })
        .take(1)
        .toBlocking()
        .forEach(new Consumer<GroupedObservable<Object, Event>>() {
            @Override
            public void accept(GroupedObservable<Object, Event> v) {
                System.out.println(v);
                v.take(1).subscribe();  // FIXME groups need consumption to a certain degree to cancel upstream
            }
        });

        System.out.println("**** finished");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testTakeUnsubscribesOnFlatMapOfGroupBy() {
        Observable.merge(
            EventStream.getEventStream("HTTP-ClusterA", 50),
            EventStream.getEventStream("HTTP-ClusterB", 20)
        )
        // group by type (2 clusters)
        .groupBy(new Function<Event, Object>() {
            @Override
            public Object apply(Event event) {
                return event.type;
            }
        })
        .flatMap(new Function<GroupedObservable<Object, Event>, Publisher<Object>>() {
            @Override
            public Publisher<Object> apply(GroupedObservable<Object, Event> g) {
                return g.map(new Function<Event, Object>() {
                    @Override
                    public Object apply(Event event) {
                        return event.instanceId + " - " + event.values.get("count200");
                    }
                });
            }
        })
        .take(20)
        .toBlocking()
        .forEach(new Consumer<Object>() {
            @Override
            public void accept(Object v) {
                System.out.println(v);
            }
        });

        System.out.println("**** finished");
    }
}