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

package uk.nhs.tis.trainee.actions.model;

import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;

/**
 * The type category of the action to be performed.
 */
public enum ActionType {
  REVIEW_DATA,
  SIGN_COJ,
  SIGN_FORM_R_PART_A,
  SIGN_FORM_R_PART_B,
  REGISTER_TSS;

  /**
   * The set of Programme action types.
   */
  @Getter
  private static final Set<ActionType> programmeActionTypes = EnumSet.of(
      REVIEW_DATA,
      SIGN_COJ,
      SIGN_FORM_R_PART_A,
      SIGN_FORM_R_PART_B);

  /**
   * The set of Placement action types.
   */
  @Getter
  private static final Set<ActionType> placementActionTypes = EnumSet.of(
      REVIEW_DATA);

  /**
   * The set of Person action types.
   */
  @Getter
  private static final Set<ActionType> personActionTypes = EnumSet.of(
      REGISTER_TSS);
}
