package com.amalitech.hilfe.notifications.content;

public record EmailContent(
        String subject,
        String preheader,
        String contentHtml,
        String ctaText,
        String footerText
) {
}
