package com.medispace.app.repository;

import com.medispace.app.model.LiquidacionMedica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LiquidacionMedicaRepository extends JpaRepository<LiquidacionMedica, Integer> {
    List<LiquidacionMedica> findByMedicoIdMedico(Integer idMedico);
}
