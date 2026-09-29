package com.xauat.oj.infrastructure.queue;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;


@Component
public class RedisJudgeQueue implements JudgeQueue {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<String> CLAIM = new DefaultRedisScript<>(
            "local job=redis.call('LPOP',KEYS[1]); if not job then return '' end; " +
            "redis.call('SET',ARGV[1] .. job,ARGV[4], 'EX', ARGV[3]); local t=redis.call('TIME'); " +
            "redis.call('ZADD',KEYS[2],tonumber(t[1])+tonumber(ARGV[3]),job); return job .. '|' .. ARGV[4]", String.class);
    private static final DefaultRedisScript<Long> ACK = new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1]) ~= ARGV[2] then return 0 end; redis.call('DEL',KEYS[1]); return redis.call('ZREM',KEYS[2],ARGV[1])", Long.class);
    private static final DefaultRedisScript<Long> REJECT = new DefaultRedisScript<>(
            "redis.call('DEL',KEYS[2]); redis.call('ZREM',KEYS[3],ARGV[1]); local n=redis.call('INCR',KEYS[5]); " +
            "if n <= tonumber(ARGV[3]) then local t=redis.call('TIME'); local delay=2^(n-1); redis.call('ZADD',KEYS[6],tonumber(t[1])+delay,ARGV[1]); return n else redis.call('RPUSH',KEYS[4],ARGV[1]..':'..ARGV[2]); return n end", Long.class);
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1]) == ARGV[1] then return redis.call('EXPIRE',KEYS[1],ARGV[2]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> RECOVER = new DefaultRedisScript<>(
            "local t=redis.call('TIME'); local jobs=redis.call('ZRANGEBYSCORE',KEYS[2],'-inf',t[1],'LIMIT',0,ARGV[2]); local n=0; " +
            "for _,job in ipairs(jobs) do if redis.call('EXISTS',ARGV[1]..job)==0 then redis.call('RPUSH',KEYS[1],job); redis.call('ZREM',KEYS[2],job); n=n+1 end end; return n", Long.class);
    private static final DefaultRedisScript<Long> RECOVER_RETRY = new DefaultRedisScript<>(
            "local t=redis.call('TIME'); local jobs=redis.call('ZRANGEBYSCORE',KEYS[2],'-inf',t[1],'LIMIT',0,ARGV[1]); local n=0; " +
            "for _,job in ipairs(jobs) do redis.call('RPUSH',KEYS[1],job); redis.call('ZREM',KEYS[2],job); n=n+1 end; return n", Long.class);

    public RedisJudgeQueue(StringRedisTemplate redis) { this.redis = redis; }

    @Override
    public void publish(String queue, String jobId) { redis.opsForList().rightPush(queue, jobId); }

    @Override
    public QueueClaim claim(String queue, String workerId, long leaseSeconds) {
        String receipt = workerId + ":" + java.util.UUID.randomUUID();
        String result = redis.execute(CLAIM, java.util.List.of(queue, queue + ":inflight"), "oj:judge:lease:", workerId, String.valueOf(leaseSeconds), receipt);
        if (result == null || result.isEmpty()) return null;
        String[] parts = result.split("\\|", 2); return new QueueClaim(parts[0], parts[1]);
    }

    @Override
    public boolean renew(String jobId, String receipt, long leaseSeconds) { return Long.valueOf(1).equals(redis.execute(RENEW, java.util.List.of("oj:judge:lease:" + jobId), receipt, String.valueOf(leaseSeconds))); }

    @Override
    public long recoverExpired(String queue, int limit) { Long value = redis.execute(RECOVER, java.util.List.of(queue, queue + ":inflight"), "oj:judge:lease:", String.valueOf(limit)); return value == null ? 0 : value; }

    @Override
    public long recoverRetries(String queue, int limit) { Long value = redis.execute(RECOVER_RETRY, java.util.List.of(queue, queue + ":retry"), String.valueOf(limit)); return value == null ? 0 : value; }

    @Override
    public void acknowledge(String queue, String jobId, String receipt) { redis.execute(ACK, java.util.List.of("oj:judge:lease:" + jobId, queue + ":inflight"), jobId, receipt); }

    @Override
    public long reject(String queue, String jobId, String reason) {
        Long count = redis.execute(REJECT, java.util.List.of(queue, "oj:judge:lease:" + jobId, queue + ":inflight", queue + ":dead-letter", "oj:judge:retry:" + jobId, queue + ":retry"), jobId, reason, "3");
        return count == null ? 0 : count;
    }
}
