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
    private static final DefaultRedisScript<Long> ACQUIRE_SLOT = new DefaultRedisScript<>(
            "local t=redis.call('TIME'); local now=tonumber(t[1]); redis.call('ZREMRANGEBYSCORE',KEYS[1],'-inf',now); "
            + "if redis.call('ZCARD',KEYS[1]) < tonumber(ARGV[2]) then redis.call('ZADD',KEYS[1],now+tonumber(ARGV[3]),ARGV[1]); redis.call('EXPIRE',KEYS[1],tonumber(ARGV[3])+5); return 1 else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> ARCHIVE_DEAD = new DefaultRedisScript<>(
            "local n=0; for i=1,tonumber(ARGV[1]) do local v=redis.call('RPOP',KEYS[1]); if not v then break end; redis.call('LPUSH',KEYS[2],v); n=n+1 end; redis.call('LTRIM',KEYS[2],0,999); return n", Long.class);
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
    public boolean publishOnce(String queue, String jobId, long dedupeTtlSeconds) {
        try {
            Boolean first = redis.opsForValue().setIfAbsent("judge:enqueued:" + queue + ":" + jobId, "1",
                    java.time.Duration.ofSeconds(dedupeTtlSeconds));
            if (Boolean.TRUE.equals(first)) { redis.opsForList().rightPush(queue, jobId); return true; }
            return false;
        } catch (RuntimeException exception) {
            // Redis 异常时退回普通投递，保证任务不丢失。
            redis.opsForList().rightPush(queue, jobId);
            return true;
        }
    }

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

    @Override
    public long size(String queue) {
        try { Long value = redis.opsForList().size(queue); return value == null ? 0 : value; }
        catch (RuntimeException exception) { return 0; }
    }

    @Override
    public long retrySize(String queue) {
        try { Long value = redis.opsForZSet().zCard(queue + ":retry"); return value == null ? 0 : value; }
        catch (RuntimeException exception) { return 0; }
    }

    @Override
    public long archiveDead(String queue, int limit) {
        try {
            Long moved = redis.execute(ARCHIVE_DEAD, java.util.List.of(queue + ":dead-letter", queue + ":dead-archive"), String.valueOf(limit));
            return moved == null ? 0 : moved;
        } catch (RuntimeException exception) { return 0; }
    }

    @Override
    public boolean acquireExecutionSlot(String subject, String token, int limit, long ttlSeconds) {
        try {
            Long acquired = redis.execute(ACQUIRE_SLOT, java.util.List.of("judge:execution:" + subject),
                    token, String.valueOf(limit), String.valueOf(ttlSeconds));
            return Long.valueOf(1).equals(acquired);
        } catch (RuntimeException exception) { return true; }
    }

    @Override
    public void releaseExecutionSlot(String subject, String token) {
        try { redis.opsForZSet().remove("judge:execution:" + subject, token); }
        catch (RuntimeException ignored) { }
    }
}
