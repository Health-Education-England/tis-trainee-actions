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
 *
 */

package uk.nhs.tis.trainee.actions.event;

import io.awspring.cloud.sqs.annotation.SqsListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.actions.dto.FormUpdateEvent;

/**
 * A listener for Form Update Events.
 */
@Slf4j
@Component
public class FormEventListener {

  /**
   * Listen for Form Updated Events on the SQS queue.
   *
   * @param event the S3 Event
   * @throws IOException when the form contents could not be read, or were not correctly
   *                     structured.
   */
  @SqsListener(value = "${application.queues.form-updated}")
  void handleFormUpdate(FormUpdateEvent event) throws IOException {
    log.info("Handling form update event {}.", event);

    Map<String, Object> templateVariables = new HashMap<>();
    templateVariables.put("formName", event.formName());
    templateVariables.put("formType", event.formType());
    templateVariables.put("lifecycleState", event.lifecycleState());
    templateVariables.put("eventDate", event.eventDate());

    String traineeId = event.traineeId();

  }
}
