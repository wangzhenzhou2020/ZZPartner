package com.yupi.yupao.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupao.common.BaseResponse;
import com.yupi.yupao.common.DeleteRequest;
import com.yupi.yupao.common.ErrorCode;
import com.yupi.yupao.common.ResultUtils;
import com.yupi.yupao.exception.BusinessException;
import com.yupi.yupao.model.domain.User;
import com.yupi.yupao.model.request.UserLoginRequest;
import com.yupi.yupao.model.request.UserRegisterRequest;
import com.yupi.yupao.model.request.UserUpdateRequest;
import com.yupi.yupao.model.vo.UserVO;
import com.yupi.yupao.service.UserService;
import com.yupi.yupao.util.DomainToVOUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.stream.Collectors;

import static com.yupi.yupao.constant.UserConstant.USER_LOGIN_STATE;

/**
 * @author wangzhenzhou
 */
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {
    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate redisTemplate;

    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long userId = userService.userRegister(userRegisterRequest);
        return ResultUtils.success(userId);

    }

    @PostMapping("/login")
    public BaseResponse<UserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null) {
//            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();

        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
//            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        UserVO userVO = userService.userLogin(userAccount, userPassword, request);

        return ResultUtils.success(userVO);
    }

    /**
     * 用户登出
     *
     * @param request
     * @return
     */
    @GetMapping("/logout")
    public BaseResponse<Integer> userLogout(HttpServletRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        int result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 获取当前用户
     *
     * @param request
     * @return
     */
    @GetMapping("/current")
    public BaseResponse<UserVO> getCurrentUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        UserVO currentUser = (UserVO) userObj;

        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        long userId = currentUser.getId();

        // TODO 校验用户是否合法
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "用户不存在");
        }

        return ResultUtils.success(currentUser);
    }

    /**
     * 获取用户信息列表
     * @param username
     * @param request
     * @return
     */
    @GetMapping("/search")
    public BaseResponse<List<UserVO>> searchUsers(String username, HttpServletRequest request) {
        UserVO loginUser = this.userService.getLoginUser(request);
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(username)) {
            queryWrapper.like("username", username);
        }
        List<User> userList = userService.list(queryWrapper);
        List<UserVO> list = userList.stream().map(DomainToVOUtils::userToUserVO).collect(Collectors.toList());
        return ResultUtils.success(list);
    }

    /**
     * 获取用户by标签列表
     * @param tagNameList
     * @return
     */
    @GetMapping("/search/tags")
    public BaseResponse<List<UserVO>> searchUsersByTags(@RequestParam(required = false) List<String> tagNameList) {
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        List<UserVO> userList = userService.searchUsersByTags(tagNameList);
        return ResultUtils.success(userList);
    }



//    /**
//     * 获取用户信息列表
//     * @param username
//     * @param request
//     * @return
//     */
//    @GetMapping("/list/username")
//    public BaseResponse<List<UserVO>> listByUsername(String username, HttpServletRequest request) {
//        UserVO currentUser = userService.getLoginUser(request);
//        if (currentUser == null) {
//            throw new BusinessException(ErrorCode.NOT_LOGIN);
//        }
//        if (!userService.isAdmin(currentUser)) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR);
//        }
//        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
//        if (StringUtils.isNotBlank(username)) {
//            queryWrapper.like("username", username);
//        }
//        List<User> userList = userService.list(queryWrapper);
//        List<UserVO> list = userList.stream().map(DomainToVOUtils::userToUserVO)
//                .collect(Collectors.toList());
//        return ResultUtils.success(list);
//    }

//    /**
//     * 获取用户by标签列表。非常耗时，因为要读取全表。
//     * @param tagNameList
//     * @return
//     */
//    @GetMapping("/list/tags")
//    public BaseResponse<List<UserVO>> listByTags(@RequestParam(required = false) List<String> tagNameList) {
//        if (CollectionUtils.isEmpty(tagNameList)) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR);
//        }
//        List<UserVO> userList = userService.searchUsersByTags(tagNameList);
//        return ResultUtils.success(userList);
//    }

    /**
     * 删除用户
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        UserVO loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        Long id = deleteRequest.getId();
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(id);
        return ResultUtils.success(b);
    }


    @PostMapping("/update")
    public BaseResponse<Integer> updateUser(@RequestBody UserUpdateRequest userUpdateRequest, HttpServletRequest request) {
        // 校验参数是否为空
        if (userUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        int result = userService.updateUser(userUpdateRequest, request);
        return ResultUtils.success(result);
    }

    /**
     * 普通模式，直接返回分页结果。
     * @param pageSize
     * @param pageNum
     * @param request
     * @return
     */
    // todo 推荐多个，未实现。这里recommend 不应该带page、pageNum参数（应该封装到一个类里），page、pageNum相关方法应该放在service中。
    @GetMapping("/recommend")
    public BaseResponse<Page<UserVO>> recommendUsers(long pageSize, long pageNum, HttpServletRequest request) {
        Page<UserVO> userVOPage = userService.recommendUsers(pageSize, pageNum, request);
        return ResultUtils.success(userVOPage);
    }

    /**
     * 心动模式。获取最匹配的用户。
     * @param num
     * @param request
     * @return
     */
    @GetMapping("/match")
    public BaseResponse<List<UserVO>> matchUsers(long num, HttpServletRequest request) {
        if (num <= 0 || num > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserVO userVO = userService.getLoginUser(request);
        return ResultUtils.success(userService.matchUsers(num, userVO));
    }
}
