package uk.nhs.tis.trainee.actions.event;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.actions.service.ActionService;

@Slf4j
@Component
public class ProgrammeMembershipListener {

  private final ActionService actionService;

  public ProgrammeMembershipListener(ActionService actionService) {
    this.actionService = actionService;
  }

  @SqsListener("https://sqs.eu-west-2.amazonaws.com/430723991443/dingers-test") // TODO: property
  public void handleProgrammeMembershipCreation(ProgrammeMembershipEvent event) {
    log.info("Programme Membership: {}", event.getProgrammeMembership());
    log.info("Operation: {}", event.getOperation());
//    actionService.createActions(null);
  }
}
