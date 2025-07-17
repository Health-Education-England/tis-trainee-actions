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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.actions.model.ActionType.REGISTER_TSS;
import static uk.nhs.tis.trainee.actions.model.ActionType.REVIEW_DATA;
import static uk.nhs.tis.trainee.actions.model.ActionType.SIGN_COJ;
import static uk.nhs.tis.trainee.actions.model.ActionType.SIGN_FORM_R_PART_A;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PERSON;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PLACEMENT;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.actions.service.ActionService.ACTIONS_EPOCH;
import static uk.nhs.tis.trainee.actions.service.ActionService.PLACEMENT_TYPES_TO_ACT_ON;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.actions.dto.AccountConfirmedEvent;
import uk.nhs.tis.trainee.actions.dto.ActionDto;
import uk.nhs.tis.trainee.actions.dto.CojReceivedEvent;
import uk.nhs.tis.trainee.actions.dto.ConditionsOfJoining;
import uk.nhs.tis.trainee.actions.dto.FormUpdateEvent;
import uk.nhs.tis.trainee.actions.dto.PlacementDto;
import uk.nhs.tis.trainee.actions.dto.ProgrammeMembershipDto;
import uk.nhs.tis.trainee.actions.dto.enumeration.FormLifecycleState;
import uk.nhs.tis.trainee.actions.event.Operation;
import uk.nhs.tis.trainee.actions.mapper.ActionMapperImpl;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.model.Action.TisReferenceInfo;
import uk.nhs.tis.trainee.actions.model.ActionType;
import uk.nhs.tis.trainee.actions.model.TisReferenceType;
import uk.nhs.tis.trainee.actions.repository.ActionRepository;

class ActionServiceTest {

  private static final String TIS_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final ObjectId ACTION_ID = ObjectId.get();
  private static final LocalDate NOW = LocalDate.now();
  private static final LocalDate PRE_EPOCH = ACTIONS_EPOCH.minusDays(1);
  private static final LocalDate POST_EPOCH = ACTIONS_EPOCH.plusDays(1);
  private static final LocalDate PAST = NOW.minusDays(1);
  private static final LocalDate FUTURE = NOW.plusDays(1);
  private static final String PLACEMENT_TYPE = "In Post";

  private ActionService service;
  private ActionRepository repository;
  private EventPublishingService eventPublishingService;

  @BeforeEach
  void setUp() {
    repository = mock(ActionRepository.class);
    eventPublishingService = mock(EventPublishingService.class);
    service = new ActionService(repository, new ActionMapperImpl(), eventPublishingService);
  }

