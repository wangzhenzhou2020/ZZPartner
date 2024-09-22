package com.yupi.yupao.model.enums;

import lombok.Getter;

import java.io.Serializable;

/**
 * teamList的操作
 * @author wangzhenzhou
 */
@Getter
public enum TeamListOptionBak implements Serializable {
    RECOMMEND(0,"推荐"),
    INDIVIDUAL(1,"个人");

    private final int value;
    private final String message;
    TeamListOptionBak(int value,String message){
        this.value = value;
        this.message = message;
    }

}
