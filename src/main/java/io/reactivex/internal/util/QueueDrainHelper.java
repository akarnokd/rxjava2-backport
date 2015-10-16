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
package io.reactivex.internal.util;

import java.util.Queue;
import java.util.concurrent.atomic.*;

import org.reactivestreams.Subscriber;

import io.reactivex.NbpObservable.NbpSubscriber;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BooleanSupplier;

/**
 * Utility class to help with the queue-drain serialization idiom.
 */
public enum QueueDrainHelper {
    ;

    /**
     * A fast-path queue-drain serialization logic.
     * <p>The decrementing of the state is left to the drain callback.
     * @param updater
     * @param instance
     * @param fastPath called if the instance is uncontended.
     * @param queue called if the instance is contended to queue up work
     * @param drain called if the instance transitions to the drain state successfully
     */
    public static <T> void queueDrain(AtomicIntegerFieldUpdater<T> updater, T instance,
            Runnable fastPath, Runnable queue, Runnable drain) {
        if (updater.get(instance) == 0 && updater.compareAndSet(instance, 0, 1)) {
            fastPath.run();
            if (updater.decrementAndGet(instance) == 0) {
                return;
            }
        } else {
            queue.run();
            if (updater.getAndIncrement(instance) != 0) {
                return;
            }
        }
        drain.run();
    }

    /**
     * A fast-path queue-drain serialization logic with the ability to leave the state
     * in fastpath/drain mode or not continue after the call to queue.
     * <p>The decrementing of the state is left to the drain callback.
     * @param updater
     * @param instance
     * @param fastPath
     * @param queue
     * @param drain
     */
    public static <T> void queueDrainIf(AtomicIntegerFieldUpdater<T> updater, T instance,
            BooleanSupplier fastPath, BooleanSupplier queue, Runnable drain) {
        if (updater.get(instance) == 0 && updater.compareAndSet(instance, 0, 1)) {
            if (fastPath.getAsBoolean()) {
                return;
            }
            if (updater.decrementAndGet(instance) == 0) {
                return;
            }
        } else {
            if (queue.getAsBoolean()) {
                return;
            }
            if (updater.getAndIncrement(instance) != 0) {
                return;
            }
        }
        drain.run();
    }

    /**
     * A fast-path queue-drain serialization logic where the drain is looped until
     * the instance state reaches 0 again.
     * @param updater
     * @param instance
     * @param fastPath
     * @param queue
     * @param drain
     */
    public static <T> void queueDrainLoop(AtomicIntegerFieldUpdater<T> updater, T instance,
            Runnable fastPath, Runnable queue, Runnable drain) {
        if (updater.get(instance) == 0 && updater.compareAndSet(instance, 0, 1)) {
            fastPath.run();
            if (updater.decrementAndGet(instance) == 0) {
                return;
            }
        } else {
            queue.run();
            if (updater.getAndIncrement(instance) != 0) {
                return;
            }
        }
        int missed = 1;
        for (;;) {
            drain.run();
            
            missed = updater.addAndGet(instance, -missed);
            if (missed == 0) {
                return;
            }
        }
    }
    
    /**
     * A fast-path queue-drain serialization logic with looped drain call and the ability to leave the state
     * in fastpath/drain mode or not continue after the call to queue.
     * @param updater
     * @param instance
     * @param fastPath
     * @param queue
     * @param drain
     */
    public static <T> void queueDrainLoopIf(AtomicIntegerFieldUpdater<T> updater, T instance,
            BooleanSupplier fastPath, BooleanSupplier queue, BooleanSupplier drain) {
        if (updater.get(instance) == 0 && updater.compareAndSet(instance, 0, 1)) {
            if (fastPath.getAsBoolean()) {
                return;
            }
            if (updater.decrementAndGet(instance) == 0) {
                return;
            }
        } else {
            if (queue.getAsBoolean()) {
                return;
            }
            if (updater.getAndIncrement(instance) != 0) {
                return;
            }
        }
        int missed = 1;
        for (;;) {
            
            if (drain.getAsBoolean()) {
                return;
            }
            
            missed = updater.addAndGet(instance, -missed);
            if (missed == 0) {
                return;
            }
        }
    }

