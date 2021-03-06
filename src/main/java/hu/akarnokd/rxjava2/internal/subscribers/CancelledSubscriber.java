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

package hu.akarnokd.rxjava2.internal.subscribers;

import org.reactivestreams.*;

import hu.akarnokd.rxjava2.plugins.RxJavaPlugins;

/**
 * A subscriber that cancels the subscription sent to it 
 * and ignores all events (onError is forwarded to RxJavaPlugins though).
 */
public enum CancelledSubscriber implements Subscriber<Object> {
    INSTANCE;
    
    @SuppressWarnings("unchecked")
    public static <T> Subscriber<T> instance() {
        return (Subscriber<T>)INSTANCE;
    }
    
    @Override
    public void onSubscribe(Subscription s) {
        s.cancel();
    }
    
    @Override
    public void onNext(Object t) {
        
    }
    
    @Override
    public void onError(Throwable t) {
        RxJavaPlugins.onError(t);
    }
    
    @Override
    public void onComplete() {
        
    }
}
