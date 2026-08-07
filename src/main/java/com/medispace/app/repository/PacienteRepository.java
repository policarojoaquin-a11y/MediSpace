package com.medispace.app.repository;

import com.medispace.app.model.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PacienteRepository extends JpaRepository<Paciente, Integer> {
    Optional<Paciente> findByDni(String dni);

    @Query(value = "SELECT COUNT(*) FROM Pacientes WHERE DNI = :dni", nativeQuery = true)
    long countByDniIncludingInactive(@Param("dni") String dni);

    // Usado por ReporteServiceImpl.recalcularDashboard() para contar nuevos pacientes del día
    // sin traer la tabla completa con findAll().
    long countByFechaCreacionBetween(LocalDateTime desde, LocalDateTime hasta);
}
