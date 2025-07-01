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

import io.awspring.cloud.sns.core.SnsNotification;
import io.awspring.cloud.sns.core.SnsTemplate;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.actions.dto.ActionBroadcastDto;
import uk.nhs.tis.trainee.actions.mapper.ActionMapper;
import uk.nhs.tis.trainee.actions.model.Action;

/**
 * A service handling publishing of events to an external message system.
 */
@Slf4j
@Service
public class EventPublishingService {

  private final SnsTemplate snsTemplate;
  private final ActionMapper actionMapper;
  private final URI topicArn;

  /**
   * The constructor of event publishing service.
   */
  public EventPublishingService(SnsTemplate snsTemplate, ActionMapper actionMapper,
                                @Value("${application.sns.arn}") URI arn) {
    this.snsTemplate = snsTemplate;
    this.actionMapper = actionMapper;
    this.topicArn = arn;
  }

  /**
   * Publish a action record with ActionStatus ACTIVE for a updated action.
   *
   * @param action The updated Action to publish.
   */
  public void publishActionUpdateEvent(Action action) {
    ActionBroadcastDto broadcastAction = actionMapper.toCurrentActionBroadcastDto(action);
    publishActionBroadcastEvent(broadcastAction);
  }

  /**
   * Publish a blank record with ActionStatus DELETED for a deleted action.
   *
   * @param action The deleted Action to publish.
   */
  public void publishActionDeleteEvent(Action action) {
    ActionBroadcastDto broadcastAction = actionMapper.toDeletedActionBroadcastDto(action);
    publishActionBroadcastEvent(broadcastAction);
  }

  /**
   * Publish an action event.
   *
   * @param action The broadcast DTO of the action to publish.
   */
  private void publishActionBroadcastEvent(ActionBroadcastDto action) {
    String actionId = action.id();
    log.info("Publishing {} event for {} action {}", action.status(), action.type(), actionId);

    SnsNotification<ActionBroadcastDto> message;
    if (topicArn.toString().endsWith(".fifo")) {
      message = SnsNotification.builder(action).groupId(actionId).build();
    } else {
      message = SnsNotification.builder(action).build();
    }
    snsTemplate.sendNotification(topicArn.toString(), message);
    log.info("Published {} event for {} action {} to topic {}", action.status(), action.type(),
        actionId, topicArn);
  }
}
