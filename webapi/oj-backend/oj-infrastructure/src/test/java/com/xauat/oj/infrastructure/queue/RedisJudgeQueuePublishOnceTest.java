package com.xauat.oj.infrastructure.queue;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 判题投递幂等性测试：同一 job 最多入队一次；Redis 异常时退回普通投递保证不丢任务。
 */
@SuppressWarnings("unchecked")
class RedisJudgeQueuePublishOnceTest {

    @Test
    void duplicatePublishIsDeduped() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ListOperations<String, String> lists = mock(ListOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForList()).thenReturn(lists);
        when(values.setIfAbsent(eq("judge:enqueued:q:job-1"), eq("1"), any(Duration.class)))
                .thenReturn(true, false);

        RedisJudgeQueue queue = new RedisJudgeQueue(redis);

        assertTrue(queue.publishOnce("q", "job-1", 10));
        assertFalse(queue.publishOnce("q", "job-1", 10));
        // 重复投递不得再次入队
        verify(lists, times(1)).rightPush("q", "job-1");
    }

    @Test
    void redisFailureFallsBackToPlainPublish() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ListOperations<String, String> lists = mock(ListOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForList()).thenReturn(lists);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        RedisJudgeQueue queue = new RedisJudgeQueue(redis);

        // 去重键不可写时仍要投递，保证任务不丢失
        assertTrue(queue.publishOnce("q", "job-2", 10));
        verify(lists).rightPush("q", "job-2");
    }
}