  @Test
  void shouldInsertAllActionsOnFirstSightOfPostEpochProgrammeMembership() {
    ProgrammeMembershipDto dto
        = new ProgrammeMembershipDto(TIS_ID, TRAINEE_ID, ACTIONS_EPOCH, null);

    when(repository.findByTraineeIdAndTisReferenceInfo(any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(repository.insert(anyIterable())).thenAnswer(inv -> inv.getArgument(0));

    List<ActionDto> actions = service.updateActions(Operation.LOAD, dto);

    int expectedActionCount = ActionType.getProgrammeActionTypes().size();
    assertThat("Unexpected action count.", actions.size(), is(expectedActionCount));

    // should broadcast inserted action
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(eventPublishingService, times(expectedActionCount))
        .publishActionUpdateEvent(actionCaptor.capture());
    List<Action> actionsPublished = actionCaptor.getAllValues();

    for (ActionType actionType : ActionType.getProgrammeActionTypes()) {
      Optional<ActionDto> actionOfType = actions.stream()
          .filter(a -> a.type().equals(actionType.toString()))
          .findFirst();
      assertThat("Missing action for type: " + actionType, actionOfType.isPresent(), is(true));
      ActionDto action = actionOfType.get();
      assertThat("Unexpected action id.", action.id(), nullValue());
      assertThat("Unexpected action type.", action.type(), is(actionType.toString()));
      assertThat("Unexpected trainee id.", action.traineeId(), is(TRAINEE_ID));
      assertThat("Unexpected available from date.", action.availableFrom(), is(NOW));
      assertThat("Unexpected due by date.", action.dueBy(), is(ACTIONS_EPOCH));
      assertThat("Unexpected completed date.", action.completed(), nullValue());

      TisReferenceInfo tisReference = action.tisReferenceInfo();
      assertThat("Unexpected TIS id.", tisReference.id(), is(TIS_ID));
      assertThat("Unexpected TIS type.", tisReference.type(),
          is(PROGRAMME_MEMBERSHIP));

      Optional<Action> actionPublishedOfType = actionsPublished.stream()
          .filter(a -> a.type().equals(actionType))
          .findFirst();
      assertThat("Missing action for type: " + actionType, actionPublishedOfType.isPresent(),
          is(true));
      Action actionPublished = actionPublishedOfType.get();
      assertThat("Unexpected action id.", actionPublished.id(), nullValue());
      assertThat("Unexpected action type.", actionPublished.type(), is(actionType));
      assertThat("Unexpected trainee id.", actionPublished.traineeId(), is(TRAINEE_ID));
      assertThat("Unexpected available from date.", actionPublished.availableFrom(),
          is(NOW));
      assertThat("Unexpected due by date.", actionPublished.dueBy(), is(ACTIONS_EPOCH));
      assertThat("Unexpected completed date.", actionPublished.completed(), nullValue());
      TisReferenceInfo refInfo = actionPublished.tisReferenceInfo();
      assertThat("Unexpected TIS id.", refInfo.id(), is(TIS_ID));
      assertThat("Unexpected TIS type.", refInfo.type(), is(PROGRAMME_MEMBERSHIP));
    }
  }

  @Test
  void shouldNotInsertActionsOnFirstSightOfPreEpochProgrammeMembership() {
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(TIS_ID, TRAINEE_ID, PRE_EPOCH, null);

    List<ActionDto> actions = service.updateActions(Operation.LOAD, dto);

    assertThat("Unexpected action count.", actions.size(), is(0));
    verify(repository).findByTraineeIdAndTisReferenceInfo(any(), any(), any());
    verifyNoMoreInteractions(repository);
    verifyNoInteractions(eventPublishingService);
  }

  @Test
  void shouldNotInsertActionsOnAlreadyActionedPostEpochProgrammeMembership() {
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(TIS_ID, TRAINEE_ID, POST_EPOCH, null);

    TisReferenceInfo tisReference = new TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP);
    List<Action> existingActions = new ArrayList<>();
    for (ActionType actionType : ActionType.getProgrammeActionTypes()) {
      Action existingAction = new Action(ObjectId.get(), actionType, TRAINEE_ID, tisReference,
          PRE_EPOCH, POST_EPOCH, null);
      existingActions.add(existingAction);
    }
    when(repository.findByTraineeIdAndTisReferenceInfo(any(), any(), any()))
        .thenReturn(existingActions);

    List<ActionDto> actions = service.updateActions(Operation.LOAD, dto);

    assertThat("Unexpected action count.", actions.size(), is(0));
    verify(repository).findByTraineeIdAndTisReferenceInfo(any(), any(), any());
    verifyNoMoreInteractions(repository);
    verifyNoMoreInteractions(eventPublishingService);
  }

  @Test
  void shouldInsertCompletedCojSignedActionOnProgrammeMembershipWithCoj() {
    ConditionsOfJoining coj = new ConditionsOfJoining(Instant.MIN, "version", Instant.MAX);
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(TIS_ID, TRAINEE_ID, ACTIONS_EPOCH, coj);
    when(repository.findByTraineeIdAndTisReferenceInfo(any(), any(), any()))
        .thenReturn(new ArrayList<>());

    when(repository.insert(anyIterable())).thenAnswer(inv -> inv.getArgument(0));

    List<ActionDto> actions = service.updateActions(Operation.LOAD, dto);

    Optional<ActionDto> actionOfType = actions.stream()
        .filter(a -> a.type().equals(SIGN_COJ.toString())).findFirst();
    assertThat("Missing action for SIGN_COJ.", actionOfType.isPresent(), is(true));
    ActionDto action = actionOfType.get();
    assertThat("Unexpected action id.", action.id(), nullValue());
    assertThat("Unexpected trainee id.", action.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected available from date.", action.availableFrom(), is(NOW));
    assertThat("Unexpected due by date.", action.dueBy(), is(ACTIONS_EPOCH));
    assertThat("Unexpected completed date.", action.completed(), is(Instant.MAX));

    TisReferenceInfo tisReference = action.tisReferenceInfo();
    assertThat("Unexpected TIS id.", tisReference.id(), is(TIS_ID));
    assertThat("Unexpected TIS type.", tisReference.type(),
        is(PROGRAMME_MEMBERSHIP));
  }

  @Test
  void shouldCompleteCojSignedActionOnProgrammeMembershipWithExistingCojAction() {
    ConditionsOfJoining coj = new ConditionsOfJoining(Instant.MIN, "version", Instant.MAX);
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(TIS_ID, TRAINEE_ID, PRE_EPOCH, coj);

    TisReferenceInfo tisReference = new TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP);
    List<Action> existingActions = List.of(new Action(ObjectId.get(), SIGN_COJ, TRAINEE_ID,
        tisReference, PRE_EPOCH, POST_EPOCH, null));
    when(repository.findByTraineeIdAndTisReferenceInfo(any(), any(), any()))
        .thenReturn(existingActions);

    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    List<ActionDto> actions = service.updateActions(Operation.LOAD, dto);
    assertThat("Unexpected action count.", actions.size(), is(0)); //since PRE_EPOCH

    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(eventPublishingService).publishActionUpdateEvent(actionCaptor.capture());
    Action actionPublished = actionCaptor.getValue();

    assertThat("Unexpected action id.", actionPublished.id(), notNullValue());
    assertThat("Unexpected action type.", actionPublished.type(), is(SIGN_COJ));
    assertThat("Unexpected trainee id.", actionPublished.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected available from date.", actionPublished.availableFrom(),
        is(PRE_EPOCH));
    assertThat("Unexpected due by date.", actionPublished.dueBy(), is(POST_EPOCH));
    assertThat("Unexpected completed date.", actionPublished.completed(), is(Instant.MAX));
    TisReferenceInfo refInfo = actionPublished.tisReferenceInfo();
    assertThat("Unexpected TIS id.", refInfo.id(), is(TIS_ID));
    assertThat("Unexpected TIS type.", refInfo.type(), is(PROGRAMME_MEMBERSHIP));
  }

  @Test
  void shouldNotUpdateActionWhenNoCojDataProvided() {
    CojReceivedEvent event = new CojReceivedEvent(TIS_ID, TRAINEE_ID, null);

    Optional<ActionDto> optionalAction = service.updateAction(event);

    assertThat("Unexpected action updated.", optionalAction.isPresent(), is(false));
    verifyNoInteractions(repository);
    verifyNoInteractions(eventPublishingService);
  }

  @Test
  void shouldNotUpdateActionWhenNoExistingCojActionFound() {
    CojReceivedEvent event = new CojReceivedEvent(TIS_ID, TRAINEE_ID,
        new ConditionsOfJoining(Instant.MIN, "version", Instant.MAX));

    when(repository.findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID,
        String.valueOf(PROGRAMME_MEMBERSHIP))).thenReturn(Collections.emptyList());

    Optional<ActionDto> optionalAction = service.updateAction(event);

    assertThat("Unexpected action updated.", optionalAction.isPresent(), is(false));
    verify(repository).findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID,
        String.valueOf(PROGRAMME_MEMBERSHIP));
    verifyNoMoreInteractions(repository);
    verifyNoInteractions(eventPublishingService);
  }

  @Test
  void shouldUpdateExistingCojAction() {
    TisReferenceInfo tisReference = new TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP);
    Action existingAction = new Action(ACTION_ID, SIGN_COJ, TRAINEE_ID, tisReference,
        PAST, FUTURE, null);
    CojReceivedEvent event = new CojReceivedEvent(TIS_ID, TRAINEE_ID,
        new ConditionsOfJoining(Instant.MIN, "version", Instant.MAX));

    when(repository.findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID,
        String.valueOf(PROGRAMME_MEMBERSHIP))).thenReturn(List.of(existingAction));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<ActionDto> optionalAction = service.updateAction(event);

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(true));
    ActionDto actionDto = optionalAction.get();
    assertThat("Unexpected action id.", actionDto.id(), is(ACTION_ID.toString()));
    assertThat("Unexpected action type.", actionDto.type(), is(SIGN_COJ.toString()));
    assertThat("Unexpected trainee id.", actionDto.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected available from date.", actionDto.availableFrom(), is(PAST));
    assertThat("Unexpected due by date.", actionDto.dueBy(), is(FUTURE));
    assertThat("Unexpected completed date.", actionDto.completed(), is(Instant.MAX));

    TisReferenceInfo refInfo = actionDto.tisReferenceInfo();
    assertThat("Unexpected TIS id.", refInfo.id(), is(TIS_ID));
    assertThat("Unexpected TIS type.", refInfo.type(), is(PROGRAMME_MEMBERSHIP));

    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(eventPublishingService).publishActionUpdateEvent(actionCaptor.capture());
    Action actionPublished = actionCaptor.getValue();

    assertThat("Unexpected action id.", actionPublished.id(), is(ACTION_ID));
    assertThat("Unexpected action type.", actionPublished.type(), is(SIGN_COJ));
    assertThat("Unexpected trainee id.", actionPublished.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected available from date.", actionPublished.availableFrom(), is(PAST));
    assertThat("Unexpected due by date.", actionPublished.dueBy(), is(FUTURE));
    assertThat("Unexpected completed date.", actionPublished.completed(), is(Instant.MAX));
  }

  @Test
  void shouldNotUpdateAlreadyCompletedCojAction() {
    TisReferenceInfo tisReference = new TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP);
    Action existingAction = new Action(ACTION_ID, SIGN_COJ, TRAINEE_ID, tisReference,
        PAST, FUTURE, Instant.now());
    CojReceivedEvent event = new CojReceivedEvent(TIS_ID, TRAINEE_ID,
        new ConditionsOfJoining(Instant.MIN, "version", Instant.MAX));

    when(repository.findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID,
        String.valueOf(PROGRAMME_MEMBERSHIP))).thenReturn(List.of(existingAction));

    Optional<ActionDto> optionalAction = service.updateAction(event);

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(false));
    verify(repository).findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID,
        String.valueOf(PROGRAMME_MEMBERSHIP));
    verifyNoMoreInteractions(repository);
    verifyNoInteractions(eventPublishingService);
  }

  @Test
  void shouldNotCompleteCojActionIfEventHasNoSyncedAt() {
    CojReceivedEvent event = new CojReceivedEvent(TIS_ID, TRAINEE_ID,
        new ConditionsOfJoining(Instant.MIN, "version", null));

    Optional<ActionDto> optionalAction = service.updateAction(event);

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(false));
    verifyNoInteractions(repository);
    verifyNoInteractions(eventPublishingService);
  }

  @Test
  void shouldNotUpdateActionWhenFormEventTraineeIdNull() {
    FormUpdateEvent event = new FormUpdateEvent("form name", "SUBMITTED", null,
        "formr-a", Instant.now(), Map.of("programmeMembershipId", TIS_ID));

    Optional<ActionDto> optionalAction = service.updateAction(event);

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(false));
    verifyNoInteractions(repository);
    verifyNoInteractions(eventPublishingService);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "unknown form type")
  void shouldNotUpdateActionWhenFormActionTypeInvalid(String formType) {
    FormUpdateEvent event = new FormUpdateEvent("form name", "SUBMITTED", TRAINEE_ID,
        formType, Instant.now(), Map.of("programmeMembershipId", TIS_ID));

    Optional<ActionDto> optionalAction = service.updateAction(event);

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(false));
    verifyNoInteractions(repository);
    verifyNoInteractions(eventPublishingService);
  }

  @ParameterizedTest
  @ValueSource(strings = {"formr-a", "formr-b"})
  void shouldNotUpdateActionWhenFormContentMissing(String formType) {
    FormUpdateEvent event = new FormUpdateEvent("form name", "SUBMITTED", TRAINEE_ID,
        formType, Instant.now(), null);

    Optional<ActionDto> optionalAction = service.updateAction(event);

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(false));
    verifyNoInteractions(repository);
    verifyNoInteractions(eventPublishingService);
  }

  @ParameterizedTest
  @ValueSource(strings = {"formr-a", "formr-b"})
  void shouldNotUpdateActionWhenFormProgrammeMembershipIdMissing(String formType) {
    FormUpdateEvent event = new FormUpdateEvent("form name", "SUBMITTED", TRAINEE_ID,
        formType, Instant.now(), Map.of("some field", "some value"));

    Optional<ActionDto> optionalAction = service.updateAction(event);

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(false));
    verifyNoInteractions(repository);
    verifyNoInteractions(eventPublishingService);
  }

  @ParameterizedTest
  @EnumSource(value = FormLifecycleState.class)
  void shouldNotUpdateActionWhenNoExistingFormActionFound(FormLifecycleState formState) {
    FormUpdateEvent event = new FormUpdateEvent("form name", formState.name(), TRAINEE_ID,
        "formr-a", Instant.now(), Map.of("programmeMembershipId", TIS_ID));

    when(repository.findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID,
        PROGRAMME_MEMBERSHIP.toString())).thenReturn(Collections.emptyList());

    Optional<ActionDto> optionalAction = service.updateAction(event);

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(false));
    verify(repository).findByTraineeIdAndTisReferenceInfo(any(), any(), any());
    verifyNoMoreInteractions(repository);
    verifyNoInteractions(eventPublishingService);
  }

  @ParameterizedTest
  @EnumSource(FormLifecycleState.class)
  void shouldNotUpdateActionWhenFormStateUnchanged(FormLifecycleState formState) {
    FormUpdateEvent event = new FormUpdateEvent("form name", formState.name(), TRAINEE_ID,
        "formr-a", Instant.now(), Map.of("programmeMembershipId", TIS_ID));
    TisReferenceInfo tisReference = new TisReferenceInfo(TRAINEE_ID, PERSON);
    Instant completedAt = null;
    if (FormLifecycleState.getCompleteSignFormStates().contains(formState)) {
      completedAt = Instant.now();
    }
    Action existingAction = new Action(ACTION_ID, SIGN_FORM_R_PART_A, TRAINEE_ID,
        tisReference, PAST, FUTURE, completedAt);
    when(repository.findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID,
        PROGRAMME_MEMBERSHIP.toString())).thenReturn(List.of(existingAction));

    Optional<ActionDto> optionalAction = service.updateAction(event);

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(false));
    verify(repository).findByTraineeIdAndTisReferenceInfo(any(), any(), any());
    verifyNoMoreInteractions(repository);
    verifyNoInteractions(eventPublishingService);
  }

  @Test
  void shouldNotUpdateActionWhenFormStateUndefined() {
    FormUpdateEvent event = new FormUpdateEvent("form name", "UNDEFINED STATE", TRAINEE_ID,
        "formr-a", Instant.now(), Map.of("programmeMembershipId", TIS_ID));
    TisReferenceInfo tisReference = new TisReferenceInfo(TRAINEE_ID, PERSON);
    Action existingAction = new Action(ACTION_ID, SIGN_FORM_R_PART_A, TRAINEE_ID,
        tisReference, PAST, FUTURE, Instant.now());
    when(repository.findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID,
        PROGRAMME_MEMBERSHIP.toString())).thenReturn(List.of(existingAction));

    Optional<ActionDto> optionalAction = service.updateAction(event);

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(false));
    verify(repository).findByTraineeIdAndTisReferenceInfo(any(), any(), any());
    verifyNoMoreInteractions(repository);
    verifyNoInteractions(eventPublishingService);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      formr-a | SUBMITTED
      formr-a | APPROVED
      formr-b | SUBMITTED
      formr-b | APPROVED
      """)
  void shouldCompleteActionWhenFormStateIsComplete(String formType, String formState) {
    Instant completedAt = Instant.now();
    FormUpdateEvent event = new FormUpdateEvent("form name", formState, TRAINEE_ID,
        formType, completedAt, Map.of("programmeMembershipId", TIS_ID));
    TisReferenceInfo tisReference = new TisReferenceInfo(TRAINEE_ID, PERSON);
    ActionType actionType = ActionType.getFormActionType(formType);
    Action existingAction = new Action(ACTION_ID, actionType, TRAINEE_ID,
        tisReference, PAST, FUTURE, null);

    when(repository.findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID,
        PROGRAMME_MEMBERSHIP.toString())).thenReturn(List.of(existingAction));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<ActionDto> optionalAction = service.updateAction(event);

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(true));
    ActionDto actionDto = optionalAction.get();
    assertThat("Unexpected action id.", actionDto.id(), is(ACTION_ID.toString()));
    assertThat("Unexpected completed date.", actionDto.completed(), is(completedAt));

    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(repository).save(actionCaptor.capture());
    verify(eventPublishingService).publishActionUpdateEvent(actionCaptor.capture());

    Action updatedAction = actionCaptor.getValue();
    assertThat("Unexpected completed date.", updatedAction.completed(), is(completedAt));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      formr-a | DELETED
      formr-a | DRAFT
      formr-a | REJECTED
      formr-a | UNSUBMITTED
      formr-a | WITHDRAWN
      formr-b | DELETED
      formr-b | DRAFT
      formr-b | REJECTED
      formr-b | UNSUBMITTED
      formr-b | WITHDRAWN
      """)
  void shouldUncompleteActionWhenFormStateIsUncomplete(String formType, String formState) {
    FormUpdateEvent event = new FormUpdateEvent("form name", formState, TRAINEE_ID,
        formType, Instant.now(), Map.of("programmeMembershipId", TIS_ID));
    TisReferenceInfo tisReference = new TisReferenceInfo(TRAINEE_ID, PERSON);
    ActionType actionType = ActionType.getFormActionType(formType);
    Action existingAction = new Action(ACTION_ID, actionType, TRAINEE_ID,
        tisReference, PAST, FUTURE, Instant.now());

    when(repository.findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID,
        PROGRAMME_MEMBERSHIP.toString())).thenReturn(List.of(existingAction));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<ActionDto> optionalAction = service.updateAction(event);

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(true));
    ActionDto actionDto = optionalAction.get();
    assertThat("Unexpected action id.", actionDto.id(), is(ACTION_ID.toString()));
    assertThat("Unexpected completed date.", actionDto.completed(), nullValue());

    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(repository).save(actionCaptor.capture());
    verify(eventPublishingService).publishActionUpdateEvent(actionCaptor.capture());

    Action updatedAction = actionCaptor.getValue();
    assertThat("Unexpected completed date.", updatedAction.completed(), nullValue());
  }

  @Test
  void shouldReturnEmptyWhenTraineeActionsNotFound() {
    when(repository.findAllByTraineeIdAndCompletedIsNullOrderByDueByAsc(TRAINEE_ID)).thenReturn(
        List.of());

    List<ActionDto> dtos = service.findIncompleteTraineeActions(TRAINEE_ID);

    assertThat("Unexpected action count.", dtos.size(), is(0));

    verifyNoMoreInteractions(eventPublishingService);
  }

  @ParameterizedTest
  @EnumSource(TisReferenceType.class)
  void shouldReturnActionsWhenTraineeActionsFound(TisReferenceType tisType) {
    TisReferenceInfo tisReference = new TisReferenceInfo(TIS_ID, tisType);
    ObjectId objectId1 = ObjectId.get();
    Action action1 = new Action(objectId1, REVIEW_DATA, TRAINEE_ID, tisReference, POST_EPOCH,
        FUTURE, null);
    ObjectId objectId2 = ObjectId.get();
    Action action2 = new Action(objectId2, REVIEW_DATA, TRAINEE_ID, tisReference, POST_EPOCH,
        FUTURE, null);
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
      assertThat("Unexpected available from date.", actDto.availableFrom(), is(POST_EPOCH));
      assertThat("Unexpected due by date.", actDto.dueBy(), is(FUTURE));
      assertThat("Unexpected completed date.", actDto.completed(), nullValue());

      TisReferenceInfo refInfo = actDto.tisReferenceInfo();
      assertThat("Unexpected TIS id.", refInfo.id(), is(TIS_ID));
      assertThat("Unexpected TIS type.", refInfo.type(), is(tisType));
    });
  }

  @Test
  void shouldNotCompleteActionWhenActionIdInvalid() {
    Optional<ActionDto> optionalAction = service.completeAsUser(TRAINEE_ID, "40");

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(false));
    verifyNoInteractions(repository);
  }

  @Test
  void shouldNotCompleteActionWhenActionNotFound() {
    when(repository.findByIdAndTraineeId(ACTION_ID, TRAINEE_ID)).thenReturn(Optional.empty());

    Optional<ActionDto> optionalAction = service.completeAsUser(TRAINEE_ID, ACTION_ID.toString());

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

    Optional<ActionDto> optionalAction = service.completeAsUser(TRAINEE_ID, ACTION_ID.toString());

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(false));
    verify(repository, never()).save(any());
    verifyNoInteractions(eventPublishingService);
  }

  @ParameterizedTest
  @MethodSource("listNonUserCompletableActionTypes")
  void shouldNotCompleteActionWhenActionNotUserCompletable(ActionType nonUserCompletableType) {
    TisReferenceInfo tisReference = new TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP);
    Action action = new Action(ACTION_ID, nonUserCompletableType, TRAINEE_ID, tisReference, PAST,
        FUTURE, null);
    when(repository.findByIdAndTraineeId(ACTION_ID, TRAINEE_ID)).thenReturn(Optional.of(action));

    Optional<ActionDto> optionalAction = service.completeAsUser(TRAINEE_ID, ACTION_ID.toString());

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(false));
    verify(repository, never()).save(any());
    verifyNoInteractions(eventPublishingService);
  }

  @ParameterizedTest
  @EnumSource(TisReferenceType.class)
  void shouldSaveCompletedActionWhenActionCompletableAndTraineeMatches(TisReferenceType tisType) {
    TisReferenceInfo tisReference = new TisReferenceInfo(TIS_ID, tisType);
    Action action = new Action(ACTION_ID, REVIEW_DATA, TRAINEE_ID, tisReference, PAST, FUTURE,
        null);
    when(repository.findByIdAndTraineeId(ACTION_ID, TRAINEE_ID)).thenReturn(Optional.of(action));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.completeAsUser(TRAINEE_ID, ACTION_ID.toString());

    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(repository).save(actionCaptor.capture());
    verify(eventPublishingService).publishActionUpdateEvent(actionCaptor.capture());

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

    Optional<ActionDto> optionalAction = service.completeAsUser(TRAINEE_ID, ACTION_ID.toString());

    assertThat("Unexpected action presence.", optionalAction.isPresent(), is(true));

    ActionDto actionDto = optionalAction.get();
    assertThat("Unexpected action id.", actionDto.id(), is(ACTION_ID.toString()));
    assertThat("Unexpected action type.", actionDto.type(), is(REVIEW_DATA.toString()));
    assertThat("Unexpected trainee id.", actionDto.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected available from date.", actionDto.availableFrom(), is(PAST));
    assertThat("Unexpected due by date.", actionDto.dueBy(), is(FUTURE));
    assertThat("Unexpected completed date.", actionDto.completed(),
        instanceOf(Instant.class));

    TisReferenceInfo refInfo = actionDto.tisReferenceInfo();
    assertThat("Unexpected TIS id.", refInfo.id(), is(TIS_ID));
    assertThat("Unexpected TIS type.", refInfo.type(), is(tisType));
  }

  @Test
  void shouldInsertActionsWhenPlacementOperationLoadAndPostEpoch() {
    PlacementDto dto = new PlacementDto(TIS_ID, TRAINEE_ID, ACTIONS_EPOCH, PLACEMENT_TYPE);

    when(repository.findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID,
        String.valueOf(PLACEMENT))).thenReturn(Collections.emptyList());

    service.updateActions(Operation.LOAD, dto);

    verify(repository).findByTraineeIdAndTisReferenceInfo(any(), any(), any());
    verify(repository).insert(anyList());
  }

  @Test
  void shouldNotInsertActionsWhenPlacementOperationLoadAndPreEpoch() {
    PlacementDto dto = new PlacementDto(TIS_ID, TRAINEE_ID, PRE_EPOCH, PLACEMENT_TYPE);

    when(repository.findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID,
        String.valueOf(PLACEMENT))).thenReturn(Collections.emptyList());

    service.updateActions(Operation.LOAD, dto);

    verifyNoInteractions(eventPublishingService);
    verify(repository, never()).insert(anyList());
  }

  @Test
  void shouldNotInsertActionAndDeleteAnyExistingNotCompleteActionsWhenPlacementTypeIgnored() {
    PlacementDto dto = new PlacementDto(TIS_ID, TRAINEE_ID, POST_EPOCH, "ignored placement type");

    when(repository.deleteByTraineeIdAndTisReferenceInfoAndActionType(eq(TRAINEE_ID),
        eq(TIS_ID), eq(String.valueOf(PLACEMENT)), any())).thenReturn(Collections.emptyList());

    service.updateActions(Operation.LOAD, dto);
    verifyNoInteractions(eventPublishingService);
    verify(repository, never()).insert(anyList());
  }

  @ParameterizedTest
  @MethodSource("providePreAndPostEpochDates")
  void shouldDeleteAnyExistingNotCompleteActionsWhenPlacementOperationIsDelete(LocalDate theDate) {
    PlacementDto dto = new PlacementDto(TIS_ID, TRAINEE_ID, theDate, PLACEMENT_TYPE);

    Action action1 = new Action(ObjectId.get(), REVIEW_DATA, TRAINEE_ID, null, null,
        POST_EPOCH, null);
    Action action2 = new Action(ObjectId.get(), REVIEW_DATA, TRAINEE_ID, null, null,
        POST_EPOCH, null);
    when(repository.deleteByTraineeIdAndTisReferenceInfoAndNotComplete(TRAINEE_ID, TIS_ID,
        String.valueOf(PLACEMENT))).thenReturn(List.of(action1, action2));

    service.updateActions(Operation.DELETE, dto);
    verify(eventPublishingService).publishActionDeleteEvent(action1);
    verify(eventPublishingService).publishActionDeleteEvent(action2);
    verify(repository, never()).insert(anyList());
  }

  @ParameterizedTest
  @MethodSource("providePreAndPostEpochDates")
  void shouldDeleteAnyExistingNotCompleteActionsWhenProgrammeMembershipOperationIsDelete(
      LocalDate theDate) {
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(TIS_ID, TRAINEE_ID, theDate, null);

    Action action1 = new Action(ObjectId.get(), REVIEW_DATA, TRAINEE_ID, null, null,
        POST_EPOCH, null);
    Action action2 = new Action(ObjectId.get(), SIGN_COJ, TRAINEE_ID, null, null,
        POST_EPOCH, null);
    when(repository.deleteByTraineeIdAndTisReferenceInfoAndNotComplete(TRAINEE_ID, TIS_ID,
        String.valueOf(PROGRAMME_MEMBERSHIP))).thenReturn(List.of(action1, action2));

    service.updateActions(Operation.DELETE, dto);
    verify(eventPublishingService).publishActionDeleteEvent(action1);
    verify(eventPublishingService).publishActionDeleteEvent(action2);
    verify(repository, never()).insert(anyList());
  }

  @Test
  void shouldNotCreatePlacementActionIfOneAlreadyExistsWithSameDueDate() {
    PlacementDto dto = new PlacementDto(TIS_ID, TRAINEE_ID, POST_EPOCH, PLACEMENT_TYPE);
    Action existingAction =
        new Action(ACTION_ID, REVIEW_DATA, TRAINEE_ID, new TisReferenceInfo(TIS_ID, PLACEMENT),
            PRE_EPOCH, POST_EPOCH, null);
    when(repository.findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID,
        String.valueOf(PLACEMENT))).thenReturn(List.of(existingAction));

    List<ActionDto> actions = service.updateActions(Operation.LOAD, dto);

    assertThat("Unexpected action count.", actions.size(), is(0));
    verify(repository).findByTraineeIdAndTisReferenceInfo(any(), any(), any());
    verifyNoMoreInteractions(repository);
    verifyNoInteractions(eventPublishingService);
  }

  @Test
  void shouldReplacePlacementActionsIfTheyAlreadyExistsWithDifferentDueDateAndPostEpoch() {
    List<Action> existingActions = new ArrayList<>();
    for (ActionType actionType : ActionType.getPlacementActionTypes()) {
      Action existingAction = new Action(ObjectId.get(), actionType, TRAINEE_ID,
          new TisReferenceInfo(TIS_ID, PLACEMENT), PRE_EPOCH, POST_EPOCH.minusDays(1),
          Instant.now());
      existingActions.add(existingAction);
      when(repository.deleteByTraineeIdAndTisReferenceInfoAndActionType(TRAINEE_ID,
          TIS_ID, String.valueOf(PLACEMENT), String.valueOf(actionType)))
          .thenReturn(Collections.singletonList(existingAction));
    }

    when(repository.insert(anyIterable())).thenAnswer(inv -> inv.getArgument(0));

    when(repository.findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID,
        String.valueOf(PLACEMENT))).thenReturn(existingActions);

    PlacementDto dto = new PlacementDto(TIS_ID, TRAINEE_ID, POST_EPOCH, PLACEMENT_TYPE);
    List<ActionDto> actions = service.updateActions(Operation.LOAD, dto);

    int expectedActionCount = ActionType.getPlacementActionTypes().size();
    assertThat("Unexpected action count.", actions.size(), is(expectedActionCount));

    verify(repository).findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID, PLACEMENT.toString());

    for (ActionType actionType : ActionType.getPlacementActionTypes()) {
      Optional<ActionDto> actionOfType = actions.stream()
          .filter(a -> a.type().equals(actionType.toString()))
          .findFirst();
      assertThat("Missing action for type: " + actionType, actionOfType.isPresent(), is(true));
      verify(repository).deleteByTraineeIdAndTisReferenceInfoAndActionType(
          TRAINEE_ID, TIS_ID, PLACEMENT.toString(), actionType.toString());
      ArgumentCaptor<Action> deletedActionCaptor = ArgumentCaptor.forClass(Action.class);
      verify(eventPublishingService).publishActionDeleteEvent(deletedActionCaptor.capture());
      Action deletedAction = deletedActionCaptor.getValue();
      assertThat("Unexpected deleted action published.",
          existingActions.contains(deletedAction), is(true));
    }

    verify(repository).insert(anyList());
    verifyNoMoreInteractions(repository);
    verify(eventPublishingService, times(expectedActionCount)).publishActionUpdateEvent(any());
  }

  @Test
  void shouldNotReplacePlacementActionsIfOneAlreadyExistsWithDifferentDueDateAndPreEpoch() {
    PlacementDto dto = new PlacementDto(TIS_ID, TRAINEE_ID, PRE_EPOCH, PLACEMENT_TYPE);
    Action existingAction =
        new Action(ACTION_ID, REVIEW_DATA, TRAINEE_ID, new TisReferenceInfo(TIS_ID, PLACEMENT),
            PRE_EPOCH, POST_EPOCH.minusDays(1), Instant.now());

    when(repository.insert(anyIterable())).thenAnswer(inv -> inv.getArgument(0));

    when(repository.findByTraineeIdAndTisReferenceInfo(TRAINEE_ID, TIS_ID,
        String.valueOf(PLACEMENT))).thenReturn(List.of(existingAction));
    when(repository.deleteByTraineeIdAndTisReferenceInfoAndActionType(TRAINEE_ID, TIS_ID,
        String.valueOf(PLACEMENT), String.valueOf(REVIEW_DATA)))
        .thenReturn(List.of(existingAction));

    List<ActionDto> actions = service.updateActions(Operation.LOAD, dto);

    assertThat("Unexpected action count.", actions.size(), is(0));
    verify(repository).findByTraineeIdAndTisReferenceInfo(any(), any(), any());
    verify(repository).deleteByTraineeIdAndTisReferenceInfoAndActionType(any(), any(), any(),
        any());
    verifyNoMoreInteractions(repository);
    verify(eventPublishingService).publishActionDeleteEvent(existingAction);
    verifyNoMoreInteractions(eventPublishingService);
  }

  @ParameterizedTest
  @MethodSource("listPlacementTypes")
  void shouldInsertActionsOnPlacementCreate(String placementType) {
    PlacementDto dto = new PlacementDto(TIS_ID, TRAINEE_ID, POST_EPOCH, placementType);

    when(repository.insert(anyIterable())).thenAnswer(inv -> inv.getArgument(0));

    when(repository.findByTraineeIdAndTisReferenceInfo(any(), any(), any()))
        .thenReturn(Collections.emptyList());

    List<ActionDto> actions = service.updateActions(Operation.LOAD, dto);

    int expectedActionCount = ActionType.getPlacementActionTypes().size();
    assertThat("Unexpected action count.", actions.size(), is(expectedActionCount));

    for (ActionType actionType : ActionType.getPlacementActionTypes()) {
      Optional<ActionDto> actionOfType = actions.stream()
          .filter(a -> a.type().equals(actionType.toString()))
          .findFirst();
      assertThat("Missing action for type: " + actionType, actionOfType.isPresent(), is(true));
      ActionDto action = actionOfType.get();
      assertThat("Unexpected action id.", action.id(), nullValue());
      assertThat("Unexpected action type.", action.type(), is(actionType.toString()));
      assertThat("Unexpected trainee id.", action.traineeId(), is(TRAINEE_ID));
      assertThat("Unexpected available from date.", action.availableFrom(),
          is(POST_EPOCH.minusWeeks(12)));
      assertThat("Unexpected due by date.", action.dueBy(), is(POST_EPOCH));
      assertThat("Unexpected completed date.", action.completed(), nullValue());

      TisReferenceInfo tisReference = action.tisReferenceInfo();
      assertThat("Unexpected TIS id.", tisReference.id(), is(TIS_ID));
      assertThat("Unexpected TIS type.", tisReference.type(), is(PLACEMENT));

      // should broadcast inserted action
      ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
      verify(eventPublishingService).publishActionUpdateEvent(actionCaptor.capture());

      Action broadcastAction = actionCaptor.getValue();
      assertThat("Unexpected action id.", broadcastAction.id(), nullValue());
      assertThat("Unexpected action type.", broadcastAction.type(), is(actionType));
      assertThat("Unexpected trainee id.", broadcastAction.traineeId(), is(TRAINEE_ID));
      assertThat("Unexpected available from date.", broadcastAction.availableFrom(),
          is(POST_EPOCH.minusWeeks(12)));
      assertThat("Unexpected due by date.", broadcastAction.dueBy(), is(POST_EPOCH));
      assertThat("Unexpected completed date.", broadcastAction.completed(), nullValue());
      TisReferenceInfo refInfo = broadcastAction.tisReferenceInfo();
      assertThat("Unexpected TIS id.", refInfo.id(), is(TIS_ID));
      assertThat("Unexpected TIS type.", refInfo.type(), is(PLACEMENT));
    }
  }

  @Test
  void shouldInsertAllActionsOnFirstSightOfAccountConfirmation() {
    AccountConfirmedEvent event = new AccountConfirmedEvent(UUID.randomUUID(), TRAINEE_ID,
        "some@email.test");

    when(repository.findByTraineeIdAndTisReferenceInfo(any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(repository.insert(anyIterable())).thenAnswer(inv -> inv.getArgument(0));

    List<ActionDto> actions = service.updateActions(Operation.LOAD, event);

    int expectedActionCount = ActionType.getPersonActionTypes().size();
    assertThat("Unexpected action count.", actions.size(), is(expectedActionCount));

    // should broadcast inserted action
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(eventPublishingService, times(expectedActionCount))
        .publishActionUpdateEvent(actionCaptor.capture());
    List<Action> actionsPublished = actionCaptor.getAllValues();

    for (ActionType actionType : ActionType.getPersonActionTypes()) {
      Optional<ActionDto> actionOfType = actions.stream()
          .filter(a -> a.type().equals(actionType.toString()))
          .findFirst();
      assertThat("Missing action for type: " + actionType, actionOfType.isPresent(), is(true));
      ActionDto action = actionOfType.get();
      assertThat("Unexpected action id.", action.id(), nullValue());
      assertThat("Unexpected action type.", action.type(), is(actionType.toString()));
      assertThat("Unexpected trainee id.", action.traineeId(), is(TRAINEE_ID));
      assertThat("Unexpected available from date.", action.availableFrom(), nullValue());
      assertThat("Unexpected due by date.", action.dueBy(), nullValue());
      assertThat("Unexpected completed date.", action.completed(), notNullValue());

      TisReferenceInfo tisReference = action.tisReferenceInfo();
      assertThat("Unexpected TIS id.", tisReference.id(), is(TRAINEE_ID));
      assertThat("Unexpected TIS type.", tisReference.type(), is(PERSON));

      Action actionPublished = actionsPublished.stream()
          .filter(a -> a.type().equals(actionType))
          .findFirst()
          .orElseThrow(
              () -> new AssertionError("Missing action published for type: " + actionType));

      assertThat("Unexpected action id.", actionPublished.id(), nullValue());
      assertThat("Unexpected action type.", actionPublished.type(), is(actionType));
      assertThat("Unexpected trainee id.", actionPublished.traineeId(), is(TRAINEE_ID));
      assertThat("Unexpected available from date.", actionPublished.availableFrom(),
          nullValue());
      assertThat("Unexpected due by date.", actionPublished.dueBy(), nullValue());
      assertThat("Unexpected completed date.", actionPublished.completed(), notNullValue());
      TisReferenceInfo refInfo = actionPublished.tisReferenceInfo();
      assertThat("Unexpected TIS id.", refInfo.id(), is(TRAINEE_ID));
      assertThat("Unexpected TIS type.", refInfo.type(), is(PERSON));
    }
  }

  @Test
  void shouldNotInsertActionsOnAlreadyActionedAccountConfirmation() {
    AccountConfirmedEvent event = new AccountConfirmedEvent(UUID.randomUUID(), TRAINEE_ID,
        "some@email.test");

    TisReferenceInfo tisReference = new TisReferenceInfo(TRAINEE_ID, PERSON);
    List<Action> existingActions = new ArrayList<>();
    for (ActionType actionType : ActionType.getPersonActionTypes()) {
      Action existingAction = new Action(ObjectId.get(), actionType, TRAINEE_ID, tisReference,
          null, null, Instant.now());
      existingActions.add(existingAction);
    }
    when(repository.findByTraineeIdAndTisReferenceInfo(any(), any(), any()))
        .thenReturn(existingActions);

    List<ActionDto> actions = service.updateActions(Operation.LOAD, event);

    assertThat("Unexpected action count.", actions.size(), is(0));
    verify(repository).findByTraineeIdAndTisReferenceInfo(any(), any(), any());
    verifyNoMoreInteractions(repository);
    verifyNoMoreInteractions(eventPublishingService);
  }

  @Test
  void shouldDeleteAnyExistingNotCompleteActionsWhenAccountConfirmationIsDelete() {
    AccountConfirmedEvent event = new AccountConfirmedEvent(UUID.randomUUID(), TRAINEE_ID,
        "some@email.test");

    Action action1 = new Action(ObjectId.get(), REGISTER_TSS, TRAINEE_ID, null, null,
        null, null);
    when(repository.deleteByTraineeIdAndTisReferenceInfoAndNotComplete(TRAINEE_ID, TRAINEE_ID,
        String.valueOf(PERSON))).thenReturn(List.of(action1));

    service.updateActions(Operation.DELETE, event);
    verify(eventPublishingService).publishActionDeleteEvent(action1);
    verify(repository, never()).insert(anyList());
  }

  static Stream<String> listPlacementTypes() {
    return PLACEMENT_TYPES_TO_ACT_ON.stream();
  }

  static Stream<ActionType> listNonUserCompletableActionTypes() {
    return Arrays.stream(ActionType.values())
        .filter(a -> !ActionType.getUserCompletableActionTypes().contains(a));
  }

  static Stream<Arguments> providePreAndPostEpochDates() {
    return Stream.of(
        Arguments.of(PRE_EPOCH),
        Arguments.of(POST_EPOCH)
    );
  }
}
