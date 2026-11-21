package com.example.transporters.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class OpenApiConfiguration implements WebMvcConfigurer {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("Transporter Assignment API").version("1.0.0")
                .description("Full lane coverage with maximum feasible transporter usage within the limit, "
                        + "and minimum cost for that usage. First submit input, then request an assignment. "
                        + "Use the request example selector to try the supplied CSV or the PDF cases. "
                        + "The CSV with maxTransporters=3 produces totalCost=134876 and transporters [1,4,6]. "
                        + "Each successful input request replaces the shared dataset used by all clients."));
    }

    @Bean
    public OpenApiCustomiser requestExamples(ObjectMapper mapper) throws IOException {
        Map<String, Example> examples = new LinkedHashMap<>();
        examples.put("Supplied CSV · 9 lanes", example(mapper, "csv-input.json",
                "9 lanes, 7 transporters. With limit 3 the optimal cost is 134876."));
        examples.put("PDF case 1 · 5 lanes", example(mapper, "case-1-input.json",
                "With limit 3 the verified optimal cost is 60139."));
        examples.put("PDF case 2 · sparse quotes", example(mapper, "case-2-input.json",
                "Limit 2 is infeasible. Limit 3 costs 7500. Limit 4 costs 7450."));
        examples.put("PDF introductory example · 10 lanes", example(mapper, "sparse-input.json",
                "Requires at least 5 transporters. With limit 5 the optimal cost is 48000."));
        return api -> {
            api.getPaths().get("/api/v1/transporters/input").getPost().getRequestBody()
                    .getContent().get("application/json").setExamples(examples);
            api.getPaths().get("/api/v1/transporters/assignment").getPost().getRequestBody()
                    .getContent().get("application/json").addExamples("Three transporters", new Example()
                            .value(Collections.singletonMap("maxTransporters", 3)));
        };
    }

    private Example example(ObjectMapper mapper, String filename, String description) throws IOException {
        try (InputStream input = new ClassPathResource("examples/" + filename).getInputStream()) {
            return new Example().description(description).value(mapper.readValue(input, Object.class));
        }
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/swagger-ui/index.html");
    }
}
