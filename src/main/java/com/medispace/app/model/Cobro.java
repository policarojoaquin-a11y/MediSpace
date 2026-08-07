package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Cobros")
@SQLDelete(sql = "UPDATE Cobros SET Visible = 0 WHERE ID_Cobro = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cobro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Cobro")
    private Integer idCobro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Turno", nullable = false)
    private Turno turno;

    @Column(name = "Metodo_Pago", nullable = false, length = 30)
    private String metodoPago;

    @Column(name = "Importe_Total", nullable = false)
    private BigDecimal importeTotal;

    @Column(name = "Importe_Cubierto_OS")
    private BigDecimal importeCubiertoOs;

    @Column(name = "Importe_Copago")
    private BigDecimal importeCopago;

    @Column(name = "Fecha_Cobro", nullable = false)
    private LocalDateTime fechaCobro;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;
}
