package com.yupi.yupao.util;

import com.yupi.yupao.model.domain.User;
import com.yupi.yupao.model.vo.UserVO;
import lombok.Data;
import org.springframework.beans.BeanUtils;

public class DomainToVOUtils {
    public static UserVO userToUserVO(User user){
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user,userVO);
        return userVO;
    }
}
