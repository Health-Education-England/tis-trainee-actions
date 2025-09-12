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

import static uk.nhs.tis.trainee.actions.model.ActionType.REGISTER_TSS;
import static uk.nhs.tis.trainee.actions.model.ActionType.REVIEW_DATA;
import static uk.nhs.tis.trainee.actions.model.ActionType.SIGN_COJ;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PERSON;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PLACEMENT;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PROGRAMME_MEMBERSHIP;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.actions.dto.AccountConfirmedEvent;
import uk.nhs.tis.trainee.actions.dto.ActionDto;
import uk.nhs.tis.trainee.actions.dto.CojReceivedEvent;
import uk.nhs.tis.trainee.actions.dto.FormUpdateEvent;
import uk.nhs.tis.trainee.actions.dto.PlacementDto;
import uk.nhs.tis.trainee.actions.dto.ProgrammeMembershipDto;
import uk.nhs.tis.trainee.actions.dto.enumeration.FormLifecycleState;
import uk.nhs.tis.trainee.actions.event.Operation;
import uk.nhs.tis.trainee.actions.mapper.ActionMapper;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.model.ActionType;
import uk.nhs.tis.trainee.actions.repository.ActionRepository;

/**
 * A service provided Action functionality.
 */
@Slf4j
@Service
public class ActionService {

  public static final LocalDate ACTIONS_EPOCH = LocalDate.of(2024, 8, 1);
  public static final List<String> PLACEMENT_TYPES_TO_ACT_ON
      = List.of("In post", "In post - Acting up", "In Post - Extension");
  public static final String FORM_PROGRAMME_MEMBERSHIP_ID_FIELD = "programmeMembershipId";

  private final ActionRepository repository;
  private final ActionMapper mapper;
  private final EventPublishingService eventPublishingService;

  /**
   * The constructor of action service.
   */
  public ActionService(ActionRepository repository, ActionMapper mapper,
      EventPublishingService eventPublishingService) {
    this.repository = repository;
    this.mapper = mapper;
    this.eventPublishingService = eventPublishingService;
  }

  /**
   * Add or update actions for a given Placement DTO.
   *
   * @param dto     The placement DTO.
   * @param actions The list of actions to supplement with new or updated actions.
   */
  private void addOrUpdatePlacementAction(PlacementDto dto, List<Action> actions) {
    List<Action> existingActions = repository.findByTraineeIdAndTisReferenceInfo(
        dto.traineeId(), dto.id(), PLACEMENT.toString());

    for (ActionType actionType : ActionType.getPlacementActionTypes()) {
      Action newAction = mapper.toAction(dto, actionType);
      if (existingActions.stream().noneMatch(a -> a.type().equals(actionType))) {
        // only add action if it does not already exist
        addActionIfDueAfterEpoch(newAction, actions);
      } else {
        if (replaceUpdatedPlacementAction(existingActions, newAction, dto.id())) {
          List<Action> deletedActions
              = repository.deleteByTraineeIdAndTisReferenceInfoAndActionType(
              newAction.traineeId(), newAction.tisReferenceInfo().id(),
              newAction.tisReferenceInfo().type().toString(),
              newAction.type().toString()); //completed actions are deleted here
          deletedActions.forEach(eventPublishingService::publishActionDeleteEvent);
          addActionIfDueAfterEpoch(newAction, actions);
        }
      }
    }
  }

