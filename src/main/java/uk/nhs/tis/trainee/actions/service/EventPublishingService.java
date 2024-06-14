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

import java.net.URI;
import java.time.Instant;

import io.awspring.cloud.sns.core.SnsNotification;
import io.awspring.cloud.sns.core.SnsTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.actions.dto.ActionBroadcastDto;
import uk.nhs.tis.trainee.actions.dto.enumeration.ActionStatus;
import uk.nhs.tis.trainee.actions.model.Action;



/**
 * A service handling publishing of events to an external message system.
 */
@Slf4j
@Service
public class EventPublishingService {

  private final SnsTemplate snsTemplate;
  private final URI topicArn;

  public EventPublishingService(SnsTemplate snsTemplate,
                                @Value("${application.sns.arn}") URI arn) {
    this.snsTemplate = snsTemplate;
    this.topicArn = arn;
  }

  /**
   * Publish a action record with ActionStatus ACTIVE for a updated action.
   *
   * @param action The updated Action to publish.
   */
  public void publishActionUpdateEvent(Action action) {
    ActionBroadcastDto broadcastAction = new ActionBroadcastDto(
        action.id().toString(),
        action.type().toString(),
        action.traineeId(),
        action.tisReferenceInfo(),
        action.availableFrom(),
        action.dueBy(),
        action.completed(),
        ActionStatus.ACTIVE,
        Instant.now());
    publishActionBroadcastEvent(broadcastAction);
  }

  /**
   * Publish a blank record with ActionStatus DELETED for a deleted action.
   *
   * @param action The deleted Action to publish.
   */
  public void publishActionDeleteEvent(Action action) {
    ActionBroadcastDto broadcastAction = new ActionBroadcastDto(
        action.id().toString(),
        null,
        null,
        null,
        null,
        null,
        null,
        ActionStatus.DELETED,
        Instant.now());
    publishActionBroadcastEvent(broadcastAction);
  }

  /**
   * Publish an action event.
   *
   * @param action The broadcast DTO of the action to publish.
   */
  public void publishActionBroadcastEvent(ActionBroadcastDto action) {
    String actionId = action.id();
    log.info("Publishing event for action {}", actionId);

    SnsNotification<ActionBroadcastDto> message = SnsNotification.builder(action)
        .groupId(actionId)
        .build();
    snsTemplate.sendNotification(topicArn.toString(), message);
    log.info("Published event for action {} to topic {}", actionId, topicArn);
  }
}
