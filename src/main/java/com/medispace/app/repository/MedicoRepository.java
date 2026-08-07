package com.medispace.app.repository;

import com.medispace.app.model.Medico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MedicoRepository extends JpaRepository<Medico, Integer> {
    Optional<Medico> findByMatricula(String matricula);

    Optional<Medico> findByUsuario_Email(String email);

    Optional<Medico> findByUsuario_IdUsuario(Integer idUsuario);

    // RN-012: turnos futuros "reservados/atendidos" que bloquean la baja del médico.
    // El literal debe coincidir EXACTO con Turno.estado ("NO_ASISTIO", con guion bajo) — un
    // desajuste acá hace que turnos No Asistió futuros bloqueen la baja indebidamente.
    @Query(value = "SELECT COUNT(*) FROM Turnos WHERE ID_Medico = :idMedico AND Fecha_Hora > GETDATE() AND Estado NOT IN ('CANCELADO', 'NO_ASISTIO') AND Visible = 1", nativeQuery = true)
    long countTurnosFuturosActivos(@Param("idMedico") Integer idMedico);
}
