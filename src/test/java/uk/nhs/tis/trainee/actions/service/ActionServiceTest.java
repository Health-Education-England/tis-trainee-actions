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
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.actions.model.ActionType.REVIEW_DATA;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PLACEMENT;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.actions.service.ActionService.PLACEMENT_TYPES_TO_ACT_ON;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.actions.dto.ActionDto;
import uk.nhs.tis.trainee.actions.dto.PlacementDto;
import uk.nhs.tis.trainee.actions.dto.ProgrammeMembershipDto;
import uk.nhs.tis.trainee.actions.event.Operation;
import uk.nhs.tis.trainee.actions.mapper.ActionMapperImpl;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.model.Action.TisReferenceInfo;
import uk.nhs.tis.trainee.actions.model.TisReferenceType;
import uk.nhs.tis.trainee.actions.repository.ActionRepository;

class ActionServiceTest {

  private static final String TIS_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final ObjectId ACTION_ID = ObjectId.get();
  private static final LocalDate NOW = LocalDate.now();
  private static final LocalDate PAST = NOW.minusDays(1);
  private static final LocalDate FUTURE = NOW.plusDays(1);
  private static final String PLACEMENT_TYPE = "In Post";

  private ActionService service;
  private ActionRepository repository;

  @BeforeEach
  void setUp() {
    repository = mock(ActionRepository.class);
    service = new ActionService(repository, new ActionMapperImpl());
  }

  @ParameterizedTest
  @EnumSource(value = Operation.class, names = {"CREATE"}, mode = EXCLUDE)
  void shouldNotInsertActionWhenProgrammeMembershipOperationNotSupported(Operation operation) {
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(TIS_ID, TRAINEE_ID, FUTURE);

    service.updateActions(operation, dto);

    verifyNoInteractions(repository);
  }

  @Test
  void shouldInsertReviewDataActionOnProgrammeMembershipCreate() {
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(TIS_ID, TRAINEE_ID, FUTURE);

    when(repository.insert(anyIterable())).thenAnswer(inv -> inv.getArgument(0));

    List<ActionDto> actions = service.updateActions(Operation.CREATE, dto);

    assertThat("Unexpected action count.", actions.size(), is(1));

    ActionDto action = actions.get(0);
    assertThat("Unexpected action id.", action.id(), nullValue());
    assertThat("Unexpected action type.", action.type(), is(REVIEW_DATA.toString()));
    assertThat("Unexpected trainee id.", action.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected available from date.", action.availableFrom(), is(NOW));
    assertThat("Unexpected due by date.", action.dueBy(), is(FUTURE));
    assertThat("Unexpected completed date.", action.completed(), nullValue());

    TisReferenceInfo tisReference = action.tisReferenceInfo();
    assertThat("Unexpected TIS id.", tisReference.id(), is(TIS_ID));
    assertThat("Unexpected TIS type.", tisReference.type(), is(PROGRAMME_MEMBERSHIP));
  }

  @Test
  void shouldReturnEmptyWhenTraineeActionsNotFound() {
    when(repository.findAllByTraineeIdAndCompletedIsNullOrderByDueByAsc(TRAINEE_ID)).thenReturn(
        List.of());

    List<ActionDto> dtos = service.findIncompleteTraineeActions(TRAINEE_ID);

    assertThat("Unexpected action count.", dtos.size(), is(0));
  }

  @ParameterizedTest
  @EnumSource(TisReferenceType.class)
  void shouldReturnActionsWhenTraineeActionsFound(TisReferenceType tisType) {
    TisReferenceInfo tisReference = new TisReferenceInfo(TIS_ID, tisType);
    ObjectId objectId1 = ObjectId.get();
    Action action1 = new Action(objectId1, REVIEW_DATA, TRAINEE_ID, tisReference, PAST, FUTURE,
        null);
    ObjectId objectId2 = ObjectId.get();
    Action action2 = new Action(objectId2, REVIEW_DATA, TRAINEE_ID, tisReference, PAST, FUTURE,
        null);
    List<Action> actions = List.of(action1, action2);

    when(repository.findAllByTraineeIdAndCompletedIsNullOrderByDueByAsc(TRAINEE_ID)).thenReturn(
        actions);

    List<ActionDto> dtos = service.findIncompleteTraineeActions(TRAINEE_ID);

    assertThat("Unexpected action count.", dtos.size(), is(2));
    ActionDto dto = dtos.get(0);
    assertThat("Unexpected action ID.", dto.id(), is(objectId1.toString()));
    dto = dtos.get(1);
    assertThat("Unexpected action ID.", dto.id(), is(objectId2.toString()));

    dtos.forEach(actDto -> {
      assertThat("Unexpected action type.", actDto.type(), is(REVIEW_DATA.toString()));
      assertThat("Unexpected trainee id.", actDto.traineeId(), is(TRAINEE_ID));
      assertThat("Unexpected available from date.", actDto.availableFrom(), is(PAST));
      assertThat("Unexpected due by date.", actDto.dueBy(), is(FUTURE));
      assertThat("Unexpected completed date.", actDto.completed(), nullValue());

      TisReferenceInfo refInfo = actDto.tisReferenceInfo();
      assertThat("Unexpected TIS id.", refInfo.id(), is(TIS_ID));
      assertThat("Unexpected TIS type.", refInfo.type(), is(tisType));
    });
  }

  @Test
  void shouldNotCompleteActionWhenActionIdInvalid() {
    Optional<ActionDto> optionalAction = service.complete(TRAINEE_ID, "40");

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(false));
    verifyNoInteractions(repository);
  }

