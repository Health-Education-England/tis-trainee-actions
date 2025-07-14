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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.tis.trainee.actions.dto.FormUpdateEvent;
import uk.nhs.tis.trainee.actions.service.ActionService;

class FormListenerTest {

  private static final String TRAINEE_ID = UUID.randomUUID().toString();

  private FormListener listener;
  private ActionService service;

  @BeforeEach
  void setUp() {
    service = mock(ActionService.class);
    listener = new FormListener(service);
  }

  @Test
  void shouldThrowExceptionWhenEventNull() {
    assertThrows(IllegalArgumentException.class,
        () -> listener.handleFormUpdate(null));
  }

  @Test
  void shouldUpdateActionWhenEventValid() {
    FormUpdateEvent event = new FormUpdateEvent("form name", "SUBMITTED",
        TRAINEE_ID, "formr-a", Instant.now(), Map.of());

    listener.handleFormUpdate(event);

    verify(service).updateAction(event);
  }
}
