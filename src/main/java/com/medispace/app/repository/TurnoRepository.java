package com.medispace.app.repository;

import com.medispace.app.model.Turno;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TurnoRepository extends JpaRepository<Turno, Integer>, JpaSpecificationExecutor<Turno> {

    boolean existsByMedicoIdMedicoAndFechaHora(Integer idMedico, LocalDateTime fechaHora);

    /**
     * Lee el turno con lock pesimista (SELECT ... FOR UPDATE) para la operación de reserva:
     * dos reservas concurrentes sobre el mismo turno se serializan a nivel de fila en la base
     * de datos — la segunda transacción espera a que la primera confirme (o revierta) antes de
     * releer el estado, evitando que ambas lean "DISPONIBLE" y violen RN-001/RN-002/RN-003 bajo
     * carga real. Requiere ejecutarse dentro de una transacción (@Transactional en el service).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Turno t WHERE t.idTurno = :idTurno")
    Optional<Turno> findByIdForUpdate(@Param("idTurno") Integer idTurno);

    // RN-002: Un paciente no puede tener 2 turnos reservados en el mismo horario
    boolean existsByPacienteIdPacienteAndFechaHoraAndEstadoIn(Integer idPaciente, LocalDateTime fechaHora, List<String> estados);

    // RN-003: Un médico no puede tener 2 turnos en el mismo horario
    boolean existsByMedicoIdMedicoAndFechaHoraAndEstadoIn(Integer idMedico, LocalDateTime fechaHora, List<String> estados);

    List<Turno> findByMedicoIdMedicoAndFechaHoraBetween(Integer idMedico, LocalDateTime desde, LocalDateTime hasta);

    List<Turno> findByFechaHoraBetween(LocalDateTime desde, LocalDateTime hasta);

    // Turnos futuros con paciente real (RESERVADO / EN_ESPERA) en un consultorio — para avisar
    // (no bloquear) al cambiar el estado de ese consultorio (checklist 27/08).
    @Query(value = "SELECT COUNT(*) FROM Turnos WHERE ID_Consultorio = :idConsultorio " +
            "AND Fecha_Hora > GETDATE() AND Estado IN ('RESERVADO', 'EN_ESPERA') AND Visible = 1", nativeQuery = true)
    long countPendientesFuturosPorConsultorio(@Param("idConsultorio") Integer idConsultorio);

    // RN-018: Turnos Atendido de un médico en un período que no tienen ningún Cobro asociado
    @Query("SELECT COUNT(t) FROM Turno t WHERE t.medico.idMedico = :idMedico " +
            "AND t.estado = 'ATENDIDO' AND t.fechaHora BETWEEN :desde AND :hasta " +
            "AND NOT EXISTS (SELECT c FROM Cobro c WHERE c.turno = t)")
    long countAtendidosSinCobro(@Param("idMedico") Integer idMedico, @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
