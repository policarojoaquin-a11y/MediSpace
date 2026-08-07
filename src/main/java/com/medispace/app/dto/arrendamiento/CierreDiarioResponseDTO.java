package com.medispace.app.dto.arrendamiento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CierreDiarioResponseDTO {
    private Integer idCierre;
    private Integer idMedico;
    private String nombreMedico;
    private Integer idConsultorio;
    private String numeroConsultorio;
    private LocalDate fecha;
    private BigDecimal totalFacturadoDia;
    private BigDecimal importeConsultorio;
    private BigDecimal importeMedico;
    private Integer cantidadTurnos;
    private String estado;
    private LocalDateTime fechaRegistro;
}
