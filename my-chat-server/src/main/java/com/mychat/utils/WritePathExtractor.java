package com.mychat.utils;

import com.mychat.common.ChatStreamEvent;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从工具事件或用户文本中提取本轮 write 目标相对路径。
 */
public final class WritePathExtractor {

    private static final Pattern PATH_HINT = Pattern.compile(
            "(?:path\\s*[=:]\\s*[\"']?|写入\\s+|相对路径\\s*[\"']?|文件\\s*[\"']?)"
                    + "([\\w./\\\\-]+\\.[\\w]+)",
            Pattern.CASE_INSENSITIVE);

    private WritePathExtractor() {
    }

    /**
     * 从 NDJSON 工具事件中取最近一次成功的 write.path（无成功则取任意 write）。
     */
    public static String fromToolEvents(List<ChatStreamEvent> events) {
        if (events == null || events.isEmpty()) {
            return null;
        }
        String lastWrite = null;
        String lastOkWrite = null;
        for (ChatStreamEvent e : events) {
            if (e == null || e.type() == null) {
                continue;
            }
            if (ChatStreamEvent.TYPE_TOOL_CALL.equals(e.type())
                    && "write".equalsIgnoreCase(e.name())) {
                String path = pathFromArgs(e.args());
                if (StringUtils.hasText(path)) {
                    lastWrite = path;
                }
            }
            if (ChatStreamEvent.TYPE_TOOL_RESULT.equals(e.type())
                    && "write".equalsIgnoreCase(e.name())
                    && Boolean.TRUE.equals(e.ok())) {
                // result 未必带 path，沿用最近 tool_call
                if (StringUtils.hasText(lastWrite)) {
                    lastOkWrite = lastWrite;
                }
            }
        }
        return StringUtils.hasText(lastOkWrite) ? lastOkWrite : lastWrite;
    }

    /** 从自由文本启发式提取文件路径。 */
    public static String hintFromText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher m = PATH_HINT.matcher(text);
        if (m.find()) {
            return m.group(1).replace("\\", "/");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String pathFromArgs(Object args) {
        if (args instanceof Map<?, ?> map) {
            Object path = map.get("path");
            if (path != null && StringUtils.hasText(path.toString())) {
                return path.toString().trim().replace("\\", "/");
            }
        }
        if (args instanceof String s) {
            return hintFromText(s);
        }
        return null;
    }
}
