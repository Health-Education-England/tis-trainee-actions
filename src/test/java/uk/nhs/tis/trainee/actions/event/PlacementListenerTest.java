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

package uk.nhs.tis.trainee.actions.event;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.actions.dto.PlacementDto;
import uk.nhs.tis.trainee.actions.service.ActionService;

class PlacementListenerTest {

  private static final String PLACEMENT_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final LocalDate START_DATE = LocalDate.now();

  private PlacementListener listener;
  private ActionService service;

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    service = mock(ActionService.class);
    listener = new PlacementListener(service);
    mapper = JsonMapper.builder()
        .findAndAddModules()
        .build();
  }

  @Test
  void shouldThrowExceptionWhenSyncEventOperationNull() throws JsonProcessingException {
    String eventJson = """
        {
          "record": {
            "data": {
              "tisId": "%s",
              "traineeId": "%s",
              "dateFrom": "%s",
              "placementType": "In post"
            }
          }
        }""".formatted(PLACEMENT_ID, TRAINEE_ID, START_DATE);
    PlacementEvent event = mapper.readValue(eventJson, PlacementEvent.class);
    assertThrows(IllegalArgumentException.class,
        () -> listener.handlePlacementSync(event));
  }

  @ParameterizedTest
  @EnumSource(Operation.class)
  void shouldThrowExceptionWhenSyncEventDataNull(Operation operation)
      throws JsonProcessingException {
    String eventJson = """
        {
          "record": {
            "operation": "%s"
          }
        }""".formatted(operation);
    PlacementEvent event = mapper.readValue(eventJson, PlacementEvent.class);
    assertThrows(IllegalArgumentException.class,
        () -> listener.handlePlacementSync(event));
  }

  @ParameterizedTest
  @EnumSource(Operation.class)
  void shouldUpdateActionsWhenSyncEventValid(Operation operation) throws JsonProcessingException {
    String eventJson = """
        {
          "record": {
            "data": {
              "tisId": "%s",
              "traineeId": "%s",
              "dateFrom": "%s",
              "placementType": "In post"
            },
            "operation": "%s"
          }
        }""".formatted(PLACEMENT_ID, TRAINEE_ID, START_DATE, operation);
    PlacementEvent event = mapper.readValue(eventJson, PlacementEvent.class);

    listener.handlePlacementSync(event);

    ArgumentCaptor<PlacementDto> dtoCaptor = ArgumentCaptor.forClass(PlacementDto.class);
    verify(service).updateActions(eq(operation), dtoCaptor.capture());

    PlacementDto dto = dtoCaptor.getValue();
    assertThat("Unexpected ID", dto.id(), is(PLACEMENT_ID));
    assertThat("Unexpected trainee ID", dto.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected start date", dto.startDate(), is(START_DATE));
  }

  @ParameterizedTest
  @EnumSource(Operation.class)
  void shouldSetDataTisIdFromRecordIfMissing(Operation operation) throws JsonProcessingException {
    String eventJson = """
        {
          "record": {
            "tisId": "%s",
            "data": {
              "traineeId": "%s",
              "dateFrom": "%s",
              "placementType": "In post"
            },
            "operation": "%s"
          }
        }""".formatted(PLACEMENT_ID, TRAINEE_ID, START_DATE, operation);
    PlacementEvent event = mapper.readValue(eventJson, PlacementEvent.class);

    listener.handlePlacementSync(event);

    ArgumentCaptor<PlacementDto> dtoCaptor = ArgumentCaptor.forClass(PlacementDto.class);
    verify(service).updateActions(eq(operation), dtoCaptor.capture());

    PlacementDto dto = dtoCaptor.getValue();
    assertThat("Unexpected ID", dto.id(), is(PLACEMENT_ID));
    assertThat("Unexpected trainee ID", dto.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected start date", dto.startDate(), is(START_DATE));
  }
}
