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

package hu.akarnokd.rxjava2.internal.subscribers.nbp;

import hu.akarnokd.rxjava2.NbpObservable.NbpSubscriber;
import hu.akarnokd.rxjava2.disposables.Disposable;
import hu.akarnokd.rxjava2.functions.Consumer;
import hu.akarnokd.rxjava2.internal.disposables.EmptyDisposable;
import hu.akarnokd.rxjava2.internal.subscriptions.SubscriptionHelper;
import hu.akarnokd.rxjava2.plugins.RxJavaPlugins;

public final class NbpSubscriptionLambdaSubscriber<T> implements NbpSubscriber<T>, Disposable {
    final NbpSubscriber<? super T> actual;
    final Consumer<? super Disposable> onSubscribe;
    final Runnable onCancel;
    
    Disposable s;
    
    public NbpSubscriptionLambdaSubscriber(NbpSubscriber<? super T> actual, 
            Consumer<? super Disposable> onSubscribe,
            Runnable onCancel) {
        this.actual = actual;
        this.onSubscribe = onSubscribe;
        this.onCancel = onCancel;
    }

    @Override
    public void onSubscribe(Disposable s) {
        // this way, multiple calls to onSubscribe can show up in tests that use doOnSubscribe to validate behavior
        try {
            onSubscribe.accept(s);
        } catch (Throwable e) {
            s.dispose();
            RxJavaPlugins.onError(e);
            
            EmptyDisposable.error(e, actual);
            return;
        }
        if (SubscriptionHelper.validateDisposable(this.s, s)) {
            return;
        }
        this.s = s;
        actual.onSubscribe(this);
    }
    
    @Override
    public void onNext(T t) {
        actual.onNext(t);
    }
    
    @Override
    public void onError(Throwable t) {
        actual.onError(t);
    }
    
    @Override
    public void onComplete() {
        actual.onComplete();
    }
    
    
    @Override
    public void dispose() {
        try {
            onCancel.run();
        } catch (Throwable e) {
            RxJavaPlugins.onError(e);
        }
        s.dispose();
    }
}
