package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Pacientes")
@SQLDelete(sql = "UPDATE Pacientes SET Visible = 0 WHERE ID_Paciente = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Paciente")
    private Integer idPaciente;

    @Column(name = "Nombre", nullable = false, length = 50)
    private String nombre;

    @Column(name = "Apellido", nullable = false, length = 50)
    private String apellido;

    @Column(name = "DNI", nullable = false, length = 20, unique = true)
    private String dni;

    @Column(name = "Telefono", length = 20)
    private String telefono;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_ObraSocial")
    private ObraSocial obraSocial;

    @Column(name = "Numero_Credencial", length = 50)
    private String numeroCredencial;

    @Column(name = "Direccion", length = 150)
    private String direccion;

    @Column(name = "Plan_OS", length = 50)
    private String planOs;

    @Column(name = "Fecha_Nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(name = "Estado", nullable = false, length = 20)
    private String estado = "ACTIVO";

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;

    @Column(name = "Fecha_Creacion", nullable = false)
    private LocalDateTime fechaCreacion;
}
