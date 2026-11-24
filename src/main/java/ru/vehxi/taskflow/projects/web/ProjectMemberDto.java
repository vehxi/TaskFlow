package ru.vehxi.taskflow.projects.web;

import ru.vehxi.taskflow.users.domain.User;

/** Participant identity displayed to managers.
 * @param id account identity
 * @param name display name */
public record ProjectMemberDto(Long id, String name) {
    /** Maps an account to its minimal public representation.
     * @param user participant or candidate
     * @return identity and display name */
    public static ProjectMemberDto from(User user) {
        return new ProjectMemberDto(user.getId(), user.getName());
    }
}
