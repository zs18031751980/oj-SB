package com.xauat.oj.api.contest;

import com.xauat.oj.core.contest.repository.ContestTeamMemberRepository;
import com.xauat.oj.core.contest.repository.ContestTeamRepository;
import org.springframework.stereotype.Service;

/**
 * 比赛 entry 解析：若用户属于某支队伍，则其提交计入队长（entry），否则计入本人。
 * 与旧后端 entry_user 语义一致，用于榜单与状态按队伍归并。
 */
@Service
public class EntryResolver {
    private final ContestTeamMemberRepository members;
    private final ContestTeamRepository teams;

    public EntryResolver(ContestTeamMemberRepository members, ContestTeamRepository teams) {
        this.members = members; this.teams = teams;
    }

    public Integer entryUser(Integer contestId, Integer userId) {
        if (contestId == null || userId == null) return userId;
        return members.findByContest_IdAndUser_Id(contestId, userId)
                .flatMap(member -> teams.findById(member.getTeamId()))
                .map(team -> team.getCaptainId())
                .orElse(userId);
    }
}
