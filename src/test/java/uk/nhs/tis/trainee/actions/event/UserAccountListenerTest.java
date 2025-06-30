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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.nhs.tis.trainee.actions.event.Operation.LOAD;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.actions.dto.AccountConfirmedEvent;
import uk.nhs.tis.trainee.actions.service.ActionService;

/**
 * Tests for {@link UserAccountListener}.
 */
class UserAccountListenerTest {

  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final String EMAIL = "some@email.test";
  private static final UUID USER_ID = UUID.randomUUID();

  private UserAccountListener listener;
  private ActionService service;

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    service = mock(ActionService.class);
    listener = new UserAccountListener(service);
    mapper = JsonMapper.builder()
        .findAndAddModules()
        .build();
  }

  @Test
  void shouldUpdateActions() throws JsonProcessingException {
    String eventJson = """
        {
          "userId": "%s",
          "traineeId": "%s",
          "email": "%s"
        }""".formatted(USER_ID, TRAINEE_ID, EMAIL);
    AccountConfirmedEvent event = mapper.readValue(eventJson, AccountConfirmedEvent.class);

    listener.handleAccountConfirmation(event);

    ArgumentCaptor<AccountConfirmedEvent> dtoCaptor
        = ArgumentCaptor.forClass(AccountConfirmedEvent.class);
    verify(service).updateActions(eq(LOAD), dtoCaptor.capture());

    AccountConfirmedEvent eventSent = dtoCaptor.getValue();
    assertThat("Unexpected ID", eventSent.userId(), is(USER_ID));
    assertThat("Unexpected trainee ID", eventSent.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected start date", eventSent.email(), is(EMAIL));
  }
}
