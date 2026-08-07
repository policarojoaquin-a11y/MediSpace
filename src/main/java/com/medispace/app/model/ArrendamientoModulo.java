package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "Arrendamiento_Modulo")
@SQLDelete(sql = "UPDATE Arrendamiento_Modulo SET Visible = 0 WHERE ID_Arrendamiento = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArrendamientoModulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Arrendamiento")
    private Integer idArrendamiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Medico", nullable = false)
    private Medico medico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Consultorio", nullable = false)
    private Consultorio consultorio;

    @Column(name = "Fecha_Inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "Fecha_Fin")
    private LocalDate fechaFin;

    @Column(name = "Dia_Semana", nullable = false, length = 20)
    private String diaSemana;

    @Column(name = "Hora_Inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "Hora_Fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "Duracion_Turno_Min", nullable = false)
    private Integer duracionTurnoMin;

    @Column(name = "Cupo_Maximo_Diario", nullable = false)
    private Integer cupoMaximoDiario;

    @Column(name = "Porcentaje_Consultorio", nullable = false)
    private BigDecimal porcentajeConsultorio;

    @Column(name = "Porcentaje_Medico", nullable = false)
    private BigDecimal porcentajeMedico;

    @Column(name = "Estado", nullable = false, length = 20)
    private String estado = "ACTIVO";

    @Column(name = "Observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;
}
