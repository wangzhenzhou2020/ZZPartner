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

    // [加入编程导航](https://yupi.icu) 入门捷径+交流答疑+项目实战+求职指导，帮你自学编程不走弯路

        private String userPassword;
}
