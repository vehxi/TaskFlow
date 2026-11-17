package ru.vehxi.taskflow.bootstrap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
/**
 * Optional initial administrator configuration.
 *
     * @param enabled whether initialization is requested
 *
     * @param name administrator display name
 *
     * @param email administrator email
 *
     * @param password initial plaintext password supplied outside Git
 */
@ConfigurationProperties("app.bootstrap")
public record BootstrapProperties(@DefaultValue("false") boolean enabled,
        @DefaultValue("Администратор") String name, @DefaultValue("") String email,
        @DefaultValue("") String password) {
    /**
     * Returns a redacted configuration description.
     *
     * @return a redacted configuration description
     */
    @Override public String toString() { return "BootstrapProperties[enabled=" + enabled + ", credentials=REDACTED]"; }
}
