package uk.nhs.tis.trainee.actions.event;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import uk.nhs.tis.trainee.actions.dto.ProgrammeMembershipDto;

@Getter
public class ProgrammeMembershipEvent extends RecordEvent {

  private ProgrammeMembershipDto programmeMembership;

  protected void unpackData(JsonNode data) {
    programmeMembership = getObjectMapper().convertValue(data, ProgrammeMembershipDto.class);
  }
}
