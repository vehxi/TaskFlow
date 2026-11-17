package ru.vehxi.taskflow.security;

import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Bounds BCrypt input without exposing invalid credential values. */
final class TaskFlowPasswordEncoder implements PasswordEncoder {
    private final PasswordEncoder delegate = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        if (!isSupported(rawPassword)) {
            throw new IllegalArgumentException("Password exceeds the supported UTF-8 length");
        }
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return isSupported(rawPassword) && delegate.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }

    private boolean isSupported(CharSequence rawPassword) {
        return rawPassword != null && rawPassword.toString().getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
