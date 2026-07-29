package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.AmaliAiProperties;
import com.amalitech.hilfe.config.EmbeddingProperties;
import com.amalitech.hilfe.config.LlmProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the {@code amali-ai.api-key} conditional gate on the real LLM/embedding
 * beans: previously {@code @ConditionalOnProperty(name = "amali-ai.api-key", matchIfMissing =
 * false)} always matched because the property defaults to an empty string rather than being
 * absent, so {@link OpenAiLlmService}/{@link OpenAiEmbeddingService} were constructed even with
 * no key configured. This only verifies the suppression direction (blank/unset key -> real beans
 * absent) since actually constructing these beans builds a real {@code RestClient}/JDK
 * {@code HttpClient}, which isn't reliable to exercise in every CI/sandbox network environment.
 * The other failure direction that matters just as much — the expression evaluating to
 * always-false, which would silently make {@code StubLlmService} permanent even with a real key
 * configured — is covered by {@link KeyGateProbe}, a dependency-free bean carrying the identical
 * expression, so both directions of the gate are pinned without needing network access.
 */
class LlmBeanConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class, OpenAiLlmService.class, OpenAiEmbeddingService.class);

    @Test
    void unsetApiKey_realBeansNotRegistered() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(OpenAiLlmService.class);
            assertThat(context).doesNotHaveBean(OpenAiEmbeddingService.class);
        });
    }

    @Test
    void blankApiKey_realBeansNotRegistered() {
        runner.withPropertyValues("amali-ai.api-key=").run(context -> {
            assertThat(context).doesNotHaveBean(OpenAiLlmService.class);
            assertThat(context).doesNotHaveBean(OpenAiEmbeddingService.class);
        });
    }

    @Test
    void keyGateExpression_blankKey_doesNotMatch() {
        new ApplicationContextRunner()
                .withUserConfiguration(KeyGateProbe.class)
                .run(context -> assertThat(context).doesNotHaveBean(KeyGateProbe.class));
    }

    @Test
    void keyGateExpression_realKey_matches() {
        new ApplicationContextRunner()
                .withUserConfiguration(KeyGateProbe.class)
                .withPropertyValues("amali-ai.api-key=real-key")
                .run(context -> assertThat(context).hasSingleBean(KeyGateProbe.class));
    }

    /**
     * Carries the exact same {@code @ConditionalOnExpression} used to gate
     * {@link OpenAiLlmService}/{@link OpenAiEmbeddingService}, with no constructor dependencies,
     * so both the "blank -> absent" and "present -> registered" directions of the gate itself can
     * be pinned without constructing a real {@code RestClient}.
     */
    @Service
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${amali-ai.api-key:}')")
    static class KeyGateProbe {
    }

    @Configuration
    static class PropertiesConfig {
        @Bean
        LlmProperties llmProperties() {
            return new LlmProperties("gpt-4o-mini", 0.2, 512);
        }

        @Bean
        EmbeddingProperties embeddingProperties() {
            return new EmbeddingProperties("text-embedding-3-small");
        }

        @Bean
        AmaliAiProperties amaliAiProperties(Environment env) {
            return new AmaliAiProperties(env.getProperty("amali-ai.api-key", ""), "http://localhost:1234", "openai");
        }
    }
}
