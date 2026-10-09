package com.modeltech.datamasteryhub.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(f -> errors.put(f.getField(), f.getDefaultMessage()));

        return ResponseEntity.badRequest().body(new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "Les données fournies sont invalides",
                request.getRequestURI(),
                errors
        ));
    }

    /**
     * Erreurs métier levées via ResponseStatusException (409 « session complète »,
     * 400 « profil obligatoire »…) : on conserve le statut voulu au lieu de les
     * laisser retomber dans le handler générique (500).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String error = status != null ? status.getReasonPhrase() : "Error";
        String message = ex.getReason() != null ? ex.getReason() : error;
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorResponse(
                LocalDateTime.now(),
                ex.getStatusCode().value(),
                error,
                message,
                request.getRequestURI(),
                null
        ));
    }

    /** Paramètre ou corps de requête illisible / de mauvais type → 400 (et non 500). */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        String message = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? "Valeur invalide pour le paramètre « " + mismatch.getName() + " »"
                : "Requête invalide";
        return ResponseEntity.badRequest().body(new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                message,
                request.getRequestURI(),
                null
        ));
    }

    /** Route inexistante → 404 (et non 500 « No static resource »). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                LocalDateTime.now(), HttpStatus.NOT_FOUND.value(), "Not Found",
                "Ressource introuvable", request.getRequestURI(), null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new ErrorResponse(
                LocalDateTime.now(), HttpStatus.METHOD_NOT_ALLOWED.value(), "Method Not Allowed",
                "Méthode non autorisée sur cette ressource", request.getRequestURI(), null));
    }

    /**
     * Contrainte d'intégrité (valeur hors liste, doublon…) → 409/400 explicite plutôt qu'un 500.
     * Le détail SQL reste dans les logs.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String detail = String.valueOf(ex.getMostSpecificCause().getMessage());
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .warn("Contrainte d'intégrité sur {} {} : {}", request.getMethod(), request.getRequestURI(), detail);
        boolean duplicate = detail.contains("duplicate key") || detail.contains("unique");
        HttpStatus status = duplicate ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ErrorResponse(
                LocalDateTime.now(), status.value(), status.getReasonPhrase(),
                duplicate ? "Cette valeur existe déjà."
                          : "Une valeur transmise n'est pas autorisée (voir les champs obligatoires ou les listes de choix).",
                request.getRequestURI(), null));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getRequestURI(),
                null
        ));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Email ou mot de passe incorrect",
                request.getRequestURI(),
                null
        ));
    }

    @ExceptionHandler({DisabledException.class, LockedException.class})
    public ResponseEntity<ErrorResponse> handleDisabled(
            RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Compte désactivé, contactez un administrateur",
                request.getRequestURI(),
                null
        ));
    }

    /** Droits insuffisants (contrôle de rôle par annotation) : 403, jamais 500. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "Accès refusé : droits insuffisants",
                request.getRequestURI(),
                null
        ));
    }

    /** Fichier refusé (type, taille) ou stockage indisponible : message lisible, pas de 500 opaque. */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorage(StorageException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI(),
                null
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(
            Exception ex, HttpServletRequest request) {
        // Le détail technique (SQL, chemins, classes) reste dans les logs ; le client reçoit une référence
        String reference = java.util.UUID.randomUUID().toString().substring(0, 8);
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("Erreur interne [{}] sur {} {}", reference, request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "Une erreur est survenue. Référence : " + reference,
                request.getRequestURI(),
                null
        ));
    }
}