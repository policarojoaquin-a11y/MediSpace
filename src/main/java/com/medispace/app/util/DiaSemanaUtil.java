package com.medispace.app.util;

import java.time.DayOfWeek;

public final class DiaSemanaUtil {

    private DiaSemanaUtil() {
    }

    public static String traducir(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "LUNES";
            case TUESDAY -> "MARTES";
            case WEDNESDAY -> "MIERCOLES";
            case THURSDAY -> "JUEVES";
            case FRIDAY -> "VIERNES";
            case SATURDAY -> "SABADO";
            case SUNDAY -> "DOMINGO";
        };
    }
}
