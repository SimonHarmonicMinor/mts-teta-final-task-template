package com.mts.teta.tagmanager.controller;

import com.mts.teta.tagmanager.controller.dto.TriggerCreateRequest;
import com.mts.teta.tagmanager.controller.dto.TriggerCreated;
import com.mts.teta.tagmanager.domain.Trigger;
import com.mts.teta.tagmanager.repository.ContainerRepository;
import com.mts.teta.tagmanager.repository.TriggerRepository;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trigger")
@RequiredArgsConstructor
@Validated
public class TriggerController {

  private final ContainerRepository containerRepository;
  private final TriggerRepository triggerRepository;

  // Удалить Trigger по ID-шнику
  // DELETE /api/trigger/1
  @DeleteMapping("/{triggerId}")
  public void deleteTrigger(@NotNull @PathVariable long triggerId) {
    triggerRepository.deleteById(triggerId);
  }

  // Создать Trigger для заданного Container
  /*
   * POST /api/trigger/container/1
   *
   * { "type": "SET_INTERVAL", "attributes": { "setTimeout": { "delayMillis": 0, "messageToSend": { "key": "value" } } }, "name": "triggerName" }
   * */
  @PostMapping("/container/{containerId}")
  @Transactional
  public TriggerCreated createTrigger(
      @NotNull @PathVariable long containerId,
      @NotNull @Valid @RequestBody TriggerCreateRequest request
  ) {
    final var container = containerRepository.findById(containerId).orElseThrow();
    final var trigger = triggerRepository.save(
        Trigger.newTrigger(
            request.getName(),
            container,
            request.getType(),
            request.getAttributes()
        ));
    return new TriggerCreated(trigger.getId());
  }
}
