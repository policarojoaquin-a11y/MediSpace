package com.medispace.app.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class MedicoRepositoryTest {

    // RN-012: el literal del query nativo debe coincidir EXACTO con Turno.estado
    // ("NO_ASISTIO", con guion bajo). Es una query nativa contra SQL Server (GETDATE()), sin
    // infraestructura de test de integración en este proyecto (no hay H2/@DataJpaTest) — este
    // test es una salvaguarda estática contra que el desajuste de string vuelva a aparecer.
    @Test
    void countTurnosFuturosActivos_QueryUsaGuionBajoEnNoAsistio() throws NoSuchMethodException {
        Method metodo = MedicoRepository.class.getMethod("countTurnosFuturosActivos", Integer.class);
        Query query = metodo.getAnnotation(Query.class);

        assertNotNull(query);
        assertTrue(query.value().contains("'NO_ASISTIO'"),
                "El literal debe ser 'NO_ASISTIO' (con guion bajo), igual que Turno.estado.");
        assertFalse(query.value().contains("'NO ASISTIO'"),
                "No debe reaparecer el literal con espacio — no matchea ningún estado real y RN-012 quedaría rota.");
    }
}
