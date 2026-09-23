package com.medispace.app.repository;

import com.medispace.app.model.DashboardGerencial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DashboardGerencialRepository extends JpaRepository<DashboardGerencial, Integer> {
    // Tolerante a filas duplicadas para la misma fecha: si por alguna razón hay más de una
    // (recálculos concurrentes, datos históricos sin índice único en Fecha), un findByFecha
    // simple reventaría con NonUniqueResultException y tiraría abajo el dashboard entero.
    // Se toma siempre la más reciente por id.
    Optional<DashboardGerencial> findFirstByFechaOrderByIdDashboardDesc(LocalDate fecha);
}
