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

/*
 * The code was inspired by the similarly named JCTools class: 
 * https://github.com/JCTools/JCTools/blob/master/jctools-core/src/main/java/org/jctools/queues/atomic
 */

package io.reactivex.internal.queue;

import java.util.*;
import java.util.concurrent.atomic.*;

import io.reactivex.internal.util.Pow2;

/**
 * A single-producer single-consumer array-backed queue which can allocate new arrays in case the consumer is slower
 * than the producer.
 */
public final class SpscLinkedArrayQueue<T> implements Queue<T> {
    static final int MAX_LOOK_AHEAD_STEP = Integer.getInteger("jctools.spsc.max.lookahead.step", 4096);
    protected volatile long producerIndex;
    @SuppressWarnings("rawtypes")
    static final AtomicLongFieldUpdater<SpscLinkedArrayQueue> PRODUCER_INDEX =
            AtomicLongFieldUpdater.newUpdater(SpscLinkedArrayQueue.class, "producerIndex");
    protected int producerLookAheadStep;
    protected long producerLookAhead;
    protected int producerMask;
    protected AtomicReferenceArray<Object> producerBuffer;
    protected int consumerMask;
    protected AtomicReferenceArray<Object> consumerBuffer;
    protected volatile long consumerIndex;
    @SuppressWarnings("rawtypes")
    static final AtomicLongFieldUpdater<SpscLinkedArrayQueue> CONSUMER_INDEX =
            AtomicLongFieldUpdater.newUpdater(SpscLinkedArrayQueue.class, "consumerIndex");
    private static final Object HAS_NEXT = new Object();

