package ru.itis.documents.exception;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.itis.documents.form.UserPlantPhotoUploadForm;
import ru.itis.documents.exception.PlantIdentificationNotFoundException;
import ru.itis.documents.dto.ApiErrorResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateFormSubmissionException.class)
    public String handleDuplicateFormSubmission(DuplicateFormSubmissionException ex,
                                                HttpServletRequest request,
                                                RedirectAttributes redirectAttributes) {
        log.warn("Duplicate form submission: method={} uri={}",
                request.getMethod(),
                request.getRequestURI(),
                ex);

        redirectAttributes.addFlashAttribute(
                "formSubmitError",
                "Форма уже была отправлена. Обновите страницу и попробуйте ещё раз."
        );

        return "redirect:" + safeBackUrl(request);
    }

    @ExceptionHandler(IntegrationException.class)
    public Object handleIntegration(IntegrationException ex, HttpServletRequest request) {
        int status = ex.getHttpStatus() > 0 ? ex.getHttpStatus() : 503;

        log.warn("Integration error: code={} status={} method={} uri={} msg={}",
                ex.getCode(), status, request.getMethod(), request.getRequestURI(), ex.getUserMessage(), ex);

        if (isAjax(request)) {
            ApiErrorResponse body = new ApiErrorResponse(ex.getCode(), ex.getUserMessage(), ex.getDetails());
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
        }

        ModelAndView mav = new ModelAndView("error/integration");
        mav.setStatus(org.springframework.http.HttpStatusCode.valueOf(status));
        mav.addObject("title", "Ошибка внешнего сервиса");
        mav.addObject("message", ex.getUserMessage());
        mav.addObject("code", ex.getCode());
        mav.addObject("details", ex.getDetails());
        mav.addObject("backUrl", safeBackUrl(request));
        return mav;
    }

    @ExceptionHandler(ExternalApiUnavailableException.class)
    public Object handleExternalApiUnavailable(ExternalApiUnavailableException ex, HttpServletRequest request) {
        int status = (ex.getStatusCode() == null || ex.getStatusCode() <= 0) ? 503 : ex.getStatusCode();

        String service = (ex.getService() == null || ex.getService().isBlank()) ? "external" : ex.getService().trim();
        String code = service.toUpperCase() + "_UNAVAILABLE";

        log.warn("External API unavailable: service={} status={} method={} uri={} msg={}",
                service, status, request.getMethod(), request.getRequestURI(), ex.getUserMessage(), ex);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("service", service);
        details.put("status", status);

        if (isAjax(request)) {
            ApiErrorResponse body = new ApiErrorResponse(code, ex.getUserMessage(), details);
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
        }

        ModelAndView mav = new ModelAndView("error/integration");
        mav.setStatus(org.springframework.http.HttpStatusCode.valueOf(status));
        mav.addObject("title", "Ошибка внешнего сервиса");
        mav.addObject("message", ex.getUserMessage());
        mav.addObject("code", code);
        mav.addObject("details", details);
        mav.addObject("backUrl", safeBackUrl(request));
        return mav;
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public Object handleNotFound(Exception ex, HttpServletRequest request) {
        log.warn("Not found: method={} uri={}",
                request.getMethod(), request.getRequestURI(), ex);

        if (isAjax(request)) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse("NOT_FOUND", "Страница не найдена", detailsOf(request, null)));
        }

        ModelAndView mav = new ModelAndView("error/404");
        mav.setStatus(org.springframework.http.HttpStatus.NOT_FOUND);
        mav.addObject("path", request.getRequestURI());
        mav.addObject("backUrl", safeBackUrl(request));
        return mav;
    }

    @ExceptionHandler(PlantIdentificationNotFoundException.class)
    public Object handlePlantIdentificationNotFound(PlantIdentificationNotFoundException ex,
                                                    HttpServletRequest request) {
        log.warn("Plant identification not found: method={} uri={} msg={}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage());

        if (isAjax(request)) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse(
                            "IDENTIFICATION_NOT_FOUND",
                            ex.getMessage(),
                            detailsOf(request, null)
                    ));
        }

        ModelAndView mav = new ModelAndView("error/404");
        mav.setStatus(org.springframework.http.HttpStatus.NOT_FOUND);
        mav.addObject("path", request.getRequestURI());
        mav.addObject("message", ex.getMessage());
        mav.addObject("backUrl", safeBackUrl(request));
        return mav;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
        String user = (request.getUserPrincipal() == null) ? "anonymous" : request.getUserPrincipal().getName();
        log.warn("Access denied: user={} method={} uri={}", user, request.getMethod(), request.getRequestURI(), ex);

        if (isAjax(request)) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse("FORBIDDEN", "Доступ запрещён", detailsOf(request, null)));
        }

        ModelAndView mav = new ModelAndView("error/403");
        mav.setStatus(org.springframework.http.HttpStatus.FORBIDDEN);
        mav.addObject("path", request.getRequestURI());
        mav.addObject("backUrl", safeBackUrl(request));
        return mav;
    }

    @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public Object handleValidation(Exception ex, HttpServletRequest request) {
        List<String> errors = extractValidationErrors(ex);

        log.warn("Validation error: method={} uri={} errors={}",
                request.getMethod(), request.getRequestURI(), errors, ex);

        if (isAjax(request)) {
            Map<String, Object> details = detailsOf(request, Map.of("errors", errors));
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse("VALIDATION_ERROR", "Некорректные данные", details));
        }

        ModelAndView mav = new ModelAndView("error/400");
        mav.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
        mav.addObject("path", request.getRequestURI());
        mav.addObject("errors", errors);
        mav.addObject("backUrl", safeBackUrl(request));
        return mav;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method not allowed: method={} uri={} supported={}",
                request.getMethod(), request.getRequestURI(), ex.getSupportedMethods(), ex);

        List<String> supportedMethods = ex.getSupportedMethods() == null
                ? List.of()
                : List.of(ex.getSupportedMethods());

        if (isAjax(request)) {
            Map<String, Object> details = detailsOf(request, Map.of("supportedMethods", supportedMethods));
            return ResponseEntity.status(405)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse(
                            "METHOD_NOT_ALLOWED",
                            "Метод запроса не поддерживается",
                            details
                    ));
        }

        ModelAndView mav = new ModelAndView("error/405");
        mav.setStatus(org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED);
        mav.addObject("path", request.getRequestURI());
        mav.addObject("supportedMethods", supportedMethods);
        mav.addObject("backUrl", safeBackUrl(request));
        return mav;
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Object handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("Unsupported media type: method={} uri={} contentType={} supported={}",
                request.getMethod(), request.getRequestURI(), request.getContentType(), ex.getSupportedMediaTypes(), ex);

        List<String> supportedMediaTypes = ex.getSupportedMediaTypes() == null
                ? List.of()
                : ex.getSupportedMediaTypes().stream()
                .map(MediaType::toString)
                .toList();

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("receivedContentType", request.getContentType());
        extra.put("supportedMediaTypes", supportedMediaTypes);

        if (isAjax(request)) {
            return ResponseEntity.status(415)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse(
                            "UNSUPPORTED_MEDIA_TYPE",
                            "Неподдерживаемый тип содержимого",
                            detailsOf(request, extra)
                    ));
        }

        ModelAndView mav = new ModelAndView("error/415");
        mav.setStatus(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        mav.addObject("path", request.getRequestURI());
        mav.addObject("receivedContentType", request.getContentType());
        mav.addObject("supportedMediaTypes", supportedMediaTypes);
        mav.addObject("backUrl", safeBackUrl(request));
        return mav;
    }

    @ExceptionHandler(Exception.class)
    public Object handleAny(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error: method={} uri={} qs={}",
                request.getMethod(), request.getRequestURI(), request.getQueryString(), ex);

        if (isAjax(request)) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse("INTERNAL_ERROR", "Ошибка сервера", detailsOf(request, null)));
        }

        ModelAndView mav = new ModelAndView("error/500");
        mav.setStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        mav.addObject("path", request.getRequestURI());
        mav.addObject("backUrl", safeBackUrl(request));
        return mav;
    }

    private static boolean isAjax(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && (uri.equals("/api") || uri.startsWith("/api/"))) return true;

        String xrw = request.getHeader("X-Requested-With");
        if (xrw != null && xrw.equalsIgnoreCase("XMLHttpRequest")) return true;

        String accept = request.getHeader("Accept");
        if (accept != null && accept.toLowerCase().contains("application/json")) return true;

        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().contains("application/json");
    }

    private static String safeBackUrl(HttpServletRequest request) {
        String ref = request.getHeader("Referer");
        if (ref == null || ref.isBlank()) {
            return "/";
        }

        try {
            URI uri = URI.create(ref);

            if (uri.getScheme() == null && uri.getHost() == null) {
                return (ref.startsWith("/") && !ref.startsWith("//")) ? ref : "/";
            }

            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null) {
                return "/";
            }

            boolean sameScheme = request.getScheme().equalsIgnoreCase(scheme);
            boolean sameHost = request.getServerName().equalsIgnoreCase(host);

            int refPort = uri.getPort();
            if (refPort == -1) {
                refPort = "https".equalsIgnoreCase(scheme) ? 443 : 80;
            }

            int reqPort = request.getServerPort();
            boolean samePort = reqPort == refPort;

            if (!sameScheme || !sameHost || !samePort) {
                return "/";
            }

            String path = uri.getRawPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }

            String query = uri.getRawQuery();
            return (query == null || query.isBlank()) ? path : path + "?" + query;
        } catch (IllegalArgumentException ex) {
            return "/";
        }
    }

    private static Map<String, Object> detailsOf(HttpServletRequest request, Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("path", request.getRequestURI());
        details.put("method", request.getMethod());
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            details.put("query", request.getQueryString());
        }
        if (extra != null) details.putAll(extra);
        return details;
    }

    private static List<String> extractValidationErrors(Exception ex) {
        if (ex instanceof BindException be) {
            return be.getBindingResult().getAllErrors().stream()
                    .map(err -> (err instanceof FieldError fe)
                            ? fe.getField() + ": " + fe.getDefaultMessage()
                            : err.getDefaultMessage())
                    .collect(Collectors.toList());
        }

        if (ex instanceof MethodArgumentNotValidException manv) {
            return manv.getBindingResult().getAllErrors().stream()
                    .map(err -> (err instanceof FieldError fe)
                            ? fe.getField() + ": " + fe.getDefaultMessage()
                            : err.getDefaultMessage())
                    .collect(Collectors.toList());
        }

        if (ex instanceof ConstraintViolationException cve) {
            List<String> out = new ArrayList<>();
            cve.getConstraintViolations().forEach(v -> out.add(v.getPropertyPath() + ": " + v.getMessage()));
            return out;
        }

        return List.of("Некорректные данные");
    }

    /**
     * Этап 11.2 (P0): если multipart слишком большой/битый — вернуть на карточку с ошибкой.
     */
    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public String handleMultipartUpload(Exception ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Long plantId = extractPlantIdFromPhotoUploadUri(request.getRequestURI());

        String message = (ex instanceof MaxUploadSizeExceededException)
                ? "Файл слишком большой (до 10 МБ)"
                : "Некорректный файл";

        log.warn("Multipart upload error: method={} uri={} plantId={} message={}",
                request.getMethod(), request.getRequestURI(), plantId, message, ex);

        UserPlantPhotoUploadForm form = new UserPlantPhotoUploadForm();
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(form, "photoForm");
        br.addError(new FieldError("photoForm", "photo", message));

        redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.photoForm", br);
        redirectAttributes.addFlashAttribute("photoForm", form);

        if (plantId != null) return "redirect:/app/plants/" + plantId;
        return "redirect:/app/plants";
    }

    private static Long extractPlantIdFromPhotoUploadUri(String uri) {
        if (uri == null) return null;
        String prefix = "/app/plants/";
        String suffix = "/photos";
        if (!uri.startsWith(prefix) || !uri.endsWith(suffix)) return null;

        String middle = uri.substring(prefix.length(), uri.length() - suffix.length());
        if (middle.endsWith("/")) middle = middle.substring(0, middle.length() - 1);

        try {
            return Long.parseLong(middle);
        } catch (Exception ignored) {
            return null;
        }
    }
}