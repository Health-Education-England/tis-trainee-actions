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

package uk.nhs.tis.trainee.actions.repository;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.nhs.tis.trainee.actions.model.ActionType.REVIEW_DATA;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PLACEMENT;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PROGRAMME_MEMBERSHIP;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.actions.DockerImageNames;
import uk.nhs.tis.trainee.actions.config.MongoConfiguration;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.model.Action.TisReferenceInfo;

@DataMongoTest
@Testcontainers
@Import(MongoConfiguration.class)
class ActionRepositoryIntegrationTest {

  private static final String TIS_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_ID_1 = UUID.randomUUID().toString();
  private static final String TRAINEE_ID_2 = UUID.randomUUID().toString();
  private static final LocalDate PAST = LocalDate.now().minusDays(1);
  private static final LocalDate FUTURE = LocalDate.now().plusDays(1);

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private ActionRepository repository;

  @AfterEach
  void cleanUp() {
    repository.deleteAll();
  }

  @Test
  void shouldNotInsertDuplicateActions() {
    TisReferenceInfo referenceInfo = new TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP);
    Action action = new Action(null, REVIEW_DATA, TRAINEE_ID_1, referenceInfo, PAST, FUTURE, null);

    Action insertedAction = repository.insert(action);
    assertThrows(DuplicateKeyException.class, () -> repository.insert(action));

    List<Action> actions = repository.findAll();
    assertThat("Unexpected action count.", actions.size(), is(1));
    assertThat("Unexpected action.", actions.get(0), is(insertedAction));
  }

  @Test
  void shouldNotInsertTheSameActionForMultipleTrainees() {
    TisReferenceInfo referenceInfo = new TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP);
    Action action1 = new Action(null, REVIEW_DATA, TRAINEE_ID_1, referenceInfo, PAST, FUTURE, null);
    Action action2 = new Action(null, REVIEW_DATA, TRAINEE_ID_2, referenceInfo, PAST, FUTURE, null);

    Action insertedAction = repository.insert(action1);
    assertThrows(DuplicateKeyException.class, () -> repository.insert(action2));

    List<Action> actions = repository.findAll();
    assertThat("Unexpected action count.", actions.size(), is(1));
    assertThat("Unexpected action.", actions.get(0), is(insertedAction));
  }

  @Test
  void shouldInsertMultipleSimilarActionsForTheSameTrainee() {
    TisReferenceInfo referenceInfo1 = new TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP);
    Action action1 = new Action(null, REVIEW_DATA, TRAINEE_ID_1, referenceInfo1, PAST, FUTURE,
        null);
    String tisId2 = UUID.randomUUID().toString();
    TisReferenceInfo referenceInfo2 = new TisReferenceInfo(tisId2, PROGRAMME_MEMBERSHIP);
    Action action2 = new Action(null, REVIEW_DATA, TRAINEE_ID_1, referenceInfo2, PAST, FUTURE,
        null);

    Action insertedAction1 = repository.insert(action1);
    Action insertedAction2 = repository.insert(action2);

    List<Action> actions = repository.findAll();
    assertThat("Unexpected action count.", actions.size(), is(2));
    assertThat("Unexpected actions.", actions, hasItems(insertedAction1, insertedAction2));
  }

  @Test
  void shouldNotDeleteCompleteActionsWhenDeleting() {
    TisReferenceInfo referenceInfo1 = new TisReferenceInfo(TIS_ID, PLACEMENT);
    Action action1 = new Action(null, REVIEW_DATA, TRAINEE_ID_1, referenceInfo1, PAST, FUTURE,
        Instant.now());

    repository.insert(action1);

    List<Action> deletedActions = repository.deleteByTraineeIdAndTisReferenceInfoAndNotComplete(
        TRAINEE_ID_1, TIS_ID, PLACEMENT.toString());
    assertThat("Unexpected delete count.", deletedActions.size(), is(0));
  }

  @Test
  void shouldDeleteNotCompleteActionsWhenDeleting() {
    TisReferenceInfo referenceInfo1 = new TisReferenceInfo(TIS_ID, PLACEMENT);
    Action action1 = new Action(null, REVIEW_DATA, TRAINEE_ID_1, referenceInfo1, PAST, FUTURE,
        null);

    repository.insert(action1);

    List<Action> deletedActions = repository.deleteByTraineeIdAndTisReferenceInfoAndNotComplete(
        TRAINEE_ID_1, TIS_ID, PLACEMENT.toString());
    assertThat("Unexpected delete count.", deletedActions.size(), is(1));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"2024-02-02T00:00:00.000Z"})
  void shouldDeleteAllActionsWhenDeleting(Instant completed) {
    TisReferenceInfo referenceInfo1 = new TisReferenceInfo(TIS_ID, PLACEMENT);
    Action action1 = new Action(null, REVIEW_DATA, TRAINEE_ID_1, referenceInfo1, PAST, FUTURE,
        completed);

    repository.insert(action1);

    List<Action> deletedActions = repository.deleteByTraineeIdAndTisReferenceInfo(
        TRAINEE_ID_1, TIS_ID, PLACEMENT.toString());
    assertThat("Unexpected delete count.", deletedActions.size(), is(1));
  }
}
