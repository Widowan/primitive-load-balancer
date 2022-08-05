package dev.wido;

@FunctionalInterface
public interface CheckedSupplier<T> {
    T get() throws Throwable;
}
