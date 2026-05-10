package com.winter.airesumeoptimizer.common.exception;

import com.winter.airesumeoptimizer.common.result.ResultCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;
    private final ResultCode resultCode;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.resultCode = null;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
        this.resultCode = resultCode;
    }
}
