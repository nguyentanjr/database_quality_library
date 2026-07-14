package org.example.controller;

import org.example.runtime.DqlRuntimeControl;
import org.example.runtime.DqlRuntimeStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dql/admin")
public class DqlAdminController {

    private final DqlRuntimeControl runtimeControl;

    public DqlAdminController(DqlRuntimeControl runtimeControl) {
        this.runtimeControl = runtimeControl;
    }

    @GetMapping("/status")
    public DqlRuntimeStatus status() {
        return runtimeControl.status();
    }

    @PostMapping("/enable")
    public DqlRuntimeStatus enable() {
        return runtimeControl.enable();
    }

    @PostMapping("/disable")
    public DqlRuntimeStatus disable() {
        return runtimeControl.disable();
    }
}
