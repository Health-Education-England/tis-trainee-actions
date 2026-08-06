/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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
 *
 */

package uk.nhs.tis.trainee.actions.job;

import static java.time.ZoneOffset.UTC;

import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.repository.ActionRepository;
import uk.nhs.tis.trainee.actions.service.EventPublishingService;

/**
 * A job to publish all Action records (or those created since a particular date), useful for
 * refreshing downstream dependants after data discrepancies.
 */
@Slf4j
@Component
public class PublishActionRefresh {

  private final ActionRepository repository;
  private final EventPublishingService eventPublishingService;
  private final String topic;

  /**
   * Create an instance of a job for publishing Action refreshes.
   *
   * @param repository            The repository used to retrieve Action records.
   * @param eventPublishingService The service used to publish action events.
   * @param topic                  The refresh topic to publish to.
   */
  public PublishActionRefresh(ActionRepository repository,
      EventPublishingService eventPublishingService,
      @Value("${application.sns.refresh-arn}") String topic) {
    this.repository = repository;
    this.eventPublishingService = eventPublishingService;
    this.topic = topic;
  }

  /**
   * Execute the job to publish all Action records as a refresh.
   *
   * @param cutoffDate An optional cutoff start date; only actions created on or after this date
   *                   will be refreshed. If empty, all actions are refreshed.
   * @return The number of published actions.
   */
  public Integer execute(Optional<LocalDate> cutoffDate) {
    log.info("Starting Action downstream refresh.");

    AtomicInteger total = new AtomicInteger();
    AtomicInteger published = new AtomicInteger();

    try (Stream<Action> actions = streamActions(cutoffDate)) {
      actions.forEach(action -> {
        total.getAndIncrement();

        String actionId = action.id() != null ? action.id().toString() : "unknown";
        log.debug("Publishing refresh notification for Action {}.", actionId);

        try {
          eventPublishingService.publishActionUpdateEvent(action, topic);
          published.getAndIncrement();
        } catch (Exception e) {
          log.error("Unable to publish refresh notification for Action {}.", actionId, e);
        }
      });
    }

    log.info("Finished Action downstream refresh, published count: {}/{}.", published, total);
    return published.get();
  }

  /**
   * Get the actions to be refreshed, optionally filtered by a cutoff date.
   *
   * @param cutoffDate An optional cutoff start date; only actions created on or after this date
   *                   will be included. If empty, all actions are included.
   * @return The filtered stream of actions to be refreshed.
   */
  private Stream<Action> streamActions(Optional<LocalDate> cutoffDate) {
    if (cutoffDate.isPresent()) {
      // Create an ObjectId from the cutoff date to filter by creation timestamp.
      Date cutoff = Date.from(cutoffDate.get().atStartOfDay().toInstant(UTC));
      ObjectId cutoffId = new ObjectId(cutoff);
      return repository.streamByIdGreaterThanEqual(cutoffId);
    }
    return repository.streamAll();
  }
}

