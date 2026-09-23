package com.medispace.app.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class MedicoRepositoryTest {

    // RN-012: la baja de un médico solo se bloquea por turnos futuros con un paciente real
    // asignado — RESERVADO o EN_ESPERA ("sin reasignar" en el texto de la regla). Los turnos
    // DISPONIBLE (cupo generado por el contrato de arrendamiento, sin paciente) NO deben
    // contar: si contaran, ningún médico con contrato activo podría darse de baja nunca
    // (bug reportado 27/08). Es una query nativa contra SQL Server (GETDATE()), sin
    // infraestructura de test de integración en este proyecto (no hay H2/@DataJpaTest) — este
    // test es una salvaguarda estática sobre el literal del query.
    @Test
    void countTurnosFuturosActivos_SoloCuentaReservadoYEnEspera() throws NoSuchMethodException {
        Method metodo = MedicoRepository.class.getMethod("countTurnosFuturosActivos", Integer.class);
        Query query = metodo.getAnnotation(Query.class);

        assertNotNull(query);
        String q = query.value();
        assertTrue(q.contains("'RESERVADO'") && q.contains("'EN_ESPERA'"),
                "RN-012 debe bloquear por turnos RESERVADO/EN_ESPERA (paciente real asignado).");
        assertFalse(q.contains("'DISPONIBLE'"),
                "RN-012 NO debe contar turnos DISPONIBLE — bloquearían la baja de cualquier médico con contrato.");
        // Guion bajo, igual que Turno.estado — nunca con espacio.
        assertFalse(q.contains("'EN ESPERA'") || q.contains("'NO ASISTIO'"),
                "Los literales de estado van con guion bajo, igual que Turno.estado.");
    }
}
