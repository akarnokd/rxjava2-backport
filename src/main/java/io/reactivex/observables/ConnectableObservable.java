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

package io.reactivex.observables;

import org.reactivestreams.*;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.functions.Functions;
import io.reactivex.internal.operators.*;

/**
 * A {@code ConnectableObservable} resembles an ordinary {@link Observable}, except that it does not begin
 * emitting items when it is subscribed to, but only when its {@link #connect} method is called. In this way you
 * can wait for all intended {@link Subscriber}s to {@link Observable#subscribe} to the {@code Observable}
 * before the {@code Observable} begins emitting items.
 * <p>
 * <img width="640" height="510" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/publishConnect.png" alt="">
 * 
 * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Connectable-Observable-Operators">RxJava Wiki:
 *      Connectable Observable Operators</a>
 * @param <T>
 *          the type of items emitted by the {@code ConnectableObservable}
 */
public abstract class ConnectableObservable<T> extends Observable<T> {
    
    protected ConnectableObservable(Publisher<T> onSubscribe) {
        super(onSubscribe);
    }
    
    /**
     * Instructs the {@code ConnectableObservable} to begin emitting the items from its underlying
     * {@link Observable} to its {@link Subscriber}s.
     *
     * @param connection
     *          the action that receives the connection subscription before the subscription to source happens
     *          allowing the caller to synchronously disconnect a synchronous source
     * @see <a href="http://reactivex.io/documentation/operators/connect.html">ReactiveX documentation: Connect</a>
     */
    public abstract void connect(Consumer<? super Disposable> connection);
    
    /**
     * Instructs the {@code ConnectableObservable} to begin emitting the items from its underlying
     * {@link Observable} to its {@link Subscriber}s.
     * <p>
     * To disconnect from a synchronous source, use the {@link #connect(java.util.function.Consumer)} method.
     *
     * @return the subscription representing the connection
     * @see <a href="http://reactivex.io/documentation/operators/connect.html">ReactiveX documentation: Connect</a>
     */
    public final Disposable connect() {
        final Disposable[] connection = new Disposable[1];
        connect(new Consumer<Disposable>() {
            @Override
            public void accept(Disposable d) {
                connection[0] = d;
            }
        });
        return connection[0];
    }

    /**
     * Returns an {@code Observable} that stays connected to this {@code ConnectableObservable} as long as there
     * is at least one subscription to this {@code ConnectableObservable}.
     * 
     * @return a {@link Observable}
     * @see <a href="http://reactivex.io/documentation/operators/refcount.html">ReactiveX documentation: RefCount</a>
     */
    public Observable<T> refCount() {
        return create(new PublisherRefCount<T>(this));
    }

    /**
     * Returns an Observable that automatically connects to this ConnectableObservable
     * when the first Subscriber subscribes.
     * 
     * @return an Observable that automatically connects to this ConnectableObservable
     *         when the first Subscriber subscribes
     */
    public Observable<T> autoConnect() {
        return autoConnect(1);
    }
    /**
     * Returns an Observable that automatically connects to this ConnectableObservable
     * when the specified number of Subscribers subscribe to it.
     * 
     * @param numberOfSubscribers the number of subscribers to await before calling connect
     *                            on the ConnectableObservable. A non-positive value indicates
     *                            an immediate connection.
     * @return an Observable that automatically connects to this ConnectableObservable
     *         when the specified number of Subscribers subscribe to it
     */
    public Observable<T> autoConnect(int numberOfSubscribers) {
        return autoConnect(numberOfSubscribers, Functions.emptyConsumer());
    }
    
    /**
     * Returns an Observable that automatically connects to this ConnectableObservable
     * when the specified number of Subscribers subscribe to it and calls the 
     * specified callback with the Subscription associated with the established connection.
     * 
     * @param numberOfSubscribers the number of subscribers to await before calling connect
     *                            on the ConnectableObservable. A non-positive value indicates
     *                            an immediate connection.
     * @param connection the callback Action1 that will receive the Subscription representing the
     *                   established connection
     * @return an Observable that automatically connects to this ConnectableObservable
     *         when the specified number of Subscribers subscribe to it and calls the 
     *         specified callback with the Subscription associated with the established connection
     */
    public Observable<T> autoConnect(int numberOfSubscribers, Consumer<? super Disposable> connection) {
        if (numberOfSubscribers <= 0) {
            this.connect(connection);
            return this;
        }
        return create(new PublisherAutoConnect<T>(this, numberOfSubscribers, connection));
    }
}
