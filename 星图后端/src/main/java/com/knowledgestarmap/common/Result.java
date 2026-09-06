package com.knowledgestarmap.common;

import com.knowledgestarmap.enums.BizErrorCode;
import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    private Result() {}

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> fail(BizErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> Result<T> fail(BizErrorCode errorCode, String detail) {
        return fail(errorCode.getCode(), detail);
    }
}
