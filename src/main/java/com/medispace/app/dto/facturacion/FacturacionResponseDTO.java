package com.medispace.app.dto.facturacion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FacturacionResponseDTO {
    private Integer idFacturacion;
    private Integer idTurno;
    private Integer idPaciente;
    private String nombrePaciente;
    private Integer idMedico;
    private String nombreMedico;
    private LocalDateTime fechaFacturacion;
    private String tipoConsulta;
    private String metodoPago;
    private String obraSocial;
    private BigDecimal importeTotal;
    private BigDecimal importeCopago;
    // RN-025: derivado (importeTotal - importeCopago), no persistido — lo que cubre la obra
    // social directamente al médico, fuera de la caja del consultorio. Informativo.
    private BigDecimal importeCubiertoOs;
    private BigDecimal porcentajeConsultorio;
    private BigDecimal porcentajeMedico;
    private String estadoPago;
    private String observaciones;
    // Método de pago cargado al reservar el turno (sección 4.4) — usado por el frontend para
    // prellenar el formulario de cobro. metodoPago sigue siendo el estado real (PENDIENTE hasta
    // que se registre el cobro), no se sobreescribe con este valor.
    private String metodoPagoPlanificado;
}
