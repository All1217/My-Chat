package com.mychat.apps.papertrade.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 模拟炒股轻量发信；未配置 SMTP 或收件人为空时跳过。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperMailService {

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_FAILED = "FAILED";

    private static final String TEST_SUBJECT = "模拟炒股 · 发信测试";
    private static final String TEST_BODY = """
            这是一封发信连通性测试。
            若你收到本邮件，说明评估结果收件人与 SMTP 可用。
            本邮件不代表新的阶段总结。
            """;

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final org.springframework.core.env.Environment environment;

    /**
     * 发送纯文本邮件。
     *
     * @return SENT / SKIPPED / FAILED
     */
    public String send(String to, String subject, String text) {
        return deliver(to, subject, text).getStatus();
    }

    /** 向当前输入的地址发一封固定测试信。 */
    public PaperMailTestVO sendTest(String to) {
        return deliver(to, TEST_SUBJECT, TEST_BODY);
    }

    /**
     * 发送纯文本邮件，并返回状态与给人看的说明。
     */
    public PaperMailTestVO deliver(String to, String subject, String text) {
        if (!StringUtils.hasText(to)) {
            log.warn("评估员发信跳过：收件人为空");
            return PaperMailTestVO.of(STATUS_SKIPPED, "收件人为空，已跳过发信");
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        String username = environment.getProperty("spring.mail.username", "");
        String password = environment.getProperty("spring.mail.password", "");
        if (sender == null || !StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("评估员发信跳过：spring.mail 未配齐 username/password；正文已落日志 subject={}", subject);
            log.info("评估员邮件草稿 to={} subject={}\n{}", to, subject, text == null ? "" : text);
            return PaperMailTestVO.of(STATUS_SKIPPED, "未配置 PAPER_MAIL_USER / PAPER_MAIL_PASS，未真正发信");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(username.trim());
            message.setTo(to.trim());
            message.setSubject(subject == null ? "模拟炒股评估" : subject);
            message.setText(text == null ? "" : text);
            sender.send(message);
            log.info("评估员邮件已发送 to={}", to.trim());
            return PaperMailTestVO.of(STATUS_SENT, "邮件已发到 " + to.trim());
        } catch (Exception e) {
            String reason = clip(redact(e.getMessage(), password), 300);
            log.warn("评估员发信失败: {}", reason);
            String detail = StringUtils.hasText(reason) ? reason : "SMTP 异常";
            return PaperMailTestVO.of(STATUS_FAILED, "发信失败：" + detail);
        }
    }

    /** 异常文案里去掉密码，避免测试接口把授权码带回页面。 */
    private static String redact(String raw, String secret) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        if (!StringUtils.hasText(secret)) {
            return raw;
        }
        return raw.replace(secret, "***");
    }

    /** 截断过长说明。 */
    private static String clip(String raw, int max) {
        if (raw == null || raw.length() <= max) {
            return raw == null ? "" : raw;
        }
        return raw.substring(0, max);
    }
}
