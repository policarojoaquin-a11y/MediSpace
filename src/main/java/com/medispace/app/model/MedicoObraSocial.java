package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "Medico_ObraSocial")
@SQLDelete(sql = "UPDATE Medico_ObraSocial SET Visible = 0 WHERE ID_Medico_ObraSocial = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicoObraSocial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Medico_ObraSocial")
    private Integer idMedicoObraSocial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Medico", nullable = false)
    private Medico medico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_ObraSocial", nullable = false)
    private ObraSocial obraSocial;

    // Coseguro que cobra ESTE médico por atender pacientes de ESTA obra social — depende del
    // arreglo particular que tenga con ella, lo decide el médico (no es un valor fijo de la
    // obra social ni un valor único del médico para todas sus obras sociales).
    @Column(name = "Importe_Coseguro")
    private BigDecimal importeCoseguro;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;
}
