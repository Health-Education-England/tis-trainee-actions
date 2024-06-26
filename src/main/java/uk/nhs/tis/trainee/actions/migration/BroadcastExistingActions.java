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

package uk.nhs.tis.trainee.actions.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.service.EventPublishingService;

/**
 * Broadcast existing actions to SNS topic.
 */
@Slf4j
@ChangeUnit(id = "broadcastExistingActions", order = "1")
public class BroadcastExistingActions {

  private final MongoTemplate mongoTemplate;
  private final EventPublishingService eventPublishingService;

  public BroadcastExistingActions(MongoTemplate mongoTemplate,
                                  EventPublishingService eventPublishingService) {
    this.mongoTemplate = mongoTemplate;
    this.eventPublishingService = eventPublishingService;
  }

  /**
   * Broadcast existing Actions.
   */
  @Execution
  public void migrate() {
    List<Action> allActions = mongoTemplate.findAll(Action.class, "Action");
    allActions.stream().forEach(eventPublishingService::publishActionUpdateEvent);
  }

  /**
   * Do not attempt rollback, the collection should be left as-is.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'BroadcastExistingActions' migration.");
  }
}
