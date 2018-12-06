package ru.kbakaras.e2.jpa;

/**
 * project: e2-jpa
 * author:  kostrovik
 * date:    2018-12-06
 * github:  https://github.com/kostrovik/e2-jpa
 */
public class E2DeserializationException extends RuntimeException {
    public E2DeserializationException(Throwable cause) {
        super(cause);
    }

    public E2DeserializationException(String message) {
        super(message);
    }
}