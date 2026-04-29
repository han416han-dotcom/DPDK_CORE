package com.dpdk.core.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({"/tasks", "/tasks/{id:[^\\.]+}"})
    public String forwardTasks() {
        return "forward:/index.html";
    }
}
