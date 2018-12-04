package ru.kbakaras.e2.jpa;

public class E2SerializationException extends RuntimeException {
    public E2SerializationException(Throwable cause) {
        super(cause);
    }

    public E2SerializationException(String message) {
        super(message);
    }
}