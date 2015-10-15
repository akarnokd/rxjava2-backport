package io.reactivex;

import io.reactivex.internal.functions.Objects;

/**
 * Simplified backport of Java 8's Optional type.
 *
 * @param <T> the value type
 */
public final class Optional<T> {
    final T value;
    protected Optional(T value) {
        this.value = value;
    }
    
    static final Optional<Object> EMPTY = new Optional<Object>(null);
    
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> empty() {
        return (Optional<T>)EMPTY;
    }
    
    public static <T> Optional<T> of(T value) {
        Objects.requireNonNull(value, "value is null");
        return new Optional<T>(value);
    }
    
    public boolean isPresent() {
        return value != null;
    }
    
    public T get() {
        return value;
    }
}
