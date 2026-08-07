package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Liquidacion_Medica")
@SQLDelete(sql = "UPDATE Liquidacion_Medica SET Visible = 0 WHERE ID_Liquidacion = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiquidacionMedica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Liquidacion")
    private Integer idLiquidacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Medico", nullable = false)
    private Medico medico;

    @Column(name = "Fecha_Desde", nullable = false)
    private LocalDate fechaDesde;

    @Column(name = "Fecha_Hasta", nullable = false)
    private LocalDate fechaHasta;

    @Column(name = "Total_Facturado", nullable = false)
    private BigDecimal totalFacturado;

    @Column(name = "Total_Consultorio", nullable = false)
    private BigDecimal totalConsultorio;

    @Column(name = "Total_Medico", nullable = false)
    private BigDecimal totalMedico;

    @Column(name = "Fecha_Generacion", nullable = false)
    private LocalDateTime fechaGeneracion;

    @Column(name = "Estado", nullable = false, length = 20)
    private String estado = "EMITIDA"; // EMITIDA, ANULADA

    @Column(name = "Observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;
}
