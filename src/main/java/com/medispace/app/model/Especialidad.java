package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "Especialidades")
@SQLDelete(sql = "UPDATE Especialidades SET Visible = 0 WHERE ID_Especialidad = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Especialidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Especialidad")
    private Integer idEspecialidad;

    @Column(name = "Nombre", nullable = false, length = 50, unique = true)
    private String nombre;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;
}
