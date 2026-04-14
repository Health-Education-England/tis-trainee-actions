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

package uk.nhs.tis.trainee.actions.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDate;
import java.util.List;
import uk.nhs.tis.trainee.actions.dto.helpers.ConditionsOfJoiningDeserializer;
import uk.nhs.tis.trainee.actions.dto.helpers.CurriculaDeserializer;

/**
 * A representation of a programme membership.
 *
 * <p>Note that operation=DELETE records are 'unenriched' by tis-trainee-sync, so they arrive with
 * uuid instead of the tisId that records from e.g. LOAD operations arrive with.</p>
 *
 * @param id                  The programme membership ID.
 * @param traineeId           The trainee ID associated with the membership.
 * @param startDate           The programme start date.
 * @param conditionsOfJoining The serialized conditions of joining for the programme membership.
 * @param curricula           The list of curricula associated with the programme membership.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProgrammeMembershipDto(
    @JsonAlias({"tisId", "uuid"})
    String id,

    @JsonAlias("personId")
    String traineeId, LocalDate startDate,

    @JsonDeserialize(using = ConditionsOfJoiningDeserializer.class)
    ConditionsOfJoining conditionsOfJoining,

    @JsonDeserialize(using = CurriculaDeserializer.class)
    List<CurriculumDto> curricula

) {

  /**
   * A representation of a curriculum associated with a programme membership.
   *
   * @param curriculumSpecialty The curriculum specialty.
   * @param curriculumSubType   The curriculum subtype.
   */
  public record CurriculumDto(String curriculumSpecialty, String curriculumSubType) {

  }

  /**
   * Identify if a programme membership is a foundation programme, by checking if any of the
   * curricula have a specialty or subtype indicating it's a foundation programme.
   *
   * @return true if the programme membership is a foundation programme, otherwise false.
   */
  public boolean isFoundationProgramme() {
    if (curricula() == null) {
      return false;
    }
    return curricula().stream()
        .anyMatch(curriculum -> {
          String specialty = curriculum.curriculumSpecialty();
          String subtype = curriculum.curriculumSubType();
          return (subtype != null && (subtype.equalsIgnoreCase("AFT"))
              || (specialty != null && specialty.equalsIgnoreCase("FOUNDATION")));
        });
  }
}
