package uk.nhs.tis.trainee.actions.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

@Getter
public abstract class RecordEvent {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

  private Operation operation;

  @JsonProperty("record")
  private void unpackRecord(JsonNode recordNode) {
    operation = getObjectMapper().convertValue(recordNode.get("operation"), Operation.class);
    unpackData(recordNode.get("data"));
  }

  protected abstract void unpackData(JsonNode dataNode);

  protected ObjectMapper getObjectMapper() {
    return OBJECT_MAPPER;
  }
}
