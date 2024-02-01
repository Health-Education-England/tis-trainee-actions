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

package uk.nhs.tis.trainee.actions.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.nhs.tis.trainee.actions.dto.ActionDto;
import uk.nhs.tis.trainee.actions.service.ActionService;

class ActionResourceTest {

  private ActionResource controller;
  private ActionService service;

  @BeforeEach
  void setUp() {
    service = mock(ActionService.class);
    controller = new ActionResource(service);
  }

  @Test
  void shouldReturnBadRequestGettingActionsWhenTokenInvalid() {
    String encodedPayload = Base64.getEncoder()
        .encodeToString("[]".getBytes(StandardCharsets.UTF_8));
    String token = String.format("aa.%s.cc", encodedPayload);

    ResponseEntity<List<ActionDto>> response = controller.getTraineeActions(token);

    assertThat("Unexpected status code.", response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
    assertThat("Unexpected response body presence.", response.hasBody(), is(false));

    verifyNoInteractions(service);
  }

  @Test
  void shouldReturnBadRequestGettingActionsWhenNoTraineeIdAvailable() {
    String encodedPayload = Base64.getEncoder()
        .encodeToString("{}".getBytes(StandardCharsets.UTF_8));
    String token = String.format("aa.%s.cc", encodedPayload);

    ResponseEntity<List<ActionDto>> response = controller.getTraineeActions(token);

    assertThat("Unexpected status code.", response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
    assertThat("Unexpected response body presence.", response.hasBody(), is(false));

    verifyNoInteractions(service);
  }

  @Test
  void shouldReturnTraineeActionsWhenTraineeIdAvailable() {
    String payload = String.format("{\"%s\":\"%s\"}", "custom:tisId", "40");
    String encodedPayload = Base64.getEncoder()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    String token = String.format("aa.%s.cc", encodedPayload);

    ActionDto dto1 = new ActionDto("1", null, null, null, null, null);
    ActionDto dto2 = new ActionDto("2", null, null, null, null, null);
    when(service.findIncompleteTraineeActions("40")).thenReturn(List.of(dto1, dto2));

    ResponseEntity<List<ActionDto>> response = controller.getTraineeActions(token);

    assertThat("Unexpected status code.", response.getStatusCode(), is(HttpStatus.OK));
    assertThat("Unexpected response body presence.", response.hasBody(), is(true));

    List<ActionDto> actions = response.getBody();
    assertThat("Unexpected action count.", actions.size(), is(2));
    assertThat("Unexpected action.", actions.get(0), sameInstance(dto1));
    assertThat("Unexpected action.", actions.get(1), sameInstance(dto2));
  }
}