    /**
     * A fast-path queue-drain serialization logic.
     * <p>The decrementing of the state is left to the drain callback.
     * @param updater
     * @param instance
     * @param fastPath called if the instance is uncontended.
     * @param queue called if the instance is contended to queue up work
     * @param drain called if the instance transitions to the drain state successfully
     */
    public static <T> void queueDrain(AtomicInteger instance,
            Runnable fastPath, Runnable queue, Runnable drain) {
        if (instance.get() == 0 && instance.compareAndSet(0, 1)) {
            fastPath.run();
            if (instance.decrementAndGet() == 0) {
                return;
            }
        } else {
            queue.run();
            if (instance.getAndIncrement() != 0) {
                return;
            }
        }
        drain.run();
    }

    /**
     * A fast-path queue-drain serialization logic with the ability to leave the state
     * in fastpath/drain mode or not continue after the call to queue.
     * <p>The decrementing of the state is left to the drain callback.
     * @param updater
     * @param instance
     * @param fastPath
     * @param queue
     * @param drain
     */
    public static <T> void queueDrainIf(AtomicInteger instance,
            BooleanSupplier fastPath, BooleanSupplier queue, Runnable drain) {
        if (instance.get() == 0 && instance.compareAndSet(0, 1)) {
            if (fastPath.getAsBoolean()) {
                return;
            }
            if (instance.decrementAndGet() == 0) {
                return;
            }
        } else {
            if (queue.getAsBoolean()) {
                return;
            }
            if (instance.getAndIncrement() != 0) {
                return;
            }
        }
        drain.run();
    }

    /**
     * A fast-path queue-drain serialization logic where the drain is looped until
     * the instance state reaches 0 again.
     * @param updater
     * @param instance
     * @param fastPath
     * @param queue
     * @param drain
     */
    public static <T> void queueDrainLoop(AtomicInteger instance,
            Runnable fastPath, Runnable queue, Runnable drain) {
        if (instance.get() == 0 && instance.compareAndSet(0, 1)) {
            fastPath.run();
            if (instance.decrementAndGet() == 0) {
                return;
            }
        } else {
            queue.run();
            if (instance.getAndIncrement() != 0) {
                return;
            }
        }
        int missed = 1;
        for (;;) {
            drain.run();
            
            missed = instance.addAndGet(-missed);
            if (missed == 0) {
                return;
            }
        }
    }
    
    /**
     * A fast-path queue-drain serialization logic with looped drain call and the ability to leave the state
     * in fastpath/drain mode or not continue after the call to queue.
     * @param updater
     * @param instance
     * @param fastPath
     * @param queue
     * @param drain
     */
    public static <T> void queueDrainLoopIf(AtomicInteger instance,
            BooleanSupplier fastPath, BooleanSupplier queue, BooleanSupplier drain) {
        if (instance.get() == 0 && instance.compareAndSet(0, 1)) {
            if (fastPath.getAsBoolean()) {
                return;
            }
            if (instance.decrementAndGet() == 0) {
                return;
            }
        } else {
            if (queue.getAsBoolean()) {
                return;
            }
            if (instance.getAndIncrement() != 0) {
                return;
            }
        }
        int missed = 1;
        for (;;) {
            
            if (drain.getAsBoolean()) {
                return;
            }
            
            missed = instance.addAndGet(-missed);
            if (missed == 0) {
                return;
            }
        }
    }

    public static <T, U> void drainLoop(Queue<T> q, Subscriber<? super U> a, boolean delayError, QueueDrain<T, U> qd) {
        
        int missed = 1;
        
        for (;;) {
            if (checkTerminated(qd.done(), q.isEmpty(), a, delayError, q, qd)) {
                return;
            }
            
            long r = qd.requested();
            boolean unbounded = r == Long.MAX_VALUE;
            long e = 0L;
            
            while (e != r) {
                boolean d = qd.done();
                T v = q.poll();
                
                boolean empty = v == null;
                
                if (checkTerminated(d, empty, a, delayError, q, qd)) {
                    return;
                }
                
                if (empty) {
                    break;
                }
                
                if (qd.accept(a, v)) {
                    e++;
                }
            }
            
            if (e != 0L && !unbounded) {
                qd.produced(e);
            }
            
            missed = qd.leave(-missed);
            if (missed == 0) {
                break;
            }
        }
    }

