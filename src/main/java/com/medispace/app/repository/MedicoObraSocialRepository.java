package com.medispace.app.repository;

import com.medispace.app.model.MedicoObraSocial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicoObraSocialRepository extends JpaRepository<MedicoObraSocial, Integer> {

    List<MedicoObraSocial> findByMedicoIdMedico(Integer idMedico);

    Optional<MedicoObraSocial> findByMedicoIdMedicoAndObraSocialIdObraSocial(Integer idMedico, Integer idObraSocial);
}
