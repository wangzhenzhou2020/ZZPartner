package com.yupi.yupao.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yupi.yupao.common.BaseResponse;
import com.yupi.yupao.common.DeleteRequest;
import com.yupi.yupao.common.ErrorCode;
import com.yupi.yupao.common.ResultUtils;
import com.yupi.yupao.exception.BusinessException;
import com.yupi.yupao.model.domain.Team;
import com.yupi.yupao.model.domain.UserTeam;
import com.yupi.yupao.model.request.*;
import com.yupi.yupao.model.vo.TeamUserVO;
import com.yupi.yupao.model.vo.UserVO;
import com.yupi.yupao.service.TeamService;
import com.yupi.yupao.service.UserService;
import com.yupi.yupao.service.UserTeamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 队伍接口
 *
 * 
 * 
 */
@RestController
@RequestMapping("/team")
//@CrossOrigin(origins = {"http://localhost:3000"})  // 之前得注册跨域配置，也可以直接写在全局跨域配置中。
@Slf4j
public class TeamController {

    @Resource
    private UserService userService;

    @Resource
    private TeamService teamService;

    @Resource
    private UserTeamService userTeamService;

    @PostMapping("/add")
    public BaseResponse<Long> addTeam(@RequestBody TeamAddRequest teamAddRequest, HttpServletRequest request) {
        if (teamAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserVO loginUser = userService.getLoginUser(request);
        Team team = new Team();
        BeanUtils.copyProperties(teamAddRequest, team);
        long teamId = teamService.addTeam(team, loginUser);
        return ResultUtils.success(teamId);
    }

    @PostMapping("/update")
    public BaseResponse<Boolean> updateTeam(@RequestBody TeamUpdateRequest teamUpdateRequest, HttpServletRequest request) {
        if (teamUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserVO loginUser = userService.getLoginUser(request);
        boolean result = teamService.updateTeam(teamUpdateRequest, loginUser);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败");
        }
        return ResultUtils.success(true);
    }

    @GetMapping("/get")
    public BaseResponse<Team> getTeamById(Long id) {
        if (id == null || id <= 0 ) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = teamService.getById(id);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        return ResultUtils.success(team);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteTeam(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null ) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long teamId = deleteRequest.getId();
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserVO loginUser = userService.getLoginUser(request);
        boolean result = teamService.deleteTeam(teamId, loginUser);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除失败");
        }
        return ResultUtils.success(true);
    }


    /**
     * 首页查询队伍列表，公开或者加密，标记当前用户是否加入队伍。
     * @param teamQuery
     * @param request
     * @return
     */
    @GetMapping("/list")
    public BaseResponse<List<TeamUserVO>> listTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserVO loginUser = userService.getLoginUser(request);
        boolean isAdmin = userService.isAdmin(loginUser);
        // 1、查询队伍列表
//        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, isAdmin, TeamListOption.RECOMMEND);
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, isAdmin);
        if(CollectionUtils.isEmpty(teamList)){
            return ResultUtils.success(new ArrayList<>());
        }
        // 2、判断当前用户是否已加入列表里的某些队伍
        final List<Long> teamIdList = teamList.stream().map(TeamUserVO::getId).collect(Collectors.toList());
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        try {
            userTeamQueryWrapper.eq("userId", loginUser.getId());
            userTeamQueryWrapper.in("teamId", teamIdList);
            List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
            // 当前队伍列表中，用户已加入的队伍 id 集合
            Set<Long> hasJoinTeamIdSet = userTeamList.stream().map(UserTeam::getTeamId).collect(Collectors.toSet());
            // 当前队伍列表中，用户是否加入某些队伍，加入则给队伍打上标记。
            teamList.forEach(team -> {
                boolean hasJoin = hasJoinTeamIdSet.contains(team.getId());
                team.setHasJoin(hasJoin);
            });
        } catch (Exception e) {
        }
        // 3、查询队伍的人数
        QueryWrapper<UserTeam> userTeamJoinQueryWrapper = new QueryWrapper<>();
        userTeamJoinQueryWrapper.in("teamId", teamIdList);
        List<UserTeam> userTeamList = userTeamService.list(userTeamJoinQueryWrapper);
        // teamId：List<UserTeam>
        Map<Long, List<UserTeam>> teamIdUserTeamListMap = userTeamList.stream()
                .collect(Collectors.groupingBy(UserTeam::getTeamId));

        teamList.forEach(team -> team.setHasJoinNum(
                teamIdUserTeamListMap.getOrDefault(team.getId(), new ArrayList<>()).size()));
        return ResultUtils.success(teamList);
    }

