package com.billion21.work.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    @GetMapping("/info")
    public SystemInfo info() {
        return new SystemInfo("billion-work", "foundation");
    }

    public record SystemInfo(String name, String stage) {}
}
