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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.actions.model.ActionType.REVIEW_DATA;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PLACEMENT;
import static uk.nhs.tis.trainee.actions.service.ActionService.ACTIONS_EPOCH;

import com.mongodb.client.result.DeleteResult;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.service.EventPublishingService;

class DeleteOldOutstandingActionsTest {
  private static final String TIS_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final ObjectId ACTION_ID_1 = ObjectId.get();
  private static final ObjectId ACTION_ID_2 = ObjectId.get();
  private static final LocalDate PRE_EPOCH = ACTIONS_EPOCH.minusDays(1);
  private DeleteOldOutstandingActions migration;
  private MongoTemplate template;
  private EventPublishingService eventPublishingService;
  DeleteResult deleted;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    eventPublishingService = mock(EventPublishingService.class);
    migration = new DeleteOldOutstandingActions(template, eventPublishingService);

    deleted = new DeleteResult() {
      @Override
      public boolean wasAcknowledged() {
        return true;
      }

      @Override
      public long getDeletedCount() {
        return 2;
      }
    };
  }

  @Test
  void shouldNotFailWhenNoActionsToProcess() {
    when(template.find(any(), eq(Action.class))).thenReturn(List.of());
    when(template.remove(any(), eq(Action.class))).thenReturn(deleted);

    assertDoesNotThrow(() -> migration.migrate());
  }

  @Test
  void shouldDeleteOldOutstandingActions() {
    Action action1 = new Action(ACTION_ID_1, REVIEW_DATA, TRAINEE_ID,
        new Action.TisReferenceInfo(TIS_ID, PLACEMENT), LocalDate.MIN, PRE_EPOCH, null);
    Action action2 = new Action(ACTION_ID_2, REVIEW_DATA, TRAINEE_ID,
        new Action.TisReferenceInfo(TIS_ID, PLACEMENT), LocalDate.MIN, PRE_EPOCH, null);

    when(template.find(any(), eq(Action.class))).thenReturn(List.of(action1, action2));
    when(template.remove(any(), eq(Action.class))).thenReturn(deleted);

    migration.migrate();

    verify(eventPublishingService).publishActionDeleteEvent(action1);
    verify(eventPublishingService).publishActionDeleteEvent(action2);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(template).remove(queryCaptor.capture(), eq(Action.class));

    var obsoleteActionsCriteria = Criteria
        .where("dueBy").lt(ACTIONS_EPOCH)
        .and("completed").exists(false);
    Query expectedQuery = Query.query(obsoleteActionsCriteria);

    Query queryUsed = queryCaptor.getValue();
    assertThat("Unexpected query.", queryUsed, is(expectedQuery));
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
  }
}
