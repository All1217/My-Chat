package com.mychat.apps.market;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 把用户输入收成 CN / US 代码。 */
public final class MarketSymbolParser {

    private static final Pattern CN_PREFIX = Pattern.compile("^(SH|SZ|BJ|SS):?(\\d{6})$");
    private static final Pattern CN_SUFFIX = Pattern.compile("^(\\d{6})\\.(SH|SZ|SS|BJ)$");
    private static final Pattern CN_DIGITS = Pattern.compile("^(\\d{6})$");
    private static final Pattern US_TICKER = Pattern.compile("^[A-Z]{1,5}(?:[.-][A-Z])?$");
    private static final Pattern US_EXCHANGE = Pattern.compile("^(?:NASDAQ|NYSE|AMEX|US):");
    private static final Set<String> SH_FUND_PREFIX = Set.of("50", "51", "52", "56", "58");
    private static final Set<String> SZ_FUND_PREFIX = Set.of("15", "16", "18");

    private MarketSymbolParser() {
    }

    /**
     * 解析代码。无法识别时抛出 IllegalArgumentException。
     */
    public static ResolvedSymbol parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("请输入股票代码");
        }
        String s = raw.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        Matcher prefix = CN_PREFIX.matcher(s);
        if (prefix.matches()) {
            return fromCnDigits(prefix.group(2), prefix.group(1));
        }
        Matcher suffix = CN_SUFFIX.matcher(s);
        if (suffix.matches()) {
            return fromCnDigits(suffix.group(1), suffix.group(2));
        }
        Matcher digits = CN_DIGITS.matcher(s);
        if (digits.matches()) {
            return fromCnDigits(digits.group(1), null);
        }
        String us = normalizeUsTicker(s);
        if (US_TICKER.matcher(us).matches()) {
            return new ResolvedSymbol(us.replace('-', '.'), "US", null);
        }
        throw new IllegalArgumentException("无法识别代码，A 股如 600519，美股如 AAPL");
    }

    /** 去掉交易所前缀与 ^ / 前导点，便于 NASDAQ:.IXIC、^NDX。 */
    static String normalizeUsTicker(String s) {
        String t = US_EXCHANGE.matcher(s).replaceFirst("");
        if (t.startsWith("^") || t.startsWith(".")) {
            t = t.substring(1);
        }
        return t;
    }

    /** 按交易所或品种判断沪深京，拼东方财富 secid。 */
    static ResolvedSymbol fromCnDigits(String code, String exchange) {
        String marketNo = marketNo(code, exchange);
        return new ResolvedSymbol(code, "CN", marketNo + "." + code);
    }

    /** 显式 SH/SZ 优先；否则按基金/股票号段。 */
    static String marketNo(String code, String exchange) {
        if (exchange != null) {
            return switch (exchange) {
                case "SH", "SS" -> "1";
                case "SZ", "BJ" -> "0";
                default -> throw new IllegalArgumentException("无法识别 A 股代码: " + code);
            };
        }
        String p2 = code.substring(0, 2);
        if (SH_FUND_PREFIX.contains(p2) || code.charAt(0) == '6' || code.charAt(0) == '9') {
            return "1";
        }
        if (SZ_FUND_PREFIX.contains(p2) || code.charAt(0) == '0' || code.charAt(0) == '2' || code.charAt(0) == '3') {
            return "0";
        }
        if (code.charAt(0) == '4' || code.charAt(0) == '8') {
            return "0";
        }
        if (code.charAt(0) == '5') {
            return "1";
        }
        if (code.charAt(0) == '1') {
            return "0";
        }
        throw new IllegalArgumentException("无法识别 A 股代码: " + code);
    }
}
