package com.medispace.app.service;

import com.medispace.app.dto.historiaclinica.EvolucionAnularDTO;
import com.medispace.app.dto.historiaclinica.EvolucionCreateDTO;
import com.medispace.app.dto.historiaclinica.EvolucionUpdateDTO;
import com.medispace.app.dto.historiaclinica.HistoriaClinicaResponseDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.AdjuntoHistoriaClinica;
import com.medispace.app.model.EvolucionClinica;
import com.medispace.app.model.HistoriaClinica;
import com.medispace.app.model.Medico;
import com.medispace.app.model.Paciente;
import com.medispace.app.model.Usuario;
import com.medispace.app.repository.AdjuntoHistoriaClinicaRepository;
import com.medispace.app.repository.EvolucionClinicaRepository;
import com.medispace.app.repository.HistoriaClinicaRepository;
import com.medispace.app.repository.MedicoRepository;
import com.medispace.app.repository.PacienteRepository;
import com.medispace.app.repository.TurnoRepository;
import com.medispace.app.service.impl.HistoriaClinicaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HistoriaClinicaServiceTest {

    @Mock
    private HistoriaClinicaRepository historiaClinicaRepository;

    @Mock
    private EvolucionClinicaRepository evolucionClinicaRepository;

    @Mock
    private MedicoRepository medicoRepository;

    @Mock
    private AdjuntoHistoriaClinicaRepository adjuntoHistoriaClinicaRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private TurnoRepository turnoRepository;

    @InjectMocks
    private HistoriaClinicaServiceImpl historiaClinicaService;

    // idMedicoAuth pasado por el controller es en realidad el ID_Usuario del JWT (ver RN-010),
    // por eso los tests usan un rango de IDs distinto al de idMedico para no enmascarar el bug corregido.
    private static final Integer USUARIO_ID_RESPONSABLE = 101;
    private static final Integer USUARIO_ID_OTRO = 102;

    private Medico medicoResponsable;
    private Medico otroMedico;
    private EvolucionClinica evolucion;

    @BeforeEach
    void setUp() {
        medicoResponsable = Medico.builder().idMedico(1).nombre("Dr. Carlos").apellido("Gomez").build();
        otroMedico = Medico.builder().idMedico(2).nombre("Dr. Roberto").build();

        Paciente paciente = Paciente.builder().idPaciente(50).nombre("Ana").apellido("Lopez").dni("30111222").build();
        HistoriaClinica hc = HistoriaClinica.builder().idHistoriaClinica(10).paciente(paciente).estado("ACTIVA").build();

        evolucion = EvolucionClinica.builder()
                .idEvolucion(100)
                .historiaClinica(hc)
                .medico(medicoResponsable)
                .fechaHora(java.time.LocalDateTime.now())
                .motivoConsulta("Dolor de cabeza")
                .diagnostico("Migraña")
                .tratamiento("Analgésicos")
                .indicaciones("Reposo 48hs")
                .estudiosSolicitados("Resonancia magnética")
                .observaciones("Paciente refiere episodios recurrentes")
                .visible(true)
                .build();
    }

    @Test
    void testRN010_BloqueaEdicionDeOtroMedico() {
        when(evolucionClinicaRepository.findById(100)).thenReturn(Optional.of(evolucion));
        when(medicoRepository.findByUsuario_IdUsuario(USUARIO_ID_OTRO)).thenReturn(Optional.of(otroMedico));

        EvolucionUpdateDTO dto = EvolucionUpdateDTO.builder()
                .diagnostico("Diagnóstico cambiado")
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            historiaClinicaService.editarEvolucion(100, USUARIO_ID_OTRO, dto);
        });

        assertTrue(ex.getMessage().contains("RN-010"));
        assertTrue(ex.getMessage().contains("No tenés permisos"));
    }

    @Test
    void testRN010_PermiteEdicionDelMedicoResponsable() {
        when(evolucionClinicaRepository.findById(100)).thenReturn(Optional.of(evolucion));
        when(medicoRepository.findByUsuario_IdUsuario(USUARIO_ID_RESPONSABLE)).thenReturn(Optional.of(medicoResponsable));
        when(evolucionClinicaRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        EvolucionUpdateDTO dto = EvolucionUpdateDTO.builder()
                .motivoConsulta("Motivo corregido")
                .diagnostico("Migraña con aura")
                .build();

        var res = historiaClinicaService.editarEvolucion(100, USUARIO_ID_RESPONSABLE, dto);

        assertNotNull(res);
        assertEquals("Migraña con aura", evolucion.getDiagnostico());
    }

    @Test
    void testRN011_AnulacionRequiereMotivoObligatorio() {
        when(evolucionClinicaRepository.findById(100)).thenReturn(Optional.of(evolucion));
        when(medicoRepository.findByUsuario_IdUsuario(USUARIO_ID_RESPONSABLE)).thenReturn(Optional.of(medicoResponsable));

        EvolucionAnularDTO dto = EvolucionAnularDTO.builder()
                .motivoAnulacion("") // Motivo vacío
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            historiaClinicaService.anularEvolucion(100, USUARIO_ID_RESPONSABLE, dto);
        });

        assertTrue(ex.getMessage().contains("RN-011"));
        assertTrue(ex.getMessage().contains("motivo obligatorio"));
    }

    @Test
    void testRN011_PersisteMotivoAntesDeAnular() {
        when(evolucionClinicaRepository.findById(100)).thenReturn(Optional.of(evolucion));
        when(medicoRepository.findByUsuario_IdUsuario(USUARIO_ID_RESPONSABLE)).thenReturn(Optional.of(medicoResponsable));
        when(evolucionClinicaRepository.saveAndFlush(any())).thenAnswer(i -> i.getArguments()[0]);

        EvolucionAnularDTO dto = EvolucionAnularDTO.builder()
                .motivoAnulacion("Carga por error en paciente equivocado")
                .build();

        historiaClinicaService.anularEvolucion(100, USUARIO_ID_RESPONSABLE, dto);

        assertTrue(evolucion.getObservaciones().contains("[ANULADA - Motivo: Carga por error en paciente equivocado]"));

        // El motivo debe guardarse (saveAndFlush) ANTES del soft-delete, para garantizar que persista.
        InOrder inOrder = inOrder(evolucionClinicaRepository);
        inOrder.verify(evolucionClinicaRepository).saveAndFlush(evolucion);
        inOrder.verify(evolucionClinicaRepository).delete(evolucion);
    }

    @Test
    void testRN010_BloqueaAnulacionDeOtroMedico() {
        when(evolucionClinicaRepository.findById(100)).thenReturn(Optional.of(evolucion));
        when(medicoRepository.findByUsuario_IdUsuario(USUARIO_ID_OTRO)).thenReturn(Optional.of(otroMedico));

        EvolucionAnularDTO dto = EvolucionAnularDTO.builder()
                .motivoAnulacion("Intento de anulación por un médico que no es el autor")
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            historiaClinicaService.anularEvolucion(100, USUARIO_ID_OTRO, dto);
        });

        assertTrue(ex.getMessage().contains("RN-010"));
        assertTrue(ex.getMessage().contains("No tenés permisos"));
        verify(evolucionClinicaRepository, never()).saveAndFlush(any());
        verify(evolucionClinicaRepository, never()).delete(any());
    }

    @Test
    void testAdministrativoVeContenidoClinicoCompleto() {
        when(historiaClinicaRepository.findByPacienteIdPaciente(50))
                .thenReturn(Optional.of(evolucion.getHistoriaClinica()));
        when(evolucionClinicaRepository.findByHistoriaClinicaIdHistoriaClinicaOrderByFechaHoraDesc(10))
                .thenReturn(List.of(evolucion));

        Usuario admin = Usuario.builder().idUsuario(200).rol("ADMINISTRATIVO").build();
        HistoriaClinicaResponseDTO response = historiaClinicaService.obtenerHistoriaClinicaPorPaciente(50, admin);

        assertEquals(1, response.getEvoluciones().size());
        var evolucionDTO = response.getEvoluciones().get(0);
        // Sección 4.5, "Ver historias clínicas: Sí" para ADMINISTRATIVO (alineado con el
        // documento original): ve el contenido clínico completo, igual que MEDICO.
        assertEquals("Dolor de cabeza", evolucionDTO.getMotivoConsulta());
        assertEquals("Migraña", evolucionDTO.getDiagnostico());
        assertEquals("Analgésicos", evolucionDTO.getTratamiento());
        assertEquals("Reposo 48hs", evolucionDTO.getIndicaciones());
        assertEquals("Resonancia magnética", evolucionDTO.getEstudiosSolicitados());
        assertEquals("Paciente refiere episodios recurrentes", evolucionDTO.getObservaciones());
        assertNotNull(evolucionDTO.getFechaHora());
        assertNotNull(evolucionDTO.getNombreMedico());
    }

    @Test
    void testMedicoVeContenidoClinicoCompleto() {
        when(historiaClinicaRepository.findByPacienteIdPaciente(50))
                .thenReturn(Optional.of(evolucion.getHistoriaClinica()));
        when(evolucionClinicaRepository.findByHistoriaClinicaIdHistoriaClinicaOrderByFechaHoraDesc(10))
                .thenReturn(List.of(evolucion));
        when(medicoRepository.findByUsuario_IdUsuario(USUARIO_ID_RESPONSABLE)).thenReturn(Optional.of(medicoResponsable));
        when(pacienteRepository.existsVinculadoAMedico(1, 50)).thenReturn(true);

        Usuario medicoUser = Usuario.builder().idUsuario(USUARIO_ID_RESPONSABLE).rol("MEDICO").build();
        HistoriaClinicaResponseDTO response = historiaClinicaService.obtenerHistoriaClinicaPorPaciente(50, medicoUser);

        var evolucionDTO = response.getEvoluciones().get(0);
        assertEquals("Dolor de cabeza", evolucionDTO.getMotivoConsulta());
        assertEquals("Migraña", evolucionDTO.getDiagnostico());
        assertEquals("Analgésicos", evolucionDTO.getTratamiento());
        assertEquals("Reposo 48hs", evolucionDTO.getIndicaciones());
        assertEquals("Resonancia magnética", evolucionDTO.getEstudiosSolicitados());
        assertEquals("Paciente refiere episodios recurrentes", evolucionDTO.getObservaciones());
    }

    @Test
    void testMedicoNoAccedeHistoriaDePacienteAjeno() {
        when(medicoRepository.findByUsuario_IdUsuario(USUARIO_ID_OTRO)).thenReturn(Optional.of(otroMedico));
        when(pacienteRepository.existsVinculadoAMedico(2, 50)).thenReturn(false);

        Usuario medicoUser = Usuario.builder().idUsuario(USUARIO_ID_OTRO).rol("MEDICO").build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> historiaClinicaService.obtenerHistoriaClinicaPorPaciente(50, medicoUser));

        assertTrue(ex.getMessage().contains("RN-010"));
        verify(historiaClinicaRepository, never()).findByPacienteIdPaciente(any());
    }

    @Test
    void testMedicoNoDescargaAdjuntoDePacienteAjeno() {
        AdjuntoHistoriaClinica adjunto = AdjuntoHistoriaClinica.builder()
                .idAdjunto(7).evolucion(evolucion).nombreArchivo("estudio.pdf").rutaArchivo("x.pdf").build();
        when(adjuntoHistoriaClinicaRepository.findById(7)).thenReturn(Optional.of(adjunto));
        when(medicoRepository.findByUsuario_IdUsuario(USUARIO_ID_OTRO)).thenReturn(Optional.of(otroMedico));
        when(pacienteRepository.existsVinculadoAMedico(2, 50)).thenReturn(false);

        Usuario medicoUser = Usuario.builder().idUsuario(USUARIO_ID_OTRO).rol("MEDICO").build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> historiaClinicaService.descargarAdjunto(7, medicoUser));

        assertTrue(ex.getMessage().contains("RN-010"));
    }

    @Test
    void testRN010_AgregarEvolucionBloqueadaSiPacienteNoEstaVinculado() {
        HistoriaClinica hc = evolucion.getHistoriaClinica();
        when(historiaClinicaRepository.findById(10)).thenReturn(Optional.of(hc));
        when(medicoRepository.findByUsuario_IdUsuario(USUARIO_ID_OTRO)).thenReturn(Optional.of(otroMedico));
        when(pacienteRepository.existsVinculadoAMedico(2, 50)).thenReturn(false);

        EvolucionCreateDTO dto = EvolucionCreateDTO.builder()
                .motivoConsulta("Control")
                .diagnostico("s/p")
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                historiaClinicaService.agregarEvolucion(10, USUARIO_ID_OTRO, dto));

        assertTrue(ex.getMessage().contains("RN-010"));
        verify(evolucionClinicaRepository, never()).save(any());
    }

    @Test
    void testRN010_AgregarEvolucionPermitidaSiPacienteVinculado() {
        HistoriaClinica hc = evolucion.getHistoriaClinica();
        when(historiaClinicaRepository.findById(10)).thenReturn(Optional.of(hc));
        when(medicoRepository.findByUsuario_IdUsuario(USUARIO_ID_RESPONSABLE)).thenReturn(Optional.of(medicoResponsable));
        when(pacienteRepository.existsVinculadoAMedico(1, 50)).thenReturn(true);
        when(evolucionClinicaRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        EvolucionCreateDTO dto = EvolucionCreateDTO.builder()
                .motivoConsulta("Control")
                .diagnostico("Estable")
                .build();

        var res = historiaClinicaService.agregarEvolucion(10, USUARIO_ID_RESPONSABLE, dto);

        assertNotNull(res);
        verify(evolucionClinicaRepository).save(any());
    }

    @Test
    void testAdjuntoRechazaExtensionNoPermitida() {
        when(evolucionClinicaRepository.findById(100)).thenReturn(Optional.of(evolucion));
        when(medicoRepository.findByUsuario_IdUsuario(USUARIO_ID_RESPONSABLE)).thenReturn(Optional.of(medicoResponsable));

        Usuario medicoUser = Usuario.builder().idUsuario(USUARIO_ID_RESPONSABLE).rol("MEDICO").build();
        MockMultipartFile archivo = new MockMultipartFile("file", "virus.exe", "application/octet-stream", "contenido".getBytes());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            historiaClinicaService.agregarAdjunto(100, medicoUser, archivo);
        });

        assertTrue(ex.getMessage().contains("RF-H3"));
        verify(adjuntoHistoriaClinicaRepository, never()).save(any());
    }

    @Test
    void testAdjuntoBloqueaSubidaDeOtroMedico() {
        when(evolucionClinicaRepository.findById(100)).thenReturn(Optional.of(evolucion));
        when(medicoRepository.findByUsuario_IdUsuario(USUARIO_ID_OTRO)).thenReturn(Optional.of(otroMedico));

        Usuario medicoUser = Usuario.builder().idUsuario(USUARIO_ID_OTRO).rol("MEDICO").build();
        MockMultipartFile archivo = new MockMultipartFile("file", "estudio.pdf", "application/pdf", "contenido".getBytes());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            historiaClinicaService.agregarAdjunto(100, medicoUser, archivo);
        });

        assertTrue(ex.getMessage().contains("RN-010"));
        verify(adjuntoHistoriaClinicaRepository, never()).save(any());
    }
}
