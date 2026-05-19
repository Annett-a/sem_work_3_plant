package ru.itis.documents.exception;

public class PlantIdentificationNotFoundException extends RuntimeException {
    public PlantIdentificationNotFoundException(String message) {
        super(message);
    }
}