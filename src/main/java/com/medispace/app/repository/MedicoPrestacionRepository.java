package com.medispace.app.repository;

import com.medispace.app.model.MedicoPrestacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicoPrestacionRepository extends JpaRepository<MedicoPrestacion, Integer> {

    List<MedicoPrestacion> findByMedicoIdMedico(Integer idMedico);

    Optional<MedicoPrestacion> findByMedicoIdMedicoAndPrestacionIdPrestacion(Integer idMedico, Integer idPrestacion);
}
