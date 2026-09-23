package com.medispace.app.repository;

import com.medispace.app.model.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PacienteRepository extends JpaRepository<Paciente, Integer> {
    Optional<Paciente> findByDni(String dni);

    @Query(value = "SELECT COUNT(*) FROM Pacientes WHERE DNI = :dni", nativeQuery = true)
    long countByDniIncludingInactive(@Param("dni") String dni);

    // Usado por ReporteServiceImpl.recalcularDashboard() para contar nuevos pacientes del día
    // sin traer la tabla completa con findAll().
    long countByFechaCreacionBetween(LocalDateTime desde, LocalDateTime hasta);

    // Un médico solo debe ver pacientes vinculados a algún turno propio (spec.md §4.2: "Médico:
    // únicamente puede visualizar información de pacientes asociados a turnos o historias
    // clínicas vinculadas a él"). Toda evolución clínica de un médico ya requiere un turno propio
    // (RN-010), así que filtrar por Turno cubre también el caso de historias clínicas.
    @Query("SELECT DISTINCT t.paciente FROM Turno t WHERE t.medico.idMedico = :idMedico AND t.paciente IS NOT NULL")
    List<Paciente> findVinculadosAMedico(@Param("idMedico") Integer idMedico);

    @Query("SELECT COUNT(t) > 0 FROM Turno t WHERE t.medico.idMedico = :idMedico AND t.paciente.idPaciente = :idPaciente")
    boolean existsVinculadoAMedico(@Param("idMedico") Integer idMedico, @Param("idPaciente") Integer idPaciente);

    // RN-019: no se da de baja una obra social con pacientes activos asociados. El
    // @SQLRestriction("Visible = 1") de Paciente hace que esto cuente solo pacientes activos.
    long countByObraSocial_IdObraSocial(Integer idObraSocial);
}
