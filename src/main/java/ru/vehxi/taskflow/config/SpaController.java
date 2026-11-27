package ru.vehxi.taskflow.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the React entry point for the currently supported client-side routes. */
@Controller
public class SpaController {
    /** Creates SPA routing. */
    public SpaController() { }
    /** Returns static React entry point; API and asset paths are not forwarded.
     * @return static React entry point; API and asset paths are not forwarded */
    @GetMapping({"/", "/login", "/account", "/admin/users", "/admin/users/new", "/admin/users/{id:[0-9]+}", "/admin/reference-data",
            "/projects", "/projects/new", "/projects/{id:[0-9]+}", "/projects/{id:[0-9]+}/tasks/new", "/projects/{id:[0-9]+}/report",
            "/tasks", "/tasks/{id:[0-9]+}", "/tasks/{id:[0-9]+}/edit"})
    public String application() { return "forward:/index.html"; }
}
