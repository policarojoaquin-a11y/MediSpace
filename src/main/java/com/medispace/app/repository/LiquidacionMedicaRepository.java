package com.medispace.app.repository;

import com.medispace.app.model.LiquidacionMedica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LiquidacionMedicaRepository extends JpaRepository<LiquidacionMedica, Integer> {
    List<LiquidacionMedica> findByMedicoIdMedico(Integer idMedico);

    // Listado general (Gerente/Administrativo) con filtros opcionales, para la pantalla de
    // Liquidaciones Médicas — no existía ningún "listar todas" antes de esto, solo por médico.
    @Query("SELECT l FROM LiquidacionMedica l " +
            "WHERE (:idMedico IS NULL OR l.medico.idMedico = :idMedico) " +
            "AND (:desde IS NULL OR l.fechaDesde >= :desde) " +
            "AND (:hasta IS NULL OR l.fechaHasta <= :hasta) " +
            "ORDER BY l.fechaGeneracion DESC")
    List<LiquidacionMedica> buscar(@Param("idMedico") Integer idMedico,
                                    @Param("desde") LocalDate desde,
                                    @Param("hasta") LocalDate hasta);

    // RN-024: liquidaciones EMITIDA del médico cuyo período [Fecha_Desde, Fecha_Hasta] se
    // superpone con el período solicitado — evita liquidar dos veces la misma facturación.
    @Query("SELECT l FROM LiquidacionMedica l " +
            "WHERE l.medico.idMedico = :idMedico " +
            "AND l.estado = 'EMITIDA' " +
            "AND l.fechaDesde <= :fechaHasta AND l.fechaHasta >= :fechaDesde")
    List<LiquidacionMedica> findSuperpuestas(@Param("idMedico") Integer idMedico,
                                              @Param("fechaDesde") LocalDate fechaDesde,
                                              @Param("fechaHasta") LocalDate fechaHasta);
}
