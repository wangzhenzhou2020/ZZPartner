package com.yupi.yupao.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * @author wangzhenzhou
 */
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = -3903316719160275851L;

    private String userAccount;


    private String userPassword;
}
