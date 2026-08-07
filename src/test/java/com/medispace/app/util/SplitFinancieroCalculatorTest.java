package com.medispace.app.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SplitFinancieroCalculatorTest {

    @Test
    void testSplit7030() {
        SplitFinancieroCalculator.Split split = SplitFinancieroCalculator.calcular(
                new BigDecimal("10000.00"), new BigDecimal("70.00"), new BigDecimal("30.00"));

        assertEquals(new BigDecimal("7000.00"), split.parteMedico());
        assertEquals(new BigDecimal("3000.00"), split.parteConsultorio());
    }

    @Test
    void testSplit6040() {
        SplitFinancieroCalculator.Split split = SplitFinancieroCalculator.calcular(
                new BigDecimal("10000.00"), new BigDecimal("60.00"), new BigDecimal("40.00"));

        assertEquals(new BigDecimal("6000.00"), split.parteMedico());
        assertEquals(new BigDecimal("4000.00"), split.parteConsultorio());
    }

    @Test
    void testRedondeoHalfUpConCentavos() {
        // 999.99 * 70 / 100 = 699.993 -> redondea a 699.99 (HALF_UP sobre el tercer decimal)
        SplitFinancieroCalculator.Split split = SplitFinancieroCalculator.calcular(
                new BigDecimal("999.99"), new BigDecimal("70.00"), new BigDecimal("30.00"));

        assertEquals(new BigDecimal("699.99"), split.parteMedico());
        assertEquals(new BigDecimal("300.00"), split.parteConsultorio());
    }
}
