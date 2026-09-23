package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Medicos")
@SQLDelete(sql = "UPDATE Medicos SET Visible = 0 WHERE ID_Medico = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Medico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Medico")
    private Integer idMedico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "Nombre", nullable = false, length = 50)
    private String nombre;

    @Column(name = "Apellido", nullable = false, length = 50)
    private String apellido;

    @Column(name = "Matricula", nullable = false, length = 50, unique = true)
    private String matricula;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Especialidad", nullable = false)
    private Especialidad especialidad;

    @Column(name = "Importe_Consulta")
    private BigDecimal importeConsulta;

    @Column(name = "Estado", nullable = false, length = 20)
    private String estado = "ACTIVO";

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;

    @Column(name = "Fecha_Creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "Fecha_Inicio_Actividad", nullable = false)
    private LocalDate fechaInicioActividad;
}
