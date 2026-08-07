package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Dashboard_Gerencial")
@SQLDelete(sql = "UPDATE Dashboard_Gerencial SET Visible = 0 WHERE ID_Dashboard = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardGerencial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Dashboard")
    private Integer idDashboard;

    @Column(name = "Fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "Total_Turnos_Dia")
    private Integer totalTurnosDia;

    @Column(name = "Turnos_Atendidos")
    private Integer turnosAtendidos;

    @Column(name = "Turnos_Cancelados")
    private Integer turnosCancelados;

    @Column(name = "Turnos_No_Asistio")
    private Integer turnosNoAsistio;

    @Column(name = "Turnos_Disponibles")
    private Integer turnosDisponibles;

    @Column(name = "Facturacion_Total_Dia")
    private BigDecimal facturacionTotalDia;

    @Column(name = "Cobros_Pendientes")
    private BigDecimal cobrosPendientes;

    @Column(name = "Nuevos_Pacientes")
    private Integer nuevosPacientes;

    @Column(name = "Fecha_Actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;
}