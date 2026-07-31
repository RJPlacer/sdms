package com.sdms.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AgeUtilTest {

    private static final LocalDate AUGUST_1_2026 = LocalDate.of(2026, 8, 1);

    @Test
    void subtractsOneWhenBirthdayHasNotOccurred() {
        assertEquals(37, AgeUtil.calculate(LocalDate.of(1988, 9, 10), AUGUST_1_2026));
    }

    @Test
    void usesFullAgeWhenBirthdayAlreadyOccurredOrIsToday() {
        assertEquals(38, AgeUtil.calculate(LocalDate.of(1988, 6, 10), AUGUST_1_2026));
        assertEquals(38, AgeUtil.calculate(LocalDate.of(1988, 8, 1), AUGUST_1_2026));
    }

    @Test
    void rejectsFutureBirthDates() {
        assertThrows(IllegalArgumentException.class,
            () -> AgeUtil.calculate(LocalDate.of(2026, 8, 2), AUGUST_1_2026));
    }
}
