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

import com.mongodb.client.result.DeleteResult;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.service.EventPublishingService;

/**
 * Delete old outstanding actions.
 */
@Slf4j
@ChangeUnit(id = "deleteOldOutstandingActions", order = "2")
public class DeleteOldOutstandingActions {

  private final MongoTemplate mongoTemplate;
  private final EventPublishingService eventPublishingService;
  private final LocalDate actionsEpoch;

  /**
   * Initialise the migration.
   *
   * @param mongoTemplate          The mongo template to use.
   * @param eventPublishingService The event publishing service to use.
   * @param actionsEpoch           The actions epoch to use.
   */
  public DeleteOldOutstandingActions(MongoTemplate mongoTemplate,
                                     EventPublishingService eventPublishingService,
                                     @Value("${application.actions-epoch}")
                                     LocalDate actionsEpoch) {
    this.mongoTemplate = mongoTemplate;
    this.eventPublishingService = eventPublishingService;
    this.actionsEpoch = actionsEpoch;
  }

  /**
   * Delete old (pre actions-epoch) outstanding Actions.
   */
  @Execution
  public void migrate() {
    var obsoleteActionsCriteria = Criteria
        .where("dueBy").lt(actionsEpoch)
        .and("completed").exists(false);
    Query query = Query.query(obsoleteActionsCriteria);
    List<Action> actions = mongoTemplate.find(query, Action.class);
    actions.forEach(eventPublishingService::publishActionDeleteEvent);
    DeleteResult deleted = mongoTemplate.remove(query, Action.class);
    log.info("{} old outstanding actions deleted.", deleted.getDeletedCount());
  }

  /**
   * Do not attempt rollback, the collection should be left as-is.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'DeleteOldOutstandingActions' migration.");
  }
}
