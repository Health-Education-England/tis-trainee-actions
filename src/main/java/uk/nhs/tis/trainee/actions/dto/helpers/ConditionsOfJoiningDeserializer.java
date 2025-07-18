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

package uk.nhs.tis.trainee.actions.dto.helpers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import uk.nhs.tis.trainee.actions.dto.ConditionsOfJoining;

/**
 * A deserializer for ConditionsOfJoining.
 *
 * <p>This deserializes a JSON string into a ConditionsOfJoining object using Jackson.</p>
 */
public class ConditionsOfJoiningDeserializer extends JsonDeserializer<ConditionsOfJoining> {

  /**
   * Deserialize a JSON string into a ConditionsOfJoining object. Note that a serialized value of
   * "null" will be converted into a null object.
   *
   * @param p    The JsonParser to read the JSON string.
   * @param ctxt The DeserializationContext.
   * @return The deserialized ConditionsOfJoining object.
   * @throws IOException If an error occurs during deserialization.
   */
  @Override
  public ConditionsOfJoining deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    String cojString = p.getValueAsString();
    ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return objectMapper.readValue(cojString,
        new TypeReference<>() {
        });
  }
}
