package com.yupi.yupao.common;

import lombok.Data;

import java.io.Serializable;

/**
 * @author wangzhenzhou
 */
@Data
public class DeleteRequest implements Serializable {

    private static final long serialVersionUID = 524583683696402859L;
    private Long id;
}
