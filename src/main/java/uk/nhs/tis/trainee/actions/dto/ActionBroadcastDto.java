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

import java.time.Instant;
import java.time.LocalDate;
import uk.nhs.tis.trainee.actions.dto.enumeration.ActionStatus;
import uk.nhs.tis.trainee.actions.model.Action.TisReferenceInfo;

/**
 * A DTO for Action data to export to NDW.
 *
 * @param id               The ID of the action.
 * @param type             The type of action.
 * @param traineeId        The ID of the trainee who the action is for.
 * @param tisReferenceInfo The TIS core object associated with the action.
 * @param availableFrom    When the action is available to complete.
 * @param dueBy            When the action is due to be completed by.
 * @param completed        When the action was completed, null if not completed.
 * @param status           The status of Actions (ACTIVE or DELETED).
 * @param statusDatetime   When the status was set.
 */
public record ActionBroadcastDto(
    String id,
    String type,
    String traineeId,
    TisReferenceInfo tisReferenceInfo,
    LocalDate availableFrom,
    LocalDate dueBy,
    Instant completed,
    ActionStatus status,
    Instant statusDatetime) {

}
