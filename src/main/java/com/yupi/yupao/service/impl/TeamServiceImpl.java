package com.yupi.yupao.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupao.common.ErrorCode;
import com.yupi.yupao.exception.BusinessException;
import com.yupi.yupao.mapper.TeamMapper;
import com.yupi.yupao.model.domain.Team;
import com.yupi.yupao.model.domain.User;
import com.yupi.yupao.model.domain.UserTeam;
import com.yupi.yupao.model.enums.TeamStatusEnum;
import com.yupi.yupao.model.request.TeamJoinRequest;
import com.yupi.yupao.model.request.TeamQuery;
import com.yupi.yupao.model.request.TeamQuitRequest;
import com.yupi.yupao.model.request.TeamUpdateRequest;
import com.yupi.yupao.model.vo.TeamUserVO;
import com.yupi.yupao.model.vo.UserVO;
import com.yupi.yupao.service.TeamService;
import com.yupi.yupao.service.UserService;
import com.yupi.yupao.service.UserTeamService;
import com.yupi.yupao.util.AlgorithmUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author wangzhenzhou
 * @description 针对表【team(队伍)】的数据库操作Service实现
 * @createDate 2024-08-18 22:13:45
 */
@Service
@Slf4j
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team>
        implements TeamService {
    @Autowired
    private UserTeamService userTeamService;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private UserService userService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private ThreadPoolExecutor threadPoolExecutor =
            new ThreadPoolExecutor(10,
                    30, 100,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(1000));
    // region 增删改查

    /**
     * 添加队伍
     *
     * @param team
     * @param loginUser
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addTeam(Team team, UserVO loginUser) {
        // 参数校验
        checkParams(team, loginUser);
        // 8. 插入队伍信息到队伍表
        Long userId = loginUser.getId();
        team.setId(null);
        team.setUserId(userId);
        boolean result = this.save(team);
        Long teamId = team.getId();
        if (!result || teamId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建队伍失败");
        }
        // 9. 插入用户  => 队伍关系到关系表
        UserTeam userTeam = new UserTeam();
        userTeam.setUserId(userId);
        userTeam.setTeamId(teamId);
        userTeam.setJoinTime(new Date());
        result = userTeamService.save(userTeam);
        if (!result) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建队伍失败");
        }
        return teamId;
    }

    @Override
    public boolean updateTeam(TeamUpdateRequest teamUpdateRequest, UserVO loginUser) {
        if (teamUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long id = teamUpdateRequest.getId();
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team oldTeam = this.getById(id);
        if (oldTeam == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "队伍不存在");
        }
        // 只有管理员或者队伍的创建者可以修改
        if (!Objects.equals(oldTeam.getUserId(), loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(teamUpdateRequest.getStatus());
        if (statusEnum.equals(TeamStatusEnum.SECRET)) {
            if (StringUtils.isBlank(teamUpdateRequest.getPassword())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "加密房间必须要设置密码");
            }
        }
        Team updateTeam = new Team();
        BeanUtils.copyProperties(teamUpdateRequest, updateTeam);
        if (AlgorithmUtils.isOtherFieldsNull(updateTeam, "id")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return this.updateById(updateTeam);
    }

    private Team getTeamById(Long teamId) {
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = this.getById(teamId);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "队伍不存在");
        }
        return team;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTeam(Long id, UserVO loginUser) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 校验队伍是否存在
        Team team = this.getTeamById(id);
        // 校验你是不是队伍的队长
        if (!Objects.equals(team.getUserId(), loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无访问权限");
        }
        // 移除所有加入队伍的关联信息
        long teamId = team.getId();
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("teamId", teamId);
        boolean result = userTeamService.remove(userTeamQueryWrapper);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除队伍关联信息失败");
        }
        result = this.removeById(teamId);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除队伍信息失败");
        }
        // 删除队伍
        return true;
    }

    // endregion

//    /**
//     * 查询TeamList
//     *
//     * @param teamQuery
//     * @param isAdminOrCreator
//     * @param teamListOption
//     * @return
//     */
//    @Override
//    public List<TeamUserVO> listTeams(TeamQuery teamQuery, boolean isAdminOrCreator, TeamListOption teamListOption) {
//        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
//        if (teamQuery != null) {
//            // id
//            Long id = teamQuery.getId();
//            queryWrapper.eq(id != null && id > 0, "id", id);
//            // idList
//            List<Long> idList = teamQuery.getIdList();
//            queryWrapper.in(CollectionUtils.isNotEmpty(idList), "id", idList);
//            // searchText
//            String searchText = teamQuery.getSearchText();
//            queryWrapper.and(StringUtils.isNotBlank(searchText), qw -> qw.like("name", searchText).or().
//                    like("description", searchText));
//            // name
//            String name = teamQuery.getName();
//            queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
//            // description
//            String description = teamQuery.getDescription();
//            queryWrapper.like(StringUtils.isNotBlank(description), "description", description);
//            // 查询最大人数相等的
//            Integer maxNum = teamQuery.getMaxNum();
//            queryWrapper.eq(maxNum != null && maxNum > 0, "maxNum", maxNum);
//            Long userId = teamQuery.getUserId();
//            // 根据创建人来查询
//            queryWrapper.eq(userId != null && userId > 0, "userId", userId);
//
//
//            // 前端并没有提供查看私有队伍功能。如果个人用户想查看自己加入的队伍，放在其他接口了。
//            // 首页队伍公开页面传递的 ，我加入的队伍，status=null，statusEnum=PUBLIC
//            Integer status = teamQuery.getStatus();
//            TeamStatusEnum statusEnum = Optional.ofNullable(TeamStatusEnum.getEnumByValue(status))
//                    .orElse(TeamStatusEnum.PUBLIC);
//            if (!isAdminOrCreator && statusEnum.equals(TeamStatusEnum.PRIVATE)) {
//                throw new BusinessException(ErrorCode.NO_AUTH);
//            }
//            queryWrapper.eq("status", statusEnum.getValue());
//
//
//        }
//
//        // teamQuery为空则 仅查询所有 未过期的队伍。 expireTime is null or expireTime > now()
//        // 如果是首页推荐， 仅查询所有 未过期的队伍
//        if (teamListOption == TeamListOption.RECOMMEND) {
//            queryWrapper.and(qw -> qw.gt("expireTime", new Date()).or().isNull("expireTime"));
//        } else if (teamListOption == TeamListOption.INDIVIDUAL) { // 我加入的为INDIVIDUAL
//
//        }
//
//        // 如果是与个人相关的队伍查询，则全部显示。
//
//        List<Team> teamList = this.list(queryWrapper);
//        if (CollectionUtils.isEmpty(teamList)) {
//            return new ArrayList<>();
//        }
//        List<TeamUserVO> teamUserVOList = new ArrayList<>();
//        // 关联查询创建人的用户信息
//        for (Team team : teamList) {
//            Long userId = team.getUserId();
//            if (userId == null) {
//                continue;
//            }
//            User user = userService.getById(userId);
//            TeamUserVO teamUserVO = new TeamUserVO();
//            BeanUtils.copyProperties(team, teamUserVO);
//            // 脱敏用户信息
//            if (user != null) {
//                UserVO userVO = new UserVO();
//                BeanUtils.copyProperties(user, userVO);
//                teamUserVO.setCreateUser(userVO);
//            }
//            teamUserVOList.add(teamUserVO);
//        }
//        return teamUserVOList;
//    }


    /**
     * 查询TeamList
     *
     * @param teamQuery
     * @param isAdminOrCreator
     * @return
     */
    @Override
    public List<TeamUserVO> listTeams(TeamQuery teamQuery, boolean isAdminOrCreator) {
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        if (teamQuery != null) {
            // id
            Long id = teamQuery.getId();
            queryWrapper.eq(id != null && id > 0, "id", id);
            // idList
            List<Long> idList = teamQuery.getIdList();
            queryWrapper.in(CollectionUtils.isNotEmpty(idList), "id", idList);
            // searchText
            String searchText = teamQuery.getSearchText();
            queryWrapper.and(StringUtils.isNotBlank(searchText), qw -> qw.like("name", searchText).or().
                    like("description", searchText));
            // name
            String name = teamQuery.getName();
            queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
            // description
            String description = teamQuery.getDescription();
            queryWrapper.like(StringUtils.isNotBlank(description), "description", description);
            // 查询最大人数相等的
            Integer maxNum = teamQuery.getMaxNum();
            queryWrapper.eq(maxNum != null && maxNum > 0, "maxNum", maxNum);
            Long userId = teamQuery.getUserId();
            // 根据创建人来查询
            queryWrapper.eq(userId != null && userId > 0, "userId", userId);


            // 前端并没有提供查看私有队伍功能。如果个人用户想查看自己加入的队伍，放在其他接口了。
            // 首页队伍公开页面传递的 ，我加入的队伍，status=null，statusEnum=PUBLIC
            Integer status = teamQuery.getStatus();
            if (status != null) {
                queryWrapper.eq("status", status);
                queryWrapper.and(qw -> qw.gt("expireTime", new Date()).or().isNull("expireTime"));
            } else {//如果是与个人相关的队伍查询，则全部显示。

            }
        }

        List<Team> teamList = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(teamList)) {
            return new ArrayList<>();
        }
        List<TeamUserVO> teamUserVOList = new ArrayList<>();
        // 关联查询创建人的用户信息
        for (Team team : teamList) {
            Long userId = team.getUserId();
            if (userId == null) {
                continue;
            }
            User user = userService.getById(userId);
            TeamUserVO teamUserVO = new TeamUserVO();
            BeanUtils.copyProperties(team, teamUserVO);
            // 脱敏用户信息
            if (user != null) {
                UserVO userVO = new UserVO();
                BeanUtils.copyProperties(user, userVO);
                teamUserVO.setCreateUser(userVO);
            }
            teamUserVOList.add(teamUserVO);
        }
        return teamUserVOList;
    }

    @Override
