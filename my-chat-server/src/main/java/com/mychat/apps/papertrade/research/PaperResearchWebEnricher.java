package com.mychat.apps.papertrade.research;

import com.mychat.tools.WebSearchTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 开关开启时用 WebSearchTools 预取网页标题，与 A 源按 URL/标题去重合并。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaperResearchWebEnricher {

    private static final int MAX_WEB_PER_SYMBOL = 3;
    private static final Pattern ITEM = Pattern.compile(
            "(?m)^\\d+\\.\\s*(.+?)\\s*$\\n\\s+(https?://\\S+)(?:\\n\\s+(.+?))?(?=\\n\\d+\\.|\\z)",
            Pattern.DOTALL);

    private final WebSearchTools webSearchTools;

    /**
     * 在已有 A 源之后追加 web 条目；无 Key/失败只记日志，不抛错。
     *
     * @return 是否至少追加了一条 web
     */
    public boolean enrich(String symbol, String name, List<PaperStoryFeed.SourceItem> sources) {
        if (sources == null) {
            return false;
        }
        String query = buildQuery(symbol, name);
        String raw;
        try {
            raw = webSearchTools.searchWeb(query);
        } catch (Exception e) {
            log.warn("工具检索失败 {}: {}", symbol, e.getMessage());
            return false;
        }
        if (!StringUtils.hasText(raw) || !raw.contains("status=200")) {
            log.debug("工具检索无可用结果 {}: {}", symbol, clip(raw, 120));
            return false;
        }
        List<PaperStoryFeed.SourceItem> web = parse(raw);
        if (web.isEmpty()) {
            return false;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (PaperStoryFeed.SourceItem s : sources) {
            seen.add(dedupeKey(s));
        }
        boolean added = false;
        int count = 0;
        for (PaperStoryFeed.SourceItem item : web) {
            if (count >= MAX_WEB_PER_SYMBOL) {
                break;
            }
            String key = dedupeKey(item);
            if (!seen.add(key)) {
                continue;
            }
            sources.add(item);
            count++;
            added = true;
        }
        return added;
    }

    static String buildQuery(String symbol, String name) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(symbol)) {
            sb.append(symbol.trim());
        }
        if (StringUtils.hasText(name)) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(name.trim());
        }
        sb.append(" A股 行情 资讯");
        return sb.toString();
    }

    static List<PaperStoryFeed.SourceItem> parse(String raw) {
        List<PaperStoryFeed.SourceItem> out = new ArrayList<>();
        Matcher m = ITEM.matcher(raw);
        while (m.find()) {
            String title = clean(m.group(1));
            String url = clean(m.group(2));
            if (!StringUtils.hasText(title)) {
                continue;
            }
            out.add(new PaperStoryFeed.SourceItem("WEB", title, url, null));
        }
        return out;
    }

    private static String dedupeKey(PaperStoryFeed.SourceItem s) {
        if (s == null) {
            return "";
        }
        if (StringUtils.hasText(s.url())) {
            return "u:" + s.url().trim().toLowerCase(Locale.ROOT);
        }
        return "t:" + (s.title() == null ? "" : s.title().trim().toLowerCase(Locale.ROOT));
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        return s.replace('\n', ' ').trim();
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
