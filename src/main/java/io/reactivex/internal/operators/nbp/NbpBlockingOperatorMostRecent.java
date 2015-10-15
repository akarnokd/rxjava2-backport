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

package io.reactivex.internal.operators.nbp;

import java.util.*;

import io.reactivex.*;
import io.reactivex.internal.util.*;

/**
 * Returns an Iterable that always returns the item most recently emitted by an Observable, or a
 * seed value if no item has yet been emitted.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/B.mostRecent.png" alt="">
 */
public enum NbpBlockingOperatorMostRecent {
    ;
    /**
     * Returns an {@code Iterable} that always returns the item most recently emitted by the {@code Observable}.
     *
     * @param source
     *            the source {@code Observable}
     * @param initialValue
     *            a default item to return from the {@code Iterable} if {@code source} has not yet emitted any
     *            items
     * @return an {@code Iterable} that always returns the item most recently emitted by {@code source}, or
     *         {@code initialValue} if {@code source} has not yet emitted any items
     */
    public static <T> Iterable<T> mostRecent(final NbpObservable<? extends T> source, final T initialValue) {
        return new Iterable<T>() {
            @Override
            public Iterator<T> iterator() {
                NbpMostRecentObserver<T> mostRecentObserver = new NbpMostRecentObserver<>(initialValue);

                /**
                 * Subscribe instead of unsafeSubscribe since this is the final subscribe in the chain
                 * since it is for BlockingObservable.
                 */
                source.subscribe(mostRecentObserver);

                return mostRecentObserver.getIterable();
            }
        };
    }

    static final class NbpMostRecentObserver<T> extends NbpObserver<T> {
        volatile Object value;
        
        private NbpMostRecentObserver(T value) {
            this.value = NotificationLite.next(value);
        }

        @Override
        public void onComplete() {
            value = NotificationLite.complete();
        }

        @Override
        public void onError(Throwable e) {
            value = NotificationLite.error(e);
        }

        @Override
        public void onNext(T args) {
            value = NotificationLite.next(args);
        }

        /**
         * The {@link Iterator} return is not thread safe. In other words don't call {@link Iterator#hasNext()} in one
         * thread expect {@link Iterator#next()} called from a different thread to work.
         * @return
         */
        public Iterator<T> getIterable() {
            return new Iterator<T>() {
                /**
                 * buffer to make sure that the state of the iterator doesn't change between calling hasNext() and next().
                 */
                private Object buf = null;

                @Override
                public boolean hasNext() {
                    buf = value;
                    return !NotificationLite.isComplete(buf);
                }

                @Override
                public T next() {
                    try {
                        // if hasNext wasn't called before calling next.
                        if (buf == null)
                            buf = value;
                        if (NotificationLite.isComplete(buf))
                            throw new NoSuchElementException();
                        if (NotificationLite.isError(buf)) {
                            throw Exceptions.propagate(NotificationLite.getError(buf));
                        }
                        return NotificationLite.getValue(buf);
                    }
                    finally {
                        buf = null;
                    }
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("Read only iterator");
                }
            };
        }
    }
}
