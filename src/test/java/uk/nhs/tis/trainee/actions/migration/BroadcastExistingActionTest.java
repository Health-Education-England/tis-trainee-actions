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

package uk.nhs.tis.trainee.actions.migration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.actions.model.ActionType.REVIEW_DATA;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PLACEMENT;

import com.mongodb.MongoException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.service.EventPublishingService;

class BroadcastExistingActionTest {
  private static final String TIS_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final ObjectId ACTION_ID_1 = ObjectId.get();
  private static final ObjectId ACTION_ID_2 = ObjectId.get();
  private static final LocalDate NOW = LocalDate.now();
  private static final LocalDate PAST = NOW.minusDays(1);
  private static final LocalDate FUTURE = NOW.plusDays(1);
  private BroadcastExistingActions migration;
  private MongoTemplate template;
  private EventPublishingService eventPublishingService;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    eventPublishingService = mock(EventPublishingService.class);
    migration = new BroadcastExistingActions(template, eventPublishingService);
  }

  @Test
  void shouldNotFailWhenNoDocumentsToMigrate() {
    when(template.findAll(Action.class, "Action")).thenReturn(List.of());

    assertDoesNotThrow(() -> migration.migrate());
  }

  @Test
  void shouldBroadcastExistingActions() {

    Action action1 = new Action(ACTION_ID_1, REVIEW_DATA, TRAINEE_ID,
        new Action.TisReferenceInfo(TIS_ID, PLACEMENT), PAST, FUTURE, null);
    Action action2 = new Action(ACTION_ID_2, REVIEW_DATA, TRAINEE_ID,
        new Action.TisReferenceInfo(TIS_ID, PLACEMENT), PAST, FUTURE, null);

    when(template.findAll(Action.class, "Action")).thenReturn(List.of(action1, action2));

    migration.migrate();

    verify(eventPublishingService).publishActionUpdateEvent(action1);
    verify(eventPublishingService).publishActionUpdateEvent(action2);
  }

  @Test
  void shouldCatchMongoExceptionNotThrowIt() {
    when(template.updateMulti(any(), any(), (String) any()))
        .thenThrow(new MongoException("exception"));
    Assertions.assertDoesNotThrow(() -> migration.migrate());
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
  }
}