//    // 查询分页，无需权限。
//    @GetMapping("/list/page")
//    public BaseResponse<Page<Team>> listTeamsByPage(TeamPageQuery teamPageQuery) {
//        if (teamPageQuery == null) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR);
//        }
//        Team team = new Team();
//        BeanUtils.copyProperties(teamPageQuery, team);
//        Page<Team> page = new Page<>(teamPageQuery.getPageNum(), teamPageQuery.getPageSize());
//        QueryWrapper<Team> queryWrapper = new QueryWrapper<>(team);
//        Page<Team> resultPage = teamService.page(page, queryWrapper);
//        return ResultUtils.success(resultPage);
//    }

    @PostMapping("/join")
    public BaseResponse<Boolean> joinTeam(@RequestBody TeamJoinRequest teamJoinRequest, HttpServletRequest request) {
        if (teamJoinRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserVO loginUser = userService.getLoginUser(request);
        boolean result = teamService.joinTeam(teamJoinRequest, loginUser);
        return ResultUtils.success(result);
    }

    @PostMapping("/quit")
    public BaseResponse<Boolean> quitTeam(@RequestBody TeamQuitRequest teamQuitRequest, HttpServletRequest request) {
        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserVO loginUser = userService.getLoginUser(request);
        boolean result = teamService.quitTeam(teamQuitRequest, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 获取我创建的队伍
     *
     * @param teamQuery
     * @param request
     * @return
     */
    @GetMapping("/list/my/create")
    public BaseResponse<List<TeamUserVO>> listMyCreateTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserVO loginUser = userService.getLoginUser(request);
        teamQuery.setUserId(loginUser.getId()); // 如果登陆了就绝不会为空
//        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true,TeamListOption.INDIVIDUAL);
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true);
        if(CollectionUtils.isEmpty(teamList)){
            return ResultUtils.success(new ArrayList<>());
        }
        // 2、用户已加入列表里的某些队伍
        teamList.forEach(team -> {
            team.setHasJoin(true);
        });
        // 3、查询队伍的人数
        final List<Long> teamIdList = teamList.stream().map(TeamUserVO::getId).collect(Collectors.toList());
        QueryWrapper<UserTeam> userTeamJoinQueryWrapper = new QueryWrapper<>();
        userTeamJoinQueryWrapper.in("teamId", teamIdList);
        List<UserTeam> userTeamList = userTeamService.list(userTeamJoinQueryWrapper);
        // teamId：List<UserTeam>
        Map<Long, List<UserTeam>> teamIdUserTeamListMap = userTeamList.stream()
                .collect(Collectors.groupingBy(UserTeam::getTeamId));

        teamList.forEach(team -> team.setHasJoinNum(
                teamIdUserTeamListMap.getOrDefault(team.getId(), new ArrayList<>()).size()));
        return ResultUtils.success(teamList);

    }


    /**
     * 获取我加入的队伍
     *
     * @param teamQuery
     * @param request
     * @return
     */
    @GetMapping("/list/my/join")
    public BaseResponse<List<TeamUserVO>> listMyJoinTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserVO loginUser = userService.getLoginUser(request);
        // 查询 userTeam表 行信息
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("teamId");
        queryWrapper.eq("userId", loginUser.getId());
        List<UserTeam> userTeamList = userTeamService.list(queryWrapper);  // 查出我加入的队伍,这可能为空
        // 取出不重复的队伍 id（按道理应该是没有重复的teamId，这里严谨一些）
        List<Long> teamIdList = userTeamList.stream().map(UserTeam::getTeamId).distinct()
                .collect(Collectors.toList());
        // 如果teamIdList为空
        if(CollectionUtils.isEmpty(teamIdList)) {
            return ResultUtils.success(new ArrayList<>());
        }
        // 查询 team 表
        teamQuery.setIdList(teamIdList);
//        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true,TeamListOption.INDIVIDUAL);
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true);
        // 2、用户已加入列表里的队伍
        teamList.forEach(team -> {
            team.setHasJoin(true);
        });
        // 3、查询并设置队伍的人数
        Map<Long, List<UserTeam>> teamIdUserTeamListMap = userTeamList.stream()
                .collect(Collectors.groupingBy(UserTeam::getTeamId));
        teamList.forEach(team -> team.setHasJoinNum(
                teamIdUserTeamListMap.getOrDefault(team.getId(), new ArrayList<>()).size()));
        return ResultUtils.success(teamList);

    }
}



























