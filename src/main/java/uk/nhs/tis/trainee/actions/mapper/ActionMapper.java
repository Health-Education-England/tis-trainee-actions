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

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

import java.time.Instant;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uk.nhs.tis.trainee.actions.dto.AccountConfirmedEvent;
import uk.nhs.tis.trainee.actions.dto.ActionBroadcastDto;
import uk.nhs.tis.trainee.actions.dto.ActionDto;
import uk.nhs.tis.trainee.actions.dto.PlacementDto;
import uk.nhs.tis.trainee.actions.dto.ProgrammeMembershipDto;
import uk.nhs.tis.trainee.actions.model.Action;
import uk.nhs.tis.trainee.actions.model.Action.TisReferenceInfo;
import uk.nhs.tis.trainee.actions.model.ActionType;

/**
 * A mapper to convert to and between Action data types.
 */
@Mapper(componentModel = SPRING)
public interface ActionMapper {

  /**
   * Convert an Action entity to an Action DTO.
   *
   * @param entity The entity to convert.
   * @return The built DTO.
   */
  @Mapping(target = "id", expression = "java(entity.id() == null ? null : entity.id().toString())")
  ActionDto toDto(Action entity);

  /**
   * Convert a list of Actions to a List of Action DTOs.
   *
   * @param entities The entities to convert.
   * @return The built DTOs.
   */
  List<ActionDto> toDtos(List<Action> entities);

  /**
   * Complete the given action, with the timestamp set to the current time.
   *
   * @param action The action to complete.
   * @return The completed action.
   */
  @Mapping(target = "completed", expression = "java(java.time.Instant.now())")
  Action complete(Action action);

  /**
   * Complete the given action, with the given timestamp.
   *
   * @param action The action to complete.
   * @return The completed action.
   */
  @Mapping(target = "completed", source = "completedAt")
  Action complete(Action action, Instant completedAt);

  /**
   * Create an action using Programme Membership data.
   *
   * @param dto  The Programme Membership to retrieve data from.
   * @param type The type of action to be created.
   * @return The created action.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "type")
  @Mapping(target = "traineeId", source = "dto.traineeId")
  @Mapping(target = "tisReferenceInfo", source = "dto")
  @Mapping(target = "availableFrom", expression = "java(java.time.LocalDate.now())")
  @Mapping(target = "dueBy", source = "dto.startDate")
  @Mapping(target = "completed", ignore = true)
  Action toAction(ProgrammeMembershipDto dto, ActionType type);

  /**
   * Create an action using an account event and ActionType.
   *
   * @param accountEvent The account event.
   * @param type         The type of action to be created.
   * @return The created action.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "type")
  @Mapping(target = "traineeId", source = "accountEvent.traineeId")
  @Mapping(target = "tisReferenceInfo", source = "accountEvent")
  @Mapping(target = "availableFrom", ignore = true)
  @Mapping(target = "dueBy", ignore = true)
  @Mapping(target = "completed", expression = "java(java.time.Instant.now())")
  Action toAction(AccountConfirmedEvent accountEvent, ActionType type);

  /**
   * Create an action using Placement data.
   *
   * @param dto  The Placement to retrieve data from.
   * @param type The type of action to be created.
   * @return The created action.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "type")
  @Mapping(target = "traineeId", source = "dto.traineeId")
  @Mapping(target = "tisReferenceInfo", source = "dto")
  @Mapping(target = "availableFrom",
      expression = "java( dto.startDate() != null ? dto.startDate().minusWeeks(12) : null )")
  @Mapping(target = "dueBy", source = "dto.startDate")
  @Mapping(target = "completed", ignore = true)
  Action toAction(PlacementDto dto, ActionType type);

  /**
   * Create a CURRENT ActionBroadcastDto from an Action.
   *
   * @param action  The Action to map from.
   * @return The ActionBroadcastDto.
   */
  @Mapping(target = "id", expression = "java(action.id().toString())")
  @Mapping(target = "type", expression = "java(action.type().toString())")
  @Mapping(target = "status", constant = "CURRENT")
  @Mapping(target = "statusDatetime", expression = "java(java.time.Instant.now())")
  ActionBroadcastDto toCurrentActionBroadcastDto(Action action);

  /**
   * Create a DELETED ActionBroadcastDto from an Action.
   *
   * @param action  The Action to map from.
   * @return The ActionBroadcastDto.
   */
  @Mapping(target = "id", expression = "java(action.id().toString())")
  @Mapping(target = "type", expression = "java(action.type().toString())")
  @Mapping(target = "traineeId", ignore = true)
  @Mapping(target = "tisReferenceInfo", ignore = true)
  @Mapping(target = "availableFrom", ignore = true)
  @Mapping(target = "dueBy", ignore = true)
  @Mapping(target = "completed", ignore = true)
  @Mapping(target = "status", constant = "DELETED")
  @Mapping(target = "statusDatetime", expression = "java(java.time.Instant.now())")
  ActionBroadcastDto toDeletedActionBroadcastDto(Action action);

  /**
   * Map a Programme Membership to a TIS reference info object.
   *
   * @param dto The Programme Membership to map.
   * @return A reference to the TIS core object.
   */
  @Mapping(target = "id", source = "dto.id")
  @Mapping(target = "type", constant = "PROGRAMME_MEMBERSHIP")
  TisReferenceInfo map(ProgrammeMembershipDto dto);

  /**
   * Map a Placement to a TIS reference info object.
   *
   * @param dto The Placement to map.
   * @return A reference to the TIS core object.
   */
  @Mapping(target = "id", source = "dto.id")
  @Mapping(target = "type", constant = "PLACEMENT")
  TisReferenceInfo map(PlacementDto dto);

  /**
   * Map an Account Event to a TIS reference info object.
   *
   * @param accountEvent The account event to map.
   * @return A reference to the TIS core object.
   */
  @Mapping(target = "id", source = "accountEvent.traineeId")
  @Mapping(target = "type", constant = "PERSON")
  TisReferenceInfo map(AccountConfirmedEvent accountEvent);
}
