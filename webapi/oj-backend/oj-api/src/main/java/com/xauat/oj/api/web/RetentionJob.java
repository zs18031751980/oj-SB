package com.xauat.oj.api.web;

import com.xauat.oj.core.contest.repository.ContestJudgeOutboxRepository;
import com.xauat.oj.core.contest.repository.JudgeDeadLetterRepository;
import com.xauat.oj.core.learning.repository.LearnBrowsingHistoryRepository;
import com.xauat.oj.core.submission.repository.SubmissionOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据保留策略：清理已处理死信、过期浏览记录，以及已终态提交的已派发 outbox 恢复记录。
 * 正式提交、判题历史、审计与事件必须保留。
 */
@Component
public class RetentionJob {
    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);
    private static final List<String> TERMINAL_STATUSES =
            List.of("AC", "WA", "CE", "TLE", "MLE", "OLE", "RE", "SIGSEGV", "SIGSYS", "Partial");

    private final JudgeDeadLetterRepository deadLetters;
    private final LearnBrowsingHistoryRepository browsingHistory;
    private final SubmissionOutboxRepository submissionOutboxes;
    private final ContestJudgeOutboxRepository contestOutboxes;
    private final int deadLetterDays;
    private final int browsingDays;
    private final int outboxDays;

    public RetentionJob(JudgeDeadLetterRepository deadLetters, LearnBrowsingHistoryRepository browsingHistory,
                        SubmissionOutboxRepository submissionOutboxes, ContestJudgeOutboxRepository contestOutboxes,
                        @Value("${oj.retention.dead-letter-days:30}") int deadLetterDays,
                        @Value("${oj.retention.browsing-days:180}") int browsingDays,
                        @Value("${oj.retention.outbox-days:7}") int outboxDays) {
        this.deadLetters = deadLetters; this.browsingHistory = browsingHistory;
        this.submissionOutboxes = submissionOutboxes; this.contestOutboxes = contestOutboxes;
        this.deadLetterDays = deadLetterDays; this.browsingDays = browsingDays; this.outboxDays = outboxDays;
    }

    @Scheduled(cron = "${oj.retention.cron:0 30 3 * * *}")
    @Transactional
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        int letters = deadLetters.deleteResolvedBefore(now.minusDays(Math.max(1, deadLetterDays)));
        int history = browsingHistory.deleteBefore(now.minusDays(Math.max(1, browsingDays)));
        LocalDateTime outboxCutoff = now.minusDays(Math.max(7, outboxDays));
        int regular = submissionOutboxes.deleteDispatchedBefore(outboxCutoff);
        int contest = contestOutboxes.deleteDispatchedFinalBefore(outboxCutoff, TERMINAL_STATUSES);
        if (letters > 0 || history > 0 || regular > 0 || contest > 0) {
            log.info("retention cleanup: deadLetters={} browsingHistory={} regularOutbox={} contestOutbox={}",
                    letters, history, regular, contest);
        }
    }
}
