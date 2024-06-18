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

package uk.nhs.tis.trainee.actions.service;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.nhs.tis.trainee.actions.model.ActionType.REVIEW_DATA;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PLACEMENT;

import io.awspring.cloud.sns.core.SnsNotification;
import io.awspring.cloud.sns.core.SnsTemplate;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.actions.dto.ActionBroadcastDto;
import uk.nhs.tis.trainee.actions.dto.enumeration.ActionStatus;
import uk.nhs.tis.trainee.actions.model.Action;

class EventPublishingServiceTest {

  private static final URI ACTION_TOPIC_ARN = URI.create("arn:sns:test");
  private static final String TRAINEE_ID = "traineeId";
  private static final String TIS_ID = "tisId";
  private static final ObjectId ACTION_ID = ObjectId.get();
  private static final String ACTION_ID_STRING = ACTION_ID.toString();
  private static final Instant COMPLETED = Instant.now();
  private static final LocalDate NOW = LocalDate.now();
  private static final LocalDate PAST = NOW.minusDays(1);
  private static final LocalDate FUTURE = NOW.plusDays(1);

  private EventPublishingService service;
  private EventPublishingService eventPublishingService;
  private SnsTemplate snsTemplate;

  @BeforeEach
  void setUp() {
    snsTemplate = mock(SnsTemplate.class);
    eventPublishingService = mock(EventPublishingService.class);
    service = new EventPublishingService(snsTemplate, ACTION_TOPIC_ARN);
  }

  @Test
  void shouldSendToTopicWhenPublishingActionUpdateEvent() {
    Action.TisReferenceInfo tisReference = new Action.TisReferenceInfo(TIS_ID, PLACEMENT);
    Action action = new Action(ACTION_ID, REVIEW_DATA, TRAINEE_ID, tisReference, PAST, FUTURE,
        COMPLETED);

    service.publishActionUpdateEvent(action);

    verify(snsTemplate).sendNotification(eq(ACTION_TOPIC_ARN.toString()),
        any(SnsNotification.class));
  }

  @Test
  void shouldSetGroupIdWhenPublishingActionUpdateEvent() {
    Action.TisReferenceInfo tisReference = new Action.TisReferenceInfo(TIS_ID, PLACEMENT);
    Action action = new Action(ACTION_ID, REVIEW_DATA, TRAINEE_ID, tisReference, PAST, FUTURE,
        COMPLETED);

    service.publishActionUpdateEvent(action);

    ArgumentCaptor<SnsNotification<ActionBroadcastDto>> messageCaptor = ArgumentCaptor.forClass(
        SnsNotification.class);
    verify(snsTemplate).sendNotification(any(), messageCaptor.capture());

    SnsNotification<ActionBroadcastDto> message = messageCaptor.getValue();
    assertThat("Unexpected group ID.", message.getGroupId(), is(ACTION_ID_STRING));
  }

  @Test
  void shouldIncludePayloadWhenPublishingActionUpdateEvent() {
    Action.TisReferenceInfo tisReference = new Action.TisReferenceInfo(TIS_ID, PLACEMENT);
    Action action = new Action(ACTION_ID, REVIEW_DATA, TRAINEE_ID, tisReference, PAST, FUTURE,
        COMPLETED);

    service.publishActionUpdateEvent(action);

    ArgumentCaptor<SnsNotification<ActionBroadcastDto>> messageCaptor = ArgumentCaptor.forClass(
        SnsNotification.class);
    verify(snsTemplate).sendNotification(any(), messageCaptor.capture());

    SnsNotification<ActionBroadcastDto> message = messageCaptor.getValue();
    ActionBroadcastDto payload = message.getPayload();
    assertThat("Unexpected action id.", payload.id(), is(ACTION_ID_STRING));
    assertThat("Unexpected action type.", payload.type(), is(REVIEW_DATA.toString()));
    assertThat("Unexpected trainee id.", payload.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected reference id", payload.tisReferenceInfo().id(), is(TIS_ID));
    assertThat("Unexpected reference type.", payload.tisReferenceInfo().type(),
        is(PLACEMENT));
    assertThat("Unexpected available from date.", payload.availableFrom(), is(PAST));
    assertThat("Unexpected due by date.", payload.dueBy(), is(FUTURE));
    assertThat("Unexpected completed date.", payload.completed(), is(COMPLETED));
    assertThat("Unexpected action status", payload.status(), is(ActionStatus.ACTIVE));
    assertThat("Unexpected action status date.", payload.statusDatetime(),
        instanceOf(Instant.class));
  }

  @Test
  void shouldSendToTopicWhenPublishingActionDeleteEvent() {
    Action.TisReferenceInfo tisReference = new Action.TisReferenceInfo(TIS_ID, PLACEMENT);
    Action action = new Action(ACTION_ID, REVIEW_DATA, TRAINEE_ID, tisReference, PAST, FUTURE,
        COMPLETED);

    service.publishActionDeleteEvent(action);

    verify(snsTemplate).sendNotification(eq(ACTION_TOPIC_ARN.toString()),
        any(SnsNotification.class));
  }

  @Test
  void shouldSetGroupIdWhenPublishingActionDeleteEvent() {
    Action.TisReferenceInfo tisReference = new Action.TisReferenceInfo(TIS_ID, PLACEMENT);
    Action action = new Action(ACTION_ID, REVIEW_DATA, TRAINEE_ID, tisReference, PAST, FUTURE,
        COMPLETED);

    service.publishActionDeleteEvent(action);

    ArgumentCaptor<SnsNotification<ActionBroadcastDto>> messageCaptor = ArgumentCaptor.forClass(
        SnsNotification.class);
    verify(snsTemplate).sendNotification(any(), messageCaptor.capture());

    SnsNotification<ActionBroadcastDto> message = messageCaptor.getValue();
    assertThat("Unexpected group ID.", message.getGroupId(), is(ACTION_ID_STRING));
  }

  @Test
  void shouldNotIncludeDetailWhenPublishingActionDeleteEvent() {
    Action.TisReferenceInfo tisReference = new Action.TisReferenceInfo(TIS_ID, PLACEMENT);
    Action action = new Action(ACTION_ID, REVIEW_DATA, TRAINEE_ID, tisReference, PAST, FUTURE,
        COMPLETED);

    service.publishActionDeleteEvent(action);

    ArgumentCaptor<SnsNotification<ActionBroadcastDto>> messageCaptor = ArgumentCaptor.forClass(
        SnsNotification.class);
    verify(snsTemplate).sendNotification(any(), messageCaptor.capture());

    SnsNotification<ActionBroadcastDto> message = messageCaptor.getValue();
    ActionBroadcastDto payload = message.getPayload();
    assertThat("Unexpected action id.", payload.id(), is(ACTION_ID_STRING));
    assertThat("Unexpected action type.", payload.type(), is(nullValue()));
    assertThat("Unexpected trainee id.", payload.traineeId(), is(nullValue()));
    assertThat("Unexpected reference info", payload.tisReferenceInfo(), is(nullValue()));
    assertThat("Unexpected available from date.", payload.availableFrom(), is(nullValue()));
    assertThat("Unexpected due by date.", payload.dueBy(), is(nullValue()));
    assertThat("Unexpected completed date.", payload.completed(), is(nullValue()));
    assertThat("Unexpected action status", payload.status(), is(ActionStatus.DELETED));
    assertThat("Unexpected action status date.", payload.statusDatetime(),
        instanceOf(Instant.class));
  }
}
