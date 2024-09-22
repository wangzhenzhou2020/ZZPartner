package com.yupi.yupao.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupao.model.domain.Team;
import com.yupi.yupao.model.request.TeamJoinRequest;
import com.yupi.yupao.model.request.TeamQuery;
import com.yupi.yupao.model.request.TeamQuitRequest;
import com.yupi.yupao.model.request.TeamUpdateRequest;
import com.yupi.yupao.model.vo.TeamUserVO;
import com.yupi.yupao.model.vo.UserVO;

import java.util.List;

/**
* @author wangzhenzhou
* @description 针对表【team(队伍)】的数据库操作Service
* @createDate 2024-08-18 22:13:45
*/
public interface TeamService extends IService<Team> {

    long addTeam(Team team, UserVO loginUser);

    boolean updateTeam(TeamUpdateRequest teamUpdateRequest, UserVO loginUser);

    boolean deleteTeam(Long id, UserVO loginUser);

//    List<TeamUserVO> listTeams(TeamQuery teamQuery, boolean isAdminOrCreator, TeamListOption teamListOption);
    List<TeamUserVO> listTeams(TeamQuery teamQuery, boolean isAdminOrCreator);

    boolean joinTeam(TeamJoinRequest teamJoinRequest, UserVO loginUser);

    boolean quitTeam(TeamQuitRequest teamQuitRequest, UserVO loginUser);
}
