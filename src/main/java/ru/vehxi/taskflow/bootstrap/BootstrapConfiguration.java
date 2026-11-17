package ru.vehxi.taskflow.bootstrap;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
/** Runs optional administrator initialization after Flyway and JPA startup. */
@Configuration
@EnableConfigurationProperties(BootstrapProperties.class)
public class BootstrapConfiguration {
    /** Creates bootstrap configuration. */
    public BootstrapConfiguration() { }
    /**
     * Creates the initialization runner.
     *
     * @param service transactional initialization service
     *
     * @param properties external administrator settings
     *
     * @return the startup runner
     */
    @Bean public ApplicationRunner administratorBootstrap(BootstrapAdminService service, BootstrapProperties properties) {
        return args -> service.initialize(properties);
    }
}
