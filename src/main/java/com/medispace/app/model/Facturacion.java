package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Facturacion")
@SQLDelete(sql = "UPDATE Facturacion SET Visible = 0 WHERE ID_Facturacion = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Facturacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Facturacion")
    private Integer idFacturacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Turno", nullable = false)
    private Turno turno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Paciente", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Medico", nullable = false)
    private Medico medico;

    @Column(name = "ID_Arrendamiento")
    private Integer idArrendamiento;

    @Column(name = "Fecha_Facturacion", nullable = false)
    private LocalDateTime fechaFacturacion;

    @Column(name = "Tipo_Consulta", nullable = false, length = 50)
    private String tipoConsulta;

    @Column(name = "Metodo_Pago", nullable = false, length = 30)
    private String metodoPago;

    @Column(name = "Obra_Social", length = 100)
    private String obraSocial;

    @Column(name = "Importe_Total", nullable = false)
    private BigDecimal importeTotal;

    @Column(name = "Importe_Copago")
    private BigDecimal importeCopago;

    @Column(name = "Porcentaje_Consultorio", nullable = false)
    private BigDecimal porcentajeConsultorio;

    @Column(name = "Porcentaje_Medico", nullable = false)
    private BigDecimal porcentajeMedico;

    @Column(name = "Estado_Pago", nullable = false, length = 20)
    private String estadoPago = "PENDIENTE"; // PENDIENTE, PAGADO, PARCIAL, ANULADO, REINTEGRADO

    @Column(name = "Observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;
}
