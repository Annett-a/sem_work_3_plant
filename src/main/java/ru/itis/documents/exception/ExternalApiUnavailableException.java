package ru.itis.documents.exception;

public class ExternalApiUnavailableException extends RuntimeException {

    private final String service;
    private final Integer statusCode;
    private final String userMessage;

    public ExternalApiUnavailableException(String service, String userMessage, Integer statusCode, Throwable cause) {
        super(userMessage, cause);
        this.service = service;
        this.statusCode = statusCode;
        this.userMessage = userMessage;
    }

    public ExternalApiUnavailableException(String service, String userMessage, Integer statusCode) {
        this(service, userMessage, statusCode, null);
    }

    public String getService() {
        return service;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getUserMessage() {
        return userMessage;
    }
}