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

import java.util.Collection;

import hu.akarnokd.rxjava2.NbpObservable;
import hu.akarnokd.rxjava2.NbpObservable.*;
import hu.akarnokd.rxjava2.disposables.Disposable;
import hu.akarnokd.rxjava2.functions.Supplier;
import hu.akarnokd.rxjava2.internal.disposables.EmptyDisposable;
import hu.akarnokd.rxjava2.internal.queue.MpscLinkedQueue;
import hu.akarnokd.rxjava2.internal.subscribers.nbp.*;
import hu.akarnokd.rxjava2.internal.subscriptions.SubscriptionHelper;
import hu.akarnokd.rxjava2.internal.util.QueueDrainHelper;
import hu.akarnokd.rxjava2.subscribers.nbp.NbpSerializedSubscriber;

public final class NbpOperatorBufferExactBoundary<T, U extends Collection<? super T>, B> implements NbpOperator<U, T> {
    final NbpObservable<B> boundary;
    final Supplier<U> bufferSupplier;
    
    public NbpOperatorBufferExactBoundary(NbpObservable<B> boundary, Supplier<U> bufferSupplier) {
        this.boundary = boundary;
        this.bufferSupplier = bufferSupplier;
    }

    @Override
    public NbpSubscriber<? super T> apply(NbpSubscriber<? super U> t) {
        return new BufferExactBondarySubscriber<T, U, B>(new NbpSerializedSubscriber<U>(t), bufferSupplier, boundary);
    }
    
    static final class BufferExactBondarySubscriber<T, U extends Collection<? super T>, B>
    extends NbpQueueDrainSubscriber<T, U, U> implements NbpSubscriber<T>, Disposable {
        /** */
        final Supplier<U> bufferSupplier;
        final NbpObservable<B> boundary;
        
        Disposable s;
        
        Disposable other;
        
        U buffer;
        
        public BufferExactBondarySubscriber(NbpSubscriber<? super U> actual, Supplier<U> bufferSupplier,
                NbpObservable<B> boundary) {
            super(actual, new MpscLinkedQueue<U>());
            this.bufferSupplier = bufferSupplier;
            this.boundary = boundary;
        }
        
        @Override
        public void onSubscribe(Disposable s) {
            if (SubscriptionHelper.validateDisposable(this.s, s)) {
                return;
            }
            this.s = s;
            
            U b;
            
            try {
                b = bufferSupplier.get();
            } catch (Throwable e) {
                cancelled = true;
                s.dispose();
                EmptyDisposable.error(e, actual);
                return;
            }
            
            if (b == null) {
                cancelled = true;
                s.dispose();
                EmptyDisposable.error(new NullPointerException("The buffer supplied is null"), actual);
                return;
            }
            buffer = b;
            
            BufferBoundarySubscriber<T, U, B> bs = new BufferBoundarySubscriber<T, U, B>(this);
            other = bs;
            
            actual.onSubscribe(this);
            
            if (!cancelled) {
                boundary.subscribe(bs);
            }
        }
        
        @Override
        public void onNext(T t) {
            synchronized (this) {
                U b = buffer;
                if (b == null) {
                    return;
                }
                b.add(t);
            }
        }
        
        @Override
        public void onError(Throwable t) {
            dispose();
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            U b;
            synchronized (this) {
                b = buffer;
                if (b == null) {
                    return;
                }
                buffer = null;
            }
            queue.offer(b);
            done = true;
            if (enter()) {
                QueueDrainHelper.drainLoop(queue, actual, false, this, this);
            }
        }
        
        @Override
        public void dispose() {
            if (!cancelled) {
                cancelled = true;
                other.dispose();
                s.dispose();
                
                if (enter()) {
                    queue.clear();
                }
            }
        }
        
        void next() {
            
            U next;
            
            try {
                next = bufferSupplier.get();
            } catch (Throwable e) {
                dispose();
                actual.onError(e);
                return;
            }
            
            if (next == null) {
                dispose();
                actual.onError(new NullPointerException("The buffer supplied is null"));
                return;
            }
            
            U b;
            synchronized (this) {
                b = buffer;
                if (b == null) {
                    return;
                }
                buffer = next;
            }
            
            fastpathEmit(b, false, this);
        }
        
        @Override
        public void accept(NbpSubscriber<? super U> a, U v) {
            actual.onNext(v);
        }
        
    }
    
    static final class BufferBoundarySubscriber<T, U extends Collection<? super T>, B> 
    extends NbpDisposableSubscriber<B> {
        final BufferExactBondarySubscriber<T, U, B> parent;
        
        public BufferBoundarySubscriber(BufferExactBondarySubscriber<T, U, B> parent) {
            this.parent = parent;
        }

        @Override
        public void onNext(B t) {
            parent.next();
        }
        
        @Override
        public void onError(Throwable t) {
            parent.onError(t);
        }
        
        @Override
        public void onComplete() {
            parent.onComplete();
        }
    }
}
