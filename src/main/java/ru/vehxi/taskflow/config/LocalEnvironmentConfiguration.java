package ru.vehxi.taskflow.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/** Reads optional local credentials as UTF-8 properties. */
@Configuration
@ConditionalOnProperty(name = "app.environment-file.enabled", havingValue = "true", matchIfMissing = true)
@PropertySource(name = "localEnvironment", value = "file:${APP_ENV_FILE:.env}",
        encoding = "UTF-8", ignoreResourceNotFound = true)
public class LocalEnvironmentConfiguration {
    /** Creates local environment configuration. */
    public LocalEnvironmentConfiguration() { }
}
