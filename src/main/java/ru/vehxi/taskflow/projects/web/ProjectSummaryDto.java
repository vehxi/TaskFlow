package ru.vehxi.taskflow.projects.web;

import ru.vehxi.taskflow.projects.domain.Project;

/** Readable project details shared by both project roles.
 * @param id project identity
 * @param name project name
 * @param description project description
 * @param managerId owning manager
 * @param version current version */
public record ProjectSummaryDto(Long id, String name, String description, Long managerId, long version) {
    /** Maps a project to the public representation.
     * @param project project entity
     * @return summary */
    public static ProjectSummaryDto from(Project project) {
        return new ProjectSummaryDto(project.getId(), project.getName(), project.getDescription(),
                project.getManager().getId(), project.getVersion());
    }
}
