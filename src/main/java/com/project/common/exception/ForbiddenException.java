package com.project.common.exception;

public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super(403, "Access denied");
    }
}
