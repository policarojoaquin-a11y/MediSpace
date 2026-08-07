package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "Consultorio")
@SQLDelete(sql = "UPDATE Consultorio SET Visible = 0 WHERE ID_Consultorio = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consultorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Consultorio")
    private Integer idConsultorio;

    @Column(name = "Numero_Consultorio", nullable = false, unique = true, length = 20)
    private String numeroConsultorio;

    @Column(name = "Descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "Estado", nullable = false, length = 20)
    private String estado = "DISPONIBLE";

    @Column(name = "Equipamiento", columnDefinition = "TEXT")
    private String equipamiento;

    @Column(name = "Ubicacion", length = 100)
    private String ubicacion;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;
}
