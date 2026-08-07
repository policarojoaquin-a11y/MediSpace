package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Turnos")
@SQLDelete(sql = "UPDATE Turnos SET Visible = 0 WHERE ID_Turno = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Turno")
    private Integer idTurno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Medico", nullable = false)
    private Medico medico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Paciente")
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Consultorio")
    private Consultorio consultorio;

    @Column(name = "Fecha_Hora", nullable = false)
    private LocalDateTime fechaHora;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Prestacion")
    private PrestacionMedica prestacion;

    @Column(name = "Estado", nullable = false, length = 20)
    private String estado = "DISPONIBLE"; // DISPONIBLE, RESERVADO, EN_ESPERA, ATENDIDO, CANCELADO, NO_ASISTIO

    @Column(name = "Fecha_Reserva")
    private LocalDateTime fechaReserva;

    // Datos cargados al reservar (sección 4.4: Tipo de Consulta, Método de Pago, Cobertura OS,
    // Copago). El circuito de Facturación real sigue arrancando recién en "Atendido" (sección
    // 4.6) — estos campos son la "intención" capturada en la reserva, para que
    // FacturacionServiceImpl.crearFacturacionAutomatica los use como valores por defecto en
    // vez de perderlos silenciosamente (bug corregido: antes se descartaban porque
    // ReservarTurnoDTO no los declaraba).
    @Column(name = "Tipo_Consulta", length = 50)
    private String tipoConsulta;

    @Column(name = "Metodo_Pago_Planificado", length = 30)
    private String metodoPagoPlanificado;

    @Column(name = "Obra_Social_Planificada", length = 100)
    private String obraSocialPlanificada;

    @Column(name = "Importe_Copago_Planificado")
    private BigDecimal importeCopagoPlanificado;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;

    @Column(name = "Fecha_Creacion", nullable = false)
    private LocalDateTime fechaCreacion;
}
