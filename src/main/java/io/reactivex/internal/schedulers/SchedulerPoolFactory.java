/**
 * Copyright 2015 David Karnok and Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.reactivex.internal.schedulers;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.plugins.RxJavaPlugins;

/**
 * Manages the creating of ScheduledExecutorServices and sets up purging
 */
public enum SchedulerPoolFactory {
    ;
    
    static final String PURGE_ENABLED_KEY = "rx2.purge-enabled";
    
    /**
     * Indicates the periodic purging of the ScheduledExecutorService is enabled.
     */
    public static final boolean PURGE_ENABLED;
    
    static final String PURGE_PERIOD_SECONDS_KEY = "rx2.purge-period-seconds";
    
    /**
     * Indicates the purge period of the ScheduledExecutorServices created by create().
     */
    public static final int PURGE_PERIOD_SECONDS;
    
    static final AtomicReference<ScheduledExecutorService> PURGE_THREAD = 
            new AtomicReference<ScheduledExecutorService>();
    
    static final ConcurrentHashMap<ScheduledThreadPoolExecutor, Object> POOLS =
            new ConcurrentHashMap<ScheduledThreadPoolExecutor, Object>();
    
    /**
     * Starts the purge thread if not already started.
     */
    public static void start() {
        for (;;) {
            ScheduledExecutorService curr = PURGE_THREAD.get();
            if (curr != null && !curr.isShutdown()) {
                return;
            }
            ScheduledExecutorService next = Executors.newScheduledThreadPool(1, new RxThreadFactory("RxSchedulerPurge-"));
            if (PURGE_THREAD.compareAndSet(curr, next)) {
                
                next.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            for (ScheduledThreadPoolExecutor e : new ArrayList<ScheduledThreadPoolExecutor>(POOLS.keySet())) {
                                if (e.isShutdown()) {
                                    POOLS.remove(e);
                                } else {
                                    e.purge();
                                }
                            }
                        } catch (Throwable e) {
                            RxJavaPlugins.onError(e);
                        }
                    }
                }, PURGE_PERIOD_SECONDS, PURGE_PERIOD_SECONDS, TimeUnit.SECONDS);
                
                return;
            } else {
                next.shutdownNow();
            }
        }
    }
    
    /**
     * Stops the purge thread.
     */
    public static void shutdown() {
        PURGE_THREAD.get().shutdownNow();
        POOLS.clear();
    }
    
    static {
        boolean purgeEnable = true;
        int purgePeriod = 1;
        
        Properties properties = System.getProperties();
        
        if (properties.containsKey(PURGE_ENABLED_KEY)) {
            purgeEnable = Boolean.getBoolean(PURGE_ENABLED_KEY);
            
            if (purgeEnable && properties.containsKey(PURGE_PERIOD_SECONDS_KEY)) {
                purgePeriod = Integer.getInteger(PURGE_PERIOD_SECONDS_KEY, purgePeriod);
            }
        }
        
        PURGE_ENABLED = purgeEnable;
        PURGE_PERIOD_SECONDS = purgePeriod;
        
        start();
    }
    
    /**
     * Creates a ScheduledExecutorService with the given factory.
     * @param factory the thread factory
     * @return the ScheduledExecutorService
     */
    public static ScheduledExecutorService create(ThreadFactory factory) {
        final ScheduledExecutorService exec = Executors.newScheduledThreadPool(1, factory);
        if (exec instanceof ScheduledThreadPoolExecutor) {
            ScheduledThreadPoolExecutor e = (ScheduledThreadPoolExecutor) exec;
            POOLS.put(e, exec);
        }
        return exec;
    }
}