    public SpscLinkedArrayQueue(final int bufferSize) {
        int p2capacity = Pow2.roundToPowerOfTwo(bufferSize);
        int mask = p2capacity - 1;
        AtomicReferenceArray<Object> buffer = new AtomicReferenceArray<Object>(p2capacity + 1);
        producerBuffer = buffer;
        producerMask = mask;
        adjustLookAheadStep(p2capacity);
        consumerBuffer = buffer;
        consumerMask = mask;
        producerLookAhead = mask - 1; // we know it's all empty to start with
        soProducerIndex(0L);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single producer thread use only.
     */
    @Override
    public final boolean offer(final T e) {
        // local load of field to avoid repeated loads after volatile reads
        final AtomicReferenceArray<Object> buffer = producerBuffer;
        final long index = lpProducerIndex();
        final int mask = producerMask;
        final int offset = calcWrappedOffset(index, mask);
        if (index < producerLookAhead) {
            return writeToQueue(buffer, e, index, offset);
        } else {
            final int lookAheadStep = producerLookAheadStep;
            // go around the buffer or resize if full (unless we hit max capacity)
            int lookAheadElementOffset = calcWrappedOffset(index + lookAheadStep, mask);
            if (null == lvElement(buffer, lookAheadElementOffset)) {// LoadLoad
                producerLookAhead = index + lookAheadStep - 1; // joy, there's plenty of room
                return writeToQueue(buffer, e, index, offset);
            } else if (null == lvElement(buffer, calcWrappedOffset(index + 1, mask))) { // buffer is not full
                return writeToQueue(buffer, e, index, offset);
            } else {
                resize(buffer, index, offset, e, mask); // add a buffer and link old to new
                return true;
            }
        }
    }

    private boolean writeToQueue(final AtomicReferenceArray<Object> buffer, final T e, final long index, final int offset) {
        soProducerIndex(index + 1);// this ensures atomic write of long on 32bit platforms
        soElement(buffer, offset, e);// StoreStore
        return true;
    }

    private void resize(final AtomicReferenceArray<Object> oldBuffer, final long currIndex, final int offset, final T e,
            final long mask) {
        final int capacity = oldBuffer.length();
        final AtomicReferenceArray<Object> newBuffer = new AtomicReferenceArray<Object>(capacity);
        producerBuffer = newBuffer;
        producerLookAhead = currIndex + mask - 1;
        soProducerIndex(currIndex + 1);// this ensures correctness on 32bit platforms
        soElement(newBuffer, offset, e);// StoreStore
        soNext(oldBuffer, newBuffer);
        soElement(oldBuffer, offset, HAS_NEXT); // new buffer is visible after element is
                                                                 // inserted
    }

    private void soNext(AtomicReferenceArray<Object> curr, AtomicReferenceArray<Object> next) {
        soElement(curr, calcDirectOffset(curr.length() - 1), next);
    }
    @SuppressWarnings("unchecked")
    private AtomicReferenceArray<Object> lvNext(AtomicReferenceArray<Object> curr) {
        return (AtomicReferenceArray<Object>)lvElement(curr, calcDirectOffset(curr.length() - 1));
    }
    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    @Override
    public final T poll() {
        // local load of field to avoid repeated loads after volatile reads
        final AtomicReferenceArray<Object> buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final int mask = consumerMask;
        final int offset = calcWrappedOffset(index, mask);
        final Object e = lvElement(buffer, offset);// LoadLoad
        boolean isNextBuffer = e == HAS_NEXT;
        if (null != e && !isNextBuffer) {
            soConsumerIndex(index + 1);// this ensures correctness on 32bit platforms
            soElement(buffer, offset, null);// StoreStore
            return (T) e;
        } else if (isNextBuffer) {
            return newBufferPoll(lvNext(buffer), index, mask);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private T newBufferPoll(AtomicReferenceArray<Object> nextBuffer, final long index, final int mask) {
        consumerBuffer = nextBuffer;
        final int offsetInNew = calcWrappedOffset(index, mask);
        final T n = (T) lvElement(nextBuffer, offsetInNew);// LoadLoad
        if (null == n) {
            return null;
        } else {
            soConsumerIndex(index + 1);// this ensures correctness on 32bit platforms
            soElement(nextBuffer, offsetInNew, null);// StoreStore
            return n;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    @Override
    public final T peek() {
        final AtomicReferenceArray<Object> buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final int mask = consumerMask;
        final int offset = calcWrappedOffset(index, mask);
        final Object e = lvElement(buffer, offset);// LoadLoad
        if (e == HAS_NEXT) {
            return newBufferPeek(lvNext(buffer), index, mask);
        }

        return (T) e;
    }
    
    @Override
    public void clear() {
        while (poll() != null || !isEmpty());
    }

    @SuppressWarnings("unchecked")
    private T newBufferPeek(AtomicReferenceArray<Object> nextBuffer, final long index, final int mask) {
        consumerBuffer = nextBuffer;
        final int offsetInNew = calcWrappedOffset(index, mask);
        return (T) lvElement(nextBuffer, offsetInNew);// LoadLoad
    }

    @Override
    public final int size() {
        /*
         * It is possible for a thread to be interrupted or reschedule between the read of the producer and
         * consumer indices, therefore protection is required to ensure size is within valid range. In the
         * event of concurrent polls/offers to this method the size is OVER estimated as we read consumer
         * index BEFORE the producer index.
         */
        long after = lvConsumerIndex();
        while (true) {
            final long before = after;
            final long currentProducerIndex = lvProducerIndex();
            after = lvConsumerIndex();
            if (before == after) {
                return (int) (currentProducerIndex - after);
            }
        }
    }
    
    @Override
    public boolean isEmpty() {
        return lvProducerIndex() == lvConsumerIndex();
    }

    private void adjustLookAheadStep(int capacity) {
        producerLookAheadStep = Math.min(capacity / 4, MAX_LOOK_AHEAD_STEP);
    }

    private long lvProducerIndex() {
        return producerIndex;
    }

    private long lvConsumerIndex() {
        return consumerIndex;
    }

    private long lpProducerIndex() {
        return producerIndex;
    }

    private long lpConsumerIndex() {
        return consumerIndex;
    }

    private void soProducerIndex(long v) {
        PRODUCER_INDEX.lazySet(this, v);
    }

    private void soConsumerIndex(long v) {
        CONSUMER_INDEX.lazySet(this, v);
    }

    private static final int calcWrappedOffset(long index, int mask) {
        return calcDirectOffset((int)index & mask);
    }
    private static final int calcDirectOffset(int index) {
        return index;
    }
    private static final void soElement(AtomicReferenceArray<Object> buffer, int offset, Object e) {
        buffer.lazySet(offset, e);
    }

    private static final <E> Object lvElement(AtomicReferenceArray<Object> buffer, int offset) {
        return buffer.get(offset);
    }

    @Override
    public final Iterator<T> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E> E[] toArray(E[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(T e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public T element() {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Offer two elements at the same time.
     * <p>Don't use the regular offer() with this at all!
     * @param first
     * @param second
     * @return
     */
    public boolean offer(T first, T second) {
        final AtomicReferenceArray<Object> buffer = producerBuffer;
        final long p = producerIndex;
        final int m = producerMask;
        
        int pi = calcWrappedOffset(p + 2, m);
        
        if (null == lvElement(buffer, pi)) {
            pi = calcWrappedOffset(p, m);
            soElement(buffer, pi + 1, second);
            soProducerIndex(p + 2);
            soElement(buffer, pi, first);
        } else {
            final int capacity = buffer.length();
            final AtomicReferenceArray<Object> newBuffer = new AtomicReferenceArray<Object>(capacity);
            producerBuffer = newBuffer;
            
            pi = calcWrappedOffset(p, m);
            soElement(newBuffer, pi + 1, second);// StoreStore
            soElement(newBuffer, pi, first);
            soNext(buffer, newBuffer);
            
            soProducerIndex(p + 2);// this ensures correctness on 32bit platforms
            
            soElement(buffer, pi, HAS_NEXT); // new buffer is visible after element is
        }

        return true;
    }
}
