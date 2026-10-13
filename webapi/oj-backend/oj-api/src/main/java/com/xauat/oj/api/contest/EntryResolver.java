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

    /**
     * 解析提交应归属的 entry 用户：属于队伍则返回队长，否则返回本人。
     *
     * @param contestId 比赛 ID
     * @param userId    提交者 ID
     * @return entry 用户 ID；入参为空时原样返回
     */
    public Integer entryUser(Integer contestId, Integer userId) {
        if (contestId == null || userId == null) return userId;
        // 先查队伍成员，再通过队伍拿到队长；任一环节缺失则回退到本人
        return members.findByContest_IdAndUser_Id(contestId, userId)
                .flatMap(member -> teams.findById(member.getTeamId()))
                .map(team -> team.getCaptainId())
                .orElse(userId);
    }
}
