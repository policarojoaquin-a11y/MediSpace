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

    @Column(name = "Codigo_Sigla", length = 20)
    private String codigoSigla;

    // "Plan" es palabra reservada en SQL Server — las comillas invertidas le indican a
    // Hibernate que debe citarla (`[Plan]`) en el DDL y en cada query generada, no solo acá.
    @Column(name = "`Plan`", length = 50)
    private String plan;

    @Column(name = "Requiere_Bono", nullable = false)
    private Boolean requiereBono = false;

    @Column(name = "Observaciones", length = 500)
    private String observaciones;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;
}
