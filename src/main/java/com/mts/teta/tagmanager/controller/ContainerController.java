package com.mts.teta.tagmanager.controller;

import static com.mts.teta.tagmanager.domain.Trigger.TriggerType.SET_INTERVAL;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mts.teta.enricher.cache.UserInfoRepository;
import com.mts.teta.tagmanager.controller.dto.ContainerCreatedResponse;
import com.mts.teta.tagmanager.controller.dto.ContainerResponse;
import com.mts.teta.tagmanager.domain.Container;
import com.mts.teta.tagmanager.domain.Trigger;
import com.mts.teta.tagmanager.repository.AppRepository;
import com.mts.teta.tagmanager.repository.ContainerRepository;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/container")
@RequiredArgsConstructor
@CrossOrigin("*")
@Validated
public class ContainerController {

  private final AppRepository appRepository;
  private final ContainerRepository containerRepository;
  private final ObjectMapper objectMapper;
  private final UserInfoRepository userInfoRepository;

  // получить список контейнеров вместе с их триггерами по ID-шнику приложения
  // GET /api/container/app/1
  @GetMapping("/app/{appId}")
  public List<ContainerResponse> getContainers(@NotNull @PathVariable long appId) {
    return containerRepository.findAllByAppId(appId)
        .stream()
        .map(ContainerResponse::new)
        .toList();
  }

  // Создать контейнер для заданного приложения
  // POST /api/container/app/1?name=containerName
  @PostMapping("/app/{appId}")
  @Transactional
  public ContainerCreatedResponse createContainer(
      @PathVariable long appId,
      @RequestParam String name
  ) {
    final var app = appRepository.findById(appId).orElseThrow();
    final var container = containerRepository.save(Container.newContainer(name, app));
    return new ContainerCreatedResponse(container.getId());
  }

  @GetMapping(value = "/{containerId}/jsFile", produces = "text/javascript;charset=UTF-8")
  @Transactional
  public byte[] getContainerAsJsFile(@NotNull @PathVariable long containerId) {
    final var container = containerRepository.findById(containerId).orElseThrow();
    final var jsFile = container.getTriggers()
        .stream()
        .map(this::triggerToJsString)
        .collect(Collectors.joining(";\n"));
    return jsFile.getBytes(UTF_8);
  }

  @SneakyThrows
  private String triggerToJsString(Trigger trigger) {
    if (trigger.getType() != SET_INTERVAL) {
      // Если будете добавлять новые типы триггеров, поддержку для них вам нужно будет
      // реализовать самостоятельно
      throw new UnsupportedOperationException(
          "Указанный тип триггера еще не поддерживается: " + trigger.getType()
      );
    }
    final var attributes = trigger.getAttributes().getSetTimeout();
    final var userIds = userInfoRepository.findAllUserIds();
    return """
        // дополнительно оборачивание в function - хак, который позволяет
        // выполнить код сразу при загрузке страницы
        (function() {
          console.log("Trigger {triggerName} is activated");
          /*
            В данном случае, никаких дополнительных листенеров не нужно, потому что мы просто регистрируем функцию
            через setInterval, которая периодически выполняется.
            Если же вы, например, захотите отслеживать события клика, или скролла, то вам нужно будет добавить
            соответствующие слушатели:
            
            document.addEventListener('click', function() {...});
            document.addEventListener('scroll', function() {...});
            
            и так далее
          */
          setInterval(function() {
              console.log("Trigger {triggerName} is performing the action");
              // здесь отправляется сообщение на бэкенд
              // Endpoint, как видите, захардкожен. При дефолтных настройках все будет работать.
              // Но лучше, если это поле будет конфигурируемым
              fetch('http://localhost:8080/api/message', {
                  method: 'POST',
                  mode: 'no-cors',
                  headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                  },
                  // к trigger.attributes прибавляем еще кастомные атрибуты: userId, event, element, app
                  body: JSON.stringify({
                      "userId": "{userId}",
                      "event": "set_interval",
                      "element": null, // setInterval не привязан к какому-то конкретному элементу на странице
                      // информация о приложении нужна, чтобы мы понимали, к кому относится данное событие
                      "app_name": "{appName}",
                      "app_id": {appId},
                      // в event_params как раз сохраняет trigger.attributes
                      "event_params": {attributes}
                  })
              })
          }, {delayMillis})
        })()
        """.replaceAll("\\{triggerName}", trigger.getName())
        .replaceAll(
            "\\{attributes}",
            // Здесь мы преобразуем Map<String, Object> в JSON, который и подставится в JSON.stringify
            objectMapper.writeValueAsString(
                attributes.getMessageToSend()
            )
        )
        .replaceAll("\\{delayMillis}", String.valueOf(attributes.getDelayMillis()))
        .replaceAll("\\{appName}", trigger.getContainer().getApp().getName())
        .replaceAll("\\{appId}", String.valueOf(trigger.getContainer().getApp().getId()))
        .replaceAll(
            "\\{userId}",
            // Здесь простая реализация: подставляем случайный userId из всех существующих.
            // Вы можете доработать и придумать что-то более интеллектуальное.
            // Например, определенные userId будут относиться к определенным приложениям и триггер будет выбирать
            // значение из соответствующего множества
            userIds.get(ThreadLocalRandom.current().nextInt(userIds.size()))
        );
  }
}
