package com.medispace.app.dto.facturacion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegistrarCobroDTO {
    private String metodoPago; // EFECTIVO, TARJETA, TRANSFERENCIA, MERCADOPAGO
    private BigDecimal importeTotal;
    // No hay campo "importeCubiertoOs" acá a propósito (RN-025): se calcula server-side como
    // importeTotal - importeCopago, no se confía en un valor libre del formulario.
    private BigDecimal importeCopago;
    private String numeroComprobante;
}
