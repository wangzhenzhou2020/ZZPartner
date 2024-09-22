package com.yupi.yupao.exception;


import com.yupi.yupao.common.ErrorCode;
import lombok.Getter;

/**
 * 会被全局异常处理器处理。有code、message、description
 */
@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    private final String description;

    /**
     * 构造函数都是抛异常的方法。
     *
     * @param code
     * @param description
     * @param message
     */
    public BusinessException(int code, String description, String message) {
        super(message);
        this.code = code;
        this.description = description;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.description = errorCode.getDescription();
    }

    public BusinessException(ErrorCode errorCode, String description) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.description = description;
    }


}
