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

package uk.nhs.tis.trainee.actions.event;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.actions.dto.PlacementDto;
import uk.nhs.tis.trainee.actions.service.ActionService;

/**
 * A listener for placement events.
 */
@Slf4j
@Component
public class PlacementListener {

  private final ActionService actionService;

  public PlacementListener(ActionService actionService) {
    this.actionService = actionService;
  }

  /**
   * Handle a placement sync event.
   *
   * @param event The event to handle.
   */
  @SqsListener("${application.queues.placement-synced}")
  public void handlePlacementSync(PlacementEvent event) {
    log.debug("Placement sync event received: {}", event);
    Operation operation = event.getOperation();
    PlacementDto dto = event.getPlacement();


    if (operation != null && dto != null) {
      actionService.updateActions(operation, event.getPlacement());
    } else {
      throw new IllegalArgumentException("Skipping event handling due to incomplete event data.");
    }
  }
}
