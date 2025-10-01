/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package uk.nhs.tis.trainee.actions.event;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.actions.service.ActionService.FORM_PROGRAMME_MEMBERSHIP_ID_FIELD;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.actions.DockerImageNames;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.model.ActionType;

@SpringBootTest
@Testcontainers
public class FormListenerIntegrationTest {

  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final String PROGRAMME_MEMBERSHIP_ID = UUID.randomUUID().toString();
  private static final LocalDate NOW = LocalDate.now();
  private static final LocalDate START_DATE = NOW.plusDays(1);
  private static final Instant FORM_UPDATED_TIME = Instant.now();
  private static final String FORM_UPDATED_QUEUE = UUID.randomUUID().toString();

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Container
  private static final LocalStackContainer localstack = new LocalStackContainer(
      DockerImageNames.LOCALSTACK)
      .withServices(SQS);

  @DynamicPropertySource
  private static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("application.queues.form-updated", () -> FORM_UPDATED_QUEUE);

    registry.add("spring.cloud.aws.region.static", localstack::getRegion);
    registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
    registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
    registry.add("spring.cloud.aws.sqs.endpoint",
        () -> localstack.getEndpointOverride(SQS).toString());
    registry.add("spring.cloud.aws.sqs.enabled", () -> true);
  }

  @BeforeAll
  static void setUpBeforeAll() throws IOException, InterruptedException {
    localstack.execInContainer("awslocal sqs create-queue --queue-name", FORM_UPDATED_QUEUE);
  }

  @Autowired
  private SqsTemplate sqsTemplate;

  @Autowired
  private MongoTemplate mongoTemplate;

  @AfterEach
  void cleanUp() {
    mongoTemplate.findAllAndRemove(new Query(), Action.class);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      formr-a | SUBMITTED
      formr-b | SUBMITTED
      """)
  void shouldCompleteFormRActionWhenFormStatusComplete(String formType, String formStatus)
      throws Exception {
    ActionType actionType = ActionType.getFormActionType(formType);
    Action existingAction = new Action(null, actionType, TRAINEE_ID,
        new Action.TisReferenceInfo(PROGRAMME_MEMBERSHIP_ID, PROGRAMME_MEMBERSHIP),
        NOW, START_DATE, null);
    mongoTemplate.insert(existingAction);

    String eventString = """
        {
          "formName": "formName.json",
          "lifecycleState": "%s",
          "traineeId": "%s",
          "formType": "%s",
          "eventDate": "%s",
          "formContentDto": {
            "%s": "%s"
          }
        }""".formatted(formStatus, TRAINEE_ID, formType, FORM_UPDATED_TIME,
        FORM_PROGRAMME_MEMBERSHIP_ID_FIELD, PROGRAMME_MEMBERSHIP_ID);

    JsonNode eventJson = JsonMapper.builder().build().readTree(eventString);
    sqsTemplate.send(FORM_UPDATED_QUEUE, eventJson);

    Criteria criteria = Criteria.where("traineeId").is(TRAINEE_ID)
        .and("type").is(actionType.toString());
    Query query = Query.query(criteria);

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          List<Action> found = mongoTemplate.find(query, Action.class);
          assertThat("Unexpected action count.", found.size(), is(1));

          Action action = found.get(0);
          assertThat("Unexpected completed date.", action.completed(), is(FORM_UPDATED_TIME));
        });
  }

  //as all permutations are tested in the ActionServiceTest, we only test a few here.
  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      formr-a | REJECTED
      formr-a | UNSUBMITTED
      formr-b | DELETED
      formr-b | DRAFT
      formr-b | WITHDRAWN
      """)
  void shouldUncompleteFormRActionWhenFormIncomplete(String formType, String formStatus)
      throws Exception {
    ActionType actionType = ActionType.getFormActionType(formType);
    Instant completedTime = Instant.now();
    Action existingAction = new Action(null, actionType, TRAINEE_ID,
        new Action.TisReferenceInfo(PROGRAMME_MEMBERSHIP_ID, PROGRAMME_MEMBERSHIP),
        NOW, START_DATE, completedTime);
    mongoTemplate.insert(existingAction);

    String eventString = """
        {
          "formName": "formName.json",
          "lifecycleState": "%s",
          "traineeId": "%s",
          "formType": "%s",
          "eventDate": "%s",
          "formContentDto": {
            "%s": "%s"
          }
        }""".formatted(formStatus, TRAINEE_ID, formType, FORM_UPDATED_TIME,
        FORM_PROGRAMME_MEMBERSHIP_ID_FIELD, PROGRAMME_MEMBERSHIP_ID);

    JsonNode eventJson = JsonMapper.builder().build().readTree(eventString);
    sqsTemplate.send(FORM_UPDATED_QUEUE, eventJson);

    Criteria criteria = Criteria.where("traineeId").is(TRAINEE_ID)
        .and("type").is(actionType.toString());
    Query query = Query.query(criteria);

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          List<Action> found = mongoTemplate.find(query, Action.class);
          assertThat("Unexpected action count.", found.size(), is(1));

          Action action = found.get(0);
          assertThat("Unexpected completed date.", action.completed(), nullValue());
        });
  }
}
