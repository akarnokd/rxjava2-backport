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

package io.reactivex.schedulers;

import static org.junit.Assert.*;

import java.lang.management.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.*;

import io.reactivex.Scheduler;
import io.reactivex.Scheduler.Worker;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.functions.Functions;
import io.reactivex.internal.schedulers.*;

public class ExecutorSchedulerTest extends AbstractSchedulerConcurrencyTests {

    final static Executor executor = Executors.newFixedThreadPool(2, new RxThreadFactory("TestCustomPool-"));
    
    @Override
    protected Scheduler getScheduler() {
        return Schedulers.from(executor);
    }

    @Test
    @Ignore("Unhandled errors are no longer thrown")
    public final void testUnhandledErrorIsDeliveredToThreadHandler() throws InterruptedException {
        SchedulerTests.testUnhandledErrorIsDeliveredToThreadHandler(getScheduler());
    }

    @Test
    public final void testHandledErrorIsNotDeliveredToThreadHandler() throws InterruptedException {
        SchedulerTests.testHandledErrorIsNotDeliveredToThreadHandler(getScheduler());
    }
    
    public static void testCancelledRetention(Scheduler.Worker w, boolean periodic) throws InterruptedException {
        System.out.println("Wait before GC");
        Thread.sleep(1000);
        
        System.out.println("GC");
        System.gc();
        
        Thread.sleep(1000);

        
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage memHeap = memoryMXBean.getHeapMemoryUsage();
        long initial = memHeap.getUsed();
        
        System.out.printf("Starting: %.3f MB%n", initial / 1024.0 / 1024.0);

        int n = 200 * 1000;
        if (periodic) {
            final CountDownLatch cdl = new CountDownLatch(n);
            final Runnable action = new Runnable() {
                @Override
                public void run() {
                    cdl.countDown();
                }
            };
            for (int i = 0; i < n; i++) {
                if (i % 50000 == 0) {
                    System.out.println("  -> still scheduling: " + i);
                }
                w.schedulePeriodically(action, 0, 1, TimeUnit.DAYS);
            }
            
            System.out.println("Waiting for the first round to finish...");
            cdl.await();
        } else {
            for (int i = 0; i < n; i++) {
                if (i % 50000 == 0) {
                    System.out.println("  -> still scheduling: " + i);
                }
                w.schedule(Functions.emptyRunnable(), 1, TimeUnit.DAYS);
            }
        }
        
        memHeap = memoryMXBean.getHeapMemoryUsage();
        long after = memHeap.getUsed();
        System.out.printf("Peak: %.3f MB%n", after / 1024.0 / 1024.0);
        
        w.dispose();
        
        System.out.println("Wait before second GC");
        Thread.sleep(1000 + SchedulerPoolFactory.PURGE_PERIOD_SECONDS * 1000);
        if (periodic) {
            System.out.println("JDK 6 purge is N log N because it removes and shifts one by one");
            int t = (int)(n * Math.log(n) / 100);
            while (t > 0) {
                System.out.printf("  >> Waiting for purge: %.2f s remaining%n", t / 1000d);
                Thread.sleep(1000);
                t -= 1000;
            }
        }
        
        System.out.println("Second GC");
        System.gc();
        
        Thread.sleep(1000);
        
        memHeap = memoryMXBean.getHeapMemoryUsage();
        long finish = memHeap.getUsed();
        System.out.printf("After: %.3f MB%n", finish / 1024.0 / 1024.0);
        
        if (finish > initial * 5) {
            fail(String.format("Tasks retained: %.3f -> %.3f -> %.3f", initial / 1024 / 1024.0, after / 1024 / 1024.0, finish / 1024 / 1024d));
        }
    }
    
    @Test(timeout = 60000)
    public void testCancelledTaskRetention() throws InterruptedException {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Scheduler s = Schedulers.from(exec);
        try {
            Scheduler.Worker w = s.createWorker();
            try {
                testCancelledRetention(w, false);
            } finally {
                w.dispose();
            }
            
            w = s.createWorker();
            try {
                testCancelledRetention(w, true);
            } finally {
                w.dispose();
            }
        } finally {
            exec.shutdownNow();
        }
    }
    
