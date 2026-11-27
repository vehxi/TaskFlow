package ru.vehxi.taskflow.references;

import ru.vehxi.taskflow.references.domain.TaskPriority;
import ru.vehxi.taskflow.references.domain.TaskType;

/** A task type or priority exposed through the API.
 * @param id value identity
 * @param name display name
 * @param active whether tasks can newly select the value
 * @param version current version */
public record ReferenceValue(Long id, String name, boolean active, long version) {
    /** @param type stored type
     * @return public reference value */
    public static ReferenceValue from(TaskType type) {
        return new ReferenceValue(type.getId(), type.getName(), type.isActive(), type.getVersion());
    }

    /** @param priority stored priority
     * @return public reference value */
    public static ReferenceValue from(TaskPriority priority) {
        return new ReferenceValue(priority.getId(), priority.getName(), priority.isActive(), priority.getVersion());
    }
}
