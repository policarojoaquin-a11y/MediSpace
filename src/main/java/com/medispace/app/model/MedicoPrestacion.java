package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "Medico_Prestacion")
@SQLDelete(sql = "UPDATE Medico_Prestacion SET Visible = 0 WHERE ID_Medico_Prestacion = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicoPrestacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Medico_Prestacion")
    private Integer idMedicoPrestacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Medico", nullable = false)
    private Medico medico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Prestacion", nullable = false)
    private PrestacionMedica prestacion;

    @Column(name = "Duracion_Estimada_Min")
    private Integer duracionEstimadaMin;

    @Column(name = "Importe_Particular")
    private BigDecimal importeParticular;

    @Column(name = "Tipo", length = 50)
    private String tipo;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;
}
