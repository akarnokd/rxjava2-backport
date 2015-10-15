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

package io.reactivex.internal.operators.nbp;

import static org.mockito.Mockito.*;

import org.junit.*;

import io.reactivex.*;
import io.reactivex.NbpObservable.NbpSubscriber;

public class NbpOperatorFinallyTest {

    private Runnable aAction0;
    private NbpSubscriber<String> NbpObserver;

    // mocking has to be unchecked, unfortunately
    @Before
    public void before() {
        aAction0 = mock(Runnable.class);
        NbpObserver = TestHelper.mockNbpSubscriber();
    }

    private void checkActionCalled(NbpObservable<String> input) {
        input.finallyDo(aAction0).subscribe(NbpObserver);
        verify(aAction0, times(1)).run();
    }

    @Test
    public void testFinallyCalledOnComplete() {
        checkActionCalled(NbpObservable.fromArray("1", "2", "3"));
    }

    @Test
    public void testFinallyCalledOnError() {
        checkActionCalled(NbpObservable.<String> error(new RuntimeException("expected")));
    }
}