  /**
   * Updates the actions associated with the given Operation and Placement data.
   *
   * @param operation The operation that triggered the update.
   * @param dto       The Placement data associated with the operation.
   * @return A list of new actions, empty if no new actions added.
   */
  public List<ActionDto> updateActions(Operation operation, PlacementDto dto) {
    boolean deleteAction = false;
    List<Action> actions = new ArrayList<>();

    if (Objects.equals(operation, Operation.LOAD)) {
      if (PLACEMENT_TYPES_TO_ACT_ON.stream().anyMatch(dto.placementType()::equalsIgnoreCase)) {

        addOrUpdatePlacementAction(dto, actions);

      } else {
        log.info("Placement {} of type {} is ignored", dto.id(), dto.placementType());
        deleteAction = true;
      }
    } else if (Objects.equals(operation, Operation.DELETE)) {
      log.info("Placement {} is deleted", dto.id());
      deleteAction = true;
    }

    if (deleteAction) {
      Action action = mapper.toAction(dto, REVIEW_DATA);
      deleteIncompleteActions(action);
    }

    if (actions.isEmpty()) {
      log.info("No new actions required for Placement {}", dto.id());
      return List.of();
    }

    log.info("Adding {} new action(s) for Placement {}.", actions.size(), dto.id());
    List<Action> actionInserted = repository.insert(actions);
    actionInserted.forEach(eventPublishingService::publishActionUpdateEvent);
    return mapper.toDtos(actionInserted);
  }

  /**
   * Updates the actions associated with the given Operation and Programme Membership data.
   *
   * @param operation The operation that triggered the update.
   * @param dto       The Programme Membership data associated with the operation.
   * @return A list of updated actions, empty if no actions required.
   */
  public List<ActionDto> updateActions(Operation operation, ProgrammeMembershipDto dto) {
    List<Action> actions = new ArrayList<>();

    List<Action> existingActions = repository.findByTraineeIdAndTisReferenceInfo(
        dto.traineeId(), dto.id(), PROGRAMME_MEMBERSHIP.toString());

    if (Objects.equals(operation, Operation.LOAD)
        && !(dto.startDate().isBefore(ACTIONS_EPOCH))) {
      for (ActionType actionType : ActionType.getProgrammeActionTypes()) {
        Action newAction = mapper.toAction(dto, actionType);
        if (existingActions.stream().noneMatch(a -> a.type().equals(actionType))) {
          // only add action if it does not already exist
          addActionIfDueAfterEpoch(newAction, actions);
        } else {
          log.info("Programme Membership {} already has action of type {}, skipping.",
              dto.id(), actionType);
        }
      }

    } else if (Objects.equals(operation, Operation.DELETE)) {
      log.info("Programme membership {} is deleted", dto.id());
      Action action = mapper.toAction(dto, REVIEW_DATA);
      deleteIncompleteActions(action);
    }

    // Handle Conditions of Joining (CoJ) action. We ignore ACTIONS_EPOCH and start date here to
    // avoid dangling CoJ actions where a programme start date has been updated to before the epoch.
    if (Objects.equals(operation, Operation.LOAD) && dto.conditionsOfJoining() != null) {
      log.info("Completing any CoJ actions for Programme Membership {}.", dto.id());
      // If a CoJ action has just been created, replace it with a new completed action. This should
      // only be possible if bulk-loading programme memberships that already have signed CoJs.
      Optional<Action> addedAction = actions.stream()
          .filter(a -> a.type().equals(SIGN_COJ)).findFirst();
      if (addedAction.isPresent()) {
        actions.remove(addedAction.get());
        actions.add(mapper.complete(addedAction.get(), dto.conditionsOfJoining().syncedAt()));
      }
      // Otherwise, if an existing CoJ action exists, complete it (if it's incomplete).
      Optional<Action> existingAction = existingActions.stream()
          .filter(a -> a.type().equals(SIGN_COJ)).findFirst();
      existingAction.ifPresent(action -> complete(action, dto.conditionsOfJoining().syncedAt()));
    }

    if (actions.isEmpty()) {
      log.info("No new actions required for Programme Membership {}", dto.id());
      return List.of();
    }

    log.info("Adding {} new action(s) for Programme Membership {}.", actions.size(), dto.id());
    List<Action> actionInserted = repository.insert(actions);
    actionInserted.forEach(eventPublishingService::publishActionUpdateEvent);
    return mapper.toDtos(actionInserted);
  }

