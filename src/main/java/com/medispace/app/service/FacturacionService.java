package com.medispace.app.service;

import com.medispace.app.dto.facturacion.FacturacionResponseDTO;
import com.medispace.app.dto.facturacion.RegistrarCobroDTO;
import com.medispace.app.model.Turno;

import java.time.LocalDate;
import java.util.List;

public interface FacturacionService {
    FacturacionResponseDTO crearFacturacionAutomatica(Turno turno);
    FacturacionResponseDTO registrarCobro(Integer idFacturacion, RegistrarCobroDTO dto);
    /** Sección 4.6: un cobro por transferencia queda PENDIENTE hasta que se valida el ingreso.
     *  Esta operación es esa validación: pasa la factura de PENDIENTE a PAGADO. */
    FacturacionResponseDTO confirmarTransferencia(Integer idFacturacion);
    FacturacionResponseDTO obtenerFacturacion(Integer id);

    /**
     * Lista facturaciones acotadas por fecha de facturación. {@code desde}/{@code hasta} son
     * opcionales: si viene solo {@code desde} se filtra ese único día; si vienen ambos, el rango
     * inclusivo; si no viene ninguno, todas. El frontend arranca filtrando por el día de hoy.
     */
    List<FacturacionResponseDTO> listarFacturaciones(LocalDate desde, LocalDate hasta);

    /**
     * Si el turno tiene una facturación asociada y no está ya anulada, la pasa a "ANULADO"
     * (p. ej. el turno se cancela o se marca "No Asistió" después de haberse puesto En Espera y
     * generado la factura). Evita que una factura huérfana en PENDIENTE bloquee la liquidación
     * (RN-005). Si ya había un cobro registrado, queda una observación de que hay un reintegro
     * pendiente. No hace nada si el turno no tiene facturación.
     */
    void anularFacturacionDeTurno(Integer idTurno);
}
