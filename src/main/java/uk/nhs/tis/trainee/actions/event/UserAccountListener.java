/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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

import static uk.nhs.tis.trainee.actions.event.Operation.LOAD;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.actions.dto.AccountConfirmedEvent;
import uk.nhs.tis.trainee.actions.service.ActionService;

/**
 * A listener for user account events.
 */
@Slf4j
@Component
public class UserAccountListener {

  private final ActionService actionService;

  /**
   * Construct a listener for user account events.
   *
   * @param actionService The action service.
   */
  public UserAccountListener(ActionService actionService) {
    this.actionService = actionService;
  }

  /**
   * Handle account confirmation events.
   *
   * @param event The account confirmation event.
   */
  @SqsListener("${application.queues.account-confirmed}")
  public void handleAccountConfirmation(AccountConfirmedEvent event) {
    log.info("Handling account confirmation event for user {}.", event.traineeId());
    actionService.updateActions(LOAD, event);
  }

}
