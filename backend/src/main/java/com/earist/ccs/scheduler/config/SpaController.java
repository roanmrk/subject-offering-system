package com.earist.ccs.scheduler.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class SpaController {

    /**
     * Forward all non-API, non-static GET requests to index.html
     * so React Router handles client-side routing.
     */
    @RequestMapping(value = {"/", "/{path:[^\\.]*}"}, method = RequestMethod.GET)
    public String forward() {
        return "forward:/index.html";
    }
}