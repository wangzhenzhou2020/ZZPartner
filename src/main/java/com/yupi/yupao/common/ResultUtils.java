package com.yupi.yupao.common;

import org.apache.poi.ss.formula.functions.T;

/**
 * 为了一眼看出返回的是error，还是success。我们不直接return new BaseResponse. 总之要传入BaseResponse的属性。
 */
public class ResultUtils {
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, data, "ok");
    }

    // error 的data 是null，不会是[]。
    /**
     * 如果异常中无code属性
     *
     * @param errorCode
     * @param description
     * @return
     */
    public static BaseResponse error(ErrorCode errorCode, String message, String description) {
        return new BaseResponse(errorCode.getCode(), null, errorCode.getMessage(), description);
    }

    /**
     * 如果异常中有code属性
     * @param code
     * @param message
     * @param description
     * @return
     */
    public static BaseResponse error(int code, String message, String description) {
        return new BaseResponse(code, null, message, description);
    }




}
