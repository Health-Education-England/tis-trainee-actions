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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.tis.trainee.actions.dto.ConditionsOfJoining;

@ExtendWith(MockitoExtension.class)
class ConditionsOfJoiningDeserializerTest {

  private static final Instant SIGNED_AT = Instant.now().minusSeconds(5);
  private static final Instant SYNCED_AT = Instant.now();
  private ConditionsOfJoiningDeserializer deserializer;

  @Mock
  private JsonParser parser;

  @Mock
  private DeserializationContext context;

  @BeforeEach
  void setUp() {
    deserializer = new ConditionsOfJoiningDeserializer();
  }

  @Test
  void shouldDeserializeValidJson() throws IOException {
    String validJson = """
        {
          "version": "1.0",
          "signedAt": "%s",
          "syncedAt": "%s"
        }
        """.formatted(SIGNED_AT, SYNCED_AT);
    when(parser.getValueAsString()).thenReturn(validJson);

    ConditionsOfJoining result = deserializer.deserialize(parser, context);

    assertEquals("1.0", result.version());
    assertEquals(SIGNED_AT, result.signedAt());
    assertEquals(SYNCED_AT, result.syncedAt());
  }

  @Test
  void shouldReturnNullWhenJsonIsNull() throws IOException {
    when(parser.getValueAsString()).thenReturn("null");

    ConditionsOfJoining result = deserializer.deserialize(parser, context);

    assertNull(result);
  }

  @Test
  void shouldThrowExceptionForInvalidJson() throws IOException {
    String invalidJson = "invalid json";
    when(parser.getValueAsString()).thenReturn(invalidJson);

    assertThrows(IOException.class, () -> deserializer.deserialize(parser, context));
  }

  @Test
  void shouldDeserializeEmptyObject() throws IOException {
    String emptyJson = "{}";
    when(parser.getValueAsString()).thenReturn(emptyJson);

    ConditionsOfJoining result = deserializer.deserialize(parser, context);

    assertNull(result.version());
    assertNull(result.signedAt());
    assertNull(result.syncedAt());
  }
}
