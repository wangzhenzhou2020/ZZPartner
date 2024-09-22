package com.yupi.yupao.model.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author wangzhenzhou
 */
@Data
public class UserVO implements Serializable {
    private static final long serialVersionUID = 9102990059570206005L;
    // id
    private Long id;
    // 昵称
    private String username;
    // 用户名
    private String userAccount;
    // 用户头像
    private String avatarUrl;
    // 性别
    private Integer gender;
    // 电话
    private String phone;
    // 邮箱
    private String email;
    // 用户状态 0 - 正常
    private Integer userStatus;
    // 创建时间
    private Date createTime;
    // 更新时间
    private Date updateTime;
    // 角色 0 - 普通用户 1 - 管理员
    private Integer userRole;
    // 星球编号
    private String planetCode;
    // 标签列表
    private String tags;
}
