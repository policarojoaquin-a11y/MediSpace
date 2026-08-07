package com.medispace.app.dto.arrendamiento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CierreDiarioRequestDTO {
    private Integer idMedico;
    private Integer idConsultorio;
    private LocalDate fecha;
}

