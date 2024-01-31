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

package uk.nhs.tis.trainee.actions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;
import uk.nhs.tis.trainee.actions.dto.ProgrammeMembershipDto;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.model.Action.TisReferenceInfo;
import uk.nhs.tis.trainee.actions.model.ActionType;

/**
 * A mapper to convert to and between Action data types.
 */
@Mapper(componentModel = ComponentModel.SPRING)
public interface ActionMapper {

  /**
   * Create an action using Programme Membership data.
   *
   * @param dto  The Programme Membership to retrieve data from.
   * @param type The type of action to be created.
   * @return The created action.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "type", source = "type")
  @Mapping(target = "traineeId", source = "dto.traineeId")
  @Mapping(target = "tisReferenceInfo", source = "dto")
  @Mapping(target = "due", source = "dto.startDate")
  @Mapping(target = "completed", ignore = true)
  Action toAction(ProgrammeMembershipDto dto, ActionType type);

  /**
   * Map a Programme Membership to a TIS reference info object.
   *
   * @param dto The Programme Membership to map.
   * @return A reference to the TIS core object.
   */
  @Mapping(target = "id", source = "dto.id")
  @Mapping(target = "type", constant = "PROGRAMME_MEMBERSHIP")
  TisReferenceInfo map(ProgrammeMembershipDto dto);
}