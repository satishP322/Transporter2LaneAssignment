package com.example.transporters.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:api-test;DB_CLOSE_DELAY=-1}")
@AutoConfigureMockMvc
class TransporterApiTest {
    private static final String INPUT_URL = "/api/v1/transporters/input";
    private static final String ASSIGNMENT_URL = "/api/v1/transporters/assignment";
    private static final String LANE = "{\"id\":1,\"origin\":\"Mumbai\",\"destination\":\"Delhi\"}";
    private static final String QUOTE = "{\"laneId\":1,\"quote\":1}";
    private static final String TRANSPORTER = "{\"id\":1,\"name\":\"Transporter T1\",\"laneQuotes\":[" + QUOTE + "]}";
    private static final String MINIMAL_INPUT = input(LANE, TRANSPORTER);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearStoredInput() {
        jdbc.update("DELETE FROM input_dataset");
    }

    @Test
    void returnsActionableErrorWhenNoInputHasBeenSubmitted() throws Exception {
        assign("{\"maxTransporters\":3}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("NO_INPUT_DATA"));
    }

    @Test
    void suppliedCsvProducesTheExactOptimalAssignment() throws Exception {
        submit(fixture("csv-input.json"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"success\",\"message\":\"Input data saved successfully.\"}"));

        assign("{\"maxTransporters\":3}")
                .andExpect(status().isOk())
                .andExpect(content().json("{"
                        + "  \"status\":\"success\","
                        + "  \"totalCost\":134876,"
                        + "  \"assignments\":["
                        + "    {\"laneId\":1,\"transporterId\":4},"
                        + "    {\"laneId\":2,\"transporterId\":1},"
                        + "    {\"laneId\":3,\"transporterId\":4},"
                        + "    {\"laneId\":4,\"transporterId\":4},"
                        + "    {\"laneId\":5,\"transporterId\":4},"
                        + "    {\"laneId\":6,\"transporterId\":1},"
                        + "    {\"laneId\":7,\"transporterId\":1},"
                        + "    {\"laneId\":8,\"transporterId\":6},"
                        + "    {\"laneId\":9,\"transporterId\":6}"
                        + "  ],"
                        + "  \"selectedTransporters\":[1,4,6]"
                        + "}"));
    }

    @Test
    void usesAllSevenCsvTransportersWhenTheLimitAllowsIt() throws Exception {
        submit(fixture("csv-input.json")).andExpect(status().isOk());

        assign("{\"maxTransporters\":7}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(129580))
                .andExpect(jsonPath("$.selectedTransporters", contains(1, 2, 3, 4, 5, 6, 7)));
    }

    @Test
    void firstPdfTestCaseUsesItsQuotesToComputeTheResult() throws Exception {
        submit(fixture("case-1-input.json")).andExpect(status().isOk());

        assign("{\"maxTransporters\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(60139))
                .andExpect(jsonPath("$.assignments", hasSize(5)))
                .andExpect(jsonPath("$.selectedTransporters", contains(1, 4, 5)));
    }

    @Test
    void sparseQuotesRespectTheLimitAndRecoverWithAFeasibleLimit() throws Exception {
        submit(fixture("case-2-input.json")).andExpect(status().isOk());

        assign("{\"maxTransporters\":2}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INFEASIBLE_ASSIGNMENT"));
        assign("{\"maxTransporters\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(7500))
                .andExpect(jsonPath("$.assignments", hasSize(5)))
                .andExpect(jsonPath("$.selectedTransporters", contains(1, 2, 3)));
        assign("{\"maxTransporters\":4}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(7450))
                .andExpect(jsonPath("$.selectedTransporters", hasSize(4)));
    }

    @Test
    void originalPdfExampleRequiresFiveTransportersForCoverage() throws Exception {
        submit(fixture("sparse-input.json")).andExpect(status().isOk());

        assign("{\"maxTransporters\":3}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INFEASIBLE_ASSIGNMENT"));
        assign("{\"maxTransporters\":5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(48000))
                .andExpect(jsonPath("$.assignments", hasSize(10)));
    }

    @Test
    void successfulInputAtomicallyReplacesThePreviousDatasetAndSupportsRetries() throws Exception {
        submit(fixture("csv-input.json")).andExpect(status().isOk());
        submit(fixture("case-2-input.json")).andExpect(status().isOk());
        submit(fixture("case-2-input.json")).andExpect(status().isOk());

        String first = assign("{\"maxTransporters\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(7500))
                .andExpect(jsonPath("$.assignments", hasSize(5)))
                .andReturn().getResponse().getContentAsString();
        String second = assign("{\"maxTransporters\":3}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM input_dataset", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentReplacementsExposeOnlyCompleteDatasets() throws Exception {
        String csv = fixture("csv-input.json");
        String sparse = fixture("case-2-input.json");
        submit(csv).andExpect(status().isOk());
        String csvResult = assign("{\"maxTransporters\":3}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        submit(sparse).andExpect(status().isOk());
        String sparseResult = assign("{\"maxTransporters\":3}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<Void> firstWriter = executor.submit(repeatedSubmission(csv, start));
            Future<Void> secondWriter = executor.submit(repeatedSubmission(sparse, start));
            Future<Void> reader = executor.submit((Callable<Void>) () -> {
                start.await();
                for (int index = 0; index < 30; index++) {
                    String result = assign("{\"maxTransporters\":3}")
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                    assertThat(result).isIn(csvResult, sparseResult);
                }
                return null;
            });
            start.countDown();
            firstWriter.get(20, TimeUnit.SECONDS);
            secondWriter.get(20, TimeUnit.SECONDS);
            reader.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM input_dataset", Integer.class)).isEqualTo(1);
    }

    @Test
    void supportsZeroAndFractionalAmountsWithoutRoundingTheSum() throws Exception {
        submit("{"
                        + "  \"lanes\":["
                        + "    {\"id\":1,\"origin\":\"A\",\"destination\":\"B\"},"
                        + "    {\"id\":2,\"origin\":\"B\",\"destination\":\"C\"},"
                        + "    {\"id\":3,\"origin\":\"C\",\"destination\":\"D\"}"
                        + "  ],"
                        + "  \"transporters\":["
                        + "    {\"id\":8,\"name\":\"Decimal quotes\",\"laneQuotes\":["
                        + "      {\"laneId\":1,\"quote\":0.10},"
                        + "      {\"laneId\":2,\"quote\":0.20},"
                        + "      {\"laneId\":3,\"quote\":0}"
                        + "    ]}"
                        + "  ]"
                        + "}").andExpect(status().isOk());

        assign("{\"maxTransporters\":999}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(0.3))
                .andExpect(jsonPath("$.assignments", hasSize(3)))
                .andExpect(jsonPath("$.selectedTransporters", contains(8)));
    }

    @Test
    void costTiesUseAsManyTransportersAsPossible() throws Exception {
        submit("{"
                        + "  \"lanes\":["
                        + "    {\"id\":1,\"origin\":\"A\",\"destination\":\"B\"},"
                        + "    {\"id\":2,\"origin\":\"B\",\"destination\":\"C\"}"
                        + "  ],"
                        + "  \"transporters\":["
                        + "    {\"id\":1,\"name\":\"T1\",\"laneQuotes\":[{\"laneId\":1,\"quote\":1},{\"laneId\":2,\"quote\":1}]},"
                        + "    {\"id\":2,\"name\":\"T2\",\"laneQuotes\":[{\"laneId\":1,\"quote\":1},{\"laneId\":2,\"quote\":1}]}"
                        + "  ]"
                        + "}").andExpect(status().isOk());

        assign("{\"maxTransporters\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(2))
                .andExpect(jsonPath("$.selectedTransporters", contains(1, 2)));
    }

    @Test
    void usesTheMaximumFeasibleTransporterCountEvenWhenThatRaisesCost() throws Exception {
        submit("{"
                        + "  \"lanes\":["
                        + "    {\"id\":1,\"origin\":\"A\",\"destination\":\"B\"},"
                        + "    {\"id\":2,\"origin\":\"B\",\"destination\":\"C\"}"
                        + "  ],"
                        + "  \"transporters\":["
                        + "    {\"id\":1,\"name\":\"T1\",\"laneQuotes\":[{\"laneId\":1,\"quote\":10},{\"laneId\":2,\"quote\":10}]},"
                        + "    {\"id\":2,\"name\":\"T2\",\"laneQuotes\":[{\"laneId\":1,\"quote\":11},{\"laneId\":2,\"quote\":11}]}"
                        + "  ]"
                        + "}").andExpect(status().isOk());

        assign("{\"maxTransporters\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(21))
                .andExpect(jsonPath("$.selectedTransporters", contains(1, 2)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInputs")
    void invalidInputPreservesTheLastSuccessfulDataset(String description, String payload, String errorCode)
            throws Exception {
        submit(MINIMAL_INPUT).andExpect(status().isOk());

        submit(payload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value(errorCode));

        assign("{\"maxTransporters\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(1))
                .andExpect(jsonPath("$.assignments", hasSize(1)));
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                invalid("duplicate lanes", input(LANE + "," + LANE, TRANSPORTER)),
                invalid("duplicate transporters", input(LANE, TRANSPORTER + "," + TRANSPORTER)),
                invalid("duplicate quotes", MINIMAL_INPUT.replace(QUOTE, QUOTE + "," + QUOTE)),
                invalid("unknown quote lane", MINIMAL_INPUT.replace("\"laneId\":1", "\"laneId\":99")),
                invalid("uncovered lane", MINIMAL_INPUT.replace(QUOTE, "")),
                invalid("null lane", input("null", TRANSPORTER)),
                invalid("null transporter", input(LANE, "null")),
                invalid("null quote", MINIMAL_INPUT.replace(QUOTE, "null")),
                invalid("missing lanes", "{\"transporters\":[" + TRANSPORTER + "]}"),
                invalid("missing transporters", "{\"lanes\":[" + LANE + "]}"),
                invalid("empty lanes", input("", TRANSPORTER)),
                invalid("empty transporters", input(LANE, "")),
                invalid("null quotes", MINIMAL_INPUT.replace("[" + QUOTE + "]", "null")),
                invalid("blank origin", MINIMAL_INPUT.replace("Mumbai", " ")),
                invalid("blank destination", MINIMAL_INPUT.replace("Delhi", " ")),
                invalid("blank transporter name", MINIMAL_INPUT.replace("Transporter T1", " ")),
                invalid("non-positive identifiers", MINIMAL_INPUT.replace("\"id\":1", "\"id\":0")),
                invalid("negative quote", MINIMAL_INPUT.replace("\"quote\":1", "\"quote\":-1")),
                invalid("excess fractional precision", MINIMAL_INPUT.replace("\"quote\":1", "\"quote\":1.001")),
                invalid("quote over maximum", MINIMAL_INPUT.replace("\"quote\":1", "\"quote\":1000000000")),
                invalid("null quote amount", MINIMAL_INPUT.replace("\"quote\":1", "\"quote\":null")),
                invalid("too many lanes", input(repeated(LANE, 201), TRANSPORTER)),
                invalid("too many transporters", input(LANE, repeated(TRANSPORTER, 16))),
                malformed("malformed JSON", "{\"lanes\":["),
                malformed("trailing input object", MINIMAL_INPUT.replace("\"quote\":1", "\"quote\":2") + " {}"),
                malformed("trailing input garbage", MINIMAL_INPUT.replace("\"quote\":1", "\"quote\":2") + " INVALID"),
                malformed("duplicate input property", MINIMAL_INPUT.replace("\"lanes\":", "\"lanes\":[],\"lanes\":")),
                malformed("duplicate quote property", MINIMAL_INPUT.replace("\"quote\":1", "\"quote\":99,\"quote\":2")),
                malformed("numeric origin", MINIMAL_INPUT.replace("\"Mumbai\"", "42")),
                malformed("fractional destination", MINIMAL_INPUT.replace("\"Delhi\"", "1.5")),
                malformed("boolean transporter name", MINIMAL_INPUT.replace("\"Transporter T1\"", "true")),
                malformed("unknown property", MINIMAL_INPUT.replace("\"lanes\":", "\"unexpected\":true,\"lanes\":")),
                malformed("fractional identifier", MINIMAL_INPUT.replace("\"id\":1", "\"id\":1.5")),
                malformed("string identifier", MINIMAL_INPUT.replace("\"id\":1", "\"id\":\"1\"")),
                malformed("string quote", MINIMAL_INPUT.replace("\"quote\":1", "\"quote\":\"1\""))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAssignmentRequests")
    void rejectsInvalidLimitsBeforeLookingUpData(String description, String payload, String errorCode)
            throws Exception {
        assign(payload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(errorCode));
    }

    static Stream<Arguments> invalidAssignmentRequests() {
        return Stream.of(
                invalid("missing limit", "{}"),
                invalid("null limit", "{\"maxTransporters\":null}"),
                invalid("zero limit", "{\"maxTransporters\":0}"),
                invalid("negative limit", "{\"maxTransporters\":-1}"),
                malformed("fractional limit", "{\"maxTransporters\":1.5}"),
                malformed("string limit", "{\"maxTransporters\":\"3\"}"),
                malformed("trailing assignment object", "{\"maxTransporters\":1} {\"maxTransporters\":2}"),
                malformed("trailing assignment garbage", "{\"maxTransporters\":1} INVALID"),
                malformed("duplicate limit property", "{\"maxTransporters\":1,\"maxTransporters\":2}"),
                malformed("unknown limit field", "{\"maxTransporters\":3,\"maximum\":3}"),
                malformed("unrequested strategy field", "{\"maxTransporters\":3,\"strategy\":\"MAX_TRANSPORTERS\"}"),
                malformed("null request body", "null"),
                malformed("limit out of integer range", "{\"maxTransporters\":2147483648}")
        );
    }

    @Test
    void publishesInteractiveSwaggerAndTheTwoDocumentedEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/transporters/input'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transporters/assignment'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transporters/input'].post.requestBody"
                        + ".content['application/json'].examples", aMapWithSize(4)))
                .andExpect(jsonPath("$.paths['/api/v1/transporters/assignment'].post.requestBody"
                        + ".content['application/json'].examples", aMapWithSize(1)))
                .andExpect(jsonPath("$.components.schemas.AssignmentRequest.required", contains("maxTransporters")))
                .andExpect(jsonPath("$.components.schemas.AssignmentRequest.properties", aMapWithSize(1)))
                .andExpect(jsonPath("$.components.schemas.AssignmentRequest.properties.strategy").doesNotExist());
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }

    @Test
    void unsupportedHttpMethodReturns405() throws Exception {
        mvc.perform(get(INPUT_URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("HTTP_405"));
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mvc.perform(post(INPUT_URL).contentType(MediaType.APPLICATION_XML).content("<input/>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("HTTP_415"));
    }

    @Test
    void unknownEndpointReturns404() throws Exception {
        mvc.perform(get("/api/v1/transporters/unknown"))
                .andExpect(status().isNotFound());
    }

    private ResultActions submit(String input) throws Exception {
        return mvc.perform(post(INPUT_URL).contentType(MediaType.APPLICATION_JSON).content(input));
    }

    private ResultActions assign(String request) throws Exception {
        return mvc.perform(post(ASSIGNMENT_URL).contentType(MediaType.APPLICATION_JSON).content(request));
    }

    private Callable<Void> repeatedSubmission(String payload, CountDownLatch start) {
        return () -> {
            start.await();
            for (int index = 0; index < 20; index++) {
                submit(payload).andExpect(status().isOk());
            }
            return null;
        };
    }

    private static String fixture(String filename) throws Exception {
        try (InputStream input = new ClassPathResource("examples/" + filename).getInputStream()) {
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        }
    }

    private static String input(String lanes, String transporters) {
        return "{\"lanes\":[" + lanes + "],\"transporters\":[" + transporters + "]}";
    }

    private static String repeated(String value, int count) {
        return String.join(",", IntStream.range(0, count).mapToObj(index -> value).collect(Collectors.toList()));
    }

    private static Arguments invalid(String description, String payload) {
        return Arguments.of(description, payload, "VALIDATION_ERROR");
    }

    private static Arguments malformed(String description, String payload) {
        return Arguments.of(description, payload, "INVALID_JSON");
    }
}
