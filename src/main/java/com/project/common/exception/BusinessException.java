package com.project.common.exception;

public abstract class BusinessException extends RuntimeException {

    private final int status;

    protected BusinessException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
