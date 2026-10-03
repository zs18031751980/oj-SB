package com.xauat.oj.infrastructure.queue;

public interface JudgeQueue {
    void publish(String queue, String jobId);

    /**
     * 幂等投递：以 judge:enqueued:&lt;queue&gt;:&lt;jobId&gt; 去重，避免 outbox 重试导致重复入队。
     * 返回 true 表示本次实际入队，false 表示此前已入队。
     */
    boolean publishOnce(String queue, String jobId, long dedupeTtlSeconds);
    QueueClaim claim(String queue, String workerId, long leaseSeconds);
    boolean renew(String jobId, String receipt, long leaseSeconds);
    long recoverExpired(String queue, int limit);
    long recoverRetries(String queue, int limit);
    void acknowledge(String queue, String jobId, String receipt);
    long reject(String queue, String jobId, String reason);

    /** 队列当前长度（用于可观测性）。 */
    long size(String queue);

    /** 重试集合大小（用于可观测性）。 */
    long retrySize(String queue);

    /** 将死信移入归档列表（保留最近 1000 条）。 */
    long archiveDead(String queue, int limit);

    /** 按主体限制并发执行槽；返回 false 表示当前无空槽。 */
    boolean acquireExecutionSlot(String subject, String token, int limit, long ttlSeconds);

    void releaseExecutionSlot(String subject, String token);
}
