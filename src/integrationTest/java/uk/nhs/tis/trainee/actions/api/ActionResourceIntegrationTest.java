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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.nhs.tis.trainee.actions.model.ActionType.REVIEW_DATA;
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
  private static final LocalDate START_DATE = LocalDate.now();

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
  void shouldReturnBadRequestWhenNoAuthorizationHeader() throws Exception {
    mockMvc.perform(get("/api/action"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnEmptyArrayWhenNoTraineeActionsFound() throws Exception {
    String payload = String.format("{\"%s\":\"%s\"}", "custom:tisId", "40");
    String encodedPayload = Base64.getEncoder()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    String token = String.format("aa.%s.cc", encodedPayload);

    mockMvc.perform(get("/api/action")
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void shouldReturnActionsOrderedByDueDate() throws Exception {
    TisReferenceInfo referenceInfo1 = new TisReferenceInfo(TIS_ID_1, PROGRAMME_MEMBERSHIP);
    Action action1 = new Action(ObjectId.get(), REVIEW_DATA, TRAINEE_ID, referenceInfo1,
        START_DATE.plusDays(1), null);
    TisReferenceInfo referenceInfo2 = new TisReferenceInfo(TIS_ID_2, PROGRAMME_MEMBERSHIP);
    Action action2 = new Action(ObjectId.get(), REVIEW_DATA, TRAINEE_ID, referenceInfo2,
        START_DATE.minusDays(1), null);
    TisReferenceInfo referenceInfo3 = new TisReferenceInfo(TIS_ID_3, PROGRAMME_MEMBERSHIP);
    Action action3 = new Action(ObjectId.get(), REVIEW_DATA, TRAINEE_ID, referenceInfo3, START_DATE,
        null);

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
    Action action1 = new Action(ObjectId.get(), REVIEW_DATA, TRAINEE_ID, referenceInfo1, START_DATE,
        null);
    TisReferenceInfo referenceInfo2 = new TisReferenceInfo(TIS_ID_2, PROGRAMME_MEMBERSHIP);
    Action action2 = new Action(ObjectId.get(), REVIEW_DATA, TRAINEE_ID, referenceInfo2, START_DATE,
        Instant.now());
    mongoTemplate.insertAll(List.of(action1, action2));

    mockMvc.perform(get("/api/action")
            .header(HttpHeaders.AUTHORIZATION, getValidToken()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$.[0].id").value(action1.id().toString()));
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
