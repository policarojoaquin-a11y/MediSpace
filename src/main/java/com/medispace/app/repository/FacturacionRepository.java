package com.medispace.app.repository;

import com.medispace.app.model.Facturacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FacturacionRepository extends JpaRepository<Facturacion, Integer> {
    Optional<Facturacion> findByTurnoIdTurno(Integer idTurno);

    List<Facturacion> findByMedicoIdMedicoAndFechaFacturacionBetween(Integer idMedico, LocalDateTime desde, LocalDateTime hasta);

    // Usado por ReporteServiceImpl para acotar por fecha a nivel de query en vez de traer
    // toda la tabla con findAll() y filtrar en memoria.
    List<Facturacion> findByFechaFacturacionBetween(LocalDateTime desde, LocalDateTime hasta);

    // RN-006: Contar facturas impagas/pendientes en el período para bloquear la liquidación
    long countByMedicoIdMedicoAndFechaFacturacionBetweenAndEstadoPagoIn(
            Integer idMedico, LocalDateTime desde, LocalDateTime hasta, List<String> estados);
}
