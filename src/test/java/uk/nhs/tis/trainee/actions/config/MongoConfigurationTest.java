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

package uk.nhs.tis.trainee.actions.config;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import uk.nhs.tis.trainee.actions.model.Action;

class MongoConfigurationTest {

  private MongoConfiguration configuration;

  private MongoTemplate template;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    configuration = new MongoConfiguration(template);
  }

  @Test
  void shouldInitIndexesForActionCollection() {
    IndexOperations indexOperations = mock(IndexOperations.class);
    when(template.indexOps(Action.class)).thenReturn(indexOperations);

    configuration.initIndexes();

    ArgumentCaptor<Index> indexCaptor = ArgumentCaptor.forClass(Index.class);
    verify(indexOperations, atLeastOnce()).ensureIndex(indexCaptor.capture());

    List<Index> indexes = indexCaptor.getAllValues();
    assertThat("Unexpected number of indexes.", indexes.size(), is(2));

    List<String> indexKeys = indexes.stream()
        .flatMap(i -> i.getIndexKeys().keySet().stream())
        .toList();
    assertThat("Unexpected number of index keys.", indexKeys.size(), is(3));
  }

  @Test
  void shouldInitTraineeIndexForActionCollection() {
    IndexOperations indexOperations = mock(IndexOperations.class);
    when(template.indexOps(Action.class)).thenReturn(indexOperations);

    configuration.initIndexes();

    ArgumentCaptor<Index> indexCaptor = ArgumentCaptor.forClass(Index.class);
    verify(indexOperations, atLeastOnce()).ensureIndex(indexCaptor.capture());

    List<Index> indexes = indexCaptor.getAllValues();
    Set<String> indexKeys = indexes.stream()
        .filter(i -> i.getIndexOptions().get("name").equals("traineeIndex"))
        .map(i -> i.getIndexKeys().keySet())
        .findAny()
        .orElseThrow();
    assertThat("Unexpected number of index keys.", indexKeys.size(), is(1));
    assertThat("Unexpected index keys.", indexKeys, hasItems("traineeId"));
  }

  @Test
  void shouldInitUniqueActionIndexForActionCollection() {
    IndexOperations indexOperations = mock(IndexOperations.class);
    when(template.indexOps(Action.class)).thenReturn(indexOperations);

    configuration.initIndexes();

    ArgumentCaptor<Index> indexCaptor = ArgumentCaptor.forClass(Index.class);
    verify(indexOperations, atLeastOnce()).ensureIndex(indexCaptor.capture());

    List<Index> indexes = indexCaptor.getAllValues();
    Set<String> indexKeys = indexes.stream()
        .filter(i -> i.getIndexOptions().get("name").equals("uniqueActionPerReference"))
        .map(i -> i.getIndexKeys().keySet())
        .findAny()
        .orElseThrow();
    assertThat("Unexpected number of index keys.", indexKeys.size(), is(2));
    assertThat("Unexpected index keys.", indexKeys, hasItems("type", "tisReferenceInfo"));
  }
}
