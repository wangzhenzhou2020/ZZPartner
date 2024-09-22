package com.yupi.yupao.common;


import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 用于给自定义异常传参数的。无description 和 data
 */
@Getter // 不用setter和构造函数
@NoArgsConstructor
public enum ErrorCode {

    SUCCESS(0,"OK",""),
    PARAMS_ERROR(40000, "请求参数错误", ""),
    NULL_ERROR(40001, "请求数据为空", ""), // 中间查询数据库的结果为空，导致逻辑问题。不用于返回空数据。
    NOT_LOGIN(40100, "未登录", ""),
    NO_AUTH(40101, "无权限", ""),
    FORBIDDEN(40301, "禁止操作", ""),
    SYSTEM_ERROR(50000, "系统内部异常", "");
    private int code;

    private String message;

    private String description;

    ErrorCode(int code,String message,String description){
        this.code = code;
        this.message = message;
        this.description = description;
    }

}
