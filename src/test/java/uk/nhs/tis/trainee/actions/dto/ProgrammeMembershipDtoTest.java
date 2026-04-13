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
 */

package uk.nhs.tis.trainee.actions.dto;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.nhs.tis.trainee.actions.dto.ProgrammeMembershipDto.CurriculumDto;

class ProgrammeMembershipDtoTest {

  private static final String PM_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final LocalDate START_DATE = LocalDate.now().minusYears(1);
  private static final ConditionsOfJoining CONDITIONS_OF_JOINING = new ConditionsOfJoining(
      Instant.now(), "10", Instant.now());

  @ParameterizedTest
  @NullAndEmptySource
  void shouldNotBeFoundationWhenNoCurricula(List<CurriculumDto> curricula) {
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(PM_ID, TRAINEE_ID, START_DATE,
        CONDITIONS_OF_JOINING, curricula);

    assertThat("Unexpected foundation status.", dto.isFoundationProgramme(), is(false));
  }

  @Test
  void shouldBeFoundationWhenSpecialtyNullAndSubTypeNull() {
    CurriculumDto curriculum = new CurriculumDto(null, null);
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(PM_ID, TRAINEE_ID, START_DATE,
        CONDITIONS_OF_JOINING, List.of(curriculum));

    assertThat("Unexpected foundation status.", dto.isFoundationProgramme(), is(false));
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = "NotAFT")
  void shouldNotBeFoundationWhenSubTypeNotAft(String subType) {
    CurriculumDto curriculum = new CurriculumDto(null, subType);
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(PM_ID, TRAINEE_ID, START_DATE,
        CONDITIONS_OF_JOINING, List.of(curriculum));

    assertThat("Unexpected foundation status.", dto.isFoundationProgramme(), is(false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"AFT", "aft", "Aft"})
  void shouldBeFoundationWhenSubTypeAft(String subType) {
    CurriculumDto curriculum = new CurriculumDto(null, subType);
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(PM_ID, TRAINEE_ID, START_DATE,
        CONDITIONS_OF_JOINING, List.of(curriculum));

    assertThat("Unexpected foundation status.", dto.isFoundationProgramme(), is(true));
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = "Not Foundation")
  void shouldNotBeFoundationWhenSpecialtyNotFoundation(String specialty) {
    CurriculumDto curriculum = new CurriculumDto(specialty, null);
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(PM_ID, TRAINEE_ID, START_DATE,
        CONDITIONS_OF_JOINING, List.of(curriculum));

    assertThat("Unexpected foundation status.", dto.isFoundationProgramme(), is(false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"FOUNDATION", "foundation", "Foundation"})
  void shouldBeFoundationWhenSpecialtyFoundation(String specialty) {
    CurriculumDto curriculum = new CurriculumDto(specialty, null);
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(PM_ID, TRAINEE_ID, START_DATE,
        CONDITIONS_OF_JOINING, List.of(curriculum));

    assertThat("Unexpected foundation status.", dto.isFoundationProgramme(), is(true));
  }

  @Test
  void shouldBeFoundationWhenAnyCurriculumIsFoundation() {
    List<CurriculumDto> curricula = List.of(
        new CurriculumDto(null, null),
        new CurriculumDto("No Foundation", "Not Aft"),
        new CurriculumDto(null, "AFT"),
        new CurriculumDto("FOUNDATION", null)
    );
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(PM_ID, TRAINEE_ID, START_DATE,
        CONDITIONS_OF_JOINING, curricula);

    assertThat("Unexpected foundation status.", dto.isFoundationProgramme(), is(true));
  }
}