  /**
   * Updates the actions associated with the given Operation and User account data.
   *
   * @param operation The operation that triggered the update.
   * @param account   The Account confirmation event data associated with the operation.
   * @return A list of updated actions, empty if no actions required.
   */
  public List<ActionDto> updateActions(Operation operation, AccountConfirmedEvent account) {
    List<Action> actions = new ArrayList<>();

    if (Objects.equals(operation, Operation.LOAD)) {
      List<Action> existingActions = repository.findByTraineeIdAndTisReferenceInfo(
          account.traineeId(), account.traineeId(), PERSON.toString());
      for (ActionType actionType : ActionType.getPersonActionTypes()) {
        Action newAction = mapper.toAction(account, actionType);
        if (existingActions.stream().noneMatch(a -> a.type().equals(actionType))) {
          // only add action if it does not already exist
          actions.add(newAction);
        } else {
          log.info("Account for person {} already has action of type {}, skipping.",
              account.traineeId(), actionType);
        }
      }
    } else if (Objects.equals(operation, Operation.DELETE)) {
      log.info("Account for person {} is deleted.", account.traineeId());
      Action action = mapper.toAction(account, REGISTER_TSS);
      deleteIncompleteActions(action);
      //None will be deleted since these are all complete actions - is this correct?
      //What if they register, deregister and then need to reregister?
      //At present, only if incomplete confirmation actions are created by some other process will
      //these be deleted.
    }

    if (actions.isEmpty()) {
      log.info("No new actions required for Person account {}", account.traineeId());
      return List.of();
    }

    log.info("Adding {} new action(s) for Person account {}.", actions.size(), account.traineeId());
    List<Action> actionInserted = repository.insert(actions);
    actionInserted.forEach(eventPublishingService::publishActionUpdateEvent);
    return mapper.toDtos(actionInserted);
  }

  /**
   * Updates the action associated with the given CoJ received event.
   *
   * @param event The CoJ received event containing the data to update the action.
   * @return An Optional containing the updated ActionDto, or empty if no action was updated.
   */
  public Optional<ActionDto> updateAction(CojReceivedEvent event) {
    log.info("Updating action for CoJ received event: {}", event);
    if (event.conditionsOfJoining() == null || event.conditionsOfJoining().syncedAt() == null) {
      log.warn("No synced CoJ data provided in the event.");
      return Optional.empty();
    }

    List<Action> existingActions = repository.findByTraineeIdAndTisReferenceInfo(
        event.traineeId(), event.id(), PROGRAMME_MEMBERSHIP.toString());
    Optional<Action> existingAction = existingActions.stream()
        .filter(a -> a.type().equals(SIGN_COJ)).findFirst();
    if (existingAction.isEmpty()) {
      log.warn("No existing CoJ action found for trainee ID: {} and programme membership ID: {}",
          event.traineeId(), event.id());
      return Optional.empty();
    } else {
      return (complete(existingAction.get(), event.conditionsOfJoining().syncedAt()));
    }
  }

