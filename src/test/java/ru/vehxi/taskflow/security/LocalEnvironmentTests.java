package ru.vehxi.taskflow.security;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import ru.vehxi.taskflow.config.LocalEnvironmentConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

class LocalEnvironmentTests {
    @TempDir Path directory;

    @Test
    void readsLocalNamesAndPasswordsAsUtf8() throws Exception {
        Path file = directory.resolve(".env");
        Files.writeString(file, "APP_BOOTSTRAP_NAME=Администратор\nAPP_BOOTSTRAP_PASSWORD=пароль-для-теста\n");
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment().withProperty("APP_ENV_FILE", file.toString()));
            context.register(LocalEnvironmentConfiguration.class);
            context.refresh();
            assertThat(context.getEnvironment().getProperty("APP_BOOTSTRAP_NAME")).isEqualTo("Администратор");
            assertThat(context.getEnvironment().getProperty("APP_BOOTSTRAP_PASSWORD")).isEqualTo("пароль-для-теста");
        }
    }
}
