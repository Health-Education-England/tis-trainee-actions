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
 *
 */

package uk.nhs.tis.trainee.actions.job;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.model.ActionType;
import uk.nhs.tis.trainee.actions.model.TisReferenceType;
import uk.nhs.tis.trainee.actions.repository.ActionRepository;
import uk.nhs.tis.trainee.actions.service.EventPublishingService;

class PublishActionRefreshTest {

  private static final String REFRESH_TOPIC = "arn:aws:sns:eu-west-2:123456:action-refresh";

  private PublishActionRefresh job;
  private ActionRepository repository;
  private EventPublishingService eventPublishingService;

  @BeforeEach
  void setUp() {
    repository = mock(ActionRepository.class);
    eventPublishingService = mock(EventPublishingService.class);
    job = new PublishActionRefresh(repository, eventPublishingService, REFRESH_TOPIC);
  }

  @Test
  void shouldStreamAllActionsWhenNoCutoffDate() {
    Action action1 = new Action(ObjectId.get(), ActionType.REVIEW_DATA, "trainee1",
        new Action.TisReferenceInfo("tis1", TisReferenceType.PROGRAMME_MEMBERSHIP),
        LocalDate.now(), LocalDate.now().plusDays(30), null);
    Action action2 = new Action(ObjectId.get(), ActionType.REVIEW_DATA, "trainee2",
        new Action.TisReferenceInfo("tis2", TisReferenceType.PLACEMENT),
        LocalDate.now(), LocalDate.now().plusDays(30), null);

    when(repository.streamAll()).thenReturn(Stream.of(action1, action2));

    Integer result = job.execute(Optional.empty());

    assertThat("Unexpected publish count.", result, is(2));
    verify(repository).streamAll();
    verify(eventPublishingService, times(2)).publishActionUpdateEvent(any(Action.class),
        eq(REFRESH_TOPIC));
  }

  @Test
  void shouldStreamFilteredActionsWhenCutoffDateProvided() {
    Action action = new Action(ObjectId.get(), ActionType.REVIEW_DATA, "trainee1",
        new Action.TisReferenceInfo("tis1", TisReferenceType.PROGRAMME_MEMBERSHIP),
        LocalDate.now(), LocalDate.now().plusDays(30), null);

    when(repository.streamByIdGreaterThanEqual(any(ObjectId.class)))
        .thenReturn(Stream.of(action));

    LocalDate cutoff = LocalDate.of(2026, 1, 1);
    Integer result = job.execute(Optional.of(cutoff));

    assertThat("Unexpected publish count.", result, is(1));
    verify(repository).streamByIdGreaterThanEqual(any(ObjectId.class));
    verify(eventPublishingService).publishActionUpdateEvent(action, REFRESH_TOPIC);
  }

  @Test
  void shouldPublishToRefreshTopic() {
    Action action = new Action(ObjectId.get(), ActionType.REVIEW_DATA, "trainee1",
        new Action.TisReferenceInfo("tis1", TisReferenceType.PROGRAMME_MEMBERSHIP),
        LocalDate.now(), LocalDate.now().plusDays(30), null);

    when(repository.streamAll()).thenReturn(Stream.of(action));

    job.execute(Optional.empty());

    ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
    verify(eventPublishingService).publishActionUpdateEvent(any(Action.class),
        topicCaptor.capture());
    assertThat("Unexpected topic.", topicCaptor.getValue(), is(REFRESH_TOPIC));
  }

  @Test
  void shouldContinueWhenPublishingFails() {
    Action action1 = new Action(ObjectId.get(), ActionType.REVIEW_DATA, "trainee1",
        new Action.TisReferenceInfo("tis1", TisReferenceType.PROGRAMME_MEMBERSHIP),
        LocalDate.now(), LocalDate.now().plusDays(30), null);
    Action action2 = new Action(ObjectId.get(), ActionType.REVIEW_DATA, "trainee2",
        new Action.TisReferenceInfo("tis2", TisReferenceType.PLACEMENT),
        LocalDate.now(), LocalDate.now().plusDays(30), null);

    when(repository.streamAll()).thenReturn(Stream.of(action1, action2));
    doThrow(new RuntimeException("SNS error"))
        .when(eventPublishingService).publishActionUpdateEvent(eq(action1), eq(REFRESH_TOPIC));

    Integer result = job.execute(Optional.empty());

    assertThat("Unexpected publish count.", result, is(1));
    verify(eventPublishingService, times(2)).publishActionUpdateEvent(any(Action.class),
        eq(REFRESH_TOPIC));
  }

  @Test
  void shouldReturnZeroWhenNoActionsFound() {
    when(repository.streamAll()).thenReturn(Stream.empty());

    Integer result = job.execute(Optional.empty());

    assertThat("Unexpected publish count.", result, is(0));
  }
}

