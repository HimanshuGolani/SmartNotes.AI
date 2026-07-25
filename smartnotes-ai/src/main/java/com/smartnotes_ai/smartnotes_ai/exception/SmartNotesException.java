package com.smartnotes_ai.smartnotes_ai.exception;

import lombok.Getter;

@Getter
public class SmartNotesException extends RuntimeException {

    private final ErrorCode code;

    public SmartNotesException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public SmartNotesException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
