package com.earist.ccs.scheduler.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class SpaController {

    /**
     * Forward all non-API GET requests to index.html so React Router handles routing.
     * Matches paths that do NOT contain a dot (i.e., not static files like .js, .css, .png).
     */
    @RequestMapping(value = {"/", "/{path:[^\\.]*}"}, method = RequestMethod.GET)
    public String forward() {
        return "forward:/index.html";
    }
}