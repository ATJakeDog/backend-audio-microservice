package com.example.GANTone;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@SpringBootApplication
@EnableAsync
public class GANToneApplication {
    public static void main(String[] args) {
        SpringApplication.run(GANToneApplication.class, args);
    }
}

@Entity
@Table(name = "tasks")
class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String modelName;
    public String status;
    public String originalAudioUrl;
    public String processedAudioUrl;

    // используем @JdbcTypeCode(SqlTypes.JSON)
    // Hibernate 6 сам кастит в jsonb без ошибок
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes_metadata", columnDefinition = "jsonb")
    public String changesMetadata;
}

interface TaskRepository extends JpaRepository<Task, Long> {}

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
class TaskController {
    private final TaskRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    public TaskController(TaskRepository repository,
                          KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping(value = "/start", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String startTask(@RequestParam("modelName") String modelName,
                            @RequestParam("file") MultipartFile file) {
        // Создаем запись сразу, не блокируя поток ожиданием загрузки
        Task task = new Task(); 
        task.modelName = modelName;
        task.status = "UPLOADING";
        task.changesMetadata = "[]"; //дефолтное валидное JSON-значение
        final Task savedTask = repository.save(task);

        // Запускаем асинхронный процесс выгрузки
        CompletableFuture.runAsync(() -> {
            try {
                String fileName = UUID.randomUUID().toString()
                        + "_" + file.getOriginalFilename();
                String storageUrl = supabaseUrl
                        + "/storage/v1/object/audio-files/" + fileName;

                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + supabaseKey);
                headers.setContentType(
                        MediaType.valueOf(file.getContentType()));

                HttpEntity<byte[]> requestEntity =
                        new HttpEntity<>(file.getBytes(), headers);
                restTemplate.exchange(storageUrl, HttpMethod.POST,
                        requestEntity, String.class);

                // Обновляем БД после успешной загрузки
                savedTask.originalAudioUrl = storageUrl;
                savedTask.status = "PENDING";
                repository.save(savedTask);

                // Только теперь отправляем в Kafka
                String message = savedTask.id + ":" + modelName;
                kafkaTemplate.send("ai-tasks", message);

            } catch (Exception e) {
                savedTask.status = "FAILED";
                repository.save(savedTask);
            }
        });

        return "Task started with ID: " + savedTask.id
                + ". Audio is uploading in background.";
    }
}