    /**
     * Drain the queue but give up with an error if there aren't enough requests.
     * @param q
     * @param a
     * @param delayError
     */
    public static <T, U> void drainMaxLoop(Queue<T> q, Subscriber<? super U> a, boolean delayError, 
            Disposable dispose, QueueDrain<T, U> qd) {
        int missed = 1;
        
        for (;;) {
            for (;;) {
                boolean d = qd.done();
                T v = q.poll();
                
                boolean empty = v == null;
                
                if (checkTerminated(d, empty, a, delayError, q, qd)) {
                    if (dispose != null) {
                        dispose.dispose();
                    }
                    return;
                }
                
                if (empty) {
                    break;
                }

                long r = qd.requested();
                if (r != 0L) {
                    if (qd.accept(a, v)) {
                        if (r != Long.MAX_VALUE) {
                            r = qd.produced(1);
                        }
                    }
                } else {
                    q.clear();
                    if (dispose != null) {
                        dispose.dispose();
                    }
                    a.onError(new IllegalStateException("Could not emit value due to lack of requests."));
                    return;
                }
            }
            
            missed = qd.leave(-missed);
            if (missed == 0) {
                break;
            }
        }
    }

    public static <T, U> boolean checkTerminated(boolean d, boolean empty, 
            Subscriber<?> s, boolean delayError, Queue<?> q, QueueDrain<T, U> qd) {
        if (qd.cancelled()) {
            q.clear();
            return true;
        }
        
        if (d) {
            if (delayError) {
                if (empty) {
                    Throwable err = qd.error();
                    if (err != null) {
                        s.onError(err);
                    } else {
                        s.onComplete();
                    }
                    return true;
                }
            } else {
                Throwable err = qd.error();
                if (err != null) {
                    q.clear();
                    s.onError(err);
                    return true;
                } else
                if (empty) {
                    s.onComplete();
                    return true;
                }
            }
        }
        
        return false;
    }
    
    public static <T, U> void drainLoop(Queue<T> q, NbpSubscriber<? super U> a, boolean delayError, Disposable dispose, NbpQueueDrain<T, U> qd) {
        
        int missed = 1;
        
        for (;;) {
            if (checkTerminated(qd.done(), q.isEmpty(), a, delayError, q, dispose, qd)) {
                return;
            }
            
            for (;;) {
                boolean d = qd.done();
                T v = q.poll();
                
                boolean empty = v == null;
                
                if (checkTerminated(d, empty, a, delayError, q, dispose, qd)) {
                    return;
                }
                
                if (empty) {
                    break;
                }

                qd.accept(a, v);
            }
            
            missed = qd.leave(-missed);
            if (missed == 0) {
                break;
            }
        }
    }

    public static <T, U> boolean checkTerminated(boolean d, boolean empty, 
            NbpSubscriber<?> s, boolean delayError, Queue<?> q, Disposable disposable, NbpQueueDrain<T, U> qd) {
        if (qd.cancelled()) {
            q.clear();
            disposable.dispose();
            return true;
        }
        
        if (d) {
            if (delayError) {
                if (empty) {
                    disposable.dispose();
                    Throwable err = qd.error();
                    if (err != null) {
                        s.onError(err);
                    } else {
                        s.onComplete();
                    }
                    return true;
                }
            } else {
                Throwable err = qd.error();
                if (err != null) {
                    q.clear();
                    disposable.dispose();
                    s.onError(err);
                    return true;
                } else
                if (empty) {
                    disposable.dispose();
                    s.onComplete();
                    return true;
                }
            }
        }
        
        return false;
    }
}