    /** A simple executor which queues tasks and executes them one-by-one if executeOne() is called. */
    static final class TestExecutor implements Executor {
        final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<Runnable>();
        @Override
        public void execute(Runnable command) {
            queue.offer(command);
        }
        public void executeOne() {
            Runnable r = queue.poll();
            if (r != null) {
                r.run();
            }
        }
        public void executeAll() {
            Runnable r;
            while ((r = queue.poll()) != null) {
                r.run();
            }
        }
    }
    
    @Test
    public void testCancelledTasksDontRun() {
        final AtomicInteger calls = new AtomicInteger();
        Runnable task = new Runnable() {
            @Override
            public void run() {
                calls.getAndIncrement();
            }
        };
        TestExecutor exec = new TestExecutor();
        Scheduler custom = Schedulers.from(exec);
        Worker w = custom.createWorker();
        try {
            Disposable s1 = w.schedule(task);
            Disposable s2 = w.schedule(task);
            Disposable s3 = w.schedule(task);
            
            s1.dispose();
            s2.dispose();
            s3.dispose();
            
            exec.executeAll();
            
            assertEquals(0, calls.get());
        } finally {
            w.dispose();
        }
    }
    @Test
    public void testCancelledWorkerDoesntRunTasks() {
        final AtomicInteger calls = new AtomicInteger();
        Runnable task = new Runnable() {
            @Override
            public void run() {
                calls.getAndIncrement();
            }
        };
        TestExecutor exec = new TestExecutor();
        Scheduler custom = Schedulers.from(exec);
        Worker w = custom.createWorker();
        try {
            w.schedule(task);
            w.schedule(task);
            w.schedule(task);
        } finally {
            w.dispose();
        }
        exec.executeAll();
        assertEquals(0, calls.get());
    }
    
    // FIXME the internal structure changed and these can't be tested
//    
//    @Test
//    public void testNoTimedTaskAfterScheduleRetention() throws InterruptedException {
//        Executor e = new Executor() {
//            @Override
//            public void execute(Runnable command) {
//                command.run();
//            }
//        };
//        ExecutorWorker w = (ExecutorWorker)Schedulers.from(e).createWorker();
//        
//        w.schedule(Functions.emptyRunnable(), 50, TimeUnit.MILLISECONDS);
//        
//        assertTrue(w.tasks.hasSubscriptions());
//        
//        Thread.sleep(150);
//        
//        assertFalse(w.tasks.hasSubscriptions());
//    }
//    
//    @Test
//    public void testNoTimedTaskPartRetention() {
//        Executor e = new Executor() {
//            @Override
//            public void execute(Runnable command) {
//                
//            }
//        };
//        ExecutorWorker w = (ExecutorWorker)Schedulers.from(e).createWorker();
//        
//        Disposable s = w.schedule(Functions.emptyRunnable(), 1, TimeUnit.DAYS);
//        
//        assertTrue(w.tasks.hasSubscriptions());
//        
//        s.dispose();
//        
//        assertFalse(w.tasks.hasSubscriptions());
//    }
//    
//    @Test
//    public void testNoPeriodicTimedTaskPartRetention() throws InterruptedException {
//        Executor e = new Executor() {
//            @Override
//            public void execute(Runnable command) {
//                command.run();
//            }
//        };
//        ExecutorWorker w = (ExecutorWorker)Schedulers.from(e).createWorker();
//        
//        final CountDownLatch cdl = new CountDownLatch(1);
//        final Runnable action = new Runnable() {
//            @Override
//            public void run() {
//                cdl.countDown();
//            }
//        };
//        
//        Disposable s = w.schedulePeriodically(action, 0, 1, TimeUnit.DAYS);
//        
//        assertTrue(w.tasks.hasSubscriptions());
//        
//        cdl.await();
//        
//        s.dispose();
//        
//        assertFalse(w.tasks.hasSubscriptions());
//    }
}