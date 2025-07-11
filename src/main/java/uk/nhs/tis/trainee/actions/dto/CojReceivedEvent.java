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

package uk.nhs.tis.trainee.actions.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A representation of a Conditions of Joining event.
 *
 * <p>Note that this looks a lot like the ProgrammeMembershipDto because generally the Record-based
 * structures wrap content which is here included at the root level, but in this case the
 * ConditionsOfJoining is a 'proper' object, not a serialized string.</p>
 *
 * @param id                  The programme membership ID.
 * @param traineeId           The trainee ID associated with the membership.
 * @param conditionsOfJoining The Conditions of Joining.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CojReceivedEvent(@JsonAlias("tisId") String id,
                               @JsonAlias("personId") String traineeId,
                               ConditionsOfJoining conditionsOfJoining) {

}
