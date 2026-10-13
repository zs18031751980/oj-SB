package com.xauat.oj.api.web;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.encoder.EncoderBase;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 结构化 JSON 日志；对常见敏感字段做脱敏，异常时回退纯文本，绝不因日志失败中断服务。 */
public class JsonLogEncoder extends EncoderBase<ILoggingEvent> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(password|passwd|token|refresh_token|access_token|secret|totp|authorization|client_secret)\"?\\s*[:=]\\s*\"?([^\"\\s,}]+)");

    /**
     * 将日志事件编码为单行 JSON。
     *
     * @param event Logback 日志事件
     * @return UTF-8 字节；序列化失败时回退为原始文本，保证日志不中断
     */
    @Override
    public byte[] encode(ILoggingEvent event) {
        try {
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("@timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
            log.put("level", event.getLevel().toString());
            log.put("logger", event.getLoggerName());
            log.put("thread", event.getThreadName());
            log.put("message", redact(event.getFormattedMessage()));
            // 合并 MDC 便于把请求 id 等上下文带入结构化日志
            Map<String, String> mdc = event.getMDCPropertyMap();
            if (mdc != null && !mdc.isEmpty()) log.put("mdc", mdc);
            if (event.getThrowableProxy() != null) log.put("stack", ThrowableProxyUtil.asString(event.getThrowableProxy()));
            return (MAPPER.writeValueAsString(log) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        } catch (Exception exception) {
            // 日志编码本身绝不能成为服务故障源，失败时退回纯文本
            return (event.getFormattedMessage() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * 对消息中的敏感字段值做脱敏（替换为 ***），仅保留字段名。
     *
     * @param message 原始日志消息
     * @return 脱敏后消息
     */
    private String redact(String message) {
        if (message == null) return "";
        Matcher matcher = SENSITIVE.matcher(message);
        StringBuilder result = new StringBuilder();
        int last = 0;
        // 逐处替换匹配到的敏感值，保留字段名与其余文本
        while (matcher.find()) {
            result.append(message, last, matcher.start(2)).append("***");
            last = matcher.end(2);
        }
        result.append(message.substring(last));
        return result.toString();
    }

    /** 无头部字节。 */
    @Override
    public byte[] headerBytes() { return null; }

    /** 无尾部字节。 */
    @Override
    public byte[] footerBytes() { return null; }
}
