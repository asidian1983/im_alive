package com.project.common.exception;

public class NotFoundException extends BusinessException {

    public NotFoundException(String resource) {
        super(404, resource + " not found");
    }
}
