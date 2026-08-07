package com.medispace.app.repository;

import com.medispace.app.model.CierreDiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CierreDiarioRepository extends JpaRepository<CierreDiario, Integer> {
    List<CierreDiario> findByMedicoIdMedicoAndFechaBetween(Integer idMedico, LocalDate desde, LocalDate hasta);
}
