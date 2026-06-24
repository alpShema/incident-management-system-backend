package com.amalitech.hilfe.config;

import graphql.language.StringValue;
import graphql.language.Value;
import graphql.scalars.ExtendedScalars;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;

@Configuration
public class GraphQlConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(ExtendedScalars.GraphQLLong)
                .scalar(instantScalar());
    }

    private GraphQLScalarType instantScalar() {
        return GraphQLScalarType.newScalar()
                .name("Instant")
                .description("ISO-8601 UTC timestamp (e.g. 2024-01-15T10:30:00Z)")
                .coercing(new Coercing<Instant, String>() {
                    @Override
                    public String serialize(Object input, GraphQLContext context, Locale locale) throws CoercingSerializeException {
                        if (input instanceof Instant instant) {
                            return instant.toString();
                        }
                        if (input instanceof String s) {
                            return s;
                        }
                        if (input == null) {
                            return null;
                        }
                        throw new CoercingSerializeException(
                                "Cannot serialize " + input.getClass().getSimpleName() + " as Instant");
                    }

                    @Override
                    public Instant parseValue(Object input, GraphQLContext context, Locale locale) throws CoercingParseValueException {
                        try {
                            return Instant.parse(input.toString());
                        } catch (DateTimeParseException e) {
                            throw new CoercingParseValueException("Invalid Instant value: " + input);
                        }
                    }

                    @Override
                    public Instant parseLiteral(Value<?> input, CoercedVariables variables,
                            GraphQLContext context, Locale locale) throws CoercingParseLiteralException {
                        if (input instanceof StringValue sv) {
                            try {
                                return Instant.parse(sv.getValue());
                            } catch (DateTimeParseException e) {
                                throw new CoercingParseLiteralException("Invalid Instant literal: " + sv.getValue());
                            }
                        }
                        throw new CoercingParseLiteralException("Expected a string literal for Instant");
                    }
                })
                .build();
    }
}
