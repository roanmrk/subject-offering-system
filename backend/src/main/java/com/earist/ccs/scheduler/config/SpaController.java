package com.earist.ccs.scheduler.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {

    // Forward any non-API route to index.html for React Router to handle
    @RequestMapping(value = {"/{path:[^\\.]*}", "/**/{path:[^\\.]*}"})
    public String forward() {
        return "forward:/index.html";
    }
}