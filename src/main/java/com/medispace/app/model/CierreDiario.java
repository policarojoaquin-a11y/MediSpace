package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Cierre_Diario")
@SQLDelete(sql = "UPDATE Cierre_Diario SET Visible = 0 WHERE ID_Cierre = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CierreDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Cierre")
    private Integer idCierre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Medico", nullable = false)
    private Medico medico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Consultorio", nullable = false)
    private Consultorio consultorio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Arrendamiento")
    private ArrendamientoModulo arrendamiento;

    @Column(name = "Fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "Total_Facturado_Dia", nullable = false)
    private BigDecimal totalFacturadoDia;

    @Column(name = "Importe_Consultorio", nullable = false)
    private BigDecimal importeConsultorio;

    @Column(name = "Importe_Medico", nullable = false)
    private BigDecimal importeMedico;

    @Column(name = "Cantidad_Turnos", nullable = false)
    private Integer cantidadTurnos;

    @Column(name = "Estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "Observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "Fecha_Registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;
}
