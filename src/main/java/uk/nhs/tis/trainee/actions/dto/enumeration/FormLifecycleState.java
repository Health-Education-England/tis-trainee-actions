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
 *
 */

package uk.nhs.tis.trainee.actions.dto.enumeration;

import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;

/**
 * An enumeration of the lifecycle states of a form.
 */
public enum FormLifecycleState {
  APPROVED,
  DELETED,
  DRAFT,
  REJECTED,
  SUBMITTED,
  UNSUBMITTED,
  WITHDRAWN;

  /**
   * The set of states that should complete the sign-form action.
   */
  @Getter
  private static final Set<FormLifecycleState> completeSignFormStates = EnumSet.of(
      APPROVED, SUBMITTED);

  /**
   * The set of states that should uncomplete the sign-form action.
   */
  @Getter
  private static final Set<FormLifecycleState> uncompleteSignFormStates = EnumSet.of(
      DELETED, DRAFT, REJECTED, UNSUBMITTED, WITHDRAWN);
}
