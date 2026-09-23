package com.medispace.app.repository;

import com.medispace.app.model.Medico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicoRepository extends JpaRepository<Medico, Integer> {
    Optional<Medico> findByMatricula(String matricula);

    Optional<Medico> findByUsuario_Email(String email);

    Optional<Medico> findByUsuario_IdUsuario(Integer idUsuario);

    // RN-012: solo bloquean la baja del médico los turnos futuros con un paciente REAL asignado
    // ("sin reasignar" en el texto de la regla) — es decir RESERVADO o EN_ESPERA. Los turnos
    // DISPONIBLE (cupo generado automáticamente por el contrato de arrendamiento, sin paciente)
    // NO bloquean: si bloquearan, ningún médico con un contrato activo podría darse de baja
    // nunca, porque siempre tiene cientos de turnos disponibles a futuro (bug reportado 27/08).
    // El literal debe coincidir EXACTO con Turno.estado (con guion bajo).
    @Query(value = "SELECT COUNT(*) FROM Turnos WHERE ID_Medico = :idMedico AND Fecha_Hora > GETDATE() AND Estado IN ('RESERVADO', 'EN_ESPERA') AND Visible = 1", nativeQuery = true)
    long countTurnosFuturosActivos(@Param("idMedico") Integer idMedico);

    // Bypassa @SQLRestriction para poder encontrar (y luego reactivar) un médico inactivo.
    @Query(value = "SELECT * FROM Medicos WHERE ID_Medico = :id", nativeQuery = true)
    Optional<Medico> findByIdIncludingInactive(@Param("id") Integer id);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE Medicos SET Visible = 1 WHERE ID_Medico = :id", nativeQuery = true)
    void reactivar(@Param("id") Integer id);

    // Listado incluyendo inactivos (query nativa: bypassa @SQLRestriction), para poder
    // encontrar y reactivar médicos dados de baja desde la UI.
    @Query(value = "SELECT * FROM Medicos ORDER BY Apellido, Nombre", nativeQuery = true)
    List<Medico> findAllIncludingInactive();
}
