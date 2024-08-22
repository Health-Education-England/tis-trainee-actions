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

import static uk.nhs.tis.trainee.actions.model.ActionType.REVIEW_DATA;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.actions.dto.ActionDto;
import uk.nhs.tis.trainee.actions.dto.PlacementDto;
import uk.nhs.tis.trainee.actions.dto.ProgrammeMembershipDto;
import uk.nhs.tis.trainee.actions.event.Operation;
import uk.nhs.tis.trainee.actions.mapper.ActionMapper;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.repository.ActionRepository;

/**
 * A service provided Action functionality.
 */
@Slf4j
@Service
public class ActionService {

  public static final List<String> PLACEMENT_TYPES_TO_ACT_ON
      = List.of("In post", "In post - Acting up", "In Post - Extension");

  private final ActionRepository repository;
  private final ActionMapper mapper;
  private final EventPublishingService eventPublishingService;
  private final LocalDate actionsEpoch;

  /**
   * The constructor of action service.
   */
  public ActionService(ActionRepository repository, ActionMapper mapper,
                       EventPublishingService eventPublishingService,
                       @Value("${application.actions-epoch}") LocalDate actionsEpoch) {
    this.repository = repository;
    this.mapper = mapper;
    this.eventPublishingService = eventPublishingService;
    this.actionsEpoch = actionsEpoch;
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

    Action action = mapper.toAction(dto, REVIEW_DATA);

    if (Objects.equals(operation, Operation.LOAD)) {
      if (PLACEMENT_TYPES_TO_ACT_ON.stream().anyMatch(dto.placementType()::equalsIgnoreCase)) {
        //find if action already exists (there should only be at most one)
        List<Action> existingActions = repository.findByTraineeIdAndTisReferenceInfo(
            action.traineeId(), action.tisReferenceInfo().id(),
            action.tisReferenceInfo().type().toString());
        if (existingActions.isEmpty()) {
          addActionIfDueAfterEpoch(action, actions);
        } else {
          if (replaceUpdatedPlacementAction(existingActions, action, dto.id())) {
            List<Action> deletedActions = repository.deleteByTraineeIdAndTisReferenceInfo(
                action.traineeId(), action.tisReferenceInfo().id(),
                action.tisReferenceInfo().type().toString());
            deletedActions.forEach(eventPublishingService::publishActionDeleteEvent);
            addActionIfDueAfterEpoch(action, actions);
          }
        }
      } else {
        log.info("Placement {} of type {} is ignored", dto.id(), dto.placementType());
        deleteAction = true;
      }
    } else if (Objects.equals(operation, Operation.DELETE)) {
      log.info("Placement {} is deleted", dto.id());
      deleteAction = true;
    }

    if (deleteAction) {
      deleteIncompleteActions(action);
    }

    if (actions.isEmpty()) {
      log.info("No new actions required for Placement {}", dto.id());
      return List.of();
    }

    log.info("Adding {} new action(s) for Placement {}.", actions.size(), dto.id());
    List<Action> actionInserted = repository.insert(actions);
    actionInserted.stream().forEach(eventPublishingService::publishActionUpdateEvent);
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

    Action action = mapper.toAction(dto, REVIEW_DATA);

    if (Objects.equals(operation, Operation.LOAD)
        && !(dto.startDate().isBefore(actionsEpoch))) {
      List<Action> existingActions = repository.findByTraineeIdAndTisReferenceInfo(
          action.traineeId(), action.tisReferenceInfo().id(),
          action.tisReferenceInfo().type().toString());
      if (existingActions.isEmpty()) {
        // only create action if it does not already exist
        // and if the programme membership starts post-epoch
        addActionIfDueAfterEpoch(action, actions);
      }
    } else if (Objects.equals(operation, Operation.DELETE)) {
      log.info("Programme membership {} is deleted", dto.id());
      deleteIncompleteActions(action);
    }

    if (actions.isEmpty()) {
      log.info("No new actions required for Programme Membership {}", dto.id());
      return List.of();
    }

    log.info("Adding {} new action(s) for Programme Membership {}.", actions.size(), dto.id());
    List<Action> actionInserted = repository.insert(actions);
    actionInserted.stream().forEach(eventPublishingService::publishActionUpdateEvent);
    return mapper.toDtos(actionInserted);
  }

  /**
   * Add action to list of actions if it is due after the actions epoch.
   *
   * @param action  The action to process.
   * @param actions The current list of actions.
   */
  private void addActionIfDueAfterEpoch(Action action, List<Action> actions) {
    if (action.dueBy().isAfter(actionsEpoch)) {
      actions.add(action);
    } else {
      log.debug("Not adding action for {} {} starting {} before epoch {}",
          action.tisReferenceInfo().type(), action.tisReferenceInfo().id(), action.dueBy(),
          actionsEpoch);
    }
  }

  /**
   * Delete any not-completed actions that match the given action item.
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
    deletedActions.stream().forEach(eventPublishingService::publishActionDeleteEvent);
  }

  /**
   * Find all incomplete actions associated with a given trainee ID.
   *
   * @param traineeId The ID of the trainee to get actions for.
   * @return The found actions, empty if no actions found.
   */
  public List<ActionDto> findIncompleteTraineeActions(String traineeId) {
    List<Action> actions = repository.findAllByTraineeIdAndCompletedIsNullOrderByDueByAsc(
        traineeId);
    return mapper.toDtos(actions);
  }

  /**
   * Complete a trainee's action.
   *
   * @param traineeId The ID of the trainee who owns the action to be completed.
   * @param actionId  The ID of the action to complete.
   * @return The completed action, or empty if not found.
   */
  public Optional<ActionDto> complete(String traineeId, String actionId) {
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

    if (action.completed() != null) {
      log.info("Skipping action completion as the action was already complete.");
      return Optional.empty();
    }

    Action completedAction = mapper.complete(action);
    completedAction = repository.save(completedAction);
    eventPublishingService.publishActionUpdateEvent(completedAction);
    log.info("Action {} marked as completed at {}.", actionId, completedAction.completed());
    return Optional.of(mapper.toDto(completedAction));
  }

  /**
   * Determine whether a placement update means that its existing actions should be replaced.
   *
   * @param existingActions The list of existing action for the placement.
   * @param action          The updated placement action.
   * @param placementId     The placement Id.
   * @return True if the actions should be replaced, otherwise false.
   */
  private boolean replaceUpdatedPlacementAction(List<Action> existingActions, Action action,
                                                String placementId) {
    Optional<Action> actionWithDifferentDueDate = existingActions.stream()
        .filter(a -> !a.dueBy().isEqual(action.dueBy()))
        .findAny();
    if (actionWithDifferentDueDate.isPresent()) {
      //the saved action has a different placement start date, so replace it
      log.info("Placement {} already has {} action(s), these are replaced and set to "
              + "not completed as placement start date has changed from {} to {}", placementId,
          existingActions.size(), actionWithDifferentDueDate.get().dueBy(), action.dueBy());
      return true;
    } else {
      log.info("Placement {} already has {} action(s), these are left as-is", placementId,
          existingActions.size());
    }
    return false;
  }
}
