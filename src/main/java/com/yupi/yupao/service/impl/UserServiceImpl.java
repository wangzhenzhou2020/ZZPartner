package com.yupi.yupao.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yupi.yupao.common.ErrorCode;
import com.yupi.yupao.constant.UserConstant;
import com.yupi.yupao.exception.BusinessException;
import com.yupi.yupao.model.request.UserRegisterRequest;
import com.yupi.yupao.model.request.UserUpdateRequest;
import com.yupi.yupao.model.vo.UserVO;
import com.yupi.yupao.service.UserService;
import com.yupi.yupao.model.domain.User;
import com.yupi.yupao.mapper.UserMapper;
import com.yupi.yupao.util.AlgorithmUtils;
import com.yupi.yupao.util.DomainToVOUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.yupi.yupao.constant.UserConstant.USER_LOGIN_STATE;

/**
 * @author wangzhenzhou
 * @description 针对表【user(用户表)】的数据库操作Service实现
 * @createDate 2024-08-18 22:14:03
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    private static final String SALT = "yupi";
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 用户注册
     *
     * @param userRegisterRequest
     * @return
     */
    @Override
    public long userRegister(UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        String planetCode = userRegisterRequest.getPlanetCode();
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword, planetCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (planetCode.length() > 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "星球编号过长");
        }
        // 账户不能包含特殊字符
        String validPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账户不能包含特殊字符");
        }
        // 密码和校验密码得相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码和校验密码不一致");
        }

        // 是否已经存在该用户
        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        userQueryWrapper.eq("userAccount", userAccount);
        long userAccountCount = this.count(userQueryWrapper);
        if (userAccountCount > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户已存在");
        }
        userQueryWrapper.clear();
        // 星球编号不能重复
        userQueryWrapper.eq("planetCode", planetCode);
        long planetCodeCount = this.count(userQueryWrapper);
        if (planetCodeCount > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "编号重复");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());

        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setPlanetCode(planetCode);

        boolean result = this.save(user);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "用户注册失败");
        }

        // 存在则校验参数
        return user.getId();
    }

    @Override
    public UserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账户名过短");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码过短");
        }
        // 账户不能包含特殊字符
        String validPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账户名不得包含特殊字符");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.getOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            return null;
        }
        // 3. 用户脱敏
//        User safetyUser = getSafetyUser(user);
        UserVO userVO = DomainToVOUtils.userToUserVO(user);
        // 4. 记录用户的登录态
//        request.getSession().setAttribute(USER_LOGIN_STATE, safetyUser);
        request.getSession().setAttribute(USER_LOGIN_STATE, userVO);
