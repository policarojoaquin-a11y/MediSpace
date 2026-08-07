package com.medispace.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "Adjuntos_HistoriaClinica")
@SQLDelete(sql = "UPDATE Adjuntos_HistoriaClinica SET Visible = 0 WHERE ID_Adjunto = ?")
@SQLRestriction("Visible = 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjuntoHistoriaClinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Adjunto")
    private Integer idAdjunto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Evolucion", nullable = false)
    private EvolucionClinica evolucion;

    @Column(name = "Nombre_Archivo", nullable = false, length = 100)
    private String nombreArchivo;

    @Column(name = "Ruta_Archivo", nullable = false, length = 255)
    private String rutaArchivo;

    @Column(name = "Tipo_Archivo", length = 30)
    private String tipoArchivo;

    @Column(name = "Fecha_Carga", nullable = false)
    private LocalDateTime fechaCarga;

    @Column(name = "Visible", nullable = false)
    private Boolean visible = true;
}
