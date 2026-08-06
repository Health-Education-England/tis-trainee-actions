/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.nhs.tis.trainee.actions.model.ActionType.REVIEW_DATA;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PLACEMENT;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PROGRAMME_MEMBERSHIP;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.actions.DockerImageNames;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.model.Action.TisReferenceInfo;
import uk.nhs.tis.trainee.actions.service.EventPublishingService;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class JobResourceIntegrationTest {

  private static final String TRAINEE_ID = UUID.randomUUID().toString();

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MongoTemplate mongoTemplate;

  @MockitoBean
  private EventPublishingService eventPublishingService;

  @AfterEach
  void cleanUp() {
    mongoTemplate.findAllAndRemove(new Query(), Action.class);
  }

  @Test
  void shouldReturnZeroWhenNoActionsExistForRefresh() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/job/action/publish-refresh"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    assertThat("Unexpected publish count.", result.getResponse().getContentAsString(), is("0"));
    verifyNoInteractions(eventPublishingService);
  }

  @Test
  void shouldPublishAllActionsWhenNoSinceDateProvided() throws Exception {
    insertAction(daysAgoObjectId(10), REVIEW_DATA.toString(), TRAINEE_ID,
        new TisReferenceInfo("pm1", PROGRAMME_MEMBERSHIP));
    insertAction(daysAgoObjectId(5), REVIEW_DATA.toString(), TRAINEE_ID,
        new TisReferenceInfo("pm2", PROGRAMME_MEMBERSHIP));
    insertAction(daysAgoObjectId(1), REVIEW_DATA.toString(), TRAINEE_ID,
        new TisReferenceInfo("pl1", PLACEMENT));

    MvcResult result = mockMvc.perform(post("/api/job/action/publish-refresh"))
        .andExpect(status().isOk())
        .andReturn();

    assertThat("Unexpected publish count.", result.getResponse().getContentAsString(), is("3"));
    verify(eventPublishingService, times(3)).publishActionUpdateEvent(any(Action.class),
        any(String.class));
  }

  @Test
  void shouldPublishOnlyActionsCreatedOnOrAfterSinceDate() throws Exception {
    LocalDate since = LocalDate.now().minusDays(7);

    // Created 3 days ago — should be published
    insertAction(daysAgoObjectId(3), REVIEW_DATA.toString(), TRAINEE_ID,
        new TisReferenceInfo("pm1", PROGRAMME_MEMBERSHIP));

    // Created on the cutoff day (1 second into the day) — should be published
    ObjectId boundaryId = new ObjectId(
        Date.from(since.atStartOfDay().plusSeconds(1).toInstant(ZoneOffset.UTC)));
    insertAction(boundaryId, REVIEW_DATA.toString(), TRAINEE_ID,
        new TisReferenceInfo("pm2", PROGRAMME_MEMBERSHIP));

    // Created 14 days ago — should NOT be published
    insertAction(daysAgoObjectId(14), REVIEW_DATA.toString(), TRAINEE_ID,
        new TisReferenceInfo("pm3", PROGRAMME_MEMBERSHIP));

    MvcResult result = mockMvc.perform(post("/api/job/action/publish-refresh")
            .param("since", since.toString()))
        .andExpect(status().isOk())
        .andReturn();

    assertThat("Unexpected publish count.", result.getResponse().getContentAsString(), is("2"));
    verify(eventPublishingService, times(2)).publishActionUpdateEvent(any(Action.class),
        any(String.class));
  }

  @Test
  void shouldPublishCompletedAndIncompleteActions() throws Exception {
    // Incomplete action
    insertAction(daysAgoObjectId(2), REVIEW_DATA.toString(), TRAINEE_ID,
        new TisReferenceInfo("pm1", PROGRAMME_MEMBERSHIP), null);

    // Completed action
    insertAction(daysAgoObjectId(1), REVIEW_DATA.toString(), TRAINEE_ID,
        new TisReferenceInfo("pm2", PROGRAMME_MEMBERSHIP), Instant.now());

    MvcResult result = mockMvc.perform(post("/api/job/action/publish-refresh"))
        .andExpect(status().isOk())
        .andReturn();

    assertThat("Unexpected publish count.", result.getResponse().getContentAsString(), is("2"));
    verify(eventPublishingService, times(2)).publishActionUpdateEvent(any(Action.class),
        any(String.class));
  }

  @Test
  void shouldPublishToRefreshTopic() throws Exception {
    insertAction(daysAgoObjectId(1), REVIEW_DATA.toString(), TRAINEE_ID,
        new TisReferenceInfo("pm1", PROGRAMME_MEMBERSHIP));

    mockMvc.perform(post("/api/job/action/publish-refresh"))
        .andExpect(status().isOk());

    verify(eventPublishingService).publishActionUpdateEvent(any(Action.class), eq("dummy"));
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Create an ObjectId with a timestamp representing a given number of days ago.
   *
   * @param daysAgo The number of days ago.
   * @return An ObjectId with the corresponding timestamp.
   */
  private ObjectId daysAgoObjectId(int daysAgo) {
    Instant instant = Instant.now().minus(java.time.Duration.ofDays(daysAgo));
    return new ObjectId(Date.from(instant));
  }

  /**
   * Create an ObjectId from a LocalDate (at start of day UTC).
   *
   * @param date The local date.
   * @return An ObjectId with the corresponding timestamp.
   */
  private ObjectId objectIdFromLocalDate(LocalDate date) {
    Instant instant = date.atStartOfDay().toInstant(ZoneOffset.UTC);
    return new ObjectId(Date.from(instant));
  }

  /**
   * Insert an action with a specific ObjectId and no completion date.
   */
  private void insertAction(ObjectId id, String type, String traineeId,
      TisReferenceInfo tisReferenceInfo) {
    insertAction(id, type, traineeId, tisReferenceInfo, null);
  }

  /**
   * Insert an action with a specific ObjectId and optional completion date.
   */
  private void insertAction(ObjectId id, String type, String traineeId,
      TisReferenceInfo tisReferenceInfo, Instant completed) {
    Action action = new Action(id, REVIEW_DATA, traineeId, tisReferenceInfo,
        LocalDate.now(), LocalDate.now().plusDays(30), completed);
    mongoTemplate.insert(action);
  }
}

