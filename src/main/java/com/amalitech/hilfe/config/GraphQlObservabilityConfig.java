package com.amalitech.hilfe.config;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.observation.DefaultExecutionRequestObservationConvention;
import org.springframework.graphql.observation.ExecutionRequestObservationContext;
import org.springframework.graphql.observation.ExecutionRequestObservationConvention;

@Configuration
public class GraphQlObservabilityConfig {

    // Picked up by Boot's GraphQlObservationAutoConfiguration (ConditionalOnMissingBean +
    // ObjectProvider.getIfAvailable) and injected into its GraphQlObservationInstrumentation bean.
    @Bean
    public ExecutionRequestObservationConvention graphQlOperationObservationConvention() {
        return new GraphQlOperationObservationConvention();
    }

    static class GraphQlOperationObservationConvention extends DefaultExecutionRequestObservationConvention {

        @Override
        public KeyValues getLowCardinalityKeyValues(ExecutionRequestObservationContext context) {
            // Always emit this tag key so every "graphql.request" observation registers the same
            // tag-key set — Prometheus requires meters sharing a name to have identical tag keys,
            // and conditionally adding the key here would make it reject the inconsistent ones.
            KeyValues keyValues = super.getLowCardinalityKeyValues(context);
            String operationName = context.getExecutionInput().getOperationName();
            String value = (operationName != null && !operationName.isEmpty()) ? operationName : "none";
            return keyValues.and(KeyValue.of("graphql.operation.name", value));
        }
    }
}