//        return safetyUser;
        return userVO;
    }

    @Override
    public int userLogout(HttpServletRequest request) {
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return 1;
    }


    /**
     * @param userVO
     * @return
     */
    @Override
    public boolean isAdmin(UserVO userVO) {
        // 仅管理员可查询
        return userVO != null && userVO.getUserRole() == UserConstant.ADMIN_ROLE;
    }

    /**
     * 根据标签搜索用户，因为这里采取的是精确搜索，全表搜索费时。如果可以运行表数据不是最新的，那么可以使用缓存。而不是list全表
     * @param tagNameList
     * @return
     */
    @Override
    public List<UserVO> searchUsersByTags(List<String> tagNameList) {
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 1. 先查询所有用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        List<User> userList = this.list(queryWrapper); // todo 全表扫描耗时大 100w数据要10s


        Gson gson = new Gson();
        // 2. 在内存中判断是否包含要求的标签
        List<UserVO> userVOList = userList.stream().filter(user -> {
            String tagsStr = user.getTags();
            Set<String> tempTagNameSet = gson.fromJson(tagsStr, new TypeToken<Set<String>>() {
            }.getType());
            tempTagNameSet = Optional.ofNullable(tempTagNameSet).orElseGet(HashSet::new);
            // set 包含 tagNameList中的标签
            for (String tagName : tagNameList) {
                if (!tempTagNameSet.contains(tagName)) {
                    return false;
                }
            }
            return true;
        }).map(DomainToVOUtils::userToUserVO).collect(Collectors.toList());
        return userVOList;
    }

    @Override
    public UserVO getLoginUser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        return (UserVO) userObj;
    }

    @Override
    public int updateUser(UserUpdateRequest userUpdateRequest, HttpServletRequest request) {
        // 如果是管理员，允许更新任意用户
        // 如果不是管理员，只允许更新当前（自己的）信息
        UserVO loginUser = this.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long userId = userUpdateRequest.getId();
        if (!isAdmin(loginUser) && userId != loginUser.getId()) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        if (userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 补充校验，如果用户没有传任何要更新的值，就直接报错，不用执行 update 语句
        if (AlgorithmUtils.isOtherFieldsNull(userUpdateRequest, "id")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 查询用户是否存在
        User oldUser = this.getById(userId);
        if (oldUser == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR,"用户不存在");
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        return userMapper.updateById(user);
    }

    /**
     * 没有做匹配推荐，只是分页效果。首页一定要快，所以用缓存。另外也只是page，没有list，不是很慢。
     *
     * @param pageSize
     * @param pageNum
     * @param request
     * @return
     */
    @Override
    public Page<UserVO> recommendUsers(long pageSize, long pageNum, HttpServletRequest request) {
        UserVO loginUser = this.getLoginUser(request);
        String redisKey = String.format("yupao:user:recommend:%s", loginUser.getId());
        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
        // 如果有缓存，直接读缓存
        Page<UserVO> userPage = (Page<UserVO>) valueOperations.get(redisKey);

        if (userPage != null) {
            return userPage;
        }
        // 无缓存，查数据库
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        Page<User> newUserPage = this.page(new Page<>(pageNum, pageSize), queryWrapper);
        // 新的缓存数据
        Page<UserVO> userVOPage = new Page<>();
        BeanUtils.copyProperties(newUserPage, userVOPage);
        List<UserVO> userVOList = newUserPage.getRecords().stream()
                .map(DomainToVOUtils::userToUserVO).collect(Collectors.toList());
        userVOPage.setRecords(userVOList);
        // 写缓存
        try {
            valueOperations.set(redisKey, userVOPage, 300000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("redis set key error", e);
        }
        return userVOPage;
    }

    /**
     * 心动模式，用户匹配
     * @param num
     * @param loginUser
     * @return
     */
    @Override
    public List<UserVO> matchUsers(long num, UserVO loginUser) {
        String redisKey = String.format("yupao:user:match:%s", loginUser.getId());
        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
        // 如果有缓存，直接读缓存
        List<UserVO> matchUserVOListCache = (List<UserVO>) valueOperations.get(redisKey);
        if (CollectionUtils.isNotEmpty(matchUserVOListCache)){
            return matchUserVOListCache;
        }

        // 查询数据库待匹配待可能用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "tags"); // 减少内存占用，提速数据库读取。
        queryWrapper.isNotNull("tags");
        List<User> mayUserList = this.list(queryWrapper);
        // 获得当前用户的tagList
        String tags = loginUser.getTags();
        Gson gson = new Gson();
        List<String> currentUserTagList = gson.fromJson(tags, new TypeToken<List<String>>() {
        }.getType());


        // 优先队列（不关系是否有序，关心topN）大顶堆
        PriorityQueue<Pair<Long, User>> priorityQueue = new PriorityQueue<>(
                (a, b) -> -(int) (a.getKey() - b.getKey())
        );
        // 依次计算所有用户和当前用户的相似度
        for (User user : mayUserList) {
            String userTags = user.getTags();
            // 无标签或者为当前用户自己
            if (StringUtils.isBlank(userTags) || Objects.equals(user.getId(), loginUser.getId())) {
                continue;
            }
            List<String> userTagList = gson.fromJson(userTags, new TypeToken<List<String>>() {
            }.getType());
            // 计算分数
            long distance = AlgorithmUtils.minDistance(currentUserTagList, userTagList);
            // 加入队列
            priorityQueue.add(new Pair<>(distance, user));
            // 如果超过num，移除
            if (priorityQueue.size() > num) {
                priorityQueue.poll();
            }
        }

        List<Long> idList = priorityQueue.stream()
                .map(pair -> pair.getValue().getId()).collect(Collectors.toList());
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.in("id", idList);
        List<User> matchUserList = this.list(wrapper);
        List<UserVO> matchUserVOList = matchUserList.stream()
                .map(DomainToVOUtils::userToUserVO).collect(Collectors.toList());
        valueOperations.set(redisKey,matchUserList,300000,TimeUnit.MILLISECONDS);

        return matchUserVOList;
    }
}




