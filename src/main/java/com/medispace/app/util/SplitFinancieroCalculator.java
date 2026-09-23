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

    /**
     * RN-025: monto que efectivamente entró a la caja del consultorio ("cobrado en mano"),
     * base del split 70/30 y de las sumas de Liquidación/Cierre de Caja/Reportes. El coseguro
     * que la obra social le paga al médico directo nunca entra a esa caja, así que no forma
     * parte del split. {@code importeCopago} ya representa lo cobrado en mano (precio de la
     * consulta menos lo que cubre la obra social — se resuelve al reservar el turno). Fallback
     * a {@code importeTotal} cuando {@code importeCopago} es null (facturas legacy sin ese dato).
     */
    public static BigDecimal montoCobradoEnMano(BigDecimal importeTotal, BigDecimal importeCopago) {
        return importeCopago != null ? importeCopago : importeTotal;
    }

    /**
     * Complemento de {@link #montoCobradoEnMano}: lo que cubrió la obra social directamente al
     * médico, fuera de la caja del consultorio. Puramente informativo — nunca negativo.
     */
    public static BigDecimal montoCubiertoPorObraSocial(BigDecimal importeTotal, BigDecimal importeCopago) {
        BigDecimal total = importeTotal != null ? importeTotal : BigDecimal.ZERO;
        BigDecimal cubierto = total.subtract(montoCobradoEnMano(total, importeCopago));
        return cubierto.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : cubierto;
    }

    public record Split(BigDecimal parteMedico, BigDecimal parteConsultorio) {
    }
}
