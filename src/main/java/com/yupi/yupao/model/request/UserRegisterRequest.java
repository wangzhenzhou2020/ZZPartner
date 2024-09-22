package com.yupi.yupao.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * @author wangzhenzhou
 */
@Data
public class UserRegisterRequest implements Serializable {
    private static final long serialVersionUID = 7575052520981104179L;
    private String userAccount;

    private String userPassword;

    private String checkPassword;

    private String planetCode;
}
