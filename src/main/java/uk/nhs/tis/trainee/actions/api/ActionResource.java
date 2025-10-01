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

package uk.nhs.tis.trainee.actions.api;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.tis.trainee.actions.api.util.AuthTokenUtil;
import uk.nhs.tis.trainee.actions.dto.ActionDto;
import uk.nhs.tis.trainee.actions.service.ActionService;

/**
 * A REST controller for Action specific functionality.
 */
@Slf4j
@RestController
@RequestMapping("/api/action")
public class ActionResource {

  private final ActionService service;

  public ActionResource(ActionService service) {
    this.service = service;
  }

  /**
   * Get available incomplete actions associated with the authenticated trainee.
   *
   * @param token The authentication token containing the trainee ID.
   * @return A list of available incomplete actions associated with the trainee, may be empty.
   */
  @GetMapping
  public ResponseEntity<List<ActionDto>> getTraineeActions(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
    log.info("Received request to get actions of authenticated user.");

    String traineeId;
    try {
      traineeId = AuthTokenUtil.getTraineeTisId(token);
      log.info("Trainee {} identified from authentication token.", traineeId);

      if (traineeId == null) {
        return ResponseEntity.badRequest().build();
      }
    } catch (IOException e) {
      log.warn("Unable to read tisId from token.", e);
      return ResponseEntity.badRequest().build();
    }

    List<ActionDto> actions = service.findIncompleteTraineeActions(traineeId);
    log.info("{} incomplete actions found for trainee {}.", actions.size(), traineeId);

    return ResponseEntity.ok(actions);
  }

  /**
   * Mark a trainee's action as completed.
   *
   * @param token    The authentication token containing the trainee ID.
   * @param actionId The ID of the action to mark as completed.
   * @return The completed action, or empty if the action was not found.
   */
  @PostMapping("/{actionId}/complete")
  ResponseEntity<ActionDto> completeAction(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
      @PathVariable String actionId) {
    log.info("Received request to complete action {}.", actionId);

    String traineeId;
    try {
      traineeId = AuthTokenUtil.getTraineeTisId(token);
      log.info("Trainee {} identified from authentication token.", traineeId);

      if (traineeId == null) {
        return ResponseEntity.badRequest().build();
      }
    } catch (IOException e) {
      log.warn("Unable to read tisId from token.", e);
      return ResponseEntity.badRequest().build();
    }

    Optional<ActionDto> action = service.completeAsUser(traineeId, actionId);
    return ResponseEntity.of(action);
  }

  /**
   * Get complete and incomplete actions associated with a trainee and programme membership. This is
   * an internal API without an authorization token.
   *
   * @param traineeId   The trainee TIS ID.
   * @param programmeId The programme membership ID.
   * @return A list of all actions associated with the trainee and programme membership, which may
   *         be empty if the programme membership or trainee were not found, but otherwise should
   *         contain an ActionDto for each programmeActionTypes and personActionTypes ActionType.
   */
  @GetMapping("/{traineeId}/{programmeId}")
  public ResponseEntity<List<ActionDto>> getTraineeProgrammeActions(
      @PathVariable String traineeId,
      @PathVariable String programmeId) {
    log.info("Received request to get actions for trainee {} programme membership {}.",
        traineeId, programmeId);

    List<ActionDto> actions = service.findTraineeProgrammeMembershipActions(traineeId, programmeId);
    log.info("{} actions found for trainee {} programme membership {}.", actions.size(),
        traineeId, programmeId);

    return ResponseEntity.ok(actions);
  }

  /**
   * Move all actions from one trainee to another.
   *
   * @param fromTraineeId The TIS ID of the trainee to move actions from.
   * @param toTraineeId   The TIS ID of the trainee to move actions to.
   * @return True if the actions were moved.
   */
  @PatchMapping("/move/{fromTraineeId}/to/{toTraineeId}")
  public ResponseEntity<Boolean> moveNotifications(@PathVariable String fromTraineeId,
      @PathVariable String toTraineeId) {
    log.info("Request to move actions from trainee {} to trainee {}",
        fromTraineeId, toTraineeId);

    service.moveActions(fromTraineeId, toTraineeId);
    return ResponseEntity.ok(true);
  }
}
