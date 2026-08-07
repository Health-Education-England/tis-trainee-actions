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

package uk.nhs.tis.trainee.actions.api;

import java.time.LocalDate;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.tis.trainee.actions.job.PublishActionRefresh;

/**
 * A controller with endpoints for manually triggering action publishing to the NDW.
 */
@Slf4j
@RestController
@RequestMapping("/api/job")
public class JobResource {

  private final PublishActionRefresh publishActionRefreshJob;

  /**
   * Create a job controller.
   *
   * @param publishActionRefreshJob A job for publishing Action refreshes.
   */
  public JobResource(PublishActionRefresh publishActionRefreshJob) {
    this.publishActionRefreshJob = publishActionRefreshJob;
  }

  /**
   * Publish all Action records as a refresh.
   *
   * @param since An optional cutoff start date; only actions created on or after this date will
   *              be published. If absent, all actions are published.
   * @return The number of exported Action records.
   */
  @PostMapping("/action/publish-refresh")
  public ResponseEntity<Integer> publishActionRefresh(
      @RequestParam Optional<LocalDate> since) {
    since.ifPresentOrElse(
        date -> log.info("Received request to publish Action refresh since {}.", date),
        () -> log.info("Received request to publish Action refresh."));
    int publishCount = publishActionRefreshJob.execute(since);
    return ResponseEntity.ok(publishCount);
  }
}
