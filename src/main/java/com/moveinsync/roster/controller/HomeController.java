package com.moveinsync.roster.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Root controller that automatically redirects users navigating to
 * the root URL (/) to the interactive Swagger UI API documentation.
 */
@Controller
@Hidden
public class HomeController {

    @GetMapping("/")
    public String redirectToSwagger() {
        return "redirect:/swagger-ui.html";
    }
}
