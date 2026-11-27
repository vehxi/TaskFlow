package ru.vehxi.taskflow.references.web;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vehxi.taskflow.references.ReferenceValue;
import ru.vehxi.taskflow.references.repository.TaskPriorityRepository;
import ru.vehxi.taskflow.references.repository.TaskTypeRepository;

/** Reference values available for reading and selecting in task forms. */
@RestController
public class ReferenceDataController {
    private final TaskTypeRepository types;
    private final TaskPriorityRepository priorities;

    /** Creates the read-only reference endpoint.
     * @param types type storage
     * @param priorities priority storage */
    public ReferenceDataController(TaskTypeRepository types, TaskPriorityRepository priorities) {
        this.types = types;
        this.priorities = priorities;
    }

    /** {@return active and historical reference values } */
    @GetMapping("/api/reference-data")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReferenceData get() {
        Sort order = Sort.by("id");
        return new ReferenceData(
                types.findAll(order).stream().map(ReferenceValue::from).toList(),
                priorities.findAll(order).stream().map(ReferenceValue::from).toList());
    }

    /** Reference types and priorities.
     * @param types available types
     * @param priorities available priorities */
    public record ReferenceData(List<ReferenceValue> types, List<ReferenceValue> priorities) { }
}
