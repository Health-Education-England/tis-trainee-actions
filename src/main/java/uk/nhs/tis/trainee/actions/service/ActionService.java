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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
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

  private final ActionRepository repository;
  private final ActionMapper mapper;

  public ActionService(ActionRepository repository, ActionMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  /**
   * Updates the actions associated with the given Operation and Placement data.
   *
   * @param operation The operation that triggered the update.
   * @param dto       The Placement data associated with the operation.
   * @return A list of updated actions, empty if no actions required.
   */
  public List<ActionDto> updateActions(Operation operation, PlacementDto dto) {
    List<Action> actions = new ArrayList<>();

    if (Objects.equals(operation, Operation.CREATE)) {
      Action action = mapper.toAction(dto, REVIEW_DATA);
      actions.add(action);
    }

    if (actions.isEmpty()) {
      log.info("No new actions required for Placement {}", dto.id());
      return List.of();
    }

    log.info("Adding {} new action(s) for Placement {}.", actions.size(), dto.id());
    return mapper.toDtos(repository.insert(actions));
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

    if (Objects.equals(operation, Operation.CREATE)) {
      Action action = mapper.toAction(dto, REVIEW_DATA);
      actions.add(action);
    }

    if (actions.isEmpty()) {
      log.info("No new actions required for Programme Membership {}", dto.id());
      return List.of();
    }

    log.info("Adding {} new action(s) for Programme Membership {}.", actions.size(), dto.id());
    return mapper.toDtos(repository.insert(actions));
  }

  /**
   * Find all incomplete actions associated with a given trainee ID.
   *
   * @param traineeId The ID of the trainee to get actions for.
   * @return The found actions, empty if no actions found.
   */
  public List<ActionDto> findIncompleteTraineeActions(String traineeId) {
    List<Action> actions = repository.findAllByTraineeIdAndCompletedIsNullOrderByDueAsc(traineeId);
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
    log.info("Action {} marked as completed at {}.", actionId, completedAction.completed());
    return Optional.of(mapper.toDto(completedAction));
  }
}