  /**
   * Updates the action associated with the given Form update event.
   *
   * @param event The form update event containing the data to update the action.
   * @return An Optional containing the updated ActionDto, or empty if no action was updated.
   */
  public Optional<ActionDto> updateAction(FormUpdateEvent event) {
    log.info("Updating action for form updated event: {}", event);
    ActionType formAction = ActionType.getFormActionType(event.formType());
    if (event.traineeId() == null || formAction == null || event.formContentDto() == null
        || event.formContentDto().get(FORM_PROGRAMME_MEMBERSHIP_ID_FIELD) == null) {
      log.warn("No Form data provided in the event.");
      return Optional.empty();
    }

    String pmUuid = event.formContentDto().get(FORM_PROGRAMME_MEMBERSHIP_ID_FIELD).toString();
    List<Action> existingActions = repository.findByTraineeIdAndTisReferenceInfo(
        event.traineeId(), pmUuid, PROGRAMME_MEMBERSHIP.toString());
    Optional<Action> existingAction = existingActions.stream()
        .filter(a -> a.type().equals(formAction)).findFirst();
    if (existingAction.isEmpty()) {
      log.warn("No existing {} action found for trainee ID: {} and programme membership ID: {}",
          formAction, event.traineeId(), pmUuid);
      return Optional.empty();
    } else {
      FormLifecycleState lifecycleState;
      try {
        lifecycleState = FormLifecycleState.valueOf(event.lifecycleState());
      } catch (IllegalArgumentException e) {
        lifecycleState = null;
      }
      if (FormLifecycleState.getCompleteSignFormStates().contains(lifecycleState)) {
        return (complete(existingAction.get(), event.eventDate()));
      } else if (FormLifecycleState.getUncompleteSignFormStates().contains(lifecycleState)) {
        return (uncomplete(existingAction.get()));
      } else {
        log.warn("Form lifecycle state {} is not handled for action update.",
            event.lifecycleState());
        return Optional.empty();
      }
    }
  }

  /**
   * Add action to list of actions if it is due after the actions epoch.
   *
   * @param action  The action to process.
   * @param actions The current list of actions.
   */
  private void addActionIfDueAfterEpoch(Action action, List<Action> actions) {
    if (!action.dueBy().isBefore(ACTIONS_EPOCH)) {
      actions.add(action);
    } else {
      log.debug("Not adding action for {} {} starting {} before epoch {}",
          action.tisReferenceInfo().type(), action.tisReferenceInfo().id(), action.dueBy(),
          ACTIONS_EPOCH);
    }
  }

  /**
   * Delete any not-completed actions (of any type) that match the given action item.
   *
   * @param likeAction The action to use to identify candidates for deletion.
   */
  private void deleteIncompleteActions(Action likeAction) {
    //remove any pre-existing saved action(s) that have not been completed
    List<Action> deletedActions = repository.deleteByTraineeIdAndTisReferenceInfoAndNotComplete(
        likeAction.traineeId(),
        likeAction.tisReferenceInfo().id(),
        likeAction.tisReferenceInfo().type().toString());
    log.info("{} obsolete not completed action(s) deleted for {} {}", deletedActions.size(),
        likeAction.tisReferenceInfo().type(), likeAction.tisReferenceInfo().id());
    deletedActions.forEach(eventPublishingService::publishActionDeleteEvent);
  }

  /**
   * Find all available incomplete actions associated with a given trainee ID.
   *
   * @param traineeId The ID of the trainee to get actions for.
   * @return The found actions, empty if no actions found.
   */
  public List<ActionDto> findIncompleteTraineeActions(String traineeId) {
    List<Action> actions = repository.findAllByTraineeIdAndCompletedIsNullOrderByDueByAsc(
        traineeId).stream()
        .filter(a -> a.availableFrom() == null || !a.availableFrom().isAfter(LocalDate.now()))
        .toList();
    return mapper.toDtos(actions);
  }

  /**
   * Find all actions associated with a given trainee ID and programme membership ID.
   *
   * @param traineeId             The ID of the trainee to get actions for.
   * @param programmeMembershipId The ID of the programme membership to get actions for.
   * @return The found actions, empty if no actions found.
   */
  public List<ActionDto> findTraineeProgrammeMembershipActions(String traineeId,
      String programmeMembershipId) {
    List<Action> programmeActions = repository.findByTraineeIdAndTisReferenceInfo(
        traineeId, programmeMembershipId, PROGRAMME_MEMBERSHIP.toString());
    List<Action> traineeActions = repository.findByTraineeIdAndTisReferenceInfo(
        traineeId, traineeId, PERSON.toString());
    List<Action> allActions = Stream.concat(programmeActions.stream(), traineeActions.stream())
        .toList();
    return mapper.toDtos(allActions);
  }

