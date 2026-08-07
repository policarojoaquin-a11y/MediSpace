package com.medispace.app.repository;

import com.medispace.app.model.AdjuntoHistoriaClinica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdjuntoHistoriaClinicaRepository extends JpaRepository<AdjuntoHistoriaClinica, Integer> {
    List<AdjuntoHistoriaClinica> findByEvolucionIdEvolucion(Integer idEvolucion);
}
