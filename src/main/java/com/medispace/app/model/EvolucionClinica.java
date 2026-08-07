package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Evolucion_Clinica")
@SQLDelete(sql = "UPDATE Evolucion_Clinica SET Visible = 0 WHERE ID_Evolucion = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvolucionClinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Evolucion")
    private Integer idEvolucion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_HistoriaClinica", nullable = false)
    private HistoriaClinica historiaClinica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Medico", nullable = false)
    private Medico medico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Turno")
    private Turno turno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Prestacion")
    private PrestacionMedica prestacion;

    @Column(name = "Fecha_Hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "Motivo_Consulta", columnDefinition = "TEXT")
    private String motivoConsulta;

    @Column(name = "Diagnostico", columnDefinition = "TEXT")
    private String diagnostico;

    @Column(name = "Tratamiento", columnDefinition = "TEXT")
    private String tratamiento;

    @Column(name = "Indicaciones", columnDefinition = "TEXT")
    private String indicaciones;

    @Column(name = "Estudios_Solicitados", columnDefinition = "TEXT")
    private String estudiosSolicitados;

    @Column(name = "Observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;

    @OneToMany(mappedBy = "evolucion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AdjuntoHistoriaClinica> adjuntos = new ArrayList<>();
}
