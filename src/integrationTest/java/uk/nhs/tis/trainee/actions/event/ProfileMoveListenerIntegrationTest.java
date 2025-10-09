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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bson.types.ObjectId;
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
import uk.nhs.tis.trainee.actions.model.ActionType;

/**
 * Integration tests for the {@link ProfileMoveListener}.
 */
@SpringBootTest
@Testcontainers
public class ProfileMoveListenerIntegrationTest {
  private static final String FROM_TRAINEE_ID = UUID.randomUUID().toString();
  private static final String TO_TRAINEE_ID = UUID.randomUUID().toString();

  private static final String PROFILE_MOVE_QUEUE = UUID.randomUUID().toString();

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
    registry.add("application.queues.profile-move", () -> PROFILE_MOVE_QUEUE);

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
        PROFILE_MOVE_QUEUE);
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
  void shouldMoveAllActionsWhenProfileMove() throws JsonProcessingException {
    ObjectId id1 = ObjectId.get();
    Action actionToMove1 = new Action(id1, ActionType.SIGN_COJ, FROM_TRAINEE_ID, null,
        LocalDate.MIN, LocalDate.MAX, Instant.MIN);
    mongoTemplate.insert(actionToMove1);

    ObjectId id2 = ObjectId.get();
    Action actionToMove2 = new Action(id2, ActionType.SIGN_FORM_R_PART_A,
        FROM_TRAINEE_ID, null, LocalDate.EPOCH, LocalDate.EPOCH, null);
    mongoTemplate.insert(actionToMove2);

    String eventString = """
        {
          "fromTraineeId": "%s",
          "toTraineeId": "%s"
        }""".formatted(FROM_TRAINEE_ID, TO_TRAINEE_ID);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(PROFILE_MOVE_QUEUE, eventJson);

    Criteria criteria = Criteria.where("traineeId").is(TO_TRAINEE_ID);
    Query query = Query.query(criteria);
    List<Action> actions = new ArrayList<>();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<Action> found = mongoTemplate.find(query, Action.class);
          assertThat("Unexpected moved action count.", found.size(),
              is(2));
          actions.addAll(found);
        });

    Action movedAction1 = actions.stream()
        .filter(a -> a.id().equals(id1))
        .findFirst()
        .orElseThrow();
    assertThat("Unexpected moved action trainee.", movedAction1.traineeId(),
        is(TO_TRAINEE_ID));
    assertThat("Unexpected moved action data.", movedAction1.withTraineeId(FROM_TRAINEE_ID),
        is(actionToMove1));

    Action movedAction2 = actions.stream()
        .filter(a -> a.id().equals(id2))
        .findFirst()
        .orElseThrow();
    assertThat("Unexpected moved action trainee.", movedAction2.traineeId(),
        is(TO_TRAINEE_ID));
    assertThat("Unexpected moved action data.", movedAction2.withTraineeId(FROM_TRAINEE_ID),
        is(actionToMove2));
  }

  @Test
  void shouldNotMoveUnexpectedActionsWhenProfileMove() throws JsonProcessingException {
    ObjectId id1 = ObjectId.get();
    Action actionToMove1 = new Action(id1, ActionType.SIGN_COJ, TO_TRAINEE_ID, null,
        LocalDate.MIN, LocalDate.MAX, Instant.MIN);
    mongoTemplate.insert(actionToMove1);

    ObjectId id2 = ObjectId.get();
    String anotherTraineeId = UUID.randomUUID().toString();
    Action actionToMove2 = new Action(id2, ActionType.SIGN_FORM_R_PART_A,
        anotherTraineeId, null, LocalDate.EPOCH, LocalDate.EPOCH, null);
    mongoTemplate.insert(actionToMove2);

    String eventString = """
        {
          "fromTraineeId": "%s",
          "toTraineeId": "%s"
        }""".formatted(FROM_TRAINEE_ID, TO_TRAINEE_ID);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(PROFILE_MOVE_QUEUE, eventJson);

    Criteria criteria = Criteria.where("traineeId").ne(FROM_TRAINEE_ID);
    Query query = Query.query(criteria);
    List<Action> actions = new ArrayList<>();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<Action> found = mongoTemplate.find(query, Action.class);
          assertThat("Unexpected unchanged action count.", found.size(),
              is(2));
          actions.addAll(found);
        });

    Action unchangedAction1 = actions.stream()
        .filter(a -> a.id().equals(id1))
        .findFirst()
        .orElseThrow();
    assertThat("Unexpected unchanged action data.", unchangedAction1, is(actionToMove1));

    Action unchangedAction2 = actions.stream()
        .filter(a -> a.id().equals(id2))
        .findFirst()
        .orElseThrow();
    assertThat("Unexpected unchanged action data.", unchangedAction2, is(actionToMove2));
  }
}
