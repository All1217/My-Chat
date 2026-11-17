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

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final org.springframework.core.env.Environment environment;

    /**
     * 发送纯文本邮件。
     *
     * @return SENT / SKIPPED / FAILED
     */
    public String send(String to, String subject, String text) {
        if (!StringUtils.hasText(to)) {
            log.warn("评估员发信跳过：收件人为空");
            return STATUS_SKIPPED;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        String username = environment.getProperty("spring.mail.username", "");
        String password = environment.getProperty("spring.mail.password", "");
        if (sender == null || !StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("评估员发信跳过：spring.mail 未配齐 username/password；正文已落日志 subject={}", subject);
            log.info("评估员邮件草稿 to={} subject={}\n{}", to, subject, text == null ? "" : text);
            return STATUS_SKIPPED;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(username.trim());
            message.setTo(to.trim());
            message.setSubject(subject == null ? "模拟炒股评估" : subject);
            message.setText(text == null ? "" : text);
            sender.send(message);
            log.info("评估员邮件已发送 to={}", to);
            return STATUS_SENT;
        } catch (Exception e) {
            log.warn("评估员发信失败: {}", e.getMessage());
            return STATUS_FAILED;
        }
    }
}
