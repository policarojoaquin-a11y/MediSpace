package com.medispace.app.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Única fuente de verdad para el split médico/consultorio (RN-006). Usado por
 * LiquidacionServiceImpl y CierreDiarioServiceImpl para que ambos cálculos económicos
 * no puedan volver a divergir entre sí.
 */
public final class SplitFinancieroCalculator {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private SplitFinancieroCalculator() {
    }

    public static Split calcular(BigDecimal importeTotal, BigDecimal porcentajeMedico, BigDecimal porcentajeConsultorio) {
        BigDecimal parteMedico = importeTotal.multiply(porcentajeMedico).divide(CIEN, 2, RoundingMode.HALF_UP);
        BigDecimal parteConsultorio = importeTotal.multiply(porcentajeConsultorio).divide(CIEN, 2, RoundingMode.HALF_UP);
        return new Split(parteMedico, parteConsultorio);
    }

    public record Split(BigDecimal parteMedico, BigDecimal parteConsultorio) {
    }
}
