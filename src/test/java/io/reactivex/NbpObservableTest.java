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

import java.util.*;

import org.junit.*;

import io.reactivex.functions.Function;

public class NbpObservableTest {
    @Test
    public void testFlatMap() {
        List<Integer> list = NbpObservable.range(1, 5).flatMap(new Function<Integer, NbpObservable<Integer>>() {
            @Override
            public NbpObservable<Integer> apply(Integer v) {
                return NbpObservable.range(v, 2);
            }
        }).getList();
        
        Assert.assertEquals(Arrays.asList(1, 2, 2, 3, 3, 4, 4, 5, 5, 6), list);
    }
}
