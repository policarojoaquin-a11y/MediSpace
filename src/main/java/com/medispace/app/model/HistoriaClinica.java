package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "Historia_Clinica")
@SQLDelete(sql = "UPDATE Historia_Clinica SET Visible = 0 WHERE ID_HistoriaClinica = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoriaClinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_HistoriaClinica")
    private Integer idHistoriaClinica;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Paciente", nullable = false, unique = true)
    private Paciente paciente;

    @Column(name = "Fecha_Creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "Estado", nullable = false, length = 20)
    private String estado = "ACTIVA";

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;
}
