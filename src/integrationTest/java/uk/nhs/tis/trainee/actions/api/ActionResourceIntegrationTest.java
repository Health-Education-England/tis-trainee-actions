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

package uk.nhs.tis.trainee.actions.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.nhs.tis.trainee.actions.model.ActionType.REGISTER_TSS;
import static uk.nhs.tis.trainee.actions.model.ActionType.REVIEW_DATA;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PERSON;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PLACEMENT;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PROGRAMME_MEMBERSHIP;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.actions.DockerImageNames;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.model.Action.TisReferenceInfo;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class ActionResourceIntegrationTest {

  private static final String TIS_ID_1 = UUID.randomUUID().toString();
  private static final String TIS_ID_2 = UUID.randomUUID().toString();
  private static final String TIS_ID_3 = UUID.randomUUID().toString();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final LocalDate NOW = LocalDate.now();
  private static final LocalDate PAST = NOW.minusDays(1);
  private static final LocalDate FUTURE = NOW.plusDays(1);

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MongoTemplate mongoTemplate;

  @AfterEach
  void cleanUp() {
    mongoTemplate.findAllAndRemove(new Query(), Action.class);
  }

  @Test
  void shouldReturnBadRequestWhenGettingActionWithNoAuthorizationHeader() throws Exception {
    mockMvc.perform(get("/api/action"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnEmptyArrayWhenNoTraineeActionsFound() throws Exception {
    mockMvc.perform(get("/api/action")
            .header(HttpHeaders.AUTHORIZATION, getValidToken()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void shouldReturnActionsOrderedByDueByDate() throws Exception {
    TisReferenceInfo referenceInfo1 = new TisReferenceInfo(TIS_ID_1, PROGRAMME_MEMBERSHIP);
    Action action1 = new Action(ObjectId.get(), REVIEW_DATA, TRAINEE_ID, referenceInfo1, null,
        FUTURE, null);
    TisReferenceInfo referenceInfo2 = new TisReferenceInfo(TIS_ID_2, PROGRAMME_MEMBERSHIP);
    Action action2 = new Action(ObjectId.get(), REVIEW_DATA, TRAINEE_ID, referenceInfo2, null, PAST,
        null);
    TisReferenceInfo referenceInfo3 = new TisReferenceInfo(TIS_ID_3, PROGRAMME_MEMBERSHIP);
    Action action3 = new Action(ObjectId.get(), REVIEW_DATA, TRAINEE_ID, referenceInfo3, null,
        NOW, null);

    mongoTemplate.insertAll(List.of(action1, action2, action3));
    mockMvc.perform(get("/api/action")
            .header(HttpHeaders.AUTHORIZATION, getValidToken()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$.[0].id").value(action2.id().toString()))
        .andExpect(jsonPath("$.[1].id").value(action3.id().toString()))
        .andExpect(jsonPath("$.[2].id").value(action1.id().toString()));
  }

  @Test
  void shouldReturnOnlyIncompleteActions() throws Exception {
    TisReferenceInfo referenceInfo1 = new TisReferenceInfo(TIS_ID_1, PROGRAMME_MEMBERSHIP);
    Action action1 = new Action(ObjectId.get(), REVIEW_DATA, TRAINEE_ID, referenceInfo1, null,
        NOW, null);
    TisReferenceInfo referenceInfo2 = new TisReferenceInfo(TIS_ID_2, PROGRAMME_MEMBERSHIP);
    Action action2 = new Action(ObjectId.get(), REVIEW_DATA, TRAINEE_ID, referenceInfo2, null,
        NOW, Instant.now());
    mongoTemplate.insertAll(List.of(action1, action2));

    mockMvc.perform(get("/api/action")
            .header(HttpHeaders.AUTHORIZATION, getValidToken()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$.[0].id").value(action1.id().toString()))
        .andExpect(jsonPath("$.[0].completed").isEmpty());
  }

  @Test
  void shouldReturnBadRequestWhenCompletingActionWithNoAuthorizationHeader() throws Exception {
    mockMvc.perform(post("/api/action/{actionId}/complete", new ObjectId()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnNotFoundWhenActionToCompleteNotFound() throws Exception {
    mockMvc.perform(post("/api/action/{actionId}/complete", new ObjectId())
            .header(HttpHeaders.AUTHORIZATION, getValidToken()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnNotFoundWhenActionToCompleteHasTraineeMismatch() throws Exception {
    TisReferenceInfo referenceInfo = new TisReferenceInfo(TIS_ID_1, PROGRAMME_MEMBERSHIP);
    Action action = new Action(null, REVIEW_DATA, "40", referenceInfo, null, NOW, null);
    action = mongoTemplate.insert(action);

    mockMvc.perform(post("/api/action/{actionId}/complete", action.id())
            .header(HttpHeaders.AUTHORIZATION, getValidToken()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnNotFoundWhenActionToCompleteAlreadyCompleted() throws Exception {
    TisReferenceInfo referenceInfo = new TisReferenceInfo(TIS_ID_1, PROGRAMME_MEMBERSHIP);
    Action action = new Action(null, REVIEW_DATA, TRAINEE_ID, referenceInfo, null, NOW,
        Instant.now());
    action = mongoTemplate.insert(action);

    mockMvc.perform(post("/api/action/{actionId}/complete", action.id())
            .header(HttpHeaders.AUTHORIZATION, getValidToken()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnCompletedActionWhenActionToCompleteCanBeCompleted() throws Exception {
    TisReferenceInfo referenceInfo = new TisReferenceInfo(TIS_ID_1, PROGRAMME_MEMBERSHIP);
    Action action = new Action(null, REVIEW_DATA, TRAINEE_ID, referenceInfo, null, NOW, null);
    action = mongoTemplate.insert(action);

    mockMvc.perform(post("/api/action/{actionId}/complete", action.id())
            .header(HttpHeaders.AUTHORIZATION, getValidToken()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").exists())
        .andExpect(jsonPath("$.id").value(action.id().toString()))
        .andExpect(jsonPath("$.type").value(REVIEW_DATA.toString()))
        .andExpect(jsonPath("$.traineeId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.tisReferenceInfo").exists())
        .andExpect(jsonPath("$.tisReferenceInfo.id").value(TIS_ID_1))
        .andExpect(jsonPath("$.tisReferenceInfo.type").value(PROGRAMME_MEMBERSHIP.toString()))
        .andExpect(jsonPath("$.dueBy").value(NOW.toString()))
        .andExpect(jsonPath("$.completed").isNotEmpty());
  }

  @Test
  void shouldReturnEmptyArrayWhenNoTraineeProgrammeActionsFound() throws Exception {
    mockMvc.perform(get("/api/action/{traineeId}/{programmeId}", TRAINEE_ID, TIS_ID_1))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void shouldReturnProgrammeAndPersonActionsWhenFound() throws Exception {
    TisReferenceInfo programmeRef = new TisReferenceInfo(TIS_ID_1, PROGRAMME_MEMBERSHIP);
    Action programmeAction = new Action(ObjectId.get(), REVIEW_DATA, TRAINEE_ID, programmeRef,
        PAST, FUTURE, null);

    TisReferenceInfo personRef = new TisReferenceInfo(TRAINEE_ID, PERSON);
    Action personAction = new Action(ObjectId.get(), REGISTER_TSS, TRAINEE_ID, personRef,
        null, null, Instant.now());

    //should not be returned
    TisReferenceInfo placementRef = new TisReferenceInfo(TIS_ID_1, PLACEMENT);
    Action placementAction = new Action(ObjectId.get(), REVIEW_DATA, TRAINEE_ID, placementRef,
        null, null, Instant.now());

    mongoTemplate.insertAll(List.of(programmeAction, personAction, placementAction));

    mockMvc.perform(get("/api/action/{traineeId}/{programmeId}", TRAINEE_ID, TIS_ID_1))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(2))

        // Verify programme action
        .andExpect(jsonPath("$.[0].id").value(programmeAction.id().toString()))
        .andExpect(jsonPath("$.[0].type").value(REVIEW_DATA.toString()))
        .andExpect(jsonPath("$.[0].traineeId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.[0].availableFrom").value(PAST.toString()))
        .andExpect(jsonPath("$.[0].dueBy").value(FUTURE.toString()))
        .andExpect(jsonPath("$.[0].completed").isEmpty())
        .andExpect(jsonPath("$.[0].tisReferenceInfo.id").value(TIS_ID_1))
        .andExpect(jsonPath("$.[0].tisReferenceInfo.type").value(PROGRAMME_MEMBERSHIP.toString()))

        // Verify person action
        .andExpect(jsonPath("$.[1].id").value(personAction.id().toString()))
        .andExpect(jsonPath("$.[1].type").value(REGISTER_TSS.toString()))
        .andExpect(jsonPath("$.[1].traineeId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.[1].availableFrom").isEmpty())
        .andExpect(jsonPath("$.[1].dueBy").isEmpty())
        .andExpect(jsonPath("$.[1].completed").isNotEmpty())
        .andExpect(jsonPath("$.[1].tisReferenceInfo.id").value(TRAINEE_ID))
        .andExpect(jsonPath("$.[1].tisReferenceInfo.type").value(PERSON.toString()));
  }

  @Test
  void shouldReturnOkAndMoveActionsWhenActionsExist() throws Exception {
    String fromTraineeId = "fromTraineeId";
    String toTraineeId = "toTraineeId";
    TisReferenceInfo programmeRef = new TisReferenceInfo(TIS_ID_1, PROGRAMME_MEMBERSHIP);
    ObjectId id1 = ObjectId.get();
    Action action1 = new Action(id1, REVIEW_DATA, fromTraineeId, programmeRef, PAST,
        FUTURE, null);
    ObjectId id2 = ObjectId.get();
    Action action2 = new Action(id2, REGISTER_TSS, fromTraineeId, programmeRef, PAST,
        FUTURE, null);

    mongoTemplate.insertAll(List.of(action1, action2));

    mockMvc.perform(patch("/api/action/move/{fromTraineeId}/to/{toTraineeId}",
            fromTraineeId, toTraineeId))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").value(true));

    // Verify actions were moved
    Action movedAction1 = mongoTemplate.findById(id1, Action.class);
    assertThat("Unexpected missing action.", movedAction1, notNullValue());
    assertThat("Unexpected action trainee ID.", movedAction1.traineeId(), is(toTraineeId));
    assertThat("Unexpected changes to moved action.", movedAction1.withTraineeId(fromTraineeId),
        is(action1));
    Action movedAction2 = mongoTemplate.findById(id2, Action.class);
    assertThat("Unexpected missing action.", movedAction2, notNullValue());
    assertThat("Unexpected action trainee ID.", movedAction2.traineeId(), is(toTraineeId));
    assertThat("Unexpected changes to moved action.", movedAction2.withTraineeId(fromTraineeId),
        is(action2));
  }

  @Test
  void shouldReturnOkWhenNoActionsExist() throws Exception {
    String fromTraineeId = "fromTraineeId";
    String toTraineeId = "toTraineeId";

    mockMvc.perform(patch("/api/action/move/{fromTraineeId}/to/{toTraineeId}",
            fromTraineeId, toTraineeId))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").value(true));
  }

  /**
   * Generate a valid authentication token.
   *
   * @return The token.
   */
  private String getValidToken() {
    String payload = String.format("{\"%s\":\"%s\"}", "custom:tisId", TRAINEE_ID);
    String encodedPayload = Base64.getEncoder()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return String.format("aa.%s.cc", encodedPayload);
  }
}
