package com.medispace.app.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, String>> handleBusinessRule(BusinessRuleException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Email o contraseña incorrectos."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "El archivo supera el tamaño máximo permitido (10MB)."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "No tenés permisos para realizar esta acción."));
    }

    // Parámetro de query mal formado (p. ej. ?desde=ayer en un endpoint que espera una fecha
    // ISO). Es un error del cliente → 400, no un 500 genérico.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "El parámetro '" + ex.getName() + "' tiene un formato inválido."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        log.error("Error no controlado", ex);
        // Se agrega el tipo de excepción (y su causa raíz) a la respuesta para poder
        // diagnosticar fallas 500 sin tener que levantar una segunda instancia a leer el
        // stack trace (ver docs/ANEXO §C.1). No expone datos sensibles: solo el nombre de
        // clase de la excepción y su mensaje.
        Throwable causa = ex;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        String detalle = causa.getClass().getSimpleName()
                + (causa.getMessage() != null ? ": " + causa.getMessage() : "");
        if (detalle.length() > 500) {
            detalle = detalle.substring(0, 500) + "…";
        }
        Map<String, String> body = new java.util.LinkedHashMap<>();
        body.put("message", "Ocurrió un error inesperado. Intente nuevamente.");
        body.put("detail", detalle);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
