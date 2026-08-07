package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "Uso_Consultorio")
@SQLDelete(sql = "UPDATE Uso_Consultorio SET Visible = 0 WHERE ID_Uso = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsoConsultorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Uso")
    private Integer idUso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Consultorio", nullable = false)
    private Consultorio consultorio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Medico", nullable = false)
    private Medico medico;

    @Column(name = "Fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "Hora_Inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "Hora_Fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "Cantidad_Pacientes")
    private Integer cantidadPacientes;

    @Column(name = "Facturacion_Generada")
    private BigDecimal facturacionGenerada;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;
}
