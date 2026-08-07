package com.medispace.app.repository;

import com.medispace.app.model.UsoConsultorio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UsoConsultorioRepository extends JpaRepository<UsoConsultorio, Integer> {
    List<UsoConsultorio> findByConsultorioIdConsultorioAndFecha(Integer idConsultorio, LocalDate fecha);

    // Usado por ReporteServiceImpl.reporteUsoConsultorios() para acotar por fecha a nivel de
    // query en vez de traer toda la tabla con findAll().
    List<UsoConsultorio> findByFechaBetween(LocalDate desde, LocalDate hasta);
}
