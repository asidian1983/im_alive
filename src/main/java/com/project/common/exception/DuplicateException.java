package com.project.common.exception;

public class DuplicateException extends BusinessException {

    public DuplicateException(String message) {
        super(409, message);
    }
}
