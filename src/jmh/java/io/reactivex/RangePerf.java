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

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import io.reactivex.internal.schedulers.SingleScheduler;
import io.reactivex.schedulers.Schedulers;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1)
@State(Scope.Thread)
public class RangePerf {
    @Param({ "1", "1000", "1000000" })
    public int times;
    
    Observable<Integer> range;
    
    Observable<Integer> rangeAsync;

    Observable<Integer> rangeAsyncPipeline;

    @Setup
    public void setup() {
        range = Observable.range(1, times);
        
        rangeAsync = range.observeOn(Schedulers.single());
        
        rangeAsyncPipeline = range.subscribeOn(new SingleScheduler()).observeOn(Schedulers.single());
    }
    
    @Benchmark
    public Object rangeSync(Blackhole bh) {
        LatchedObserver<Integer> lo = new LatchedObserver<Integer>(bh);
        
        range.subscribe(lo);
        
        return lo;
    }

    @Benchmark
    public void rangeAsync(Blackhole bh) throws Exception {
        LatchedObserver<Integer> lo = new LatchedObserver<Integer>(bh);
        
        rangeAsync.subscribe(lo);
        
        if (times == 1) {
            while (lo.latch.getCount() != 0);
        } else {
            lo.latch.await();
        }
    }

    @Benchmark
    public void rangePipeline(Blackhole bh) throws Exception {
        LatchedObserver<Integer> lo = new LatchedObserver<Integer>(bh);
        
        rangeAsyncPipeline.subscribe(lo);
        
        if (times == 1) {
            while (lo.latch.getCount() != 0);
        } else {
            lo.latch.await();
        }
    }

}