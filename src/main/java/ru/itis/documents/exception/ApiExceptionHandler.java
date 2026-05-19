package ru.itis.documents.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import ru.itis.documents.dto.ApiErrorResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "ru.itis.documents.controller.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IntegrationException.class)
    public ResponseEntity<ApiErrorResponse> handleIntegration(IntegrationException ex, HttpServletRequest request) {
        int status = ex.getHttpStatus() > 0 ? ex.getHttpStatus() : 503;

        log.warn("Integration error: code={} status={} method={} uri={} msg={}",
                ex.getCode(), status, request.getMethod(), request.getRequestURI(), ex.getUserMessage(), ex);

        ApiErrorResponse body = new ApiErrorResponse(
                ex.getCode(),
                ex.getUserMessage(),
                detailsOf(request, ex.getDetails())
        );
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(ExternalApiUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleExternalApiUnavailable(ExternalApiUnavailableException ex, HttpServletRequest request) {
        int status = (ex.getStatusCode() == null || ex.getStatusCode() <= 0) ? 503 : ex.getStatusCode();

        String service = (ex.getService() == null || ex.getService().isBlank()) ? "external" : ex.getService().trim();
        String code = service.toUpperCase() + "_UNAVAILABLE";

        log.warn("External API unavailable: service={} status={} method={} uri={} msg={}",
                service, status, request.getMethod(), request.getRequestURI(), ex.getUserMessage(), ex);

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("service", service);
        extra.put("status", status);

        ApiErrorResponse body = new ApiErrorResponse(code, ex.getUserMessage(), detailsOf(request, extra));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
        String user = (request.getUserPrincipal() == null) ? "anonymous" : request.getUserPrincipal().getName();
        log.warn("Access denied (API): user={} method={} uri={}", user, request.getMethod(), request.getRequestURI(), ex);

        ApiErrorResponse body = new ApiErrorResponse("FORBIDDEN", "Доступ запрещён", detailsOf(request, null));
        return ResponseEntity.status(403).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        String code = switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            default -> "HTTP_" + status;
        };

        log.warn("ResponseStatusException (API): status={} code={} method={} uri={} reason={}",
                status, code, request.getMethod(), request.getRequestURI(), ex.getReason(), ex);

        String msg = (ex.getReason() == null || ex.getReason().isBlank()) ? "Ошибка запроса" : ex.getReason();
        ApiErrorResponse body = new ApiErrorResponse(code, msg, detailsOf(request, null));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(Exception ex, HttpServletRequest request) {
        List<String> errors = extractValidationErrors(ex);

        log.warn("Validation error (API): method={} uri={} errors={}",
                request.getMethod(), request.getRequestURI(), errors, ex);

        Map<String, Object> extra = Map.of("errors", errors);
        ApiErrorResponse body = new ApiErrorResponse("VALIDATION_ERROR", "Некорректные данные", detailsOf(request, extra));
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing request parameter (API): method={} uri={} param={} expectedType={}",
                request.getMethod(), request.getRequestURI(), ex.getParameterName(), ex.getParameterType(), ex);

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("param", ex.getParameterName());
        extra.put("expectedType", ex.getParameterType());

        ApiErrorResponse body = new ApiErrorResponse("MISSING_PARAM", "Не хватает параметра запроса", detailsOf(request, extra));
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch (API): method={} uri={} param={} value={} requiredType={}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getName(),
                ex.getValue(),
                ex.getRequiredType() == null ? null : ex.getRequiredType().getSimpleName(),
                ex);

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("param", ex.getName());
        extra.put("value", ex.getValue());
        extra.put("requiredType", ex.getRequiredType() == null ? null : ex.getRequiredType().getSimpleName());

        ApiErrorResponse body = new ApiErrorResponse("BAD_PARAM", "Некорректный параметр", detailsOf(request, extra));
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleBadJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Bad JSON (API): method={} uri={} contentType={}",
                request.getMethod(), request.getRequestURI(), request.getContentType(), ex);

        ApiErrorResponse body = new ApiErrorResponse("BAD_JSON", "Некорректное тело запроса", detailsOf(request, null));
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                   HttpServletRequest request) {
        log.warn("Method not allowed (API): method={} uri={} supported={}",
                request.getMethod(), request.getRequestURI(), ex.getSupportedMethods(), ex);

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("supportedMethods", ex.getSupportedMethods() == null
                ? List.of()
                : List.of(ex.getSupportedMethods()));

        ApiErrorResponse body = new ApiErrorResponse(
                "METHOD_NOT_ALLOWED",
                "Метод запроса не поддерживается",
                detailsOf(request, extra)
        );
        return ResponseEntity.status(405).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                                       HttpServletRequest request) {
        log.warn("Unsupported media type (API): method={} uri={} contentType={} supported={}",
                request.getMethod(), request.getRequestURI(), request.getContentType(), ex.getSupportedMediaTypes(), ex);

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("receivedContentType", request.getContentType());
        extra.put("supportedMediaTypes", ex.getSupportedMediaTypes() == null
                ? List.of()
                : ex.getSupportedMediaTypes().stream().map(MediaType::toString).toList());

        ApiErrorResponse body = new ApiErrorResponse(
                "UNSUPPORTED_MEDIA_TYPE",
                "Неподдерживаемый тип содержимого",
                detailsOf(request, extra)
        );
        return ResponseEntity.status(415).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAny(Exception ex, HttpServletRequest request) {
        log.error("Unhandled API error: method={} uri={} qs={}",
                request.getMethod(), request.getRequestURI(), request.getQueryString(), ex);

        ApiErrorResponse body = new ApiErrorResponse("INTERNAL_ERROR", "Ошибка сервера", detailsOf(request, null));
        return ResponseEntity.status(500).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static Map<String, Object> detailsOf(HttpServletRequest request, Object extra) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("path", request.getRequestURI());
        details.put("method", request.getMethod());
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            details.put("query", request.getQueryString());
        }

        if (extra instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) {
                    details.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        } else if (extra != null) {
            details.put("extra", extra);
        }
        return details;
    }

    private static List<String> extractValidationErrors(Exception ex) {
        if (ex instanceof BindException be) {
            return be.getBindingResult().getAllErrors().stream()
                    .map(err -> {
                        if (err instanceof FieldError fe) return fe.getField() + ": " + fe.getDefaultMessage();
                        return err.getDefaultMessage();
                    })
                    .collect(Collectors.toList());
        }

        if (ex instanceof MethodArgumentNotValidException manv) {
            return manv.getBindingResult().getAllErrors().stream()
                    .map(err -> {
                        if (err instanceof FieldError fe) return fe.getField() + ": " + fe.getDefaultMessage();
                        return err.getDefaultMessage();
                    })
                    .collect(Collectors.toList());
        }

        if (ex instanceof ConstraintViolationException cve) {
            List<String> out = new ArrayList<>();
            cve.getConstraintViolations().forEach(v -> out.add(v.getPropertyPath() + ": " + v.getMessage()));
            return out;
        }

        return List.of("Некорректные данные");
    }
}