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

package io.reactivex.internal.operators;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.reactivex.internal.util.BackpressureHelper;

public class BackpressureHelperTest {
    @Test
    public void testAddCap() {
        assertEquals(2L, BackpressureHelper.addCap(1, 1));
        assertEquals(Long.MAX_VALUE, BackpressureHelper.addCap(1, Long.MAX_VALUE - 1));
        assertEquals(Long.MAX_VALUE, BackpressureHelper.addCap(1, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, BackpressureHelper.addCap(Long.MAX_VALUE - 1, Long.MAX_VALUE - 1));
        assertEquals(Long.MAX_VALUE, BackpressureHelper.addCap(Long.MAX_VALUE, Long.MAX_VALUE));
    }
    
    @Test
    public void testMultiplyCap() {
        assertEquals(6, BackpressureHelper.multiplyCap(2, 3));
        assertEquals(Long.MAX_VALUE, BackpressureHelper.multiplyCap(2, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, BackpressureHelper.multiplyCap(Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, BackpressureHelper.multiplyCap(1L << 32, 1L << 32));

    }
}