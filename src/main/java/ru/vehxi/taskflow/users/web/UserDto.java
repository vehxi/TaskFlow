package ru.vehxi.taskflow.users.web;

import java.util.Set;
import java.util.stream.Collectors;
import ru.vehxi.taskflow.users.domain.*;

/** Public account data without credentials.
     * @param id user identity
     * @param name display name
     * @param email normalized address
 *
     * @param roles role codes
     * @param active login availability
     * @param version optimistic version */
public record UserDto(Long id, String name, String email, Set<RoleCode> roles, boolean active, long version) {
    /** Returns credential-free DTO.
     * @param user account with initialized roles
     * @return credential-free DTO */
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getName(), user.getEmail(),
                user.getRoles().stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet()),
                user.isActive(), user.getVersion());
    }
}
