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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.tis.trainee.actions.dto.ProgrammeMembershipDto.CurriculumDto;

@ExtendWith(MockitoExtension.class)
class CurriculaDeserializerTest {

  private CurriculaDeserializer deserializer;

  @Mock
  private JsonParser parser;

  @Mock
  private DeserializationContext context;

  @BeforeEach
  void setUp() {
    deserializer = new CurriculaDeserializer();
  }

  @Test
  void shouldDeserializeValidJson() throws IOException {
    String validJson = """
        [
          {
            "curriculumTisId": "123",
            "curriculumSubType": "subType1",
            "curriculumSpecialty": "specialty1"
          },
          {
            "curriculumTisId": "321",
            "curriculumSubType": "subType2",
            "curriculumSpecialty": "specialty2"
          }
        ]
        """;
    when(parser.getValueAsString()).thenReturn(validJson);

    List<CurriculumDto> result = deserializer.deserialize(parser, context);

    assertThat("Unexpected curriculum count.", result, hasSize(2));

    CurriculumDto curriculum1 = result.get(0);
    assertThat("Unexpected curriculum sub type.", curriculum1.curriculumSubType(), is("subType1"));
    assertThat("Unexpected curriculum specialty.", curriculum1.curriculumSpecialty(),
        is("specialty1"));

    CurriculumDto curriculum2 = result.get(1);
    assertThat("Unexpected curriculum sub type.", curriculum2.curriculumSubType(), is("subType2"));
    assertThat("Unexpected curriculum specialty.", curriculum2.curriculumSpecialty(),
        is("specialty2"));
  }

  @Test
  void shouldReturnNullWhenJsonIsNull() throws IOException {
    when(parser.getValueAsString()).thenReturn("null");

    List<CurriculumDto> result = deserializer.deserialize(parser, context);

    assertThat("Unexpected result.", result, nullValue());
  }

  @Test
  void shouldThrowExceptionForInvalidJson() throws IOException {
    String invalidJson = "invalid json";
    when(parser.getValueAsString()).thenReturn(invalidJson);

    assertThrows(IOException.class, () -> deserializer.deserialize(parser, context));
  }

  @Test
  void shouldDeserializeEmptyList() throws IOException {
    String emptyJson = "[]";
    when(parser.getValueAsString()).thenReturn(emptyJson);

    List<CurriculumDto> result = deserializer.deserialize(parser, context);

    assertThat("Unexpected curriculum count.", result, hasSize(0));
  }
}
