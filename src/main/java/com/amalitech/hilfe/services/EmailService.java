package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.MailProperties;
import com.amalitech.hilfe.notifications.content.EmailContent;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "mail.enabled", havingValue = "true")
public class EmailService {

    private static final String TEMPLATE_PATH = "templates/email/base-email-template.html";

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final EmailRateLimiter rateLimiter;

    private volatile String cachedTemplate;

    @Async("applicationTaskExecutor")
    public void send(String toAddress, EmailContent content, String ctaHref) {
        try {
            rateLimiter.acquireBlocking();
            String html = renderTemplate(content, ctaHref);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(mailProperties.fromAddress(), mailProperties.fromName());
            helper.setTo(toAddress);
            helper.setSubject(content.subject());
            helper.setText(html, true);

            mailSender.send(message);
            log.debug("Email sent to {} — subject: {}", toAddress, content.subject());
        } catch (Exception e) {
            log.error("Failed to send email to {}", toAddress, e);
        }
    }

    private String renderTemplate(EmailContent content, String ctaHref) throws IOException {
        return loadTemplate()
                .replace("{{subject}}", content.subject())
                .replace("{{preheader}}", content.preheader())
                .replace("{{content}}", content.contentHtml())
                .replace("{{cta_href}}", ctaHref)
                .replace("{{cta_text}}", content.ctaText())
                .replace("{{footer_text}}", "© " + Year.now().getValue() + " HILFE. All rights reserved.");
    }

    private String loadTemplate() throws IOException {
        String local = cachedTemplate;
        if (local == null) {
            synchronized (this) {
                local = cachedTemplate;
                if (local == null) {
                    ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
                    local = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    cachedTemplate = local;
                }
            }
        }
        return local;
    }
}
