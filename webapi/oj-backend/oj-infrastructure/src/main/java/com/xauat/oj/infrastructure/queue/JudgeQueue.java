package com.xauat.oj.infrastructure.queue;

public interface JudgeQueue {
    void publish(String queue, String jobId);
    QueueClaim claim(String queue, String workerId, long leaseSeconds);
    boolean renew(String jobId, String receipt, long leaseSeconds);
    long recoverExpired(String queue, int limit);
    long recoverRetries(String queue, int limit);
    void acknowledge(String queue, String jobId, String receipt);
    long reject(String queue, String jobId, String reason);
}
