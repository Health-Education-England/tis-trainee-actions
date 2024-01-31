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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.actions.model.ActionType.REVIEW_DATA;
import static uk.nhs.tis.trainee.actions.model.TisReferenceType.PROGRAMME_MEMBERSHIP;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uk.nhs.tis.trainee.actions.dto.ProgrammeMembershipDto;
import uk.nhs.tis.trainee.actions.event.Operation;
import uk.nhs.tis.trainee.actions.mapper.ActionMapperImpl;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.model.Action.TisReferenceInfo;
import uk.nhs.tis.trainee.actions.repository.ActionRepository;

class ActionServiceTest {

  private static final String TIS_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

  private ActionService service;
  private ActionRepository repository;

  @BeforeEach
  void setUp() {
    repository = mock(ActionRepository.class);
    service = new ActionService(repository, new ActionMapperImpl());
  }

  @ParameterizedTest
  @EnumSource(value = Operation.class, names = {"CREATE"}, mode = EXCLUDE)
  void shouldNotInsertActionWhenProgrammeMembershipOperationNotSupported(Operation operation) {
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(TIS_ID, TRAINEE_ID, TOMORROW);

    service.updateActions(operation, dto);

    verifyNoInteractions(repository);
  }

  @Test
  void shouldInsertDataReviewActionOnProgrammeMembershipCreate() {
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(TIS_ID, TRAINEE_ID, TOMORROW);

    when(repository.insert(anyIterable())).thenAnswer(inv -> inv.getArgument(0));

    List<Action> actions = service.updateActions(Operation.CREATE, dto);

    assertThat("Unexpected action count.", actions.size(), is(1));

    Action action = actions.get(0);
    assertThat("Unexpected action id.", action.id(), nullValue());
    assertThat("Unexpected action type.", action.type(), is(REVIEW_DATA));
    assertThat("Unexpected trainee id.", action.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected due date.", action.due(), is(TOMORROW));
    assertThat("Unexpected completed date.", action.completed(), nullValue());

    TisReferenceInfo tisReference = action.tisReferenceInfo();
    assertThat("Unexpected TIS id.", tisReference.id(), is(TIS_ID));
    assertThat("Unexpected TIS type.", tisReference.type(), is(PROGRAMME_MEMBERSHIP));
  }
}