  /**
   * Complete an action.
   *
   * @param action      The action to complete.
   * @param complete    Whether to complete the action or not.
   * @param completedAt The timestamp when the action was completed. If null, current time is used.
   * @return The completed action, or empty if not found.
   */
  private Optional<ActionDto> updateActionStatus(Action action, boolean complete,
      Instant completedAt) {
    if ((action.completed() != null && complete)
        || (action.completed() == null && !complete)) {
      log.info("Skipping action completion = {} as the action already had that status.", complete);
      return Optional.empty();
    }

    Action updatedAction;
    if (complete) {
      if (completedAt == null) {
        updatedAction = mapper.complete(action);
      } else {
        updatedAction = mapper.complete(action, completedAt);
      }
    } else {
      updatedAction = mapper.uncomplete(action);
    }
    updatedAction = repository.save(updatedAction);
    eventPublishingService.publishActionUpdateEvent(updatedAction);
    log.info("Action {} marked as completed = {} at {}.", updatedAction.id(),
        complete, updatedAction.completed());
    return Optional.of(mapper.toDto(updatedAction));
  }

  /**
   * Complete an action.
   *
   * @param action      The action to complete.
   * @param completedAt The timestamp when the action was completed. If null, current time is used.
   * @return The completed action, or empty if not found.
   */
  private Optional<ActionDto> complete(Action action, Instant completedAt) {
    return updateActionStatus(action, true, completedAt);
  }

  /**
   * Un-complete an action.
   *
   * @param action The action to un-complete.
   * @return The uncompleted action, or empty if not found.
   */
  private Optional<ActionDto> uncomplete(Action action) {
    return updateActionStatus(action, false, null);
  }

  /**
   * Complete a trainee's action. It must be a user-completable action.
   *
   * @param traineeId The ID of the trainee who owns the action to be completed.
   * @param actionId  The ID of the action to complete.
   * @return The completed action, or empty if not found.
   */
  public Optional<ActionDto> completeAsUser(String traineeId, String actionId) {
    if (!ObjectId.isValid(actionId)) {
      log.info("Skipping action completion due to invalid id.");
      return Optional.empty();
    }

    Optional<Action> optionalAction = repository.findByIdAndTraineeId(new ObjectId(actionId),
        traineeId);

    if (optionalAction.isEmpty()) {
      log.info("Skipping action completion as the action was not found.");
      return Optional.empty();
    }

    Action action = optionalAction.get();

    if (!ActionType.getUserCompletableActionTypes().contains(action.type())) {
      log.info("Skipping action completion as the action type {} is not user-completable.",
          action.type());
      return Optional.empty();
    }

    return complete(action, null);
  }

  /**
   * Determine whether a placement update means that its existing actions of given type should be
   * replaced.
   *
   * @param existingActions The list of existing action for the placement.
   * @param action          The updated placement action.
   * @param placementId     The placement Id.
   * @return True if the actions should be replaced, otherwise false.
   */
  private boolean replaceUpdatedPlacementAction(List<Action> existingActions, Action action,
      String placementId) {
    List<Action> actionsOfType = existingActions.stream()
        .filter(a -> a.type().equals(action.type()))
        .toList();
    Optional<Action> actionWithDifferentDueDate = actionsOfType.stream()
        .filter(a -> !a.dueBy().isEqual(action.dueBy()))
        .findAny();
    if (actionWithDifferentDueDate.isPresent()) {
      //the saved action has a different placement start date, so replace it
      log.info("Placement {} already has {} {} action(s), these are replaced and set to "
              + "not completed as placement start date has changed from {} to {}", placementId,
          actionsOfType.size(), action.type(), actionWithDifferentDueDate.get().dueBy(),
          action.dueBy());
      return true;
    } else {
      log.info("Placement {} already has {} {} action(s), these are left as-is", placementId,
          actionsOfType.size(), action.type());
    }
    return false;
  }
}
