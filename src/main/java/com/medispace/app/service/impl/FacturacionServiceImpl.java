package com.medispace.app.service.impl;

import com.medispace.app.dto.facturacion.FacturacionResponseDTO;
import com.medispace.app.dto.facturacion.RegistrarCobroDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.ArrendamientoModulo;
import com.medispace.app.model.Cobro;
import com.medispace.app.model.Facturacion;
import com.medispace.app.model.MedicoPrestacion;
import com.medispace.app.model.Turno;
import com.medispace.app.repository.ArrendamientoModuloRepository;
import com.medispace.app.repository.CobroRepository;
import com.medispace.app.repository.FacturacionRepository;
import com.medispace.app.repository.MedicoPrestacionRepository;
import com.medispace.app.service.FacturacionService;
import com.medispace.app.util.DiaSemanaUtil;
import com.medispace.app.util.SplitFinancieroCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FacturacionServiceImpl implements FacturacionService {

    private final FacturacionRepository facturacionRepository;
    private final CobroRepository cobroRepository;
    private final ArrendamientoModuloRepository arrendamientoModuloRepository;
    private final MedicoPrestacionRepository medicoPrestacionRepository;

    // RN-006: split de fallback cuando el turno no tiene un contrato de arrendamiento vigente
    // que lo respalde (p. ej. datos de test/seed sin consultorio asociado).
    private static final BigDecimal PORCENTAJE_MEDICO_DEFAULT = new BigDecimal("70.00");
    private static final BigDecimal PORCENTAJE_CONSULTORIO_DEFAULT = new BigDecimal("30.00");

    // Pedido de negocio (checklist 27/08): los únicos métodos de pago válidos del consultorio
    // son efectivo y transferencia. Tarjeta / MercadoPago / "obra social" no se aceptan como
    // método de cobro. Ver docs/ANEXO_CAMBIOS_POST_ENTREGA.md.
    private static final java.util.Set<String> METODOS_PAGO_VALIDOS = java.util.Set.of("EFECTIVO", "TRANSFERENCIA");

    @Override
    @Transactional
    public FacturacionResponseDTO crearFacturacionAutomatica(Turno turno) {
        if (turno.getPaciente() == null) {
            throw new BusinessRuleException("No se puede facturar un turno sin paciente asignado.");
        }

        // Idempotente: la factura pendiente se genera al pasar el turno a "En Espera" y NO se
        // vuelve a crear cuando el médico lo marca "Atendido" (ni ante reintentos).
        Optional<Facturacion> yaExiste = facturacionRepository.findByTurnoIdTurno(turno.getIdTurno());
        if (yaExiste.isPresent() && !"ANULADO".equalsIgnoreCase(yaExiste.get().getEstadoPago())) {
            return mapToDTO(yaExiste.get());
        }
        // Si la factura previa quedó ANULADO (el turno se canceló) y el cupo se re-reservó para
        // otra atención, se REUTILIZA esa misma fila con los datos nuevos. findByTurnoIdTurno
        // devuelve una sola factura por turno: crear otra rompería esa unicidad y la atención
        // nueva no podría cobrarse nunca ("Esta facturación está anulada"). Ver docs/ANEXO §J.
        boolean refacturando = yaExiste.isPresent();

        // El importe base de la factura es el precio de la prestación elegida al reservar
        // (Medico_Prestacion.importeParticular): "el valor de cada prestación varía según el
        // tipo de atención, ya que no es equivalente el costo de una consulta al de un estudio"
        // (relevamiento §1.10). Si el turno no tiene una prestación específica ("Consulta
        // general") o el médico no le cargó importe, se usa el importe de consulta del médico.
        // El circuito de cobro puede ajustar este valor a mano (p. ej. "consulta + electro").
        BigDecimal importeConsulta = null;
        if (turno.getPrestacion() != null) {
            importeConsulta = medicoPrestacionRepository
                    .findByMedicoIdMedicoAndPrestacionIdPrestacion(
                            turno.getMedico().getIdMedico(), turno.getPrestacion().getIdPrestacion())
                    .map(MedicoPrestacion::getImporteParticular)
                    .orElse(null);
        }
        if (importeConsulta == null) {
            importeConsulta = turno.getMedico().getImporteConsulta() != null
                    ? turno.getMedico().getImporteConsulta() : BigDecimal.ZERO;
        }

        // RN-006: Split configurado a nivel del contrato de arrendamiento vigente (médico +
        // consultorio + día/horario del turno), no un valor fijo por turno.
        ArrendamientoModulo arrendamiento = resolverContratoVigente(turno);
        BigDecimal porcentajeMedico = arrendamiento != null ? arrendamiento.getPorcentajeMedico() : PORCENTAJE_MEDICO_DEFAULT;
        BigDecimal porcentajeConsultorio = arrendamiento != null ? arrendamiento.getPorcentajeConsultorio() : PORCENTAJE_CONSULTORIO_DEFAULT;

        // Sección 4.4/4.6: preferir lo cargado al reservar el turno (tipo de consulta, obra
        // social, copago) sobre los valores por defecto — antes se perdían silenciosamente
        // porque ReservarTurnoDTO no los declaraba.
        String tipoConsulta = (turno.getTipoConsulta() != null && !turno.getTipoConsulta().isBlank())
                ? turno.getTipoConsulta()
                : (turno.getPrestacion() != null ? turno.getPrestacion().getNombre() : "Consulta Médica");

        String obraSocialNombre = (turno.getObraSocialPlanificada() != null && !turno.getObraSocialPlanificada().isBlank())
                ? turno.getObraSocialPlanificada()
                : (turno.getPaciente().getObraSocial() != null ? turno.getPaciente().getObraSocial().getNombre() : "Particular");

        // RN-025: importeCopago es lo que el paciente paga EN MANO (precio de la consulta menos
        // lo que cubre la obra social, resuelto al reservar el turno) — base del split 70/30 en
        // Liquidación/Cierre/Reportes. Sin dato planificado (turno legacy o reservado sin pasar
        // por el modal), se asume que se cobra el importe completo (mismo criterio que "Particular"),
        // no ZERO — de lo contrario esos turnos liquidarían $0 para el médico.
        BigDecimal importeCopago = turno.getImporteCopagoPlanificado() != null
                ? turno.getImporteCopagoPlanificado()
                : importeConsulta;

        Facturacion facturacion = yaExiste.orElseGet(Facturacion::new);
        facturacion.setTurno(turno);
        facturacion.setPaciente(turno.getPaciente());
        facturacion.setMedico(turno.getMedico());
        facturacion.setIdArrendamiento(arrendamiento != null ? arrendamiento.getIdArrendamiento() : null);
        facturacion.setFechaFacturacion(LocalDateTime.now());
        facturacion.setTipoConsulta(tipoConsulta);
        facturacion.setMetodoPago("PENDIENTE");
        facturacion.setObraSocial(obraSocialNombre);
        facturacion.setImporteTotal(importeConsulta);
        facturacion.setImporteCopago(importeCopago);
        facturacion.setPorcentajeMedico(porcentajeMedico);
        facturacion.setPorcentajeConsultorio(porcentajeConsultorio);
        facturacion.setEstadoPago("PENDIENTE");
        facturacion.setVisible(true);
        if (refacturando) {
            // Se conserva la observación anterior (puede decir "hay un cobro registrado que
            // requiere reintegro manual") y se le suma el rastro de la refacturación.
            String obsPrevia = facturacion.getObservaciones() != null ? facturacion.getObservaciones() + " " : "";
            facturacion.setObservaciones(obsPrevia
                    + "[Refacturada " + LocalDate.now() + ": el cupo se reasignó a una nueva atención tras una cancelación previa.]");
        } else {
            facturacion.setObservaciones(null);
        }

        facturacion = facturacionRepository.save(facturacion);
        return mapToDTO(facturacion);
    }

    @Override
    @Transactional
    public FacturacionResponseDTO registrarCobro(Integer idFacturacion, RegistrarCobroDTO dto) {
        Facturacion facturacion = facturacionRepository.findById(idFacturacion)
                .orElseThrow(() -> new BusinessRuleException("Facturación no encontrada."));

        if ("PAGADO".equalsIgnoreCase(facturacion.getEstadoPago())) {
            throw new BusinessRuleException("Esta facturación ya fue cobrada.");
        }
        if ("ANULADO".equalsIgnoreCase(facturacion.getEstadoPago())) {
            throw new BusinessRuleException("Esta facturación está anulada (el turno se canceló).");
        }

        String metodo = dto.getMetodoPago() != null ? dto.getMetodoPago().trim().toUpperCase() : "";
        if (!METODOS_PAGO_VALIDOS.contains(metodo)) {
            throw new BusinessRuleException("Método de pago no válido. Solo se acepta EFECTIVO o TRANSFERENCIA.");
        }

        // Cobros.Importe_Total y Facturacion.Importe_Total son NOT NULL: sin este chequeo, un
        // request sin importe reventaba con un 500 (PropertyValueException) en vez de un 400 claro.
        if (dto.getImporteTotal() == null) {
            throw new BusinessRuleException("El importe total del cobro es obligatorio.");
        }

        // RN-025: "Cubierto OS" ya no se toma del formulario — se calcula server-side (Total
        // menos Copago) para que no pueda quedar inconsistente con lo que realmente se cobró.
        Cobro cobro = Cobro.builder()
                .turno(facturacion.getTurno())
                .metodoPago(metodo)
                .importeTotal(dto.getImporteTotal())
                .importeCubiertoOs(SplitFinancieroCalculator.montoCubiertoPorObraSocial(dto.getImporteTotal(), dto.getImporteCopago()))
                .importeCopago(dto.getImporteCopago())
                .fechaCobro(LocalDateTime.now())
                .visible(true)
                .build();

        cobroRepository.save(cobro);

        facturacion.setMetodoPago(metodo);
        facturacion.setImporteTotal(dto.getImporteTotal());
        if (dto.getImporteCopago() != null) {
            facturacion.setImporteCopago(dto.getImporteCopago());
        }
        // Sección 4.6: "Transferencias pendientes quedan en estado 'Pendiente' hasta validación."
        // Efectivo se da por confirmado al momento del registro del cobro. El cobro por
        // transferencia SÍ queda registrado (el registro de Cobro se guardó arriba), solo que
        // la factura queda PENDIENTE hasta confirmarTransferencia() — el frontend distingue
        // "PENDIENTE sin cobro" de "PENDIENTE por transferencia a validar" por el metodoPago.
        facturacion.setEstadoPago("TRANSFERENCIA".equals(metodo) ? "PENDIENTE" : "PAGADO");

        facturacion = facturacionRepository.save(facturacion);
        return mapToDTO(facturacion);
    }

    @Override
    @Transactional
    public FacturacionResponseDTO confirmarTransferencia(Integer idFacturacion) {
        Facturacion facturacion = facturacionRepository.findById(idFacturacion)
                .orElseThrow(() -> new BusinessRuleException("Facturación no encontrada."));

        if (!"TRANSFERENCIA".equalsIgnoreCase(facturacion.getMetodoPago())) {
            throw new BusinessRuleException("Esta factura no tiene un cobro por transferencia pendiente de validación.");
        }
        if (!"PENDIENTE".equalsIgnoreCase(facturacion.getEstadoPago())) {
            throw new BusinessRuleException("La factura no está en estado PENDIENTE.");
        }

        facturacion.setEstadoPago("PAGADO");
        facturacion = facturacionRepository.save(facturacion);
        return mapToDTO(facturacion);
    }

    @Override
    public FacturacionResponseDTO obtenerFacturacion(Integer id) {
        Facturacion facturacion = facturacionRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Facturación no encontrada."));
        return mapToDTO(facturacion);
    }

    @Override
    public List<FacturacionResponseDTO> listarFacturaciones(LocalDate desde, LocalDate hasta) {
        List<Facturacion> facturaciones;
        if (desde != null) {
            LocalDate finRango = (hasta != null) ? hasta : desde;
            facturaciones = facturacionRepository.findByFechaFacturacionBetween(
                    desde.atStartOfDay(), finRango.atTime(LocalTime.MAX));
        } else {
            facturaciones = facturacionRepository.findAll();
        }
        return facturaciones.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void anularFacturacionDeTurno(Integer idTurno) {
        facturacionRepository.findByTurnoIdTurno(idTurno).ifPresent(f -> {
            if ("ANULADO".equalsIgnoreCase(f.getEstadoPago())) {
                return;
            }
            boolean teniaCobro = "PAGADO".equalsIgnoreCase(f.getEstadoPago())
                    || cobroRepository.findByTurnoIdTurno(idTurno).isPresent();
            f.setEstadoPago("ANULADO");
            String obsPrevias = f.getObservaciones() != null ? f.getObservaciones() + " " : "";
            f.setObservaciones(obsPrevias + "[Anulada automáticamente: el turno se canceló"
                    + (teniaCobro ? " — hay un cobro registrado que requiere reintegro manual" : "") + ".]");
            facturacionRepository.save(f);
        });
    }

    // Mismo criterio de matching (médico + consultorio + día/horario) que usa
    // TurnoServiceImpl.generarTurnosParaContrato() para generar los turnos de un contrato,
    // en sentido inverso: dado un turno ya atendido, encontrar el contrato que lo respalda.
    private ArrendamientoModulo resolverContratoVigente(Turno turno) {
        if (turno.getConsultorio() == null) {
            return null;
        }
        String diaSemana = DiaSemanaUtil.traducir(turno.getFechaHora().getDayOfWeek());
        List<ArrendamientoModulo> contratos = arrendamientoModuloRepository.findContratoVigente(
                turno.getMedico().getIdMedico(),
                turno.getConsultorio().getIdConsultorio(),
                diaSemana,
                turno.getFechaHora().toLocalTime(),
                turno.getFechaHora().toLocalDate());
        return contratos.isEmpty() ? null : contratos.get(0);
    }

    private FacturacionResponseDTO mapToDTO(Facturacion f) {
        return FacturacionResponseDTO.builder()
                .idFacturacion(f.getIdFacturacion())
                .idTurno(f.getTurno().getIdTurno())
                .idPaciente(f.getPaciente().getIdPaciente())
                .nombrePaciente(f.getPaciente().getNombre() + " " + f.getPaciente().getApellido())
                .idMedico(f.getMedico().getIdMedico())
                .nombreMedico(f.getMedico().getNombre() + " " + f.getMedico().getApellido())
                .fechaFacturacion(f.getFechaFacturacion())
                .tipoConsulta(f.getTipoConsulta())
                .metodoPago(f.getMetodoPago())
                .obraSocial(f.getObraSocial())
                .importeTotal(f.getImporteTotal())
                .importeCopago(f.getImporteCopago())
                .importeCubiertoOs(SplitFinancieroCalculator.montoCubiertoPorObraSocial(f.getImporteTotal(), f.getImporteCopago()))
                .porcentajeConsultorio(f.getPorcentajeConsultorio())
                .porcentajeMedico(f.getPorcentajeMedico())
                .estadoPago(f.getEstadoPago())
                .observaciones(f.getObservaciones())
                .metodoPagoPlanificado(f.getTurno() != null ? f.getTurno().getMetodoPagoPlanificado() : null)
                .build();
    }
}
