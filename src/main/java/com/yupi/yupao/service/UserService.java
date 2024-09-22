package com.yupi.yupao.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupao.model.domain.User;
import com.yupi.yupao.model.request.UserRegisterRequest;
import com.yupi.yupao.model.request.UserUpdateRequest;
import com.yupi.yupao.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author wangzhenzhou
* @description 针对表【user(用户表)】的数据库操作Service
* @createDate 2024-08-18 22:14:03
*/
public interface UserService extends IService<User> {

    long userRegister(UserRegisterRequest userRegisterRequest);

    UserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    int userLogout(HttpServletRequest request);

    boolean isAdmin(UserVO userVO);

//    UserVO getCurrentUser(HttpServletRequest request);

    List<UserVO> searchUsersByTags(List<String> tagNameList);

    UserVO getLoginUser(HttpServletRequest request);

    int updateUser(UserUpdateRequest userUpdateRequest, HttpServletRequest request);

    Page<UserVO> recommendUsers(long pageSize, long pageNum, HttpServletRequest request);

    List<UserVO> matchUsers(long num, UserVO loginUser);
}
