/**
 * Copyright 2015 David Karnok
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

import java.util.Properties;
import java.util.concurrent.*;

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
    
    static {
        boolean purgeEnable = true;
        int purgePeriod = 2;
        
        Properties properties = System.getProperties();
        
        if (properties.containsKey(PURGE_ENABLED_KEY)) {
            purgeEnable = Boolean.getBoolean(PURGE_ENABLED_KEY);
            
            if (purgeEnable && properties.containsKey(PURGE_PERIOD_SECONDS_KEY)) {
                purgePeriod = Integer.getInteger(PURGE_PERIOD_SECONDS_KEY, purgePeriod);
            }
        }
        
        PURGE_ENABLED = purgeEnable;
        PURGE_PERIOD_SECONDS = purgePeriod;
    }
    
    /**
     * Creates a ScheduledExecutorService with the given factory.
     * @param factory the thread factory
     * @return the ScheduledExecutorService
     */
    public static ScheduledExecutorService create(ThreadFactory factory) {
        final ScheduledExecutorService exec = Executors.newScheduledThreadPool(1, factory);
        if (PURGE_ENABLED && (exec instanceof ScheduledThreadPoolExecutor)) {
            final ScheduledThreadPoolExecutor pool = (ScheduledThreadPoolExecutor) exec;
            exec.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    pool.purge();
                }
            }, PURGE_PERIOD_SECONDS, PURGE_PERIOD_SECONDS, TimeUnit.SECONDS);
        }
        return exec;
    }
}
