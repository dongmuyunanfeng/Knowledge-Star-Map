package com.knowledgestarmap.exception;

import com.knowledgestarmap.enums.BizErrorCode;
import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final Integer code;
    private final String message;

    public BizException(BizErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    public BizException(BizErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.code = errorCode.getCode();
        this.message = detailMessage;
    }
}