  @Test
  void shouldNotCompleteActionWhenActionNotFound() {
    when(repository.findByIdAndTraineeId(ACTION_ID, TRAINEE_ID)).thenReturn(Optional.empty());

    Optional<ActionDto> optionalAction = service.complete(TRAINEE_ID, ACTION_ID.toString());

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(false));
    verify(repository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(TisReferenceType.class)
  void shouldNotCompleteActionWhenActionAlreadyCompleted(TisReferenceType tisType) {
    TisReferenceInfo tisReference = new TisReferenceInfo(TIS_ID, tisType);
    Action action = new Action(ACTION_ID, REVIEW_DATA, TRAINEE_ID, tisReference, PAST, FUTURE,
        Instant.now());
    when(repository.findByIdAndTraineeId(ACTION_ID, TRAINEE_ID)).thenReturn(Optional.of(action));

    Optional<ActionDto> optionalAction = service.complete(TRAINEE_ID, ACTION_ID.toString());

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(false));
    verify(repository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(TisReferenceType.class)
  void shouldSaveCompletedActionWhenActionCompletableAndTraineeMatches(TisReferenceType tisType) {
    TisReferenceInfo tisReference = new TisReferenceInfo(TIS_ID, tisType);
    Action action = new Action(ACTION_ID, REVIEW_DATA, TRAINEE_ID, tisReference, PAST, FUTURE,
        null);
    when(repository.findByIdAndTraineeId(ACTION_ID, TRAINEE_ID)).thenReturn(Optional.of(action));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.complete(TRAINEE_ID, ACTION_ID.toString());

    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(repository).save(actionCaptor.capture());

    Action completedAction = actionCaptor.getValue();
    assertThat("Unexpected action id.", completedAction.id(), is(ACTION_ID));
    assertThat("Unexpected action type.", completedAction.type(), is(REVIEW_DATA));
    assertThat("Unexpected trainee id.", completedAction.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected available from date.", completedAction.availableFrom(), is(PAST));
    assertThat("Unexpected due by date.", completedAction.dueBy(), is(FUTURE));
    assertThat("Unexpected completed date.", completedAction.completed(),
        instanceOf(Instant.class));

    TisReferenceInfo refInfo = completedAction.tisReferenceInfo();
    assertThat("Unexpected TIS id.", refInfo.id(), is(TIS_ID));
    assertThat("Unexpected TIS type.", refInfo.type(), is(tisType));
  }

  @ParameterizedTest
  @EnumSource(TisReferenceType.class)
  void shouldReturnCompletedActionWhenActionCompletableAndTraineeMatches(TisReferenceType tisType) {
    TisReferenceInfo tisReference = new TisReferenceInfo(TIS_ID, tisType);
    Action action = new Action(ACTION_ID, REVIEW_DATA, TRAINEE_ID, tisReference, PAST, FUTURE,
        null);
    when(repository.findByIdAndTraineeId(ACTION_ID, TRAINEE_ID)).thenReturn(Optional.of(action));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<ActionDto> optionalAction = service.complete(TRAINEE_ID, ACTION_ID.toString());

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(true));

    ActionDto actionDto = optionalAction.get();
    assertThat("Unexpected action id.", actionDto.id(), is(ACTION_ID.toString()));
    assertThat("Unexpected action type.", actionDto.type(), is(REVIEW_DATA.toString()));
    assertThat("Unexpected trainee id.", actionDto.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected available from date.", actionDto.availableFrom(), is(PAST));
    assertThat("Unexpected due by date.", actionDto.dueBy(), is(FUTURE));
    assertThat("Unexpected completed date.", actionDto.completed(), instanceOf(Instant.class));

    TisReferenceInfo refInfo = actionDto.tisReferenceInfo();
    assertThat("Unexpected TIS id.", refInfo.id(), is(TIS_ID));
    assertThat("Unexpected TIS type.", refInfo.type(), is(tisType));
  }

  @ParameterizedTest
  @EnumSource(value = Operation.class, names = {"CREATE"}, mode = EXCLUDE)
  void shouldNotInsertActionWhenPlacementOperationNotSupported(Operation operation) {
    PlacementDto dto = new PlacementDto(TIS_ID, TRAINEE_ID, FUTURE, PLACEMENT_TYPE);

    service.updateActions(operation, dto);

    verifyNoInteractions(repository);
  }

  @Test
  void shouldNotInsertActionWhenPlacementTypeIgnored() {
    PlacementDto dto = new PlacementDto(TIS_ID, TRAINEE_ID, FUTURE, "ignored placement type");

    service.updateActions(Operation.CREATE, dto);

    verifyNoInteractions(repository);
  }

  @ParameterizedTest
  @MethodSource("listPlacementTypes")
  void shouldInsertReviewDataActionOnPlacementCreate(String placementType) {
    PlacementDto dto = new PlacementDto(TIS_ID, TRAINEE_ID, FUTURE, placementType);

    when(repository.insert(anyIterable())).thenAnswer(inv -> inv.getArgument(0));

    List<ActionDto> actions = service.updateActions(Operation.CREATE, dto);

    assertThat("Unexpected action count.", actions.size(), is(1));

    ActionDto action = actions.get(0);
    assertThat("Unexpected action id.", action.id(), nullValue());
    assertThat("Unexpected action type.", action.type(), is(REVIEW_DATA.toString()));
    assertThat("Unexpected trainee id.", action.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected available from date.", action.availableFrom(),
        is(FUTURE.minusWeeks(12)));
    assertThat("Unexpected due by date.", action.dueBy(), is(FUTURE));
    assertThat("Unexpected completed date.", action.completed(), nullValue());

    TisReferenceInfo tisReference = action.tisReferenceInfo();
    assertThat("Unexpected TIS id.", tisReference.id(), is(TIS_ID));
    assertThat("Unexpected TIS type.", tisReference.type(), is(PLACEMENT));
  }

  static Stream<String> listPlacementTypes() {
    return PLACEMENT_TYPES_TO_ACT_ON.stream();
  }
}
