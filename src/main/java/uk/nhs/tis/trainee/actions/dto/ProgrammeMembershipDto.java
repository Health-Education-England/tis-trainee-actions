package uk.nhs.tis.trainee.actions.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProgrammeMembershipDto(@JsonAlias("tisId") String id, @JsonAlias("personId") String traineeId, LocalDate startDate) {

}
