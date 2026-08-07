package com.medispace.app.dto.turno;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReservarTurnoDTO {
    private Integer idPaciente;
    private Integer idPrestacion;
    // Datos de la reserva (sección 4.4) — nombres alineados 1:1 con el payload de turnos.js
    // (tipoConsulta, metodoPago, obraSocial, copago) para que Jackson no los descarte por
    // desajuste de nombre.
    private String tipoConsulta;
    private String metodoPago;
    private String obraSocial;
    private BigDecimal copago;
}
