package dev.wido;

import java.util.Optional;

public class Utils {
    public static <T> Optional<T> uncheckedLift(CheckedSupplier<T> supplier) {
        try {
            return Optional.ofNullable(supplier.get());
        } catch (Throwable e) {
            return Optional.empty();
        }
    }
}
