package com.example.GANTone;

import jakarta.persistence.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
public class GANToneApplication {
    public static void main(String[] args) { SpringApplication.run(GANToneApplication.class, args); }
}

@Entity
@Table(name = "tasks")
class Task {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String modelName;
    public String status;
}

interface TaskRepository extends JpaRepository<Task, Long> {}

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*") // Для React
class TaskController {
    private final TaskRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public TaskController(TaskRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/start")
    public String startTask(@RequestParam String modelName) {
        // Сохранение в Supabase
        Task task = new Task();
        task.modelName = modelName;
        task.status = "PENDING";
        task = repository.save(task);

        // Отправление в Kafka (формат: id:modelName)
        String message = task.id + ":" + modelName;
        kafkaTemplate.send("ai-tasks", message);

        return "Task started with ID: " + task.id;
    }
}