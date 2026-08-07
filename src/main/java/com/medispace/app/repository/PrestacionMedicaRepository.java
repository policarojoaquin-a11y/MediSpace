package com.medispace.app.repository;

import com.medispace.app.model.PrestacionMedica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PrestacionMedicaRepository extends JpaRepository<PrestacionMedica, Integer> {
    Optional<PrestacionMedica> findByNombreIgnoreCase(String nombre);
}
