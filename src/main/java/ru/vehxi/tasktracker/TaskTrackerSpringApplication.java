package ru.vehxi.tasktracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the task tracker application. */
@SpringBootApplication
public class TaskTrackerSpringApplication {

    /** Creates the application bootstrap component. */
    public TaskTrackerSpringApplication() {
    }

    /**
     * Starts the application.
     *
     * @param args command-line configuration arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(TaskTrackerSpringApplication.class, args);
    }

}
