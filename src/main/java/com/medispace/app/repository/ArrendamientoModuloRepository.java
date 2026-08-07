package com.medispace.app.repository;

import com.medispace.app.model.ArrendamientoModulo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ArrendamientoModuloRepository extends JpaRepository<ArrendamientoModulo, Integer> {

    List<ArrendamientoModulo> findByMedicoIdMedico(Integer idMedico);

    // RN-013: Un consultorio no puede asignarse a 2 médicos el mismo día y horario
    @Query(value = "SELECT COUNT(*) FROM Arrendamiento_Modulo " +
            "WHERE ID_Consultorio = :idConsultorio " +
            "AND Dia_Semana = :diaSemana " +
            "AND Estado = 'ACTIVO' " +
            "AND Visible = 1 " +
            "AND (Fecha_Fin IS NULL OR Fecha_Fin >= :fechaInicio) " +
            "AND (:fechaFin IS NULL OR Fecha_Inicio <= :fechaFin) " +
            "AND (Hora_Inicio < CAST(:horaFin AS TIME) AND Hora_Fin > CAST(:horaInicio AS TIME))", nativeQuery = true)
    long countSuperposicionesConsultorio(
            @Param("idConsultorio") Integer idConsultorio,
            @Param("diaSemana") String diaSemana,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin);

    // Contrato vigente de un médico en un consultorio para un día/hora/fecha dados — mismo
    // criterio de matching (médico + consultorio + día/horario) que usa la generación
    // automática de turnos (TurnoServiceImpl.generarTurnosParaContrato), en sentido inverso.
    // Orden por Fecha_Inicio DESC para desempatar si dos contratos matchearan igual.
    @Query("SELECT a FROM ArrendamientoModulo a " +
            "WHERE a.medico.idMedico = :idMedico " +
            "AND a.consultorio.idConsultorio = :idConsultorio " +
            "AND a.diaSemana = :diaSemana " +
            "AND a.estado = 'ACTIVO' " +
            "AND a.horaInicio <= :hora AND a.horaFin > :hora " +
            "AND a.fechaInicio <= :fecha AND (a.fechaFin IS NULL OR a.fechaFin >= :fecha) " +
            "ORDER BY a.fechaInicio DESC")
    List<ArrendamientoModulo> findContratoVigente(
            @Param("idMedico") Integer idMedico,
            @Param("idConsultorio") Integer idConsultorio,
            @Param("diaSemana") String diaSemana,
            @Param("hora") LocalTime hora,
            @Param("fecha") LocalDate fecha);

    // Variante a nivel de día (sin horario específico) para Cierre Diario, que opera sobre
    // "todo el día" en vez de un turno puntual. Mismo criterio médico+consultorio+día,
    // desempatado igual por Fecha_Inicio DESC.
    @Query("SELECT a FROM ArrendamientoModulo a " +
            "WHERE a.medico.idMedico = :idMedico " +
            "AND a.consultorio.idConsultorio = :idConsultorio " +
            "AND a.diaSemana = :diaSemana " +
            "AND a.estado = 'ACTIVO' " +
            "AND a.fechaInicio <= :fecha AND (a.fechaFin IS NULL OR a.fechaFin >= :fecha) " +
            "ORDER BY a.fechaInicio DESC")
    List<ArrendamientoModulo> findContratoVigentePorFecha(
            @Param("idMedico") Integer idMedico,
            @Param("idConsultorio") Integer idConsultorio,
            @Param("diaSemana") String diaSemana,
            @Param("fecha") LocalDate fecha);
}
