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
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.*;

import io.reactivex.Observable;
import io.reactivex.Single.*;
import io.reactivex.annotations.*;
import io.reactivex.disposables.*;
import io.reactivex.functions.*;
import io.reactivex.internal.disposables.EmptyDisposable;
import io.reactivex.internal.functions.Functions;
import io.reactivex.internal.functions.Objects;
import io.reactivex.internal.operators.nbp.*;
import io.reactivex.internal.subscribers.nbp.*;
import io.reactivex.internal.util.Exceptions;
import io.reactivex.observables.nbp.*;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.*;
import io.reactivex.subscribers.nbp.*;

/**
 * Observable for delivering a sequence of values without backpressure. 
 * @param <T>
 */
public class NbpObservable<T> {

    public interface NbpOnSubscribe<T> extends Consumer<NbpSubscriber<? super T>> {
        
    }
    
    public interface NbpOperator<Downstream, Upstream> extends Function<NbpSubscriber<? super Downstream>, NbpSubscriber<? super Upstream>> {
        
    }
    
    public interface NbpSubscriber<T> {
        
        void onSubscribe(Disposable d);

        void onNext(T value);
        
        void onError(Throwable e);
        
        void onComplete();
        
    }
    
    public interface NbpTransformer<Upstream, Downstream> extends Function<NbpObservable<Upstream>, NbpObservable<Downstream>> {
        
    }
    
    /** An empty observable instance as there is no need to instantiate this more than once. */
    static final NbpObservable<Object> EMPTY = create(new NbpOnSubscribe<Object>() {
        @Override
        public void accept(NbpSubscriber<? super Object> s) {
            s.onSubscribe(EmptyDisposable.INSTANCE);
            s.onComplete();
        }
    });
    
    /** A never NbpObservable instance as there is no need to instantiate this more than once. */
    static final NbpObservable<Object> NEVER = create(new NbpOnSubscribe<Object>() {
        @Override
        public void accept(NbpSubscriber<? super Object> s) {
            s.onSubscribe(EmptyDisposable.INSTANCE);
        }
    });
    
    static final Object OBJECT = new Object();
    
    public static <T> NbpObservable<T> amb(Iterable<? extends NbpObservable<? extends T>> sources) {
        Objects.requireNonNull(sources, "sources is null");
        return create(new NbpOnSubscribeAmb<T>(null, sources));
    }
    
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> amb(NbpObservable<? extends T>... sources) {
        Objects.requireNonNull(sources, "sources is null");
        int len = sources.length;
        if (len == 0) {
            return empty();
        } else
        if (len == 1) {
            return (NbpObservable<T>)sources[0];
        }
        return create(new NbpOnSubscribeAmb<T>(sources, null));
    }
    
