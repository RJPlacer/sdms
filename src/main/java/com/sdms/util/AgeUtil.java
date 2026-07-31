package com.sdms.util;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

public final class AgeUtil {

    private AgeUtil() {}

    public static int calculate(LocalDate birthDate) {
        return calculate(birthDate, LocalDate.now());
    }

    public static int calculate(LocalDate birthDate, LocalDate asOfDate) {
        Objects.requireNonNull(birthDate, "birthDate");
        Objects.requireNonNull(asOfDate, "asOfDate");
        if (birthDate.isAfter(asOfDate)) {
            throw new IllegalArgumentException("Birth date cannot be in the future.");
        }
        return Period.between(birthDate, asOfDate).getYears();
    }
}
