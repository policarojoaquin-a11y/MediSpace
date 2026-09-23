package com.medispace.app.service;

import com.medispace.app.dto.UsuarioCreateDTO;
import com.medispace.app.dto.medico.MedicoCreateDTO;
import com.medispace.app.dto.medico.MedicoObraSocialDTO;
import com.medispace.app.dto.medico.MedicoPrestacionDTO;
import com.medispace.app.dto.medico.MedicoSelfUpdateDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Medico;
import com.medispace.app.model.MedicoObraSocial;
import com.medispace.app.model.MedicoPrestacion;
import com.medispace.app.model.ObraSocial;
import com.medispace.app.model.PrestacionMedica;
import com.medispace.app.model.Usuario;
import com.medispace.app.model.Especialidad;
import com.medispace.app.repository.*;
import com.medispace.app.service.impl.MedicoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MedicoServiceTest {

    @Mock
    private MedicoRepository medicoRepository;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private EspecialidadRepository especialidadRepository;

    @Mock
    private PrestacionMedicaRepository prestacionMedicaRepository;

    @Mock
    private ObraSocialRepository obraSocialRepository;

    @Mock
    private MedicoPrestacionRepository medicoPrestacionRepository;

    @Mock
    private MedicoObraSocialRepository medicoObraSocialRepository;

    @InjectMocks
    private MedicoServiceImpl medicoService;

    private Medico medicoGuardado;

    @BeforeEach
    void setUp() {
        Usuario usuario = Usuario.builder()
                .idUsuario(1)
                .email("medico@test.com")
                .rol("MEDICO")
                .build();

        Especialidad especialidad = Especialidad.builder()
                .idEspecialidad(1)
                .nombre("Cardiología")
                .build();

        medicoGuardado = Medico.builder()
                .idMedico(1)
                .usuario(usuario)
                .nombre("Dr. Juan")
                .apellido("Pérez")
                .matricula("MAT001")
                .especialidad(especialidad)
                .estado("ACTIVO")
                .build();
    }

    @Test
    void testRN012_BajaBloqueadaSiHayTurnosFuturos() {
        when(medicoRepository.findById(1)).thenReturn(Optional.of(medicoGuardado));
        when(medicoRepository.countTurnosFuturosActivos(1)).thenReturn(3L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            medicoService.eliminarMedico(1);
        });

        assertTrue(ex.getMessage().contains("RN-012"));
        assertTrue(ex.getMessage().contains("turno(s) futuro(s)"));
        verify(medicoRepository, never()).delete(any());
    }

    @Test
    void testRN012_BajaExitosaSinTurnosFuturos() {
        when(medicoRepository.findById(1)).thenReturn(Optional.of(medicoGuardado));
        when(medicoRepository.countTurnosFuturosActivos(1)).thenReturn(0L);

        assertDoesNotThrow(() -> medicoService.eliminarMedico(1));

        verify(medicoRepository, times(1)).delete(medicoGuardado);
    }

    private MedicoCreateDTO.MedicoCreateDTOBuilder medicoCreateDTOBase() {
        return MedicoCreateDTO.builder()
                .nombre("Juan")
                .apellido("Pérez")
                .matricula("MAT002")
                .idEspecialidad(1)
                .fechaInicioActividad(LocalDate.now())
                .email("nuevo.medico@test.com");
    }

    @Test
    void testCrearMedico_CreaUsuarioConRolMedicoYPasswordMatricula() {
        MedicoCreateDTO dto = medicoCreateDTOBase().build();

        when(medicoRepository.findByMatricula(dto.getMatricula())).thenReturn(Optional.empty());
        when(especialidadRepository.findById(1)).thenReturn(Optional.of(
                Especialidad.builder().idEspecialidad(1).nombre("Cardiología").build()));
        when(usuarioService.crearUsuario(any())).thenReturn(medicoGuardado.getUsuario());
        when(medicoRepository.save(any())).thenReturn(medicoGuardado);

        medicoService.crearMedico(dto);

        ArgumentCaptor<UsuarioCreateDTO> captor = ArgumentCaptor.forClass(UsuarioCreateDTO.class);
        verify(usuarioService, times(1)).crearUsuario(captor.capture());

        UsuarioCreateDTO usuarioCreado = captor.getValue();
        assertEquals("MEDICO", usuarioCreado.getRol());
        assertEquals(dto.getEmail(), usuarioCreado.getEmail());
        assertEquals(dto.getMatricula(), usuarioCreado.getPassword());
    }

    @Test
    void testCrearMedico_EmailDuplicadoPropagaBusinessRuleException() {
        MedicoCreateDTO dto = medicoCreateDTOBase().build();

        when(medicoRepository.findByMatricula(dto.getMatricula())).thenReturn(Optional.empty());
        when(usuarioService.crearUsuario(any()))
                .thenThrow(new BusinessRuleException("El email ya se encuentra registrado."));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            medicoService.crearMedico(dto);
        });

        assertEquals("El email ya se encuentra registrado.", ex.getMessage());
        verify(medicoRepository, never()).save(any());
    }

    @Test
    void testAgregarPrestacion_Success() {
        PrestacionMedica prestacion = PrestacionMedica.builder().idPrestacion(1).nombre("Consulta general").build();

        when(medicoRepository.findById(1)).thenReturn(Optional.of(medicoGuardado));
        when(prestacionMedicaRepository.findById(1)).thenReturn(Optional.of(prestacion));
        when(medicoPrestacionRepository.findByMedicoIdMedicoAndPrestacionIdPrestacion(1, 1)).thenReturn(Optional.empty());
        when(medicoPrestacionRepository.save(any())).thenAnswer(i -> {
            MedicoPrestacion mp = i.getArgument(0);
            mp.setIdMedicoPrestacion(50);
            return mp;
        });

        MedicoPrestacionDTO dto = MedicoPrestacionDTO.builder()
                .idPrestacion(1)
                .duracionEstimadaMin(30)
                .importeParticular(new java.math.BigDecimal("5000.00"))
                .tipo("Presencial")
                .build();

        MedicoPrestacionDTO response = medicoService.agregarPrestacion(1, dto);

        assertNotNull(response);
        assertEquals(50, response.getIdMedicoPrestacion());
        assertEquals("Consulta general", response.getNombrePrestacion());
        verify(medicoPrestacionRepository, times(1)).save(any());
    }

    @Test
    void testAgregarPrestacion_DuplicadaLanzaBusinessRuleException() {
        PrestacionMedica prestacion = PrestacionMedica.builder().idPrestacion(1).nombre("Consulta general").build();
        MedicoPrestacion existente = MedicoPrestacion.builder().idMedicoPrestacion(50).medico(medicoGuardado).prestacion(prestacion).build();

        when(medicoRepository.findById(1)).thenReturn(Optional.of(medicoGuardado));
        when(prestacionMedicaRepository.findById(1)).thenReturn(Optional.of(prestacion));
        when(medicoPrestacionRepository.findByMedicoIdMedicoAndPrestacionIdPrestacion(1, 1)).thenReturn(Optional.of(existente));

        MedicoPrestacionDTO dto = MedicoPrestacionDTO.builder().idPrestacion(1).build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            medicoService.agregarPrestacion(1, dto);
        });

        assertTrue(ex.getMessage().toLowerCase().contains("ya está asociada"));
        verify(medicoPrestacionRepository, never()).save(any());
    }

    @Test
    void testActualizarMisDatos_ActualizaNombreSinTocarEspecialidadNiImporteNiObrasSociales() {
        Especialidad especialidadOriginal = medicoGuardado.getEspecialidad();
        medicoGuardado.setImporteConsulta(new java.math.BigDecimal("5000.00"));

        when(medicoRepository.findByUsuario_Email("medico@test.com")).thenReturn(Optional.of(medicoGuardado));
        when(medicoRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
        when(medicoObraSocialRepository.findByMedicoIdMedico(1)).thenReturn(java.util.List.of());

        MedicoSelfUpdateDTO dto = MedicoSelfUpdateDTO.builder()
                .nombre("Dr. Juan Actualizado")
                .apellido("Pérez")
                .build();

        var response = medicoService.actualizarMisDatos("medico@test.com", dto);

        assertEquals("Dr. Juan Actualizado", medicoGuardado.getNombre());
        // Especialidad, importe de consulta y obras sociales no forman parte de
        // MedicoSelfUpdateDTO — deben permanecer intactos tras la autoedición. Las obras
        // sociales con las que trabaja son admin-only (ver agregarObraSocial/eliminarObraSocial);
        // lo único que el propio médico puede tocar es el coseguro de una relación existente,
        // vía actualizarCoseguroPropio.
        assertEquals(especialidadOriginal, medicoGuardado.getEspecialidad());
        assertEquals(new java.math.BigDecimal("5000.00"), medicoGuardado.getImporteConsulta());
        verify(medicoObraSocialRepository, never()).save(any());
        assertNotNull(response);
    }

    @Test
    void testActualizarCoseguroPropio_Success() {
        ObraSocial os = ObraSocial.builder().idObraSocial(9).nombre("OSDE").build();
        MedicoObraSocial existente = MedicoObraSocial.builder()
                .idMedicoObraSocial(70).medico(medicoGuardado).obraSocial(os).build();

        when(medicoRepository.findByUsuario_Email("medico@test.com")).thenReturn(Optional.of(medicoGuardado));
        when(medicoObraSocialRepository.findById(70)).thenReturn(Optional.of(existente));
        when(medicoObraSocialRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        MedicoObraSocialDTO dto = MedicoObraSocialDTO.builder()
                .importeCoseguro(new java.math.BigDecimal("1500.00"))
                .build();

        MedicoObraSocialDTO response = medicoService.actualizarCoseguroPropio("medico@test.com", 70, dto);

        assertEquals(new java.math.BigDecimal("1500.00"), response.getImporteCoseguro());
        assertEquals(new java.math.BigDecimal("1500.00"), existente.getImporteCoseguro());
    }

    @Test
    void testActualizarCoseguroPropio_DeOtroMedicoLanzaBusinessRuleException() {
        Medico otroMedico = Medico.builder().idMedico(2).build();
        ObraSocial os = ObraSocial.builder().idObraSocial(9).nombre("OSDE").build();
        MedicoObraSocial deOtroMedico = MedicoObraSocial.builder()
                .idMedicoObraSocial(71).medico(otroMedico).obraSocial(os).build();

        when(medicoRepository.findByUsuario_Email("medico@test.com")).thenReturn(Optional.of(medicoGuardado));
        when(medicoObraSocialRepository.findById(71)).thenReturn(Optional.of(deOtroMedico));

        MedicoObraSocialDTO dto = MedicoObraSocialDTO.builder()
                .importeCoseguro(new java.math.BigDecimal("1500.00"))
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                medicoService.actualizarCoseguroPropio("medico@test.com", 71, dto));

        assertTrue(ex.getMessage().toLowerCase().contains("otro médico"));
        verify(medicoObraSocialRepository, never()).save(any());
    }

    @Test
    void testAgregarObraSocial_Success() {
        ObraSocial os = ObraSocial.builder().idObraSocial(9).nombre("OSDE").build();

        when(medicoRepository.findById(1)).thenReturn(Optional.of(medicoGuardado));
        when(obraSocialRepository.findById(9)).thenReturn(Optional.of(os));
        when(medicoObraSocialRepository.findByMedicoIdMedicoAndObraSocialIdObraSocial(1, 9)).thenReturn(Optional.empty());
        when(medicoObraSocialRepository.save(any())).thenAnswer(i -> {
            MedicoObraSocial mos = i.getArgument(0);
            mos.setIdMedicoObraSocial(80);
            return mos;
        });

        MedicoObraSocialDTO dto = MedicoObraSocialDTO.builder()
                .idObraSocial(9)
                .importeCoseguro(new java.math.BigDecimal("1000.00"))
                .build();

        MedicoObraSocialDTO response = medicoService.agregarObraSocial(1, dto);

        assertNotNull(response);
        assertEquals(80, response.getIdMedicoObraSocial());
        assertEquals("OSDE", response.getNombreObraSocial());
        verify(medicoObraSocialRepository, times(1)).save(any());
    }

    @Test
    void testAgregarObraSocial_DuplicadaLanzaBusinessRuleException() {
        ObraSocial os = ObraSocial.builder().idObraSocial(9).nombre("OSDE").build();
        MedicoObraSocial existente = MedicoObraSocial.builder().idMedicoObraSocial(80).medico(medicoGuardado).obraSocial(os).build();

        when(medicoRepository.findById(1)).thenReturn(Optional.of(medicoGuardado));
        when(obraSocialRepository.findById(9)).thenReturn(Optional.of(os));
        when(medicoObraSocialRepository.findByMedicoIdMedicoAndObraSocialIdObraSocial(1, 9)).thenReturn(Optional.of(existente));

        MedicoObraSocialDTO dto = MedicoObraSocialDTO.builder().idObraSocial(9).build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                medicoService.agregarObraSocial(1, dto));

        assertTrue(ex.getMessage().toLowerCase().contains("ya está asociada"));
        verify(medicoObraSocialRepository, never()).save(any());
    }

    @Test
    void testEliminarPrestacion_BajaLogica() {
        PrestacionMedica prestacion = PrestacionMedica.builder().idPrestacion(1).nombre("Consulta general").build();
        MedicoPrestacion existente = MedicoPrestacion.builder().idMedicoPrestacion(50).medico(medicoGuardado).prestacion(prestacion).build();

        when(medicoPrestacionRepository.findById(50)).thenReturn(Optional.of(existente));

        medicoService.eliminarPrestacion(50);

        verify(medicoPrestacionRepository, times(1)).delete(existente);
    }

    // ---- Cartilla propia del médico (RN-017): agregar/editar/quitar sus propias prestaciones y
    // obras sociales resolviendo el médico desde el email del JWT, con chequeo de pertenencia.

    @Test
    void testAgregarPrestacionPropia_ResuelveMedicoPorEmailYAgrega() {
        PrestacionMedica prestacion = PrestacionMedica.builder().idPrestacion(1).nombre("Ecografía").build();

        when(medicoRepository.findByUsuario_Email("medico@test.com")).thenReturn(Optional.of(medicoGuardado));
        when(medicoRepository.findById(1)).thenReturn(Optional.of(medicoGuardado));
        when(prestacionMedicaRepository.findById(1)).thenReturn(Optional.of(prestacion));
        when(medicoPrestacionRepository.findByMedicoIdMedicoAndPrestacionIdPrestacion(1, 1)).thenReturn(Optional.empty());
        when(medicoPrestacionRepository.save(any())).thenAnswer(i -> {
            MedicoPrestacion mp = i.getArgument(0);
            mp.setIdMedicoPrestacion(60);
            return mp;
        });

        MedicoPrestacionDTO dto = MedicoPrestacionDTO.builder()
                .idPrestacion(1).duracionEstimadaMin(20).importeParticular(new java.math.BigDecimal("8000.00")).build();

        MedicoPrestacionDTO response = medicoService.agregarPrestacionPropia("medico@test.com", dto);

        assertEquals(60, response.getIdMedicoPrestacion());
        assertEquals("Ecografía", response.getNombrePrestacion());
        verify(medicoPrestacionRepository, times(1)).save(any());
    }

    @Test
    void testActualizarPrestacionPropia_DeOtroMedicoLanzaBusinessRuleException() {
        Medico otroMedico = Medico.builder().idMedico(2).build();
        PrestacionMedica prestacion = PrestacionMedica.builder().idPrestacion(1).nombre("Consulta general").build();
        MedicoPrestacion deOtro = MedicoPrestacion.builder().idMedicoPrestacion(99).medico(otroMedico).prestacion(prestacion).build();

        when(medicoRepository.findByUsuario_Email("medico@test.com")).thenReturn(Optional.of(medicoGuardado));
        when(medicoPrestacionRepository.findById(99)).thenReturn(Optional.of(deOtro));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                medicoService.actualizarPrestacionPropia("medico@test.com", 99,
                        MedicoPrestacionDTO.builder().duracionEstimadaMin(10).build()));

        assertTrue(ex.getMessage().toLowerCase().contains("otro médico"));
        verify(medicoPrestacionRepository, never()).save(any());
    }

    @Test
    void testEliminarPrestacionPropia_DeLaCartillaPropia_BajaLogica() {
        PrestacionMedica prestacion = PrestacionMedica.builder().idPrestacion(1).nombre("Consulta general").build();
        MedicoPrestacion propia = MedicoPrestacion.builder().idMedicoPrestacion(50).medico(medicoGuardado).prestacion(prestacion).build();

        when(medicoRepository.findByUsuario_Email("medico@test.com")).thenReturn(Optional.of(medicoGuardado));
        when(medicoPrestacionRepository.findById(50)).thenReturn(Optional.of(propia));

        medicoService.eliminarPrestacionPropia("medico@test.com", 50);

        verify(medicoPrestacionRepository, times(1)).delete(propia);
    }

    @Test
    void testAgregarObraSocialPropia_ResuelveMedicoPorEmailYAgrega() {
        ObraSocial os = ObraSocial.builder().idObraSocial(9).nombre("OSDE").build();

        when(medicoRepository.findByUsuario_Email("medico@test.com")).thenReturn(Optional.of(medicoGuardado));
        when(medicoRepository.findById(1)).thenReturn(Optional.of(medicoGuardado));
        when(obraSocialRepository.findById(9)).thenReturn(Optional.of(os));
        when(medicoObraSocialRepository.findByMedicoIdMedicoAndObraSocialIdObraSocial(1, 9)).thenReturn(Optional.empty());
        when(medicoObraSocialRepository.save(any())).thenAnswer(i -> {
            MedicoObraSocial mos = i.getArgument(0);
            mos.setIdMedicoObraSocial(90);
            return mos;
        });

        MedicoObraSocialDTO response = medicoService.agregarObraSocialPropia("medico@test.com",
                MedicoObraSocialDTO.builder().idObraSocial(9).importeCoseguro(new java.math.BigDecimal("1200.00")).build());

        assertEquals(90, response.getIdMedicoObraSocial());
        assertEquals("OSDE", response.getNombreObraSocial());
        verify(medicoObraSocialRepository, times(1)).save(any());
    }

    @Test
    void testEliminarObraSocialPropia_DeOtroMedicoLanzaBusinessRuleException() {
        Medico otroMedico = Medico.builder().idMedico(2).build();
        ObraSocial os = ObraSocial.builder().idObraSocial(9).nombre("OSDE").build();
        MedicoObraSocial deOtro = MedicoObraSocial.builder().idMedicoObraSocial(71).medico(otroMedico).obraSocial(os).build();

        when(medicoRepository.findByUsuario_Email("medico@test.com")).thenReturn(Optional.of(medicoGuardado));
        when(medicoObraSocialRepository.findById(71)).thenReturn(Optional.of(deOtro));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                medicoService.eliminarObraSocialPropia("medico@test.com", 71));

        assertTrue(ex.getMessage().toLowerCase().contains("otro médico"));
        verify(medicoObraSocialRepository, never()).delete(any());
    }
}
