package com.medispace.app.repository;

import com.medispace.app.model.Cobro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CobroRepository extends JpaRepository<Cobro, Integer> {
    Optional<Cobro> findByTurnoIdTurno(Integer idTurno);
}
