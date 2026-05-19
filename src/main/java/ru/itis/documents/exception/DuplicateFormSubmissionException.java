package ru.itis.documents.exception;

public class DuplicateFormSubmissionException extends RuntimeException {

    public DuplicateFormSubmissionException() {
        super("Форма уже была отправлена или устарела");
    }
}