//    @Transactional(rollbackFor = Exception.class)
    public boolean joinTeam(TeamJoinRequest teamJoinRequest, UserVO loginUser) {
        if (teamJoinRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        Long teamId = teamJoinRequest.getTeamId();
        Team team = this.getTeamById(teamId);
        Date expireTime = team.getExpireTime();
        if (expireTime != null && expireTime.before(new Date())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已过期");
        }
        TeamStatusEnum teamStatusEnum = TeamStatusEnum.getEnumByValue(team.getStatus());
//        if (TeamStatusEnum.PRIVATE.equals(teamStatusEnum)) {   // zz，9.12
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "禁止加入私有队伍");
//        }
        String password = teamJoinRequest.getPassword();
        if (TeamStatusEnum.SECRET.equals(teamStatusEnum)) {
            if (StringUtils.isBlank(password) || !password.equals(team.getPassword())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
            }
        }
        // 不能重复加入已加入的队伍
        long userId = loginUser.getId();
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();

        userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("userId", userId);
        userTeamQueryWrapper.eq("teamId", teamId);
        long hasUserJoinTeam = userTeamService.count(userTeamQueryWrapper);
        if (hasUserJoinTeam > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户已加入该队伍");
        }


        RReadWriteLock ss = redissonClient.getReadWriteLock("ss");
        RLock readLock = ss.readLock();
        RLock writeLock = ss.writeLock();

        // 只有一个线程能获取到锁 。用户不能同时多次加入一个队伍。且一个队伍不能超过maxNum个人。

        // todo 这里应该限流，不允许同一个用户同时加入多个不同的队伍。因为用双锁有可能发生死锁，所以如果不对用户id上锁，可以对用户id限流。但不是万全之法。

//        // by zz，一个锁
//        RLock teamLock = redissonClient.getLock(String.format("yupao:join_team:teamId:{%s}", teamId));
//        // 这样可以防不同用户同时 加入同一个队伍。但防不了一个用户同时加入不同队伍
//        try {
//            // 抢到锁并执行,10s中都抢不到锁就放弃了，加入队伍失败。
//            if (teamLock.tryLock(10000, -1, TimeUnit.MILLISECONDS)) {
//                System.out.println("getTeamLock: " + Thread.currentThread().getId());
//
////                    long j=0;   // 模拟cpu延时
////                    for (long i = 0; i < 20000000000L; i++) {
////                        j=2*i;
////                    }
////                    log.info("\n"+"after sleep: " + j );
//
//                // 两次查询可以开线程然后收集结果，好使的。但优化效果好像不大
////                CompletableFuture<Long> futureCountTeamUserByTeamId= CompletableFuture.supplyAsync(() -> {
////                    // 已加入队伍的人数
////                    return (Long) this.countTeamUserByTeamId(teamId);
////                },threadPoolExecutor);
////                CompletableFuture<Long> futureHasJoinNum = CompletableFuture.supplyAsync(() -> {
////                    // 该用户已加入的队伍数量
////                    QueryWrapper<UserTeam> userTeamQueryWrapper2 = new QueryWrapper<>();
////                    userTeamQueryWrapper2.eq("userId", userId);
////                    Long hasJoinNum = userTeamService.count(userTeamQueryWrapper2);
////                    return hasJoinNum;
////                }, threadPoolExecutor);
////
////                CompletableFuture<Void> combinedFuture =
////                        CompletableFuture.allOf(futureCountTeamUserByTeamId, futureHasJoinNum);
////                combinedFuture.join();
////                try {
////                    // 通过 get() 获取两个任务的结果
////                    Long teamHasJoinNum = futureCountTeamUserByTeamId.get();
////                    if (teamHasJoinNum >= team.getMaxNum()) {
////                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已满");
////                    }
////                    Long hasJoinNum = futureHasJoinNum.get();
////                    if (hasJoinNum >= 5) {
////                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多创建和加入 5 个队伍");
////                    }
////                    // 在两个结果都返回后处理逻辑
////                    log.info("队伍人数: " + teamHasJoinNum);
////                    log.info("用户已加入的队伍数量: " + hasJoinNum);
////                } catch (InterruptedException | ExecutionException e) {
////                    e.printStackTrace();
////                }
//
//
//                // 已加入队伍的人数
//                long teamHasJoinNum = this.countTeamUserByTeamId(teamId);
//                if (teamHasJoinNum >= team.getMaxNum()) {
//                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已满");
//                }
//                // 该用户已加入的队伍数量
//                userTeamQueryWrapper = new QueryWrapper<>();
//                userTeamQueryWrapper.eq("userId", userId);
//                long hasJoinNum = userTeamService.count(userTeamQueryWrapper);
//                log.info("\n" + "before hasJoinNum: " + hasJoinNum);
//                if (hasJoinNum >= 5) {
//                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多创建和加入 5 个队伍");
//                }
//
//                // 修改队伍信息
//                UserTeam userTeam = new UserTeam();
//                userTeam.setUserId(userId);
//                userTeam.setTeamId(teamId);
//                userTeam.setJoinTime(new Date());
//                boolean save = userTeamService.save(userTeam);
////                    hasJoinNum = userTeamService.count(userTeamQueryWrapper);
////                    log.info("\n"+"after hasJoinNum: "+ hasJoinNum);
//                return save;
////                }
//
//            } else {
//                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "加入队伍失败");
//            }
//        } catch (InterruptedException e) {
//            log.error("doCacheRecommendUser error", e);
//            return false;
//        } finally {
//            // 即保证必须加锁成功，才能释放锁。防止出现没加锁但释放锁的代码。同时只能释放自己的锁，稳健。
//            if (teamLock.isHeldByCurrentThread()) {
//                System.out.println("unTeamLock: " + Thread.currentThread().getId());
//                teamLock.unlock();
//            }
//        }

        // by zz，如果用了两个锁，那么必须保证这两个锁的获取和释放在其他地方也是一样的。
        RLock teamLock = redissonClient.getLock(String.format("yupao:join_team:teamId:{%s}", teamId));
        RLock userLock = redissonClient.getLock(String.format("yupao:join_team:userId:{%s}", userId));
        try {
            // 抢到team锁并执行,10s中都抢不到锁就放弃了，加入队伍失败。
            if (teamLock.tryLock(10000, -1, TimeUnit.MILLISECONDS)) {
                System.out.println("getTeamLock: " + Thread.currentThread().getId());


                try {
                    //  抢user锁
                    if (userLock.tryLock(10000, -1, TimeUnit.MILLISECONDS)) {
                        System.out.println("getUserLock: " + Thread.currentThread().getId());

//                    long j=0;   // 模拟cpu延时
//                    for (long i = 0; i < 20000000000L; i++) {
//                        j=2*i;
//                    }
//                    log.info("\n"+"after sleep: " + j );

                        // 两次查询可以开线程然后收集结果，好使的。但优化效果好像不大
//                CompletableFuture<Long> futureCountTeamUserByTeamId= CompletableFuture.supplyAsync(() -> {
//                    // 已加入队伍的人数
//                    return (Long) this.countTeamUserByTeamId(teamId);
//                },threadPoolExecutor);
//                CompletableFuture<Long> futureHasJoinNum = CompletableFuture.supplyAsync(() -> {
//                    // 该用户已加入的队伍数量
//                    QueryWrapper<UserTeam> userTeamQueryWrapper2 = new QueryWrapper<>();
//                    userTeamQueryWrapper2.eq("userId", userId);
//                    Long hasJoinNum = userTeamService.count(userTeamQueryWrapper2);
//                    return hasJoinNum;
//                }, threadPoolExecutor);
//
//                CompletableFuture<Void> combinedFuture =
//                        CompletableFuture.allOf(futureCountTeamUserByTeamId, futureHasJoinNum);
//                combinedFuture.join();
//                try {
//                    // 通过 get() 获取两个任务的结果
//                    Long teamHasJoinNum = futureCountTeamUserByTeamId.get();
//                    if (teamHasJoinNum >= team.getMaxNum()) {
//                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已满");
//                    }
//                    Long hasJoinNum = futureHasJoinNum.get();
//                    if (hasJoinNum >= 5) {
//                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多创建和加入 5 个队伍");
//                    }
//                    // 在两个结果都返回后处理逻辑
//                    log.info("队伍人数: " + teamHasJoinNum);
//                    log.info("用户已加入的队伍数量: " + hasJoinNum);
//                } catch (InterruptedException | ExecutionException e) {
//                    e.printStackTrace();
//                }


                        // 已加入队伍的人数
                        long teamHasJoinNum = this.countTeamUserByTeamId(teamId);
                        if (teamHasJoinNum >= team.getMaxNum()) {
                            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已满");
                        }
                        // 该用户已加入的队伍数量
                        userTeamQueryWrapper = new QueryWrapper<>();
                        userTeamQueryWrapper.eq("userId", userId);
                        long hasJoinNum = userTeamService.count(userTeamQueryWrapper);
                        log.info("\n" + "before hasJoinNum: " + hasJoinNum);
                        if (hasJoinNum >= 5) {
                            throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多创建和加入 5 个队伍");
                        }

                        // 修改队伍信息
                        UserTeam userTeam = new UserTeam();
                        userTeam.setUserId(userId);
                        userTeam.setTeamId(teamId);
                        userTeam.setJoinTime(new Date());
                        boolean save = userTeamService.save(userTeam);
                        return save;
                    } else {  //抢不到user锁
                        log.error("doCacheRecommendUser error");
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "加入队伍失败");
                    }
                } catch (InterruptedException e) {
                    log.error("doCacheRecommendUser error", e);
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "加入队伍失败");
                } finally {  //释放user锁
                    // 即保证必须加锁成功，才能释放锁。防止出现没加锁但释放锁的代码。同时只能释放自己的锁，稳健。
                    if (userLock.isHeldByCurrentThread()) {
                        System.out.println("unTeamLock: " + Thread.currentThread().getId());
                        teamLock.unlock();
                    }
                }


            } else {   //抢不到team锁
                log.error("doCacheRecommendUser error");
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "加入队伍失败");
            }
        } catch (InterruptedException e) {
            log.error("doCacheRecommendUser error", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "加入队伍失败");
        } finally {
            // 释放 team锁
            if (teamLock.isHeldByCurrentThread()) {
                System.out.println("unTeamLock: " + Thread.currentThread().getId());
                teamLock.unlock();
            }
        }


    }

    /**
     * 用户退出队伍。应该加锁，防止同时多个多个用户退出队伍。队伍剩余人数的不同会导致逻辑的不同。
     *
     * @param teamQuitRequest
     * @param loginUser
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean quitTeam(TeamQuitRequest teamQuitRequest, UserVO loginUser) {
        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long teamId = teamQuitRequest.getTeamId();
        Team team = this.getTeamById(teamId);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        long userId = loginUser.getId();
        UserTeam queryUserTeam = new UserTeam();
        queryUserTeam.setTeamId(teamId);
        queryUserTeam.setUserId(userId);
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>(queryUserTeam);
        long count = userTeamService.count(queryWrapper);
        if (count == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未加入队伍");
        }
        // 用户退出队伍
        RLock lock = redissonClient.getLock(String.format("yupao:quit_team:{%s}", teamId));
        try {
            // 抢到锁并执行
            if (lock.tryLock(10000, -1, TimeUnit.MILLISECONDS)) {
                System.out.println("getLock: " + Thread.currentThread().getId());
                // 1. 查询已加入队伍的所有用户和加入时间
                QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
                userTeamQueryWrapper.eq("teamId", teamId);
                userTeamQueryWrapper.last("order by id asc limit 2");
                List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
                long teamHasJoinNum = userTeamList.size();
                // 队伍只剩一人，解散
                if (userTeamList.size() == 1) {
                    // 删除队伍
                    this.removeById(teamId);
                } else if (teamHasJoinNum > 1) {
                    // 队伍还剩至少两人
                    // 是队长
                    if (team.getUserId() == userId) {
                        // 把队伍转移给最早加入的用户
                        UserTeam nextUserTeam = userTeamList.get(1);
                        Long nextTeamLeaderId = nextUserTeam.getUserId();
                        // 更新当前队伍的队长
                        Team updateTeam = new Team();
                        updateTeam.setId(teamId);
                        updateTeam.setUserId(nextTeamLeaderId);
                        boolean result = this.updateById(updateTeam);
                        if (!result) {
                            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新队伍队长失败");
                        }
                    }
                }
                // 移除队伍、成员关系
                boolean result = userTeamService.remove(queryWrapper);
                if (!result) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除队伍信息失败");
                }
                return true;
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "退出队伍失败");
            }
        } catch (InterruptedException e) {
            log.error("doCacheRecommendUser error", e);
            return false;
        } finally {
            // 只能释放自己的锁
            if (lock.isHeldByCurrentThread()) {
                System.out.println("unLock: " + Thread.currentThread().getId());
                lock.unlock();
            }
        }
    }

    private void checkParams(Team team, UserVO loginUser) {
        // 1. 请求参数是否为空？
        if (team == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 是否登录，未登录不允许创建
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        final long userId = loginUser.getId();
        // 3. 校验信息
        //   1. 队伍人数 > 1 且 <= 20
        int maxNum = Optional.ofNullable(team.getMaxNum()).orElse(0);
        if (maxNum < 1 || maxNum > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍人数不满足要求");
        }
        //   2. 队伍标题 <= 20。不允许同名队伍
        String name = team.getName();
        if (StringUtils.isBlank(name) || name.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍标题不满足要求");
        }
        QueryWrapper<Team> teamQueryWrapper = new QueryWrapper<>();
        teamQueryWrapper.eq("name", name);
        long teamCount = this.count(teamQueryWrapper);
        if (teamCount > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "存在重复队伍名");
        }
        //   3. 描述 <= 512
        String description = team.getDescription();
        if (StringUtils.isNotBlank(description) && description.length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍描述过长");
        }
        //   4. status 是否公开（int）不传默认为 0（公开）
//        int status = Optional.ofNullable(team.getStatus()).orElse(0);     // by zz
//        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);  // by zz
//        if (statusEnum == null) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍状态不满足要求");
//        }
        Integer status = Optional.ofNullable(team.getStatus()).orElse(0);
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
        //   5. 如果 status 是加密状态，一定要有密码，且密码 <= 32
        String password = team.getPassword();
        if (statusEnum.equals(TeamStatusEnum.SECRET)) {
            if (StringUtils.isBlank(password) || password.length() > 32) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码设置不正确");
            }
        }
        // 6. 要求   当前时间 < 超时时间
        Date expireTime = team.getExpireTime();
        if (new Date().after(expireTime)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已过期");
        }
        // 7. 校验用户最多创建 5 个队伍
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("userId", userId);
        long hasJoinNum = userTeamService.count(userTeamQueryWrapper);
        if (hasJoinNum > 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多创建和加入 5 个队伍");
        }
    }

    /**
     * 获取某队伍当前人数
     *
     * @param teamId
     * @return
     */
    private long countTeamUserByTeamId(Long teamId) {
        if (teamId == null || teamId < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍ID有误");
        }
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("teamId", teamId);
        return userTeamService.count(userTeamQueryWrapper);
    }
}




