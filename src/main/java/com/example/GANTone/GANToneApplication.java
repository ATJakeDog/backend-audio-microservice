package com.example.GANTone;

import jakarta.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.ThreadLocalRandom;

@SpringBootApplication
@EnableAsync
public class GANToneApplication {
    public static void main(String[] args) {
        SpringApplication.run(GANToneApplication.class, args);
    }

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("task-");
        executor.initialize();
        return executor;
    }
}

@Entity
@Table(name = "tasks")
class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String correlationId;
    public String modelName;
    public String status;
    public Integer progress;
    public String originalAudioUrl;
    public String processedAudioUrl;
    public Instant createdAt;
    public Instant updatedAt;

    // используется @JdbcTypeCode(SqlTypes.JSON)
    // Hibernate 6 сам кастит в jsonb без ошибок
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes_metadata", columnDefinition = "jsonb")
    public String changesMetadata;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}

interface TaskRepository extends JpaRepository<Task, Long> {}

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
class TaskController {
    private final TaskRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Executor taskExecutor;
    private final ObjectMapper objectMapper;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    public TaskController(TaskRepository repository,
                          KafkaTemplate<String, String> kafkaTemplate,
                          Executor taskExecutor,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskStatusResponse> getTask(@PathVariable Long id) {
        return repository.findById(id)
                .map(task -> ResponseEntity.ok(toResponse(task)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTasks() {
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                while (true) {
                    List<TaskStatusResponse> snapshot = repository.findAll().stream()
                            .sorted(Comparator.comparingLong(task -> task.id == null ? 0L : task.id))
                            .map(this::toResponse)
                            .toList();

                    emitter.send(SseEmitter.event()
                            .name("tasks")
                            .data(new TaskSnapshotResponse(snapshot)));

                    Thread.sleep(1500);
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }, taskExecutor);

        return emitter;
    }

    @PostMapping(value = "/start", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StartTaskResponse> startTask(@RequestParam("modelName") String modelName,
                                                       @RequestParam("file") MultipartFile file) throws Exception {
        // Считываются байты сразу, пока запрос жив
        byte[] fileBytes = file.getBytes();
        String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("audio.wav");
        String contentType = Optional.ofNullable(file.getContentType()).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);

        Task task = new Task();
        task.modelName = modelName;
        task.status = "UPLOADING";
        task.progress = 0;
        task.changesMetadata = "[]";
        task.correlationId = UUID.randomUUID().toString();
        final Task savedTask = repository.save(task);

        // Передача в поток данных, а не ссылки на файл
        CompletableFuture.runAsync(() -> {
            try {
                String publicStorageUrl = uploadToSupabase(fileBytes, originalFilename, contentType);

                updateTask(savedTask.id, taskToUpdate -> {
                    taskToUpdate.originalAudioUrl = publicStorageUrl;
                    taskToUpdate.status = "PENDING";
                    taskToUpdate.progress = 20;
                });

                sendKafkaEvent("ai-tasks", new TaskEvent(
                        "TASK_CREATED",
                        savedTask.id,
                        modelName,
                        savedTask.correlationId,
                        20,
                        "PENDING"
                ));

            } catch (Exception e) {
                e.printStackTrace(); 
                updateTask(savedTask.id, taskToUpdate -> {
                    taskToUpdate.status = "FAILED";
                    taskToUpdate.progress = 100;
                });
            }
        }, taskExecutor);

        return ResponseEntity.accepted().body(new StartTaskResponse(
                savedTask.id,
                savedTask.correlationId,
                modelName,
                "UPLOADING",
                0,
                "Task started with ID: " + savedTask.id + ". Audio is uploading in background."
        ));
    }

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchStartResponse> startBatch(@RequestParam("file") MultipartFile file,
                                                         @RequestParam(value = "count", defaultValue = "20") Integer count,
                                                         @RequestParam(value = "models", defaultValue = "whisper,gan,denoise") String models) throws Exception {
        byte[] fileBytes = file.getBytes();
        String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("audio.wav");
        String contentType = Optional.ofNullable(file.getContentType()).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);

        String publicStorageUrl = uploadToSupabase(fileBytes, originalFilename, contentType);
        String[] enabledModels = models.split(",");
        List<StartTaskResponse> createdTasks = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            String modelName = enabledModels[ThreadLocalRandom.current().nextInt(enabledModels.length)].trim();

            Task task = new Task();
            task.modelName = modelName;
            task.status = "PENDING";
            task.progress = 20;
            task.originalAudioUrl = publicStorageUrl;
            task.changesMetadata = "[]";
            task.correlationId = UUID.randomUUID().toString();

            Task savedTask = repository.save(task);
            createdTasks.add(new StartTaskResponse(
                    savedTask.id,
                    savedTask.correlationId,
                    modelName,
                    savedTask.status,
                    savedTask.progress,
                    "Batch task " + savedTask.id + " queued."
            ));

            sendKafkaEvent("ai-tasks", new TaskEvent(
                    "TASK_CREATED",
                    savedTask.id,
                    modelName,
                    savedTask.correlationId,
                    20,
                    "PENDING"
            ));
        }

        return ResponseEntity.accepted().body(new BatchStartResponse(count, publicStorageUrl, createdTasks));
    }

    private Task updateTask(Long taskId, Consumer<Task> updater) {
        Task task = repository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Task not found: " + taskId));
        updater.accept(task);
        return repository.save(task);
    }

    private String uploadToSupabase(byte[] fileBytes, String originalFilename, String contentType) throws Exception {
        String fileName = UUID.randomUUID().toString() + "_" + originalFilename;
        String storageUrl = supabaseUrl + "/storage/v1/object/audio-files/" + fileName;

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + supabaseKey);
        headers.setContentType(MediaType.valueOf(contentType));

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(fileBytes, headers);
        restTemplate.exchange(storageUrl, HttpMethod.POST, requestEntity, String.class);

        return supabaseUrl + "/storage/v1/object/public/audio-files/" + fileName;
    }

    private void sendKafkaEvent(String topic, TaskEvent event) {
        try {
            kafkaTemplate.send(topic, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Kafka event", e);
        }
    }

    private TaskStatusResponse toResponse(Task task) {
        return new TaskStatusResponse(
                task.id,
                task.correlationId,
                task.modelName,
                task.status,
                task.progress == null ? 0 : task.progress,
                task.originalAudioUrl,
                task.processedAudioUrl,
                task.changesMetadata,
                task.createdAt,
                task.updatedAt
        );
    }
}

record StartTaskResponse(Long taskId, String correlationId, String modelName, String status, Integer progress, String message) {}

record BatchStartResponse(Integer count, String originalAudioUrl, List<StartTaskResponse> tasks) {}

record TaskSnapshotResponse(List<TaskStatusResponse> tasks) {}

record TaskEvent(String eventType, Long taskId, String modelName, String correlationId, Integer progress, String status) {}

record TaskStatusResponse(Long id, String correlationId, String modelName, String status, Integer progress,
                          String originalAudioUrl, String processedAudioUrl, String changesMetadata,
                          Instant createdAt, Instant updatedAt) {}