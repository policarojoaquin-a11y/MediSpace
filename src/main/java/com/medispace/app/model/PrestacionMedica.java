package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Prestaciones_Medicas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrestacionMedica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Prestacion")
    private Integer idPrestacion;

    @Column(name = "Nombre", length = 50)
    private String nombre;
}
