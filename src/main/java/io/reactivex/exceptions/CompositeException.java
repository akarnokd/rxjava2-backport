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

package io.reactivex.exceptions;

import java.util.*;

/**
 * A runtime exception that allows collecting multiple exceptions into one single instance.
 */
public final class CompositeException extends RuntimeException {
    /** */
    private static final long serialVersionUID = 2004635183691362481L;

    final List<Throwable> suppressed = new ArrayList<Throwable>();
    
    public CompositeException() {
        super();
    }

    public CompositeException(String message) {
        super(message);
    }
    
    public CompositeException(Throwable... exceptions) {
        if (exceptions == null) {
            suppressed.add(new NullPointerException("exceptions is null"));
        } else {
            for (Throwable t : exceptions) {
                suppressed.add(t != null ? t : new NullPointerException("One of the exceptions is null"));
            }
        }
    }
    
    /**
     * Adds a suppressed exception to this composite.
     * <p>The method is named this way to avoid conflicts with Java 7 environments
     * and its addSuppressed() method.
     * @param e the exception to suppress, nulls are converted to NullPointerExceptions
     */
    public void suppress(Throwable e) {
        suppressed.add(e != null ? e : new NullPointerException("null exception"));
    }

    /**
     * Returns a copy of all exceptions in this composite, including any cause and
     * suppressed exceptions.
     * @return the copy of all contained exceptions
     */
    public List<Throwable> getExceptions() {
        Throwable cause = getCause();
        List<Throwable> list = new ArrayList<Throwable>(cause != null 
                ? 1 + suppressed.size() : suppressed.size());
        if (cause != null) {
            list.add(cause);
        }
        for (Throwable t : suppressed) {
            list.add(t);
        }
        
        return list;
    }
    
    /**
     * Returns true if this CompositeException doesn't have a cause or
     * any suppressed exceptions.
     * @return true if this CompositeException doesn't have a cause or
     * any suppressed exceptions.
     */
    public boolean isEmpty() {
        return suppressed.isEmpty() && getCause() == null;
    }
    
}
