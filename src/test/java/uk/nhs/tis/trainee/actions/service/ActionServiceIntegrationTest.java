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

package uk.nhs.tis.trainee.actions.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.nhs.tis.trainee.actions.event.Operation.CREATE;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.actions.DockerImageNames;
import uk.nhs.tis.trainee.actions.dto.ProgrammeMembershipDto;
import uk.nhs.tis.trainee.actions.model.Action;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ActionServiceIntegrationTest {

  private static final String TIS_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_ID_1 = UUID.randomUUID().toString();
  private static final String TRAINEE_ID_2 = UUID.randomUUID().toString();
  private static final LocalDate START_DATE = LocalDate.now().minusDays(1);

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private ActionService service;

  @AfterEach
  void cleanUp() {
    mongoTemplate.findAllAndRemove(new Query(), Action.class);
  }

  @Test
  void shouldNotInsertDuplicateActions() {
    ProgrammeMembershipDto dto = new ProgrammeMembershipDto(TIS_ID, TRAINEE_ID_1, START_DATE);

    service.updateActions(CREATE, dto);
    assertThrows(DuplicateKeyException.class, () -> service.updateActions(CREATE, dto));

    Criteria criteria = Criteria.where("traineeId").is(TRAINEE_ID_1);
    Query query = Query.query(criteria);
    List<Action> actions = mongoTemplate.find(query, Action.class);
    assertThat("Unexpected action count.", actions.size(), is(1));
  }

  @Test
  void shouldNotInsertTheSameActionForMultipleTrainees() {
    ProgrammeMembershipDto dto1 = new ProgrammeMembershipDto(TIS_ID, TRAINEE_ID_1, START_DATE);
    ProgrammeMembershipDto dto2 = new ProgrammeMembershipDto(TIS_ID, TRAINEE_ID_2, START_DATE);

    service.updateActions(CREATE, dto1);
    assertThrows(DuplicateKeyException.class, () -> service.updateActions(CREATE, dto2));

    Criteria criteria1 = Criteria.where("traineeId").is(TRAINEE_ID_1);
    Query query1 = Query.query(criteria1);
    List<Action> actions1 = mongoTemplate.find(query1, Action.class);
    assertThat("Unexpected action count.", actions1.size(), is(1));

    Criteria criteria2 = Criteria.where("traineeId").is(TRAINEE_ID_2);
    Query query2 = Query.query(criteria2);
    List<Action> actions2 = mongoTemplate.find(query2, Action.class);
    assertThat("Unexpected action count.", actions2.size(), is(0));
  }
}
