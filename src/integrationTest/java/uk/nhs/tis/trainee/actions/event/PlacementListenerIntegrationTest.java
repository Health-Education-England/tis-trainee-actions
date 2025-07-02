/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
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
 */

package uk.nhs.tis.trainee.actions.event;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;
import static uk.nhs.tis.trainee.actions.event.Operation.LOAD;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PLACEMENT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
import uk.nhs.tis.trainee.actions.model.Action.TisReferenceInfo;
import uk.nhs.tis.trainee.actions.model.ActionType;

@SpringBootTest
@Testcontainers
class PlacementListenerIntegrationTest {

  private static final String PLACEMENT_ID = UUID.randomUUID().toString();
  private static final LocalDate START_DATE = LocalDate.now();

  private static final String PLACEMENT_SYNCED_QUEUE = UUID.randomUUID().toString();

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
    registry.add("application.queues.placement-synced",
        () -> PLACEMENT_SYNCED_QUEUE);

    registry.add("spring.cloud.aws.region.static", localstack::getRegion);
    registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
    registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
    registry.add("spring.cloud.aws.sqs.endpoint",
        () -> localstack.getEndpointOverride(SQS).toString());
    registry.add("spring.cloud.aws.sqs.enabled", () -> true);
  }

  @BeforeAll
  static void setUpBeforeAll() throws IOException, InterruptedException {
    localstack.execInContainer("awslocal sqs create-queue --queue-name",
        PLACEMENT_SYNCED_QUEUE);
  }

  @Autowired
  private SqsTemplate sqsTemplate;

  @Autowired
  private MongoTemplate mongoTemplate;

  @AfterEach
  void cleanUp() {
    mongoTemplate.findAllAndRemove(new Query(), Action.class);
  }

  @Test
  void shouldInsertAllActionsWhenPlacementCreated() throws JsonProcessingException {
    String traineeId = UUID.randomUUID().toString();
    String eventString = """
        {
          "record": {
            "data": {
              "tisId": "%s",
              "traineeId": "%s",
              "dateFrom": "%s",
              "placementType": "In post"
            },
            "operation": "%s"
          }
        }""".formatted(PLACEMENT_ID, traineeId, START_DATE, LOAD);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(PLACEMENT_SYNCED_QUEUE, eventJson);

    Criteria criteria = Criteria.where("traineeId").is(traineeId);
    Query query = Query.query(criteria);
    List<Action> actions = new ArrayList<>();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<Action> found = mongoTemplate.find(query, Action.class);
          assertThat("Unexpected action count.", found.size(),
              is(ActionType.getPlacementActionTypes().size()));
          actions.addAll(found);
        });

    for (ActionType actionType : ActionType.getPlacementActionTypes()) {
      Optional<Action> actionOptional = actions.stream()
          .filter(a -> a.type().equals(actionType))
          .findFirst();

      assertThat("Missing action for type: " + actionType, actionOptional.isPresent(), is(true));
      Action action = actionOptional.get();
      assertThat("Unexpected action id.", action.id(), notNullValue());
      assertThat("Unexpected action type.", action.type(), is(actionType));
      assertThat("Unexpected trainee id.", action.traineeId(), is(traineeId));
      assertThat("Unexpected available from date.", action.availableFrom(),
          is(START_DATE.minusWeeks(12)));
      assertThat("Unexpected due by date.", action.dueBy(), is(START_DATE));
      assertThat("Unexpected completed date.", action.completed(), nullValue());

      TisReferenceInfo tisReference = action.tisReferenceInfo();
      assertThat("Unexpected TIS id.", tisReference.id(), is(PLACEMENT_ID));
      assertThat("Unexpected TIS type.", tisReference.type(), is(PLACEMENT));
    }
  }
}