    /**
     * Returns the default 'island' size or capacity-increment hint for unbounded buffers.
     * @return
     */
    static int bufferSize() {
        return Observable.bufferSize();
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, R> NbpObservable<R> combineLatest(Function<? super Object[], ? extends R> combiner, boolean delayError, int bufferSize, NbpObservable<? extends T>... sources) {
        return combineLatest(sources, combiner, delayError, bufferSize);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, R> NbpObservable<R> combineLatest(Iterable<? extends NbpObservable<? extends T>> sources, Function<? super Object[], ? extends R> combiner) {
        return combineLatest(sources, combiner, false, bufferSize());
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, R> NbpObservable<R> combineLatest(Iterable<? extends NbpObservable<? extends T>> sources, Function<? super Object[], ? extends R> combiner, boolean delayError) {
        return combineLatest(sources, combiner, delayError, bufferSize());
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, R> NbpObservable<R> combineLatest(Iterable<? extends NbpObservable<? extends T>> sources, Function<? super Object[], ? extends R> combiner, boolean delayError, int bufferSize) {
        Objects.requireNonNull(sources, "sources is null");
        Objects.requireNonNull(combiner, "combiner is null");
        validateBufferSize(bufferSize);
        
        // the queue holds a pair of values so we need to double the capacity
        int s = bufferSize << 1;
        return create(new NbpOnSubscribeCombineLatest<T, R>(null, sources, combiner, s, delayError));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, R> NbpObservable<R> combineLatest(NbpObservable<? extends T>[] sources, Function<? super Object[], ? extends R> combiner) {
        return combineLatest(sources, combiner, false, bufferSize());
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, R> NbpObservable<R> combineLatest(NbpObservable<? extends T>[] sources, Function<? super Object[], ? extends R> combiner, boolean delayError) {
        return combineLatest(sources, combiner, delayError, bufferSize());
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, R> NbpObservable<R> combineLatest(NbpObservable<? extends T>[] sources, Function<? super Object[], ? extends R> combiner, boolean delayError, int bufferSize) {
        validateBufferSize(bufferSize);
        Objects.requireNonNull(combiner, "combiner is null");
        if (sources.length == 0) {
            return empty();
        }
        // the queue holds a pair of values so we need to double the capacity
        int s = bufferSize << 1;
        return create(new NbpOnSubscribeCombineLatest<T, R>(sources, null, combiner, s, delayError));
    }
    
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, R> NbpObservable<R> combineLatest(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, 
            BiFunction<? super T1, ? super T2, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), false, bufferSize(), p1, p2);
    }
    
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, T3, R> NbpObservable<R> combineLatest(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, 
            NbpObservable<? extends T3> p3, 
            Function3<? super T1, ? super T2, ? super T3, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), false, bufferSize(), p1, p2, p3);
    }
    
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, T3, T4, R> NbpObservable<R> combineLatest(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, 
            NbpObservable<? extends T3> p3, NbpObservable<? extends T4> p4,
            Function4<? super T1, ? super T2, ? super T3, ? super T4, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), false, bufferSize(), p1, p2, p3, p4);
    }
    
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, T3, T4, T5, R> NbpObservable<R> combineLatest(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, 
            NbpObservable<? extends T3> p3, NbpObservable<? extends T4> p4,
            NbpObservable<? extends T5> p5,
            Function5<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), false, bufferSize(), p1, p2, p3, p4, p5);
    }
    
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, T3, T4, T5, T6, R> NbpObservable<R> combineLatest(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, 
            NbpObservable<? extends T3> p3, NbpObservable<? extends T4> p4,
            NbpObservable<? extends T5> p5, NbpObservable<? extends T6> p6,
            Function6<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), false, bufferSize(), p1, p2, p3, p4, p5, p6);
    }
    
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, T3, T4, T5, T6, T7, R> NbpObservable<R> combineLatest(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, 
            NbpObservable<? extends T3> p3, NbpObservable<? extends T4> p4,
            NbpObservable<? extends T5> p5, NbpObservable<? extends T6> p6,
            NbpObservable<? extends T7> p7,
            Function7<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), false, bufferSize(), p1, p2, p3, p4, p5, p6, p7);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, T3, T4, T5, T6, T7, T8, R> NbpObservable<R> combineLatest(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, 
            NbpObservable<? extends T3> p3, NbpObservable<? extends T4> p4,
            NbpObservable<? extends T5> p5, NbpObservable<? extends T6> p6,
            NbpObservable<? extends T7> p7, NbpObservable<? extends T8> p8,
            Function8<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? super T8, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), false, bufferSize(), p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> NbpObservable<R> combineLatest(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, 
            NbpObservable<? extends T3> p3, NbpObservable<? extends T4> p4,
            NbpObservable<? extends T5> p5, NbpObservable<? extends T6> p6,
            NbpObservable<? extends T7> p7, NbpObservable<? extends T8> p8,
            NbpObservable<? extends T9> p9,
            Function9<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? super T8, ? super T9, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), false, bufferSize(), p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> concat(int prefetch, Iterable<? extends NbpObservable<? extends T>> sources) {
        Objects.requireNonNull(sources, "sources is null");
        return fromIterable(sources).concatMap((Function)Functions.identity(), prefetch);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> concat(Iterable<? extends NbpObservable<? extends T>> sources) {
        Objects.requireNonNull(sources, "sources is null");
        return fromIterable(sources).concatMap((Function)Functions.identity());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static final <T> NbpObservable<T> concat(NbpObservable<? extends NbpObservable<? extends T>> sources) {
        return concat(sources, bufferSize());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static final <T> NbpObservable<T> concat(NbpObservable<? extends NbpObservable<? extends T>> sources, int bufferSize) {
        return sources.concatMap((Function)Functions.identity());
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> concat(NbpObservable<? extends T> p1, NbpObservable<? extends T> p2) {
        return concatArray(p1, p2);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> concat(
            NbpObservable<? extends T> p1, NbpObservable<? extends T> p2,
            NbpObservable<? extends T> p3) {
        return concatArray(p1, p2, p3);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> concat(
            NbpObservable<? extends T> p1, NbpObservable<? extends T> p2,
            NbpObservable<? extends T> p3, NbpObservable<? extends T> p4) {
        return concatArray(p1, p2, p3, p4);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> concat(
            NbpObservable<? extends T> p1, NbpObservable<? extends T> p2, 
            NbpObservable<? extends T> p3, NbpObservable<? extends T> p4,
            NbpObservable<? extends T> p5
    ) {
        return concatArray(p1, p2, p3, p4, p5);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> concat(
            NbpObservable<? extends T> p1, NbpObservable<? extends T> p2, 
            NbpObservable<? extends T> p3, NbpObservable<? extends T> p4,
            NbpObservable<? extends T> p5, NbpObservable<? extends T> p6
    ) {
        return concatArray(p1, p2, p3, p4, p5, p6);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> concat(
            NbpObservable<? extends T> p1, NbpObservable<? extends T> p2,
            NbpObservable<? extends T> p3, NbpObservable<? extends T> p4,
            NbpObservable<? extends T> p5, NbpObservable<? extends T> p6,
            NbpObservable<? extends T> p7
    ) {
        return concatArray(p1, p2, p3, p4, p5, p6, p7);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> concat(
            NbpObservable<? extends T> p1, NbpObservable<? extends T> p2, 
            NbpObservable<? extends T> p3, NbpObservable<? extends T> p4,
            NbpObservable<? extends T> p5, NbpObservable<? extends T> p6,
            NbpObservable<? extends T> p7, NbpObservable<? extends T> p8
    ) {
        return concatArray(p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> concat(
            NbpObservable<? extends T> p1, NbpObservable<? extends T> p2, 
            NbpObservable<? extends T> p3, NbpObservable<? extends T> p4,
            NbpObservable<? extends T> p5, NbpObservable<? extends T> p6,
            NbpObservable<? extends T> p7, NbpObservable<? extends T> p8,
            NbpObservable<? extends T> p9
    ) {
        return concatArray(p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> concatArray(int prefetch, NbpObservable<? extends T>... sources) {
        Objects.requireNonNull(sources, "sources is null");
        return fromArray(sources).concatMap((Function)Functions.identity(), prefetch);
    }

    /**
     *
     * TODO named this way because of overload conflict with concat(NbpObservable&lt;NbpObservable&gt)
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> concatArray(NbpObservable<? extends T>... sources) {
        if (sources.length == 0) {
            return empty();
        } else
        if (sources.length == 1) {
            return (NbpObservable<T>)sources[0];
        }
        return fromArray(sources).concatMap((Function)Functions.identity());
    }

    public static <T> NbpObservable<T> create(NbpOnSubscribe<T> onSubscribe) {
        Objects.requireNonNull(onSubscribe, "onSubscribe is null");
        // TODO plugin wrapper
        return new NbpObservable<T>(onSubscribe);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> defer(Supplier<? extends NbpObservable<? extends T>> supplier) {
        Objects.requireNonNull(supplier, "supplier is null");
        return create(new NbpOnSubscribeDefer<T>(supplier));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    @SuppressWarnings("unchecked")
    public static <T> NbpObservable<T> empty() {
        return (NbpObservable<T>)EMPTY;
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> error(Supplier<? extends Throwable> errorSupplier) {
        Objects.requireNonNull(errorSupplier, "errorSupplier is null");
        return create(new NbpOnSubscribeErrorSource<T>(errorSupplier));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> error(final Throwable e) {
        Objects.requireNonNull(e, "e is null");
        return error(new Supplier<Throwable>() {
            @Override
            public Throwable get() {
                return e;
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> fromArray(T... values) {
        Objects.requireNonNull(values, "values is null");
        if (values.length == 0) {
            return empty();
        } else
        if (values.length == 1) {
            return just(values[0]);
        }
        return create(new NbpOnSubscribeArraySource<T>(values));
    }

    // TODO match naming with RxJava 1.x
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> fromCallable(Callable<? extends T> supplier) {
        Objects.requireNonNull(supplier, "supplier is null");
        return create(new NbpOnSubscribeScalarAsyncSource<T>(supplier));
    }

    /*
     * It doesn't add cancellation support by default like 1.x
     * if necessary, one can use composition to achieve it:
     * futureObservable.doOnCancel(() -> future.cancel(true));
     */
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> fromFuture(Future<? extends T> future) {
        Objects.requireNonNull(future, "future is null");
        return create(new NbpOnSubscribeFutureSource<T>(future, 0L, null));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> fromFuture(Future<? extends T> future, long timeout, TimeUnit unit) {
        Objects.requireNonNull(future, "future is null");
        Objects.requireNonNull(unit, "unit is null");
        NbpObservable<T> o = create(new NbpOnSubscribeFutureSource<T>(future, timeout, unit));
        return o;
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public static <T> NbpObservable<T> fromFuture(Future<? extends T> future, long timeout, TimeUnit unit, Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler is null");
        NbpObservable<T> o = fromFuture(future, timeout, unit); 
        return o.subscribeOn(scheduler);
    }

    @SchedulerSupport(SchedulerKind.IO)
    public static <T> NbpObservable<T> fromFuture(Future<? extends T> future, Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler is null");
        NbpObservable<T> o = fromFuture(future);
        return o.subscribeOn(Schedulers.io());
    }

    public static <T> NbpObservable<T> fromIterable(Iterable<? extends T> source) {
        Objects.requireNonNull(source, "source is null");
        return create(new NbpOnSubscribeIterableSource<T>(source));
    }

    public static <T> NbpObservable<T> fromPublisher(final Publisher<? extends T> publisher) {
        Objects.requireNonNull(publisher, "publisher is null");
        return create(new NbpOnSubscribe<T>() {
            @Override
            public void accept(final NbpSubscriber<? super T> s) {
                publisher.subscribe(new Subscriber<T>() {

                    @Override
                    public void onComplete() {
                        s.onComplete();
                    }

                    @Override
                    public void onError(Throwable t) {
                        s.onError(t);
                    }

                    @Override
                    public void onNext(T t) {
                        s.onNext(t);
                    }

                    @Override
                    public void onSubscribe(Subscription inner) {
                        s.onSubscribe(Disposables.from(inner));
                        inner.request(Long.MAX_VALUE);
                    }
                    
                });
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> generate(final Consumer<NbpSubscriber<T>> generator) {
        Objects.requireNonNull(generator, "generator  is null");
        return generate(Functions.<Object>nullSupplier(), 
        new BiFunction<Object, NbpSubscriber<T>, Object>() {
            @Override
            public Object apply(Object s, NbpSubscriber<T> o) {
                generator.accept(o);
                return s;
            }
        }, Functions.<Object>emptyConsumer());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, S> NbpObservable<T> generate(Supplier<S> initialState, final BiConsumer<S, NbpSubscriber<T>> generator) {
        Objects.requireNonNull(generator, "generator  is null");
        return generate(initialState, new BiFunction<S, NbpSubscriber<T>, S>() {
            @Override
            public S apply(S s, NbpSubscriber<T> o) {
                generator.accept(s, o);
                return s;
            }
        }, Functions.emptyConsumer());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, S> NbpObservable<T> generate(
            final Supplier<S> initialState, 
            final BiConsumer<S, NbpSubscriber<T>> generator, 
            Consumer<? super S> disposeState) {
        Objects.requireNonNull(generator, "generator  is null");
        return generate(initialState, new BiFunction<S, NbpSubscriber<T>, S>() {
            @Override
            public S apply(S s, NbpSubscriber<T> o) {
                generator.accept(s, o);
                return s;
            }
        }, disposeState);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, S> NbpObservable<T> generate(Supplier<S> initialState, BiFunction<S, NbpSubscriber<T>, S> generator) {
        return generate(initialState, generator, Functions.emptyConsumer());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, S> NbpObservable<T> generate(Supplier<S> initialState, BiFunction<S, NbpSubscriber<T>, S> generator, Consumer<? super S> disposeState) {
        Objects.requireNonNull(initialState, "initialState is null");
        Objects.requireNonNull(generator, "generator  is null");
        Objects.requireNonNull(disposeState, "diposeState is null");
        return create(new NbpOnSubscribeGenerate<T, S>(initialState, generator, disposeState));
    }

    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public static NbpObservable<Long> interval(long initialDelay, long period, TimeUnit unit) {
        return interval(initialDelay, period, unit, Schedulers.computation());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public static NbpObservable<Long> interval(long initialDelay, long period, TimeUnit unit, Scheduler scheduler) {
        if (initialDelay < 0) {
            initialDelay = 0L;
        }
        if (period < 0) {
            period = 0L;
        }
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");

        return create(new NbpOnSubscribeIntervalSource(initialDelay, period, unit, scheduler));
    }

    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public static NbpObservable<Long> interval(long period, TimeUnit unit) {
        return interval(period, period, unit, Schedulers.computation());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public static NbpObservable<Long> interval(long period, TimeUnit unit, Scheduler scheduler) {
        return interval(period, period, unit, scheduler);
    }

    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public static NbpObservable<Long> intervalRange(long start, long count, long initialDelay, long period, TimeUnit unit) {
        return intervalRange(start, count, initialDelay, period, unit, Schedulers.computation());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public static NbpObservable<Long> intervalRange(long start, long count, long initialDelay, long period, TimeUnit unit, Scheduler scheduler) {

        long end = start + (count - 1);
        if (end < 0) {
            throw new IllegalArgumentException("Overflow! start + count is bigger than Long.MAX_VALUE");
        }

        if (initialDelay < 0) {
            initialDelay = 0L;
        }
        if (period < 0) {
            period = 0L;
        }
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");

        return create(new NbpOnSubscribeIntervalRangeSource(start, end, initialDelay, period, unit, scheduler));
    }

    public static <T> NbpObservable<T> just(T value) {
        Objects.requireNonNull(value, "The value is null");
        return new NbpObservableScalarSource<T>(value);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static final <T> NbpObservable<T> just(T v1, T v2) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        
        return fromArray(v1, v2);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static final <T> NbpObservable<T> just(T v1, T v2, T v3) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        Objects.requireNonNull(v3, "The third value is null");
        
        return fromArray(v1, v2, v3);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static final <T> NbpObservable<T> just(T v1, T v2, T v3, T v4) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        Objects.requireNonNull(v3, "The third value is null");
        Objects.requireNonNull(v4, "The fourth value is null");
        
        return fromArray(v1, v2, v3, v4);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static final <T> NbpObservable<T> just(T v1, T v2, T v3, T v4, T v5) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        Objects.requireNonNull(v3, "The third value is null");
        Objects.requireNonNull(v4, "The fourth value is null");
        Objects.requireNonNull(v5, "The fifth value is null");
        
        return fromArray(v1, v2, v3, v4, v5);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static final <T> NbpObservable<T> just(T v1, T v2, T v3, T v4, T v5, T v6) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        Objects.requireNonNull(v3, "The third value is null");
        Objects.requireNonNull(v4, "The fourth value is null");
        Objects.requireNonNull(v5, "The fifth value is null");
        Objects.requireNonNull(v6, "The sixth value is null");
        
        return fromArray(v1, v2, v3, v4, v5, v6);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static final <T> NbpObservable<T> just(T v1, T v2, T v3, T v4, T v5, T v6, T v7) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        Objects.requireNonNull(v3, "The third value is null");
        Objects.requireNonNull(v4, "The fourth value is null");
        Objects.requireNonNull(v5, "The fifth value is null");
        Objects.requireNonNull(v6, "The sixth value is null");
        Objects.requireNonNull(v7, "The seventh value is null");
        
        return fromArray(v1, v2, v3, v4, v5, v6, v7);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static final <T> NbpObservable<T> just(T v1, T v2, T v3, T v4, T v5, T v6, T v7, T v8) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        Objects.requireNonNull(v3, "The third value is null");
        Objects.requireNonNull(v4, "The fourth value is null");
        Objects.requireNonNull(v5, "The fifth value is null");
        Objects.requireNonNull(v6, "The sixth value is null");
        Objects.requireNonNull(v7, "The seventh value is null");
        Objects.requireNonNull(v8, "The eigth value is null");
        
        return fromArray(v1, v2, v3, v4, v5, v6, v7, v8);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static final <T> NbpObservable<T> just(T v1, T v2, T v3, T v4, T v5, T v6, T v7, T v8, T v9) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        Objects.requireNonNull(v3, "The third value is null");
        Objects.requireNonNull(v4, "The fourth value is null");
        Objects.requireNonNull(v5, "The fifth value is null");
        Objects.requireNonNull(v6, "The sixth value is null");
        Objects.requireNonNull(v7, "The seventh value is null");
        Objects.requireNonNull(v8, "The eigth value is null");
        Objects.requireNonNull(v9, "The ninth is null");
        
        return fromArray(v1, v2, v3, v4, v5, v6, v7, v8, v9);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> merge(int maxConcurrency, int bufferSize, Iterable<? extends NbpObservable<? extends T>> sources) {
        return fromIterable(sources).flatMap((Function)Functions.identity(), false, maxConcurrency, bufferSize);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> merge(int maxConcurrency, int bufferSize, NbpObservable<? extends T>... sources) {
        return fromArray(sources).flatMap((Function)Functions.identity(), false, maxConcurrency, bufferSize);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> merge(int maxConcurrency, NbpObservable<? extends T>... sources) {
        return fromArray(sources).flatMap((Function)Functions.identity(), maxConcurrency);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> merge(Iterable<? extends NbpObservable<? extends T>> sources) {
        return fromIterable(sources).flatMap((Function)Functions.identity());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> merge(Iterable<? extends NbpObservable<? extends T>> sources, int maxConcurrency) {
        return fromIterable(sources).flatMap((Function)Functions.identity(), maxConcurrency);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> NbpObservable<T> merge(NbpObservable<? extends NbpObservable<? extends T>> sources) {
        return sources.flatMap((Function)Functions.identity());
    }

    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> merge(NbpObservable<? extends NbpObservable<? extends T>> sources, int maxConcurrency) {
        return sources.flatMap((Function)Functions.identity(), maxConcurrency);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @BackpressureSupport(BackpressureKind.FULL)
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> merge(NbpObservable<? extends T> p1, NbpObservable<? extends T> p2) {
        Objects.requireNonNull(p1, "p1 is null");
        Objects.requireNonNull(p2, "p2 is null");
        return fromArray(p1, p2).flatMap((Function)Functions.identity(), false, 2);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @BackpressureSupport(BackpressureKind.FULL)
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> merge(NbpObservable<? extends T> p1, NbpObservable<? extends T> p2, NbpObservable<? extends T> p3) {
        Objects.requireNonNull(p1, "p1 is null");
        Objects.requireNonNull(p2, "p2 is null");
        Objects.requireNonNull(p3, "p3 is null");
        return fromArray(p1, p2, p3).flatMap((Function)Functions.identity(), false, 3);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @BackpressureSupport(BackpressureKind.FULL)
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> merge(
            NbpObservable<? extends T> p1, NbpObservable<? extends T> p2, 
            NbpObservable<? extends T> p3, NbpObservable<? extends T> p4) {
        Objects.requireNonNull(p1, "p1 is null");
        Objects.requireNonNull(p2, "p2 is null");
        Objects.requireNonNull(p3, "p3 is null");
        Objects.requireNonNull(p4, "p4 is null");
        return fromArray(p1, p2, p3, p4).flatMap((Function)Functions.identity(), false, 4);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> merge(NbpObservable<? extends T>... sources) {
        return fromArray(sources).flatMap((Function)Functions.identity(), sources.length);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> mergeDelayError(boolean delayErrors, Iterable<? extends NbpObservable<? extends T>> sources) {
        return fromIterable(sources).flatMap((Function)Functions.identity(), true);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> mergeDelayError(int maxConcurrency, int bufferSize, Iterable<? extends NbpObservable<? extends T>> sources) {
        return fromIterable(sources).flatMap((Function)Functions.identity(), true, maxConcurrency, bufferSize);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> mergeDelayError(int maxConcurrency, int bufferSize, NbpObservable<? extends T>... sources) {
        return fromArray(sources).flatMap((Function)Functions.identity(), true, maxConcurrency, bufferSize);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> mergeDelayError(int maxConcurrency, Iterable<? extends NbpObservable<? extends T>> sources) {
        return fromIterable(sources).flatMap((Function)Functions.identity(), true, maxConcurrency);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> mergeDelayError(int maxConcurrency, NbpObservable<? extends T>... sources) {
        return fromArray(sources).flatMap((Function)Functions.identity(), true, maxConcurrency);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> NbpObservable<T> mergeDelayError(NbpObservable<? extends NbpObservable<? extends T>> sources) {
        return sources.flatMap((Function)Functions.identity(), true);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> mergeDelayError(NbpObservable<? extends NbpObservable<? extends T>> sources, int maxConcurrency) {
        return sources.flatMap((Function)Functions.identity(), true, maxConcurrency);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @BackpressureSupport(BackpressureKind.FULL)
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> mergeDelayError(NbpObservable<? extends T> p1, NbpObservable<? extends T> p2) {
        Objects.requireNonNull(p1, "p1 is null");
        Objects.requireNonNull(p2, "p2 is null");
        return fromArray(p1, p2).flatMap((Function)Functions.identity(), true, 2);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @BackpressureSupport(BackpressureKind.FULL)
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> mergeDelayError(NbpObservable<? extends T> p1, NbpObservable<? extends T> p2, NbpObservable<? extends T> p3) {
        Objects.requireNonNull(p1, "p1 is null");
        Objects.requireNonNull(p2, "p2 is null");
        Objects.requireNonNull(p3, "p3 is null");
        return fromArray(p1, p2, p3).flatMap((Function)Functions.identity(), true, 3);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @BackpressureSupport(BackpressureKind.FULL)
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> mergeDelayError(
            NbpObservable<? extends T> p1, NbpObservable<? extends T> p2, 
            NbpObservable<? extends T> p3, NbpObservable<? extends T> p4) {
        Objects.requireNonNull(p1, "p1 is null");
        Objects.requireNonNull(p2, "p2 is null");
        Objects.requireNonNull(p3, "p3 is null");
        Objects.requireNonNull(p4, "p4 is null");
        return fromArray(p1, p2, p3, p4).flatMap((Function)Functions.identity(), true, 4);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> mergeDelayError(NbpObservable<? extends T>... sources) {
        return fromArray(sources).flatMap((Function)Functions.identity(), true, sources.length);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    @SuppressWarnings("unchecked")
    public static <T> NbpObservable<T> never() {
        return (NbpObservable<T>)NEVER;
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static NbpObservable<Integer> range(final int start, final int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count >= required but it was " + count);
        } else
        if (count == 0) {
            return empty();
        } else
        if (count == 1) {
            return just(start);
        } else
        if ((long)start + (count - 1) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Integer overflow");
        }
        return create(new NbpOnSubscribe<Integer>() {
            @Override
            public void accept(NbpSubscriber<? super Integer> s) {
                BooleanDisposable d = new BooleanDisposable();
                s.onSubscribe(d);
                
                long end = start - 1L + count;
                for (long i = start; i <= end && !d.isDisposed(); i++) {
                    s.onNext((int)i);
                }
                if (!d.isDisposed()) {
                    s.onComplete();
                }
            }
        });
    }

    /**
     *
     * @deprecated use composition
     */
    @SchedulerSupport(SchedulerKind.CUSTOM)
    @Deprecated
    public static NbpObservable<Integer> range(int start, int count, Scheduler scheduler) {
        return range(start, count).subscribeOn(scheduler);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<Boolean> sequenceEqual(NbpObservable<? extends T> p1, NbpObservable<? extends T> p2) {
        return sequenceEqual(p1, p2, Objects.equalsPredicate(), bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<Boolean> sequenceEqual(NbpObservable<? extends T> p1, NbpObservable<? extends T> p2, BiPredicate<? super T, ? super T> isEqual) {
        return sequenceEqual(p1, p2, isEqual, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<Boolean> sequenceEqual(NbpObservable<? extends T> p1, NbpObservable<? extends T> p2, BiPredicate<? super T, ? super T> isEqual, int bufferSize) {
        Objects.requireNonNull(p1, "p1 is null");
        Objects.requireNonNull(p2, "p2 is null");
        Objects.requireNonNull(isEqual, "isEqual is null");
        validateBufferSize(bufferSize);
        return create(new NbpOnSubscribeSequenceEqual<T>(p1, p2, isEqual, bufferSize));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<Boolean> sequenceEqual(NbpObservable<? extends T> p1, NbpObservable<? extends T> p2, int bufferSize) {
        return sequenceEqual(p1, p2, Objects.equalsPredicate(), bufferSize);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @BackpressureSupport(BackpressureKind.FULL)
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> switchOnNext(int bufferSize, NbpObservable<? extends NbpObservable<? extends T>> sources) {
        return sources.switchMap((Function)Functions.identity(), bufferSize);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T> NbpObservable<T> switchOnNext(NbpObservable<? extends NbpObservable<? extends T>> sources) {
        return sources.switchMap((Function)Functions.identity());
    }

    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public static NbpObservable<Long> timer(long delay, TimeUnit unit) {
        return timer(delay, unit, Schedulers.computation());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public static NbpObservable<Long> timer(long delay, TimeUnit unit, Scheduler scheduler) {
        if (delay < 0) {
            delay = 0L;
        }
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");

        return create(new NbpOnSubscribeTimerOnceSource(delay, unit, scheduler));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, D> NbpObservable<T> using(Supplier<? extends D> resourceSupplier, Function<? super D, ? extends NbpObservable<? extends T>> sourceSupplier, Consumer<? super D> disposer) {
        return using(resourceSupplier, sourceSupplier, disposer, true);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, D> NbpObservable<T> using(Supplier<? extends D> resourceSupplier, Function<? super D, ? extends NbpObservable<? extends T>> sourceSupplier, Consumer<? super D> disposer, boolean eager) {
        Objects.requireNonNull(resourceSupplier, "resourceSupplier is null");
        Objects.requireNonNull(sourceSupplier, "sourceSupplier is null");
        Objects.requireNonNull(disposer, "disposer is null");
        return create(new NbpOnSubscribeUsing<T, D>(resourceSupplier, sourceSupplier, disposer, eager));
    }

    private static void validateBufferSize(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize > 0 required but it was " + bufferSize);
        }
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, R> NbpObservable<R> zip(Iterable<? extends NbpObservable<? extends T>> sources, Function<? super Object[], ? extends R> zipper) {
        Objects.requireNonNull(zipper, "zipper is null");
        Objects.requireNonNull(sources, "sources is null");
        return create(new NbpOnSubscribeZip<T, R>(null, sources, zipper, bufferSize(), false));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, R> NbpObservable<R> zip(NbpObservable<? extends NbpObservable<? extends T>> sources, final Function<Object[], R> zipper) {
        Objects.requireNonNull(zipper, "zipper is null");
        return sources.toList().flatMap(new Function<List<? extends NbpObservable<? extends T>>, NbpObservable<R>>() {
            @Override
            public NbpObservable<R> apply(List<? extends NbpObservable<? extends T>> list) {
                return zipIterable(zipper, false, bufferSize(), list);
            }
        });
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, R> NbpObservable<R> zip(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, 
            BiFunction<? super T1, ? super T2, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, R> NbpObservable<R> zip(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, 
            BiFunction<? super T1, ? super T2, ? extends R> zipper, boolean delayError) {
        return zipArray(Functions.toFunction(zipper), delayError, bufferSize(), p1, p2);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, R> NbpObservable<R> zip(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, 
            BiFunction<? super T1, ? super T2, ? extends R> zipper, boolean delayError, int bufferSize) {
        return zipArray(Functions.toFunction(zipper), delayError, bufferSize, p1, p2);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, T3, R> NbpObservable<R> zip(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, NbpObservable<? extends T3> p3, 
            Function3<? super T1, ? super T2, ? super T3, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2, p3);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, T3, T4, R> NbpObservable<R> zip(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, NbpObservable<? extends T3> p3,
            NbpObservable<? extends T4> p4,
            Function4<? super T1, ? super T2, ? super T3, ? super T4, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2, p3, p4);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, T3, T4, T5, R> NbpObservable<R> zip(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, NbpObservable<? extends T3> p3,
            NbpObservable<? extends T4> p4, NbpObservable<? extends T5> p5,
            Function5<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2, p3, p4, p5);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, T3, T4, T5, T6, R> NbpObservable<R> zip(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, NbpObservable<? extends T3> p3,
            NbpObservable<? extends T4> p4, NbpObservable<? extends T5> p5, NbpObservable<? extends T6> p6,
            Function6<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2, p3, p4, p5, p6);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, T3, T4, T5, T6, T7, R> NbpObservable<R> zip(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, NbpObservable<? extends T3> p3,
            NbpObservable<? extends T4> p4, NbpObservable<? extends T5> p5, NbpObservable<? extends T6> p6,
            NbpObservable<? extends T7> p7,
            Function7<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2, p3, p4, p5, p6, p7);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, T3, T4, T5, T6, T7, T8, R> NbpObservable<R> zip(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, NbpObservable<? extends T3> p3,
            NbpObservable<? extends T4> p4, NbpObservable<? extends T5> p5, NbpObservable<? extends T6> p6,
            NbpObservable<? extends T7> p7, NbpObservable<? extends T8> p8,
            Function8<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? super T8, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> NbpObservable<R> zip(
            NbpObservable<? extends T1> p1, NbpObservable<? extends T2> p2, NbpObservable<? extends T3> p3,
            NbpObservable<? extends T4> p4, NbpObservable<? extends T5> p5, NbpObservable<? extends T6> p6,
            NbpObservable<? extends T7> p7, NbpObservable<? extends T8> p8, NbpObservable<? extends T9> p9,
            Function9<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? super T8, ? super T9, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, R> NbpObservable<R> zipArray(Function<? super Object[], ? extends R> zipper, 
            boolean delayError, int bufferSize, NbpObservable<? extends T>... sources) {
        if (sources.length == 0) {
            return empty();
        }
        Objects.requireNonNull(zipper, "zipper is null");
        validateBufferSize(bufferSize);
        return create(new NbpOnSubscribeZip<T, R>(sources, null, zipper, bufferSize, delayError));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public static <T, R> NbpObservable<R> zipIterable(Function<? super Object[], ? extends R> zipper,
            boolean delayError, int bufferSize, 
            Iterable<? extends NbpObservable<? extends T>> sources) {
        Objects.requireNonNull(zipper, "zipper is null");
        Objects.requireNonNull(sources, "sources is null");
        validateBufferSize(bufferSize);
        return create(new NbpOnSubscribeZip<T, R>(null, sources, zipper, bufferSize, delayError));
    }

    
    protected final NbpOnSubscribe<T> onSubscribe;

    protected NbpObservable(NbpOnSubscribe<T> onSubscribe) {
        this.onSubscribe = onSubscribe;
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<Boolean> all(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return lift(new NbpOperatorAll<T>(predicate));
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> ambWith(NbpObservable<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return amb(this, other);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<Boolean> any(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return lift(new NbpOperatorAny<T>(predicate));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> asObservable() {
        return create(new NbpOnSubscribe<T>() {
            @Override
            public void accept(NbpSubscriber<? super T> s) {
                NbpObservable.this.subscribe(s);
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<List<T>> buffer(int count) {
        return buffer(count, count);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<List<T>> buffer(int count, int skip) {
        return buffer(count, skip, new Supplier<List<T>>() {
            @Override
            public List<T> get() {
                return new ArrayList<T>();
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U extends Collection<? super T>> NbpObservable<U> buffer(int count, int skip, Supplier<U> bufferSupplier) {
        if (count <= 0) {
            throw new IllegalArgumentException("count > 0 required but it was " + count);
        }
        if (skip <= 0) {
            throw new IllegalArgumentException("skip > 0 required but it was " + count);
        }
        Objects.requireNonNull(bufferSupplier, "bufferSupplier is null");
        return lift(new NbpOperatorBuffer<T, U>(count, skip, bufferSupplier));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U extends Collection<? super T>> NbpObservable<U> buffer(int count, Supplier<U> bufferSupplier) {
        return buffer(count, count, bufferSupplier);
    }

    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<List<T>> buffer(long timespan, long timeskip, TimeUnit unit) {
        return buffer(timespan, timeskip, unit, Schedulers.computation(), new Supplier<List<T>>() {
            @Override
            public List<T> get() {
                return new ArrayList<T>();
            }
        });
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<List<T>> buffer(long timespan, long timeskip, TimeUnit unit, Scheduler scheduler) {
        return buffer(timespan, timeskip, unit, scheduler, new Supplier<List<T>>() {
            @Override
            public List<T> get() {
                return new ArrayList<T>();
            }
        });
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final <U extends Collection<? super T>> NbpObservable<U> buffer(long timespan, long timeskip, TimeUnit unit, Scheduler scheduler, Supplier<U> bufferSupplier) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        Objects.requireNonNull(bufferSupplier, "bufferSupplier is null");
        return lift(new NbpOperatorBufferTimed<T, U>(timespan, timeskip, unit, scheduler, bufferSupplier, Integer.MAX_VALUE, false));
    }

    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<List<T>> buffer(long timespan, TimeUnit unit) {
        return buffer(timespan, unit, Integer.MAX_VALUE, Schedulers.computation());
    }

    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<List<T>> buffer(long timespan, TimeUnit unit, int count) {
        return buffer(timespan, unit, count, Schedulers.computation());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<List<T>> buffer(long timespan, TimeUnit unit, int count, Scheduler scheduler) {
        return buffer(timespan, unit, count, scheduler, new Supplier<List<T>>() {
            @Override
            public List<T> get() {
                return new ArrayList<T>();
            }
        }, false);
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final <U extends Collection<? super T>> NbpObservable<U> buffer(
            long timespan, TimeUnit unit, 
            int count, Scheduler scheduler, 
            Supplier<U> bufferSupplier, 
            boolean restartTimerOnMaxSize) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        Objects.requireNonNull(bufferSupplier, "bufferSupplier is null");
        if (count <= 0) {
            throw new IllegalArgumentException("count > 0 required but it was " + count);
        }
        return lift(new NbpOperatorBufferTimed<T, U>(timespan, timespan, unit, scheduler, bufferSupplier, count, restartTimerOnMaxSize));
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<List<T>> buffer(long timespan, TimeUnit unit, Scheduler scheduler) {
        return buffer(timespan, unit, Integer.MAX_VALUE, scheduler, new Supplier<List<T>>() {
            @Override
            public List<T> get() {
                return new ArrayList<T>();
            }
        }, false);
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final <TOpening, TClosing> NbpObservable<List<T>> buffer(
            NbpObservable<? extends TOpening> bufferOpenings, 
            Function<? super TOpening, ? extends NbpObservable<? extends TClosing>> bufferClosingSelector) {
        return buffer(bufferOpenings, bufferClosingSelector, new Supplier<List<T>>() {
            @Override
            public List<T> get() {
                return new ArrayList<T>();
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <TOpening, TClosing, U extends Collection<? super T>> NbpObservable<U> buffer(
            NbpObservable<? extends TOpening> bufferOpenings, 
            Function<? super TOpening, ? extends NbpObservable<? extends TClosing>> bufferClosingSelector,
            Supplier<U> bufferSupplier) {
        Objects.requireNonNull(bufferOpenings, "bufferOpenings is null");
        Objects.requireNonNull(bufferClosingSelector, "bufferClosingSelector is null");
        Objects.requireNonNull(bufferSupplier, "bufferSupplier is null");
        return lift(new NbpOperatorBufferBoundary<T, U, TOpening, TClosing>(bufferOpenings, bufferClosingSelector, bufferSupplier));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <B> NbpObservable<List<T>> buffer(NbpObservable<B> boundary) {
        /*
         * XXX: javac complains if this is not manually cast, Eclipse is fine
         */
        return buffer(boundary, new Supplier<List<T>>() {
            @Override
            public List<T> get() {
                return new ArrayList<T>();
            }
        });
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final <B> NbpObservable<List<T>> buffer(NbpObservable<B> boundary, final int initialCapacity) {
        return buffer(boundary, new Supplier<List<T>>() {
            @Override
            public List<T> get() {
                return new ArrayList<T>(initialCapacity);
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <B, U extends Collection<? super T>> NbpObservable<U> buffer(NbpObservable<B> boundary, Supplier<U> bufferSupplier) {
        Objects.requireNonNull(boundary, "boundary is null");
        Objects.requireNonNull(bufferSupplier, "bufferSupplier is null");
        return lift(new NbpOperatorBufferExactBoundary<T, U, B>(boundary, bufferSupplier));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <B> NbpObservable<List<T>> buffer(Supplier<? extends NbpObservable<B>> boundarySupplier) {
        return buffer(boundarySupplier, new Supplier<List<T>>() {
            @Override
            public List<T> get() {
                return new ArrayList<T>();
            }
        });
        
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <B, U extends Collection<? super T>> NbpObservable<U> buffer(Supplier<? extends NbpObservable<B>> boundarySupplier, Supplier<U> bufferSupplier) {
        Objects.requireNonNull(boundarySupplier, "boundarySupplier is null");
        Objects.requireNonNull(bufferSupplier, "bufferSupplier is null");
        return lift(new NbpOperatorBufferBoundarySupplier<T, U, B>(boundarySupplier, bufferSupplier));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> cache() {
        return NbpCachedObservable.from(this);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> cache(int capacityHint) {
        return NbpCachedObservable.from(this, capacityHint);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U> NbpObservable<U> cast(final Class<U> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        return map(new Function<T, U>() {
            @Override
            public U apply(T v) {
                return clazz.cast(v);
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U> NbpObservable<U> collect(Supplier<? extends U> initialValueSupplier, BiConsumer<? super U, ? super T> collector) {
        Objects.requireNonNull(initialValueSupplier, "initalValueSupplier is null");
        Objects.requireNonNull(collector, "collector is null");
        return lift(new NbpOperatorCollect<T, U>(initialValueSupplier, collector));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U> NbpObservable<U> collectInto(final U initialValue, BiConsumer<? super U, ? super T> collector) {
        Objects.requireNonNull(initialValue, "initialValue is null");
        return collect(new Supplier<U>() {
            @Override
            public U get() {
                return initialValue;
            }
        }, collector);
    }

    public final <R> NbpObservable<R> compose(Function<? super NbpObservable<T>, ? extends NbpObservable<R>> convert) {
        return to(convert);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> concatMap(Function<? super T, ? extends NbpObservable<? extends R>> mapper) {
        return concatMap(mapper, 2);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> concatMap(Function<? super T, ? extends NbpObservable<? extends R>> mapper, int prefetch) {
        Objects.requireNonNull(mapper, "mapper is null");
        if (prefetch <= 0) {
            throw new IllegalArgumentException("prefetch > 0 required but it was " + prefetch);
        }
        return lift(new NbpOperatorConcatMap<T, R>(mapper, prefetch));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U> NbpObservable<U> concatMapIterable(final Function<? super T, ? extends Iterable<? extends U>> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return concatMap(new Function<T, NbpObservable<U>>() {
            @Override
            public NbpObservable<U> apply(T v) {
                return fromIterable(mapper.apply(v));
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U> NbpObservable<U> concatMapIterable(final Function<? super T, ? extends Iterable<? extends U>> mapper, int prefetch) {
        return concatMap(new Function<T, NbpObservable<U>>() {
            @Override
            public NbpObservable<U> apply(T v) {
                return fromIterable(mapper.apply(v));
            }
        }, prefetch);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> concatWith(NbpObservable<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return concat(this, other);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<Boolean> contains(final Object o) {
        Objects.requireNonNull(o, "o is null");
        return any(new Predicate<T>() {
            @Override
            public boolean test(T v) {
                return Objects.equals(v, o);
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<Long> count() {
        return lift(NbpOperatorCount.instance());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U> NbpObservable<T> debounce(Function<? super T, ? extends NbpObservable<U>> debounceSelector) {
        Objects.requireNonNull(debounceSelector, "debounceSelector is null");
        return lift(new NbpOperatorDebounce<T, U>(debounceSelector));
    }

    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<T> debounce(long timeout, TimeUnit unit) {
        return debounce(timeout, unit, Schedulers.computation());
    }

    @BackpressureSupport(BackpressureKind.ERROR)
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> debounce(long timeout, TimeUnit unit, Scheduler scheduler) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return lift(new NbpOperatorDebounceTimed<T>(timeout, unit, scheduler));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> defaultIfEmpty(T value) {
        Objects.requireNonNull(value, "value is null");
        return switchIfEmpty(just(value));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    // TODO a more efficient implementation if necessary
    public final <U> NbpObservable<T> delay(final Function<? super T, ? extends NbpObservable<U>> itemDelay) {
        Objects.requireNonNull(itemDelay, "itemDelay is null");
        return flatMap(new Function<T, NbpObservable<T>>() {
            @Override
            public NbpObservable<T> apply(final T v) {
                return itemDelay.apply(v).take(1).map(new Function<U, T>() {
                    @Override
                    public T apply(U u) {
                        return v;
                    }
                }).defaultIfEmpty(v);
            }
        });
    }

    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<T> delay(long delay, TimeUnit unit) {
        return delay(delay, unit, Schedulers.computation(), false);
    }

    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<T> delay(long delay, TimeUnit unit, boolean delayError) {
        return delay(delay, unit, Schedulers.computation(), delayError);
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> delay(long delay, TimeUnit unit, Scheduler scheduler) {
        return delay(delay, unit, scheduler, false);
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> delay(long delay, TimeUnit unit, Scheduler scheduler, boolean delayError) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        
        return lift(new NbpOperatorDelay<T>(delay, unit, scheduler, delayError));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U, V> NbpObservable<T> delay(Supplier<? extends NbpObservable<U>> delaySupplier,
            Function<? super T, ? extends NbpObservable<V>> itemDelay) {
        return delaySubscription(delaySupplier).delay(itemDelay);
    }

    /**
     * Returns an Observable that delays the subscription to this Observable
     * until the other Observable emits an element or completes normally.
     * <p>
     * <dl>
     *  <dt><b>Backpressure:</b></dt>
     *  <dd>The operator forwards the backpressure requests to this Observable once
     *  the subscription happens and requests Long.MAX_VALUE from the other Observable</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This method does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the value type of the other Observable, irrelevant
     * @param other the other Observable that should trigger the subscription
     *        to this Observable.
     * @return an Observable that delays the subscription to this Observable
     *         until the other Observable emits an element or completes normally.
     */
    @Experimental
    public final <U> NbpObservable<T> delaySubscription(NbpObservable<U> other) {
        Objects.requireNonNull(other, "other is null");
        return create(new NbpOnSubscribeDelaySubscriptionOther<T, U>(this, other));
    }
    
    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<T> delaySubscription(long delay, TimeUnit unit) {
        return delaySubscription(delay, unit, Schedulers.computation());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    // TODO a more efficient implementation if necessary
    public final NbpObservable<T> delaySubscription(long delay, TimeUnit unit, Scheduler scheduler) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        
        return timer(delay, unit, scheduler).flatMap(new Function<Long, NbpObservable<T>>() {
            @Override
            public NbpObservable<T> apply(Long v) {
                return NbpObservable.this;
            }
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public final <U> NbpObservable<T> delaySubscription(final Supplier<? extends NbpObservable<U>> delaySupplier) {
        Objects.requireNonNull(delaySupplier, "delaySupplier is null");
        return fromCallable(new Callable<NbpObservable<U>>() {
            @Override
            public NbpObservable<U> call() throws Exception {
                return delaySupplier.get();
            }
        })
        .flatMap((Function)Functions.identity())
        .take(1)
        .cast(Object.class)
        .defaultIfEmpty(OBJECT)
        .flatMap(new Function<Object, NbpObservable<T>>() {
            @Override
            public NbpObservable<T> apply(Object v) {
                return NbpObservable.this;
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <T2> NbpObservable<T2> dematerialize() {
        @SuppressWarnings("unchecked")
        NbpObservable<Try<Optional<T2>>> m = (NbpObservable<Try<Optional<T2>>>)this;
        return m.lift(NbpOperatorDematerialize.<T2>instance());
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> distinct() {
        return distinct((Function)Functions.identity(), new Supplier<Collection<T>>() {
            @Override
            public Collection<T> get() {
                return new HashSet<T>();
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <K> NbpObservable<T> distinct(Function<? super T, K> keySelector) {
        return distinct(keySelector, new Supplier<Collection<K>>() {
            @Override
            public Collection<K> get() {
                return new HashSet<K>();
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <K> NbpObservable<T> distinct(Function<? super T, K> keySelector, Supplier<? extends Collection<? super K>> collectionSupplier) {
        Objects.requireNonNull(keySelector, "keySelector is null");
        Objects.requireNonNull(collectionSupplier, "collectionSupplier is null");
        return lift(NbpOperatorDistinct.withCollection(keySelector, collectionSupplier));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> distinctUntilChanged() {
        return lift(NbpOperatorDistinct.<T>untilChanged());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <K> NbpObservable<T> distinctUntilChanged(Function<? super T, K> keySelector) {
        Objects.requireNonNull(keySelector, "keySelector is null");
        return lift(NbpOperatorDistinct.untilChanged(keySelector));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> doOnCancel(Runnable onCancel) {
        return doOnLifecycle(Functions.emptyConsumer(), onCancel);
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> doOnComplete(Runnable onComplete) {
        return doOnEach(Functions.emptyConsumer(), Functions.emptyConsumer(), onComplete, Functions.emptyRunnable());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    private NbpObservable<T> doOnEach(Consumer<? super T> onNext, Consumer<? super Throwable> onError, Runnable onComplete, Runnable onAfterTerminate) {
        Objects.requireNonNull(onNext, "onNext is null");
        Objects.requireNonNull(onError, "onError is null");
        Objects.requireNonNull(onComplete, "onComplete is null");
        Objects.requireNonNull(onAfterTerminate, "onAfterTerminate is null");
        return lift(new NbpOperatorDoOnEach<T>(onNext, onError, onComplete, onAfterTerminate));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> doOnEach(final Consumer<? super Try<Optional<T>>> consumer) {
        Objects.requireNonNull(consumer, "consumer is null");
        return doOnEach(
                new Consumer<T>() {
                    @Override
                    public void accept(T v) {
                        consumer.accept(Try.ofValue(Optional.of(v)));
                    }
                },
                new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable e) {
                        consumer.accept(Try.<Optional<T>>ofError(e));
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        consumer.accept(Try.ofValue(Optional.<T>empty()));
                    }
                },
                Functions.emptyRunnable()
                );
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> doOnEach(final NbpSubscriber<? super T> observer) {
        Objects.requireNonNull(observer, "observer is null");
        return doOnEach(new Consumer<T>() {
            @Override
            public void accept(T v) {
                observer.onNext(v);
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) {
                observer.onError(e);
            }
        }, new Runnable() {
            @Override
            public void run() {
                observer.onComplete();
            }
        }, Functions.emptyRunnable());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> doOnError(Consumer<? super Throwable> onError) {
        return doOnEach(Functions.emptyConsumer(), onError, Functions.emptyRunnable(), Functions.emptyRunnable());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> doOnLifecycle(final Consumer<? super Disposable> onSubscribe, final Runnable onCancel) {
        Objects.requireNonNull(onSubscribe, "onSubscribe is null");
        Objects.requireNonNull(onCancel, "onCancel is null");
        return lift(new NbpOperator<T, T>() {
            @Override
            public NbpSubscriber<? super T> apply(NbpSubscriber<? super T> s) {
                return new NbpSubscriptionLambdaSubscriber<T>(s, onSubscribe, onCancel);
            }
        });
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> doOnNext(Consumer<? super T> onNext) {
        return doOnEach(onNext, Functions.emptyConsumer(), Functions.emptyRunnable(), Functions.emptyRunnable());
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> doOnSubscribe(Consumer<? super Disposable> onSubscribe) {
        return doOnLifecycle(onSubscribe, Functions.emptyRunnable());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> doOnTerminate(final Runnable onTerminate) {
        return doOnEach(Functions.emptyConsumer(), new Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) {
                onTerminate.run();
            }
        }, onTerminate, Functions.emptyRunnable());
    }

    /**
     *
     * @deprecated use {@link #doOnCancel(Runnable)} instead
     */
    @SchedulerSupport(SchedulerKind.NONE)
    @Deprecated
    public final NbpObservable<T> doOnUnsubscribe(Runnable onUnsubscribe) {
        return doOnCancel(onUnsubscribe);
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> elementAt(long index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("index >= 0 required but it was " + index);
        }
        return lift(new NbpOperatorElementAt<T>(index, null));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> elementAt(long index, T defaultValue) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("index >= 0 required but it was " + index);
        }
        Objects.requireNonNull(defaultValue, "defaultValue is null");
        return lift(new NbpOperatorElementAt<T>(index, defaultValue));
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> endWith(Iterable<? extends T> values) {
        return concatArray(this, fromIterable(values));
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> endWith(NbpObservable<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return concatArray(this, other);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> endWith(T value) {
        Objects.requireNonNull(value, "value is null");
        return concatArray(this, just(value));
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> endWithArray(T... values) {
        NbpObservable<T> fromArray = fromArray(values);
        if (fromArray == empty()) {
            return this;
        }
        return concatArray(this, fromArray);
    }

    /**
     * @deprecated use {@link #any(Predicate)}
     */
    @SchedulerSupport(SchedulerKind.NONE)
    @Deprecated
    public final NbpObservable<Boolean> exists(Predicate<? super T> predicate) {
        return any(predicate);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return lift(new NbpOperatorFilter<T>(predicate));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> finallyDo(Runnable onFinally) {
        return doOnEach(Functions.emptyConsumer(), Functions.emptyConsumer(), Functions.emptyRunnable(), onFinally);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> first() {
        return take(1).single();
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> first(T defaultValue) {
        return take(1).single(defaultValue);
    }

    public final <R> NbpObservable<R> flatMap(Function<? super T, ? extends NbpObservable<? extends R>> mapper) {
        return flatMap(mapper, false);
    }


    public final <R> NbpObservable<R> flatMap(Function<? super T, ? extends NbpObservable<? extends R>> mapper, boolean delayError) {
        return flatMap(mapper, delayError, Integer.MAX_VALUE);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> flatMap(Function<? super T, ? extends NbpObservable<? extends R>> mapper, boolean delayErrors, int maxConcurrency) {
        return flatMap(mapper, delayErrors, maxConcurrency, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> flatMap(Function<? super T, ? extends NbpObservable<? extends R>> mapper, 
            boolean delayErrors, int maxConcurrency, int bufferSize) {
        Objects.requireNonNull(mapper, "mapper is null");
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency > 0 required but it was " + maxConcurrency);
        }
        validateBufferSize(bufferSize);
        if (this instanceof NbpObservableScalarSource) {
            NbpObservableScalarSource<T> scalar = (NbpObservableScalarSource<T>) this;
            return create(scalar.scalarFlatMap(mapper));
        }
        return lift(new NbpOperatorFlatMap<T, R>(mapper, delayErrors, maxConcurrency, bufferSize));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> flatMap(
            Function<? super T, ? extends NbpObservable<? extends R>> onNextMapper, 
            Function<? super Throwable, ? extends NbpObservable<? extends R>> onErrorMapper, 
            Supplier<? extends NbpObservable<? extends R>> onCompleteSupplier) {
        Objects.requireNonNull(onNextMapper, "onNextMapper is null");
        Objects.requireNonNull(onErrorMapper, "onErrorMapper is null");
        Objects.requireNonNull(onCompleteSupplier, "onCompleteSupplier is null");
        return merge(lift(new NbpOperatorMapNotification<T, R>(onNextMapper, onErrorMapper, onCompleteSupplier)));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> flatMap(
            Function<? super T, ? extends NbpObservable<? extends R>> onNextMapper, 
            Function<Throwable, ? extends NbpObservable<? extends R>> onErrorMapper, 
            Supplier<? extends NbpObservable<? extends R>> onCompleteSupplier, 
            int maxConcurrency) {
        Objects.requireNonNull(onNextMapper, "onNextMapper is null");
        Objects.requireNonNull(onErrorMapper, "onErrorMapper is null");
        Objects.requireNonNull(onCompleteSupplier, "onCompleteSupplier is null");
        return merge(lift(new NbpOperatorMapNotification<T, R>(onNextMapper, onErrorMapper, onCompleteSupplier)), maxConcurrency);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> flatMap(Function<? super T, ? extends NbpObservable<? extends R>> mapper, int maxConcurrency) {
        return flatMap(mapper, false, maxConcurrency, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U, R> NbpObservable<R> flatMap(Function<? super T, ? extends NbpObservable<? extends U>> mapper, BiFunction<? super T, ? super U, ? extends R> resultSelector) {
        return flatMap(mapper, resultSelector, false, bufferSize(), bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U, R> NbpObservable<R> flatMap(Function<? super T, ? extends NbpObservable<? extends U>> mapper, BiFunction<? super T, ? super U, ? extends R> combiner, boolean delayError) {
        return flatMap(mapper, combiner, delayError, bufferSize(), bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U, R> NbpObservable<R> flatMap(Function<? super T, ? extends NbpObservable<? extends U>> mapper, BiFunction<? super T, ? super U, ? extends R> combiner, boolean delayError, int maxConcurrency) {
        return flatMap(mapper, combiner, delayError, maxConcurrency, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U, R> NbpObservable<R> flatMap(final Function<? super T, ? extends NbpObservable<? extends U>> mapper, final BiFunction<? super T, ? super U, ? extends R> combiner, boolean delayError, int maxConcurrency, int bufferSize) {
        Objects.requireNonNull(mapper, "mapper is null");
        Objects.requireNonNull(combiner, "combiner is null");
        return flatMap(new Function<T, NbpObservable<R>>() {
            @Override
            public NbpObservable<R> apply(final T t) {
                @SuppressWarnings("unchecked")
                NbpObservable<U> u = (NbpObservable<U>)mapper.apply(t);
                return u.map(new Function<U, R>() {
                    @Override
                    public R apply(U w) {
                        return combiner.apply(t, w);
                    }
                });
            }
        }, delayError, maxConcurrency, bufferSize);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U, R> NbpObservable<R> flatMap(Function<? super T, ? extends NbpObservable<? extends U>> mapper, BiFunction<? super T, ? super U, ? extends R> combiner, int maxConcurrency) {
        return flatMap(mapper, combiner, false, maxConcurrency, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U> NbpObservable<U> flatMapIterable(final Function<? super T, ? extends Iterable<? extends U>> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return flatMap(new Function<T, NbpObservable<U>>() {
            @Override
            public NbpObservable<U> apply(T v) {
                return fromIterable(mapper.apply(v));
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U, V> NbpObservable<V> flatMapIterable(final Function<? super T, ? extends Iterable<? extends U>> mapper, BiFunction<? super T, ? super U, ? extends V> resultSelector) {
        return flatMap(new Function<T, NbpObservable<U>>() {
            @Override
            public NbpObservable<U> apply(T t) {
                return fromIterable(mapper.apply(t));
            }
        }, resultSelector, false, bufferSize(), bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U> NbpObservable<U> flatMapIterable(final Function<? super T, ? extends Iterable<? extends U>> mapper, int bufferSize) {
        return flatMap(new Function<T, NbpObservable<U>>() {
            @Override
            public NbpObservable<U> apply(T v) {
                return fromIterable(mapper.apply(v));
            }
        }, false, bufferSize);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final Disposable forEach(Consumer<? super T> onNext) {
        return subscribe(onNext);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final Disposable forEachWhile(Predicate<? super T> onNext) {
        return forEachWhile(onNext, RxJavaPlugins.errorConsumer(), Functions.emptyRunnable());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final Disposable forEachWhile(Predicate<? super T> onNext, Consumer<? super Throwable> onError) {
        return forEachWhile(onNext, onError, Functions.emptyRunnable());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final Disposable forEachWhile(final Predicate<? super T> onNext, Consumer<? super Throwable> onError,
            final Runnable onComplete) {
        Objects.requireNonNull(onNext, "onNext is null");
        Objects.requireNonNull(onError, "onError is null");
        Objects.requireNonNull(onComplete, "onComplete is null");

        final AtomicReference<Disposable> subscription = new AtomicReference<Disposable>();
        return subscribe(new Consumer<T>() {
            @Override
            public void accept(T v) {
                if (!onNext.test(v)) {
                    subscription.get().dispose();
                    onComplete.run();
                }
            }
        }, onError, onComplete, new Consumer<Disposable>() {
            @Override
            public void accept(Disposable s) {
                subscription.lazySet(s);
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final List<T> getList() {
        final List<T> result = new ArrayList<T>();
        final Throwable[] error = { null };
        final CountDownLatch cdl = new CountDownLatch(1);
        
        subscribe(new NbpSubscriber<T>() {
            @Override
            public void onComplete() {
                cdl.countDown();
            }
            @Override
            public void onError(Throwable e) {
                error[0] = e;
                cdl.countDown();
            }
            @Override
            public void onNext(T value) {
                result.add(value);
            }
            @Override
            public void onSubscribe(Disposable d) {
                
            }
        });
        
        if (cdl.getCount() != 0) {
            try {
                cdl.await();
            } catch (InterruptedException ex) {
                throw Exceptions.propagate(ex);
            }
        }
        
        Throwable e = error[0];
        if (e != null) {
            throw Exceptions.propagate(e);
        }
        
        return result;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public final <K> NbpObservable<NbpGroupedObservable<K, T>> groupBy(Function<? super T, ? extends K> keySelector) {
        return groupBy(keySelector, (Function)Functions.identity(), false, bufferSize());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerKind.NONE)
    public final <K> NbpObservable<NbpGroupedObservable<K, T>> groupBy(Function<? super T, ? extends K> keySelector, boolean delayError) {
        return groupBy(keySelector, (Function)Functions.identity(), delayError, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <K, V> NbpObservable<NbpGroupedObservable<K, V>> groupBy(Function<? super T, ? extends K> keySelector, 
            Function<? super T, ? extends V> valueSelector) {
        return groupBy(keySelector, valueSelector, false, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <K, V> NbpObservable<NbpGroupedObservable<K, V>> groupBy(Function<? super T, ? extends K> keySelector, 
            Function<? super T, ? extends V> valueSelector, boolean delayError) {
        return groupBy(keySelector, valueSelector, false, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <K, V> NbpObservable<NbpGroupedObservable<K, V>> groupBy(Function<? super T, ? extends K> keySelector, 
            Function<? super T, ? extends V> valueSelector, 
            boolean delayError, int bufferSize) {
        Objects.requireNonNull(keySelector, "keySelector is null");
        Objects.requireNonNull(valueSelector, "valueSelector is null");
        validateBufferSize(bufferSize);

        return lift(new NbpOperatorGroupBy<T, K, V>(keySelector, valueSelector, bufferSize, delayError));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> ignoreElements() {
        return lift(NbpOperatorIgnoreElements.<T>instance());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<Boolean> isEmpty() {
        return all(new Predicate<T>() {
            @Override
            public boolean test(T v) {
                return false;
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> last() {
        return takeLast(1).single();
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> last(T defaultValue) {
        return takeLast(1).single(defaultValue);
    }

    public final <R> NbpObservable<R> lift(NbpOperator<? extends R, ? super T> onLift) {
        Objects.requireNonNull(onLift, "onLift is null");
        return create(new NbpOnSubscribeLift<R, T>(this, onLift));
    }

    /**
     * @deprecated use {@link #take(long)} instead
     */
    @SchedulerSupport(SchedulerKind.NONE)
    @Deprecated
    public final NbpObservable<T> limit(long n) {
        return take(n);
    }

    public final <R> NbpObservable<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return lift(new NbpOperatorMap<T, R>(mapper));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<Try<Optional<T>>> materialize() {
        return lift(NbpOperatorMaterialize.<T>instance());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> mergeWith(NbpObservable<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return merge(this, other);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    @Deprecated
    public final NbpObservable<NbpObservable<T>> nest() {
        return just(this);
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> observeOn(Scheduler scheduler) {
        return observeOn(scheduler, false, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> observeOn(Scheduler scheduler, boolean delayError) {
        return observeOn(scheduler, delayError, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> observeOn(Scheduler scheduler, boolean delayError, int bufferSize) {
        Objects.requireNonNull(scheduler, "scheduler is null");
        validateBufferSize(bufferSize);
        return lift(new NbpOperatorObserveOn<T>(scheduler, delayError, bufferSize));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U> NbpObservable<U> ofType(final Class<U> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        return filter(new Predicate<T>() {
            @Override
            public boolean test(T v) {
                return clazz.isInstance(v);
            }
        }).cast(clazz);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> onErrorResumeNext(Function<? super Throwable, ? extends NbpObservable<? extends T>> resumeFunction) {
        Objects.requireNonNull(resumeFunction, "resumeFunction is null");
        return lift(new NbpOperatorOnErrorNext<T>(resumeFunction, false));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> onErrorResumeNext(final NbpObservable<? extends T> next) {
        Objects.requireNonNull(next, "next is null");
        return onErrorResumeNext(new Function<Throwable, NbpObservable<? extends T>>() {
            @Override
            public NbpObservable<? extends T> apply(Throwable e) {
                return next;
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> onErrorReturn(Function<? super Throwable, ? extends T> valueSupplier) {
        Objects.requireNonNull(valueSupplier, "valueSupplier is null");
        return lift(new NbpOperatorOnErrorReturn<T>(valueSupplier));
    }

    // TODO would result in ambiguity with onErrorReturn(Function)
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> onErrorReturnValue(final T value) {
        Objects.requireNonNull(value, "value is null");
        return onErrorReturn(new Function<Throwable, T>() {
            @Override
            public T apply(Throwable e) {
                return value;
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> onExceptionResumeNext(final NbpObservable<? extends T> next) {
        Objects.requireNonNull(next, "next is null");
        return lift(new NbpOperatorOnErrorNext<T>(new Function<Throwable, NbpObservable<? extends T>>() {
            @Override
            public NbpObservable<? extends T> apply(Throwable e) {
                return next;
            }
        }, true));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpConnectableObservable<T> publish() {
        return publish(bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> publish(Function<? super NbpObservable<T>, ? extends NbpObservable<R>> selector) {
        return publish(selector, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> publish(Function<? super NbpObservable<T>, ? extends NbpObservable<R>> selector, int bufferSize) {
        validateBufferSize(bufferSize);
        Objects.requireNonNull(selector, "selector is null");
        return NbpOperatorPublish.create(this, selector, bufferSize);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpConnectableObservable<T> publish(int bufferSize) {
        validateBufferSize(bufferSize);
        return NbpOperatorPublish.create(this, bufferSize);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> reduce(BiFunction<T, T, T> reducer) {
        return scan(reducer).last();
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> reduce(R seed, BiFunction<R, ? super T, R> reducer) {
        return scan(seed, reducer).last();
    }

    // Naming note, a plain scan would cause ambiguity with the value-seeded version
    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> reduceWith(Supplier<R> seedSupplier, BiFunction<R, ? super T, R> reducer) {
        return scanWith(seedSupplier, reducer).last();
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> repeat() {
        return repeat(Long.MAX_VALUE);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> repeat(long times) {
        if (times < 0) {
            throw new IllegalArgumentException("times >= 0 required but it was " + times);
        }
        if (times == 0) {
            return empty();
        }
        return create(new NbpOnSubscribeRepeat<T>(this, times));
    }

    /**
     *
     * @deprecated use composition
     */
    @Deprecated
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> repeat(long times, Scheduler scheduler) {
        return repeat(times).subscribeOn(scheduler);
    }

    /**
     *
     * @deprecated use composition
     */
    @SchedulerSupport(SchedulerKind.CUSTOM)
    @Deprecated
    public final NbpObservable<T> repeat(Scheduler scheduler) {
        return repeat().subscribeOn(scheduler);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> repeatUntil(BooleanSupplier stop) {
        Objects.requireNonNull(stop, "stop is null");
        return create(new NbpOnSubscribeRepeatUntil<T>(this, stop));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> repeatWhen(final Function<? super NbpObservable<Object>, ? extends NbpObservable<?>> handler) {
        Objects.requireNonNull(handler, "handler is null");
        
        Function<NbpObservable<Try<Optional<Object>>>, NbpObservable<?>> f = new Function<NbpObservable<Try<Optional<Object>>>, NbpObservable<?>>() {
            @Override
            public NbpObservable<?> apply(NbpObservable<Try<Optional<Object>>> no) {
                return handler.apply(no.map(new Function<Try<Optional<Object>>, Object>() {
                    @Override
                    public Object apply(Try<Optional<Object>> v) {
                        return 0;
                    }
                }));
            }
        }
        ;
        
        return create(new NbpOnSubscribeRedo<T>(this, f));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpConnectableObservable<T> replay() {
        return NbpOperatorReplay.createFrom(this);
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> replay(Function<? super NbpObservable<T>, ? extends NbpObservable<R>> selector) {
        Objects.requireNonNull(selector, "selector is null");
        return NbpOperatorReplay.multicastSelector(new Supplier<NbpConnectableObservable<T>>() {
            @Override
            public NbpConnectableObservable<T> get() {
                return replay();
            }
        }, selector);
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> replay(Function<? super NbpObservable<T>, ? extends NbpObservable<R>> selector, final int bufferSize) {
        Objects.requireNonNull(selector, "selector is null");
        return NbpOperatorReplay.multicastSelector(new Supplier<NbpConnectableObservable<T>>() {
            @Override
            public NbpConnectableObservable<T> get() {
                return replay(bufferSize);
            }
        }, selector);
    }
    
    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final <R> NbpObservable<R> replay(Function<? super NbpObservable<T>, ? extends NbpObservable<R>> selector, int bufferSize, long time, TimeUnit unit) {
        return replay(selector, bufferSize, time, unit, Schedulers.computation());
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final <R> NbpObservable<R> replay(Function<? super NbpObservable<T>, ? extends NbpObservable<R>> selector, final int bufferSize, final long time, final TimeUnit unit, final Scheduler scheduler) {
        if (bufferSize < 0) {
            throw new IllegalArgumentException("bufferSize < 0");
        }
        Objects.requireNonNull(selector, "selector is null");
        return NbpOperatorReplay.multicastSelector(new Supplier<NbpConnectableObservable<T>>() {
            @Override
            public NbpConnectableObservable<T> get() {
                return replay(bufferSize, time, unit, scheduler);
            }
        }, selector);
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final <R> NbpObservable<R> replay(final Function<? super NbpObservable<T>, ? extends NbpObservable<R>> selector, final int bufferSize, final Scheduler scheduler) {
        return NbpOperatorReplay.multicastSelector(new Supplier<NbpConnectableObservable<T>>() {
            @Override
            public NbpConnectableObservable<T> get() {
                return replay(bufferSize);
            }
        }, 
        new Function<NbpObservable<T>, NbpObservable<R>>() {
            @Override
            public NbpObservable<R> apply(NbpObservable<T> t) {
                return selector.apply(t).observeOn(scheduler);
            }
        });
    }

    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final <R> NbpObservable<R> replay(Function<? super NbpObservable<T>, ? extends NbpObservable<R>> selector, long time, TimeUnit unit) {
        return replay(selector, time, unit, Schedulers.computation());
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final <R> NbpObservable<R> replay(Function<? super NbpObservable<T>, ? extends NbpObservable<R>> selector, final long time, final TimeUnit unit, final Scheduler scheduler) {
        Objects.requireNonNull(selector, "selector is null");
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return NbpOperatorReplay.multicastSelector(new Supplier<NbpConnectableObservable<T>>() {
            @Override
            public NbpConnectableObservable<T> get() {
                return replay(time, unit, scheduler);
            }
        }, selector);
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final <R> NbpObservable<R> replay(final Function<? super NbpObservable<T>, ? extends NbpObservable<R>> selector, final Scheduler scheduler) {
        Objects.requireNonNull(selector, "selector is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return NbpOperatorReplay.multicastSelector(new Supplier<NbpConnectableObservable<T>>() {
            @Override
            public NbpConnectableObservable<T> get() {
                return replay();
            }
        }, 
        new Function<NbpObservable<T>, NbpObservable<R>>() {
            @Override
            public NbpObservable<R> apply(NbpObservable<T> t) {
                return selector.apply(t).observeOn(scheduler);
            }
        });
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpConnectableObservable<T> replay(final int bufferSize) {
        return NbpOperatorReplay.create(this, bufferSize);
    }
    
    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpConnectableObservable<T> replay(int bufferSize, long time, TimeUnit unit) {
        return replay(bufferSize, time, unit, Schedulers.computation());
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpConnectableObservable<T> replay(final int bufferSize, final long time, final TimeUnit unit, final Scheduler scheduler) {
        if (bufferSize < 0) {
            throw new IllegalArgumentException("bufferSize < 0");
        }
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return NbpOperatorReplay.create(this, time, unit, scheduler, bufferSize);
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpConnectableObservable<T> replay(final int bufferSize, final Scheduler scheduler) {
        return NbpOperatorReplay.observeOn(replay(bufferSize), scheduler);
    }
    
    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpConnectableObservable<T> replay(long time, TimeUnit unit) {
        return replay(time, unit, Schedulers.computation());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpConnectableObservable<T> replay(final long time, final TimeUnit unit, final Scheduler scheduler) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return NbpOperatorReplay.create(this, time, unit, scheduler);
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpConnectableObservable<T> replay(final Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler is null");
        return NbpOperatorReplay.observeOn(replay(), scheduler);
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> retry() {
        return retry(Long.MAX_VALUE, Functions.alwaysTrue());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> retry(BiPredicate<? super Integer, ? super Throwable> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        
        return create(new NbpOnSubscribeRetryBiPredicate<T>(this, predicate));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> retry(long times) {
        return retry(times, Functions.alwaysTrue());
    }
    
    // Retries at most times or until the predicate returns false, whichever happens first
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> retry(long times, Predicate<? super Throwable> predicate) {
        if (times < 0) {
            throw new IllegalArgumentException("times >= 0 required but it was " + times);
        }
        Objects.requireNonNull(predicate, "predicate is null");

        return create(new NbpOnSubscribeRetryPredicate<T>(this, times, predicate));
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> retry(Predicate<? super Throwable> predicate) {
        return retry(Long.MAX_VALUE, predicate);
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> retryUntil(final BooleanSupplier stop) {
        Objects.requireNonNull(stop, "stop is null");
        return retry(Long.MAX_VALUE, new Predicate<Throwable>() {
            @Override
            public boolean test(Throwable e) {
                return !stop.getAsBoolean();
            }
        });
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> retryWhen(
            final Function<? super NbpObservable<? extends Throwable>, ? extends NbpObservable<?>> handler) {
        Objects.requireNonNull(handler, "handler is null");
        
        Function<NbpObservable<Try<Optional<Object>>>, NbpObservable<?>> f = new Function<NbpObservable<Try<Optional<Object>>>, NbpObservable<?>>() {
            @Override
            public NbpObservable<?> apply(NbpObservable<Try<Optional<Object>>> no) {
                return handler.apply(no
                        .takeWhile(new Predicate<Try<Optional<Object>>>() {
                            @Override
                            public boolean test(Try<Optional<Object>> e) {
                                return e.hasError();
                            }
                        })
                        .map(new Function<Try<Optional<Object>>, Throwable>() {
                            @Override
                            public Throwable apply(Try<Optional<Object>> t) {
                                return t.error();
                            }
                        })
                );
            }
        }
        ;
        
        return create(new NbpOnSubscribeRedo<T>(this, f));
    }
    
    // TODO decide if safe subscription or unsafe should be the default
    @SchedulerSupport(SchedulerKind.NONE)
    public final void safeSubscribe(NbpSubscriber<? super T> s) {
        Objects.requireNonNull(s, "s is null");
        if (s instanceof NbpSafeSubscriber) {
            subscribe(s);
        } else {
            subscribe(new NbpSafeSubscriber<T>(s));
        }
    }
    
    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<T> sample(long period, TimeUnit unit) {
        return sample(period, unit, Schedulers.computation());
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> sample(long period, TimeUnit unit, Scheduler scheduler) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return lift(new NbpOperatorSampleTimed<T>(period, unit, scheduler));
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final <U> NbpObservable<T> sample(NbpObservable<U> sampler) {
        Objects.requireNonNull(sampler, "sampler is null");
        return lift(new NbpOperatorSampleWithObservable<T>(sampler));
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> scan(BiFunction<T, T, T> accumulator) {
        Objects.requireNonNull(accumulator, "accumulator is null");
        return lift(new NbpOperatorScan<T>(accumulator));
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> scan(final R seed, BiFunction<R, ? super T, R> accumulator) {
        Objects.requireNonNull(seed, "seed is null");
        return scanWith(new Supplier<R>() {
            @Override
            public R get() {
                return seed;
            }
        }, accumulator);
    }
    
    // Naming note, a plain scan would cause ambiguity with the value-seeded version
    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> scanWith(Supplier<R> seedSupplier, BiFunction<R, ? super T, R> accumulator) {
        Objects.requireNonNull(seedSupplier, "seedSupplier is null");
        Objects.requireNonNull(accumulator, "accumulator is null");
        return lift(new NbpOperatorScanSeed<T, R>(seedSupplier, accumulator));
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> serialize() {
        return lift(new NbpOperator<T, T>() {
            @Override
            public NbpSubscriber<? super T> apply(NbpSubscriber<? super T> s) {
                return new NbpSerializedSubscriber<T>(s);
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> share() {
        return publish().refCount();
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> single() {
        return lift(NbpOperatorSingle.<T>instanceNoDefault());
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> single(T defaultValue) {
        Objects.requireNonNull(defaultValue, "defaultValue is null");
        return lift(new NbpOperatorSingle<T>(defaultValue));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> skip(long n) {
//        if (n < 0) {
//            throw new IllegalArgumentException("n >= 0 required but it was " + n);
//        } else
        // FIXME negative skip allowed?!
        if (n <= 0) {
            return this;
        }
        return lift(new NbpOperatorSkip<T>(n));
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> skip(long time, TimeUnit unit, Scheduler scheduler) {
        // TODO consider inlining this behavior
        return skipUntil(timer(time, unit, scheduler));
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> skipLast(int n) {
        if (n < 0) {
            throw new IndexOutOfBoundsException("n >= 0 required but it was " + n);
        } else
            if (n == 0) {
                return this;
            }
        return lift(new NbpOperatorSkipLast<T>(n));
    }
    
    @SchedulerSupport(SchedulerKind.TRAMPOLINE)
    public final NbpObservable<T> skipLast(long time, TimeUnit unit) {
        return skipLast(time, unit, Schedulers.trampoline(), false, bufferSize());
    }
    
    @SchedulerSupport(SchedulerKind.TRAMPOLINE)
    public final NbpObservable<T> skipLast(long time, TimeUnit unit, boolean delayError) {
        return skipLast(time, unit, Schedulers.trampoline(), delayError, bufferSize());
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> skipLast(long time, TimeUnit unit, Scheduler scheduler) {
        return skipLast(time, unit, scheduler, false, bufferSize());
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> skipLast(long time, TimeUnit unit, Scheduler scheduler, boolean delayError) {
        return skipLast(time, unit, scheduler, delayError, bufferSize());
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> skipLast(long time, TimeUnit unit, Scheduler scheduler, boolean delayError, int bufferSize) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        validateBufferSize(bufferSize);
     // the internal buffer holds pairs of (timestamp, value) so double the default buffer size
        int s = bufferSize << 1; 
        return lift(new NbpOperatorSkipLastTimed<T>(time, unit, scheduler, s, delayError));
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final <U> NbpObservable<T> skipUntil(NbpObservable<U> other) {
        Objects.requireNonNull(other, "other is null");
        return lift(new NbpOperatorSkipUntil<T, U>(other));
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> skipWhile(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return lift(new NbpOperatorSkipWhile<T>(predicate));
    }
    
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> startWith(Iterable<? extends T> values) {
        return concatArray(fromIterable(values), this);
    }
    
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> startWith(NbpObservable<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return concatArray(other, this);
    }
    
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> startWith(T value) {
        Objects.requireNonNull(value, "value is null");
        return concatArray(just(value), this);
    }

    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> startWithArray(T... values) {
        NbpObservable<T> fromArray = fromArray(values);
        if (fromArray == empty()) {
            return this;
        }
        return concatArray(fromArray, this);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final Disposable subscribe() {
        return subscribe(Functions.emptyConsumer(), RxJavaPlugins.errorConsumer(), Functions.emptyRunnable(), Functions.emptyConsumer());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final Disposable subscribe(Consumer<? super T> onNext) {
        return subscribe(onNext, RxJavaPlugins.errorConsumer(), Functions.emptyRunnable(), Functions.emptyConsumer());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final Disposable subscribe(Consumer<? super T> onNext, Consumer<? super Throwable> onError) {
        return subscribe(onNext, onError, Functions.emptyRunnable(), Functions.emptyConsumer());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final Disposable subscribe(Consumer<? super T> onNext, Consumer<? super Throwable> onError, 
            Runnable onComplete) {
        return subscribe(onNext, onError, onComplete, Functions.emptyConsumer());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final Disposable subscribe(Consumer<? super T> onNext, Consumer<? super Throwable> onError, 
            Runnable onComplete, Consumer<? super Disposable> onSubscribe) {
        Objects.requireNonNull(onNext, "onNext is null");
        Objects.requireNonNull(onError, "onError is null");
        Objects.requireNonNull(onComplete, "onComplete is null");
        Objects.requireNonNull(onSubscribe, "onSubscribe is null");

        NbpLambdaSubscriber<T> ls = new NbpLambdaSubscriber<T>(onNext, onError, onComplete, onSubscribe);

        unsafeSubscribe(ls);

        return ls;
    }

    public final void subscribe(NbpSubscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        onSubscribe.accept(subscriber);
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> subscribeOn(Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler is null");
        return create(new NbpOnSubscribeSubscribeOn<T>(this, scheduler));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> switchIfEmpty(NbpObservable<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return lift(new NbpOperatorSwitchIfEmpty<T>(other));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> switchMap(Function<? super T, ? extends NbpObservable<? extends R>> mapper) {
        return switchMap(mapper, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <R> NbpObservable<R> switchMap(Function<? super T, ? extends NbpObservable<? extends R>> mapper, int bufferSize) {
        Objects.requireNonNull(mapper, "mapper is null");
        validateBufferSize(bufferSize);
        return lift(new NbpOperatorSwitchMap<T, R>(mapper, bufferSize));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> take(long n) {
        if (n < 0) {
            throw new IllegalArgumentException("n >= required but it was " + n);
        } else
        if (n == 0) {
         // FIXME may want to subscribe an cancel immediately
//            return lift(s -> CancelledSubscriber.INSTANCE);
            return empty(); 
        }
        return lift(new NbpOperatorTake<T>(n));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> take(long time, TimeUnit unit, Scheduler scheduler) {
        // TODO consider inlining this behavior
        return takeUntil(timer(time, unit, scheduler));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> takeFirst(Predicate<? super T> predicate) {
        return filter(predicate).take(1);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> takeLast(int n) {
        if (n < 0) {
            throw new IndexOutOfBoundsException("n >= required but it was " + n);
        } else
        if (n == 0) {
            return ignoreElements();
        } else
        if (n == 1) {
            return lift(NbpOperatorTakeLastOne.<T>instance());
        }
        return lift(new NbpOperatorTakeLast<T>(n));
    }
    
    @SchedulerSupport(SchedulerKind.TRAMPOLINE)
    public final NbpObservable<T> takeLast(long count, long time, TimeUnit unit) {
        return takeLast(count, time, unit, Schedulers.trampoline(), false, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> takeLast(long count, long time, TimeUnit unit, Scheduler scheduler) {
        return takeLast(count, time, unit, scheduler, false, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> takeLast(long count, long time, TimeUnit unit, Scheduler scheduler, boolean delayError, int bufferSize) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        validateBufferSize(bufferSize);
        if (count < 0) {
            throw new IndexOutOfBoundsException("count >= 0 required but it was " + count);
        }
        return lift(new NbpOperatorTakeLastTimed<T>(count, time, unit, scheduler, bufferSize, delayError));
    }

    @SchedulerSupport(SchedulerKind.TRAMPOLINE)
    public final NbpObservable<T> takeLast(long time, TimeUnit unit) {
        return takeLast(time, unit, Schedulers.trampoline(), false, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.TRAMPOLINE)
    public final NbpObservable<T> takeLast(long time, TimeUnit unit, boolean delayError) {
        return takeLast(time, unit, Schedulers.trampoline(), delayError, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> takeLast(long time, TimeUnit unit, Scheduler scheduler) {
        return takeLast(time, unit, scheduler, false, bufferSize());
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> takeLast(long time, TimeUnit unit, Scheduler scheduler, boolean delayError) {
        return takeLast(time, unit, scheduler, delayError, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> takeLast(long time, TimeUnit unit, Scheduler scheduler, boolean delayError, int bufferSize) {
        return takeLast(Long.MAX_VALUE, time, unit, scheduler, delayError, bufferSize);
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<List<T>> takeLastBuffer(int count) {
        return takeLast(count).toList();
    }
    
    @SchedulerSupport(SchedulerKind.TRAMPOLINE)
    public final NbpObservable<List<T>> takeLastBuffer(int count, long time, TimeUnit unit) {
        return takeLast(count, time, unit).toList();
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<List<T>> takeLastBuffer(int count, long time, TimeUnit unit, Scheduler scheduler) {
        return takeLast(count, time, unit, scheduler).toList();
    }
    
    @SchedulerSupport(SchedulerKind.TRAMPOLINE)
    public final NbpObservable<List<T>> takeLastBuffer(long time, TimeUnit unit) {
        return takeLast(time, unit).toList();
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<List<T>> takeLastBuffer(long time, TimeUnit unit, Scheduler scheduler) {
        return takeLast(time, unit, scheduler).toList();
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U> NbpObservable<T> takeUntil(NbpObservable<U> other) {
        Objects.requireNonNull(other, "other is null");
        return lift(new NbpOperatorTakeUntil<T, U>(other));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> takeUntil(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return lift(new NbpOperatorTakeUntilPredicate<T>(predicate));
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<T> takeWhile(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return lift(new NbpOperatorTakeWhile<T>(predicate));
    }
    
    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<T> throttleFirst(long windowDuration, TimeUnit unit) {
        return throttleFirst(windowDuration, unit, Schedulers.computation());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> throttleFirst(long skipDuration, TimeUnit unit, Scheduler scheduler) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return lift(new NbpOperatorThrottleFirstTimed<T>(skipDuration, unit, scheduler));
    }

    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<T> throttleLast(long intervalDuration, TimeUnit unit) {
        return sample(intervalDuration, unit);
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> throttleLast(long intervalDuration, TimeUnit unit, Scheduler scheduler) {
        return sample(intervalDuration, unit, scheduler);
    }

    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<T> throttleWithTimeout(long timeout, TimeUnit unit) {
        return debounce(timeout, unit);
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> throttleWithTimeout(long timeout, TimeUnit unit, Scheduler scheduler) {
        return debounce(timeout, unit, scheduler);
    }

    @SchedulerSupport(SchedulerKind.TRAMPOLINE)
    public final NbpObservable<Timed<T>> timeInterval() {
        return timeInterval(TimeUnit.MILLISECONDS, Schedulers.trampoline());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<Timed<T>> timeInterval(Scheduler scheduler) {
        return timeInterval(TimeUnit.MILLISECONDS, scheduler);
    }
    
    @SchedulerSupport(SchedulerKind.TRAMPOLINE)
    public final NbpObservable<Timed<T>> timeInterval(TimeUnit unit) {
        return timeInterval(unit, Schedulers.trampoline());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<Timed<T>> timeInterval(TimeUnit unit, Scheduler scheduler) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return lift(new NbpOperatorTimeInterval<T>(unit, scheduler));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <V> NbpObservable<T> timeout(Function<? super T, ? extends NbpObservable<V>> timeoutSelector) {
        return timeout0(null, timeoutSelector, null);
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final <V> NbpObservable<T> timeout(Function<? super T, ? extends NbpObservable<V>> timeoutSelector, NbpObservable<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return timeout0(null, timeoutSelector, other);
    }

    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<T> timeout(long timeout, TimeUnit timeUnit) {
        return timeout0(timeout, timeUnit, null, Schedulers.computation());
    }
    
    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<T> timeout(long timeout, TimeUnit timeUnit, NbpObservable<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return timeout0(timeout, timeUnit, other, Schedulers.computation());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> timeout(long timeout, TimeUnit timeUnit, NbpObservable<? extends T> other, Scheduler scheduler) {
        Objects.requireNonNull(other, "other is null");
        return timeout0(timeout, timeUnit, other, scheduler);
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> timeout(long timeout, TimeUnit timeUnit, Scheduler scheduler) {
        return timeout0(timeout, timeUnit, null, scheduler);
    }
    
    public final <U, V> NbpObservable<T> timeout(Supplier<? extends NbpObservable<U>> firstTimeoutSelector, 
            Function<? super T, ? extends NbpObservable<V>> timeoutSelector) {
        Objects.requireNonNull(firstTimeoutSelector, "firstTimeoutSelector is null");
        return timeout0(firstTimeoutSelector, timeoutSelector, null);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U, V> NbpObservable<T> timeout(
            Supplier<? extends NbpObservable<U>> firstTimeoutSelector, 
            Function<? super T, ? extends NbpObservable<V>> timeoutSelector, 
                    NbpObservable<? extends T> other) {
        Objects.requireNonNull(firstTimeoutSelector, "firstTimeoutSelector is null");
        Objects.requireNonNull(other, "other is null");
        return timeout0(firstTimeoutSelector, timeoutSelector, other);
    }
    
    private NbpObservable<T> timeout0(long timeout, TimeUnit timeUnit, NbpObservable<? extends T> other, 
            Scheduler scheduler) {
        Objects.requireNonNull(timeUnit, "timeUnit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return lift(new NbpOperatorTimeoutTimed<T>(timeout, timeUnit, scheduler, other));
    }

    private <U, V> NbpObservable<T> timeout0(
            Supplier<? extends NbpObservable<U>> firstTimeoutSelector, 
            Function<? super T, ? extends NbpObservable<V>> timeoutSelector, 
                    NbpObservable<? extends T> other) {
        Objects.requireNonNull(timeoutSelector, "timeoutSelector is null");
        return lift(new NbpOperatorTimeout<T, U, V>(firstTimeoutSelector, timeoutSelector, other));
    }

    @SchedulerSupport(SchedulerKind.TRAMPOLINE)
    public final NbpObservable<Timed<T>> timestamp() {
        return timestamp(TimeUnit.MILLISECONDS, Schedulers.trampoline());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<Timed<T>> timestamp(Scheduler scheduler) {
        return timestamp(TimeUnit.MILLISECONDS, scheduler);
    }

    @SchedulerSupport(SchedulerKind.TRAMPOLINE)
    public final NbpObservable<Timed<T>> timestamp(TimeUnit unit) {
        return timestamp(unit, Schedulers.trampoline());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<Timed<T>> timestamp(final TimeUnit unit, final Scheduler scheduler) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return map(new Function<T, Timed<T>>() {
            @Override
            public Timed<T> apply(T v) {
                return new Timed<T>(v, scheduler.now(unit), unit);
            }
        });
    }

    public final <R> R to(Function<? super NbpObservable<T>, R> convert) {
        return convert.apply(this);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpBlockingObservable<T> toBlocking() {
        return NbpBlockingObservable.from(this);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<List<T>> toList() {
        return lift(NbpOperatorToList.<T>defaultInstance());
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<List<T>> toList(final int capacityHint) {
        if (capacityHint <= 0) {
            throw new IllegalArgumentException("capacityHint > 0 required but it was " + capacityHint);
        }
        return lift(new NbpOperatorToList<T, List<T>>(new Supplier<List<T>>() {
            @Override
            public List<T> get() {
                return new ArrayList<T>(capacityHint);
            }
        }));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U extends Collection<? super T>> NbpObservable<U> toList(Supplier<U> collectionSupplier) {
        Objects.requireNonNull(collectionSupplier, "collectionSupplier is null");
        return lift(new NbpOperatorToList<T, U>(collectionSupplier));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <K> NbpObservable<Map<K, T>> toMap(final Function<? super T, ? extends K> keySelector) {
        return collect(new Supplier<Map<K, T>>() {
            @Override
            public Map<K, T> get() {
                return new HashMap<K, T>();
            }
        }, new BiConsumer<Map<K, T>, T>() {
            @Override
            public void accept(Map<K, T> m, T t) {
                K key = keySelector.apply(t);
                m.put(key, t);
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <K, V> NbpObservable<Map<K, V>> toMap(
            final Function<? super T, ? extends K> keySelector, 
            final Function<? super T, ? extends V> valueSelector) {
        Objects.requireNonNull(keySelector, "keySelector is null");
        Objects.requireNonNull(valueSelector, "valueSelector is null");
        return collect(new Supplier<Map<K, V>>() {
            @Override
            public Map<K, V> get() {
                return new HashMap<K, V>();
            }
        }, new BiConsumer<Map<K, V>, T>() {
            @Override
            public void accept(Map<K, V> m, T t) {
                K key = keySelector.apply(t);
                V value = valueSelector.apply(t);
                m.put(key, value);
            }
        });
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final <K, V> NbpObservable<Map<K, V>> toMap(
            final Function<? super T, ? extends K> keySelector, 
            final Function<? super T, ? extends V> valueSelector,
            Supplier<? extends Map<K, V>> mapSupplier) {
        return collect(mapSupplier, new BiConsumer<Map<K, V>, T>() {
            @Override
            public void accept(Map<K, V> m, T t) {
                K key = keySelector.apply(t);
                V value = valueSelector.apply(t);
                m.put(key, value);
            }
        });
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final <K> NbpObservable<Map<K, Collection<T>>> toMultimap(Function<? super T, ? extends K> keySelector) {
        @SuppressWarnings({ "rawtypes", "unchecked" })
        Function<? super T, ? extends T> valueSelector = (Function)Functions.identity();
        Supplier<Map<K, Collection<T>>> mapSupplier = new Supplier<Map<K, Collection<T>>>() {
            @Override
            public Map<K, Collection<T>> get() {
                return new HashMap<K, Collection<T>>();
            }
        };
        Function<K, Collection<T>> collectionFactory = new Function<K, Collection<T>>() {
            @Override
            public Collection<T> apply(K k) {
                return new ArrayList<T>();
            }
        };
        return toMultimap(keySelector, valueSelector, mapSupplier, collectionFactory);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <K, V> NbpObservable<Map<K, Collection<V>>> toMultimap(Function<? super T, ? extends K> keySelector, Function<? super T, ? extends V> valueSelector) {
        Supplier<Map<K, Collection<V>>> mapSupplier = new Supplier<Map<K, Collection<V>>>() {
            @Override
            public Map<K, Collection<V>> get() {
                return new HashMap<K, Collection<V>>();
            }
        };
        Function<K, Collection<V>> collectionFactory = new Function<K, Collection<V>>() {
            @Override
            public Collection<V> apply(K k) {
                return new ArrayList<V>();
            }
        };
        return toMultimap(keySelector, valueSelector, mapSupplier, collectionFactory);
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    @SuppressWarnings("unchecked")
    public final <K, V> NbpObservable<Map<K, Collection<V>>> toMultimap(
            final Function<? super T, ? extends K> keySelector, 
            final Function<? super T, ? extends V> valueSelector, 
            final Supplier<? extends Map<K, Collection<V>>> mapSupplier,
            final Function<? super K, ? extends Collection<? super V>> collectionFactory) {
        Objects.requireNonNull(keySelector, "keySelector is null");
        Objects.requireNonNull(valueSelector, "valueSelector is null");
        Objects.requireNonNull(mapSupplier, "mapSupplier is null");
        Objects.requireNonNull(collectionFactory, "collectionFactory is null");
        return collect(mapSupplier, new BiConsumer<Map<K, Collection<V>>, T>() {
            @Override
            public void accept(Map<K, Collection<V>> m, T t) {
                K key = keySelector.apply(t);

                Collection<V> coll = m.get(key);
                if (coll == null) {
                    coll = (Collection<V>)collectionFactory.apply(key);
                    m.put(key, coll);
                }

                V value = valueSelector.apply(t);

                coll.add(value);
            }
        });
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final <K, V> NbpObservable<Map<K, Collection<V>>> toMultimap(
            Function<? super T, ? extends K> keySelector, 
            Function<? super T, ? extends V> valueSelector,
            Supplier<Map<K, Collection<V>>> mapSupplier
            ) {
        return toMultimap(keySelector, valueSelector, mapSupplier, new Function<K, Collection<V>>() {
            @Override
            public Collection<V> apply(K k) {
                return new ArrayList<V>();
            }
        });
    }
    
    public final Observable<T> toObservable(BackpressureStrategy strategy) {
        Observable<T> o = Observable.create(new Publisher<T>() {
            @Override
            public void subscribe(final Subscriber<? super T> s) {
                NbpObservable.this.subscribe(new NbpSubscriber<T>() {

                    @Override
                    public void onComplete() {
                        s.onComplete();
                    }

                    @Override
                    public void onError(Throwable e) {
                        s.onError(e);
                    }

                    @Override
                    public void onNext(T value) {
                        s.onNext(value);
                    }

                    @Override
                    public void onSubscribe(final Disposable d) {
                        s.onSubscribe(new Subscription() {

                            @Override
                            public void cancel() {
                                d.dispose();
                            }

                            @Override
                            public void request(long n) {
                                // no backpressure so nothing we can do about this
                            }
                            
                        });
                    }
                    
                });
            }
        });
        
        switch (strategy) {
        case BUFFER:
            return o.onBackpressureBuffer();
        case DROP:
            return o.onBackpressureDrop();
        case LATEST:
            return o.onBackpressureLatest();
        default:
            return o;
        }
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final Single<T> toSingle() {
        return Single.create(new SingleOnSubscribe<T>() {
            @Override
            public void accept(final SingleSubscriber<? super T> s) {
                NbpObservable.this.subscribe(new NbpSubscriber<T>() {
                    T last;
                    @Override
                    public void onSubscribe(Disposable d) {
                        s.onSubscribe(d);
                    }
                    @Override
                    public void onNext(T value) {
                        last = value;
                    }
                    @Override
                    public void onError(Throwable e) {
                        s.onError(e);
                    }
                    @Override
                    public void onComplete() {
                        T v = last;
                        last = null;
                        if (v != null) {
                            s.onSuccess(v);
                        } else {
                            s.onError(new NoSuchElementException());
                        }
                    }
                    
                    
                });
            }
        });
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<List<T>> toSortedList() {
        return toSortedList(Functions.naturalOrder());
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<List<T>> toSortedList(final Comparator<? super T> comparator) {
        Objects.requireNonNull(comparator, "comparator is null");
        return toList().map(new Function<List<T>, List<T>>() {
            @Override
            public List<T> apply(List<T> v) {
                Collections.sort(v, comparator);
                return v;
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<List<T>> toSortedList(final Comparator<? super T> comparator, int capacityHint) {
        Objects.requireNonNull(comparator, "comparator is null");
        return toList(capacityHint).map(new Function<List<T>, List<T>>() {
            @Override
            public List<T> apply(List<T> v) {
                Collections.sort(v, comparator);
                return v;
            }
        });
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<List<T>> toSortedList(int capacityHint) {
        return toSortedList(Functions.<T>naturalOrder(), capacityHint);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    // TODO decide if safe subscription or unsafe should be the default
    public final void unsafeSubscribe(NbpSubscriber<? super T> s) {
        Objects.requireNonNull(s, "s is null");
        subscribe(s);
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<T> unsubscribeOn(Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler is null");
        return lift(new NbpOperatorUnsubscribeOn<T>(scheduler));
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<NbpObservable<T>> window(long count) {
        return window(count, count, bufferSize());
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<NbpObservable<T>> window(long count, long skip) {
        return window(count, skip, bufferSize());
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final NbpObservable<NbpObservable<T>> window(long count, long skip, int bufferSize) {
        if (skip <= 0) {
            throw new IllegalArgumentException("skip > 0 required but it was " + skip);
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count > 0 required but it was " + count);
        }
        validateBufferSize(bufferSize);
        return lift(new NbpOperatorWindow<T>(count, skip, bufferSize));
    }
    
    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<NbpObservable<T>> window(long timespan, long timeskip, TimeUnit unit) {
        return window(timespan, timeskip, unit, Schedulers.computation(), bufferSize());
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<NbpObservable<T>> window(long timespan, long timeskip, TimeUnit unit, Scheduler scheduler) {
        return window(timespan, timeskip, unit, scheduler, bufferSize());
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<NbpObservable<T>> window(long timespan, long timeskip, TimeUnit unit, Scheduler scheduler, int bufferSize) {
        validateBufferSize(bufferSize);
        Objects.requireNonNull(scheduler, "scheduler is null");
        Objects.requireNonNull(unit, "unit is null");
        return lift(new NbpOperatorWindowTimed<T>(timespan, timeskip, unit, scheduler, Long.MAX_VALUE, bufferSize, false));
    }

    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<NbpObservable<T>> window(long timespan, TimeUnit unit) {
        return window(timespan, unit, Schedulers.computation(), Long.MAX_VALUE, false);
    }
    
    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<NbpObservable<T>> window(long timespan, TimeUnit unit, 
            long count) {
        return window(timespan, unit, Schedulers.computation(), count, false);
    }
    
    @SchedulerSupport(SchedulerKind.COMPUTATION)
    public final NbpObservable<NbpObservable<T>> window(long timespan, TimeUnit unit, 
            long count, boolean restart) {
        return window(timespan, unit, Schedulers.computation(), count, restart);
    }
    
    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<NbpObservable<T>> window(long timespan, TimeUnit unit, 
            Scheduler scheduler) {
        return window(timespan, unit, scheduler, Long.MAX_VALUE, false);
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<NbpObservable<T>> window(long timespan, TimeUnit unit, 
            Scheduler scheduler, long count) {
        return window(timespan, unit, scheduler, count, false);
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<NbpObservable<T>> window(long timespan, TimeUnit unit, 
            Scheduler scheduler, long count, boolean restart) {
        return window(timespan, unit, scheduler, count, restart, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.CUSTOM)
    public final NbpObservable<NbpObservable<T>> window(
            long timespan, TimeUnit unit, Scheduler scheduler, 
            long count, boolean restart, int bufferSize) {
        validateBufferSize(bufferSize);
        Objects.requireNonNull(scheduler, "scheduler is null");
        Objects.requireNonNull(unit, "unit is null");
        if (count <= 0) {
            throw new IllegalArgumentException("count > 0 required but it was " + count);
        }
        return lift(new NbpOperatorWindowTimed<T>(timespan, timespan, unit, scheduler, count, bufferSize, restart));
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final <B> NbpObservable<NbpObservable<T>> window(NbpObservable<B> boundary) {
        return window(boundary, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <B> NbpObservable<NbpObservable<T>> window(NbpObservable<B> boundary, int bufferSize) {
        Objects.requireNonNull(boundary, "boundary is null");
        return lift(new NbpOperatorWindowBoundary<T, B>(boundary, bufferSize));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U, V> NbpObservable<NbpObservable<T>> window(
            NbpObservable<U> windowOpen, 
            Function<? super U, ? extends NbpObservable<V>> windowClose) {
        return window(windowOpen, windowClose, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U, V> NbpObservable<NbpObservable<T>> window(
            NbpObservable<U> windowOpen, 
            Function<? super U, ? extends NbpObservable<V>> windowClose, int bufferSize) {
        Objects.requireNonNull(windowOpen, "windowOpen is null");
        Objects.requireNonNull(windowClose, "windowClose is null");
        return lift(new NbpOperatorWindowBoundarySelector<T, U, V>(windowOpen, windowClose, bufferSize));
    }
    
    @SchedulerSupport(SchedulerKind.NONE)
    public final <B> NbpObservable<NbpObservable<T>> window(Supplier<? extends NbpObservable<B>> boundary) {
        return window(boundary, bufferSize());
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <B> NbpObservable<NbpObservable<T>> window(Supplier<? extends NbpObservable<B>> boundary, int bufferSize) {
        Objects.requireNonNull(boundary, "boundary is null");
        return lift(new NbpOperatorWindowBoundarySupplier<T, B>(boundary, bufferSize));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U, R> NbpObservable<R> withLatestFrom(NbpObservable<? extends U> other, BiFunction<? super T, ? super U, ? extends R> combiner) {
        Objects.requireNonNull(other, "other is null");
        Objects.requireNonNull(combiner, "combiner is null");

        return lift(new NbpOperatorWithLatestFrom<T, U, R>(combiner, other));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U, R> NbpObservable<R> zipWith(Iterable<U> other,  BiFunction<? super T, ? super U, ? extends R> zipper) {
        Objects.requireNonNull(other, "other is null");
        Objects.requireNonNull(zipper, "zipper is null");
        return create(new NbpOnSubscribeZipIterable<T, U, R>(this, other, zipper));
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U, R> NbpObservable<R> zipWith(NbpObservable<? extends U> other, BiFunction<? super T, ? super U, ? extends R> zipper) {
        Objects.requireNonNull(other, "other is null");
        return zip(this, other, zipper);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U, R> NbpObservable<R> zipWith(NbpObservable<? extends U> other, BiFunction<? super T, ? super U, ? extends R> zipper, boolean delayError) {
        return zip(this, other, zipper, delayError);
    }

    @SchedulerSupport(SchedulerKind.NONE)
    public final <U, R> NbpObservable<R> zipWith(NbpObservable<? extends U> other, BiFunction<? super T, ? super U, ? extends R> zipper, boolean delayError, int bufferSize) {
        return zip(this, other, zipper, delayError, bufferSize);
    }


 }