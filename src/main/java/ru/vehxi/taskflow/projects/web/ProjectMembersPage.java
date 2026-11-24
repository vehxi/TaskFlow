package ru.vehxi.taskflow.projects.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** Project membership page and the matching version of the project.
 * @param items participants
 * @param page zero-based page
 * @param size page capacity
 * @param totalElements participant count
 * @param totalPages page count
 * @param projectVersion version of the project */
public record ProjectMembersPage(List<ProjectMemberDto> items, int page, int size,
        long totalElements, int totalPages, long projectVersion) {
    /** Maps the page and its version.
     * @param members participant page
     * @param version matching project version
     * @return membership page */
    public static ProjectMembersPage from(Page<ProjectMemberDto> members, long version) {
        return new ProjectMembersPage(members.getContent(), members.getNumber(), members.getSize(),
                members.getTotalElements(), members.getTotalPages(), version);
    }
}
