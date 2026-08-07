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
    private BigDecimal importeCubiertoOs;
    private BigDecimal importeCopago;
    private String numeroComprobante;
}
