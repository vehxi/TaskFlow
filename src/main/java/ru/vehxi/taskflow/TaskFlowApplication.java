package ru.vehxi.taskflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the TaskFlow application. */
@SpringBootApplication
public class TaskFlowApplication {

    /** Creates the application bootstrap component. */
    public TaskFlowApplication() {
    }

    /**
     * Starts the application.
     *
     * @param args command-line configuration arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(TaskFlowApplication.class, args);
    }

}
