package com.example.transporters.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class JacksonConfiguration {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictStringValues() {
        // Jackson 2.13 coerces numbers and booleans to String despite ALLOW_COERCION_OF_SCALARS=false.
        return builder -> builder.deserializerByType(String.class, new JsonDeserializer<String>() {
            @Override
            public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                if (parser.hasToken(JsonToken.VALUE_STRING)) {
                    return parser.getText();
                }
                return (String) context.handleUnexpectedToken(String.class, parser);
            }
        });
    }
}
