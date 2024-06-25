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

package uk.nhs.tis.trainee.actions.repository;

import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.DeleteQuery;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import uk.nhs.tis.trainee.actions.model.Action;

/**
 * A repository of trainee actions.
 */
@Repository
public interface ActionRepository extends MongoRepository<Action, ObjectId> {

  /**
   * Get all incomplete actions associated with a given trainee ID.
   *
   * @param traineeId The ID of the trainee to get actions for.
   * @return A list of incomplete actions for the trainee.
   */
  List<Action> findAllByTraineeIdAndCompletedIsNullOrderByDueByAsc(String traineeId);

  /**
   * Find an action by its action ID and associated trainee ID.
   *
   * @param id        The ID of the action to find.
   * @param traineeId The trainee that should be associated with the action.
   * @return The found trainee action, or empty if not found.
   */
  Optional<Action> findByIdAndTraineeId(ObjectId id, String traineeId);

  /**
   * Delete specific TIS entity action(s) for a trainee where these are not complete.
   *
   * @param traineeId The trainee ID.
   * @param tisId     The TIS ID of the entity.
   * @param type      The entity type.
   */
  @DeleteQuery(value = "{$and : [{'traineeId': ?0}, "
      + "{'tisReferenceInfo.id': ?1}, "
      + "{'tisReferenceInfo.type': ?2}, "
      + "{'completed': null}]}")
  List<Action> deleteByTraineeIdAndTisReferenceInfoAndNotComplete(String traineeId, String tisId,
      String type);

  /**
   * Delete specific TIS entity action(s) for a trainee.
   *
   * @param traineeId The trainee ID.
   * @param tisId     The TIS ID of the entity.
   * @param type      The entity type.
   */
  @DeleteQuery(value = "{$and : [{'traineeId': ?0}, "
      + "{'tisReferenceInfo.id': ?1}, "
      + "{'tisReferenceInfo.type': ?2}]}")
  List<Action> deleteByTraineeIdAndTisReferenceInfo(String traineeId, String tisId, String type);

  /**
   * Find specific TIS entity action(s) for a trainee.
   *
   * @param traineeId The trainee ID.
   * @param tisId     The TIS ID of the entity.
   * @param type      The entity type.
   */
  @Query(value = "{$and : [{'traineeId': ?0}, "
      + "{'tisReferenceInfo.id': ?1}, "
      + "{'tisReferenceInfo.type': ?2}]}")
  List<Action> findByTraineeIdAndTisReferenceInfo(String traineeId, String tisId, String type);
}
