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

import static uk.nhs.tis.trainee.actions.model.ActionType.REVIEW_DATA;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
//import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.actions.dto.ActionDto;
import uk.nhs.tis.trainee.actions.mapper.ActionMapper;
import uk.nhs.tis.trainee.actions.model.Action;
//import uk.nhs.tis.trainee.actions.model.History.TisReferenceInfo;
//import uk.nhs.tis.trainee.actions.model.ProgrammeMembership;
//import uk.nhs.tis.trainee.actions.model.TisReferenceType;
import uk.nhs.tis.trainee.actions.repository.ActionRepository;

@Service
public class ActionService {

  private final ActionRepository repository;
  private final ActionMapper mapper;

  public ActionService(ActionRepository repository, ActionMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

//  public Action createActions(ProgrammeMembership programmeMembership) {
//    TisReferenceInfo tisReference = new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP,
//        programmeMembership.getTisId());
//    Action action = new Action(null, REVIEW_DATA, programmeMembership.getPersonId(), tisReference, Instant.now(), null);
//    return repository.insert(action);
//  }
//
//  public Optional<ActionDto> complete(String actionId) {
//    if (!ObjectId.isValid(actionId)) {
//      throw new IllegalArgumentException("The action ID was not a valid Object ID");
//    }
//
//    ObjectId objectId = new ObjectId(actionId);
//    Optional<Action> action = repository.findById(objectId);
//
//    if (action.isPresent()) {
//      Action completedAction = mapper.completeAction(action.get(), Instant.now());
//      completedAction = repository.save(completedAction);
//      return Optional.of(mapper.toDto(completedAction));
//    }
//
//    return Optional.empty();
//  }

  public List<ActionDto> find(String traineeId) {
    List<Action> actions = repository.findAllByTraineeIdOrderByDueAsc(traineeId);
    return mapper.toDtos(actions);
  }

  public void delete() {

  }
}
