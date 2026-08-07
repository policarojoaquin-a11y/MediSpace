package com.medispace.app.repository;

import com.medispace.app.model.DashboardGerencial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DashboardGerencialRepository extends JpaRepository<DashboardGerencial, Integer> {
    Optional<DashboardGerencial> findByFecha(LocalDate fecha);
}
