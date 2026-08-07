package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "ObraSocial")
@SQLDelete(sql = "UPDATE ObraSocial SET Visible = 0 WHERE ID_ObraSocial = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObraSocial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_ObraSocial")
    private Integer idObraSocial;

    @Column(name = "Nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;
}
