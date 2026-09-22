package com.earist.ccs.scheduler.controller;

import com.earist.ccs.scheduler.model.Schedule;
import com.earist.ccs.scheduler.repository.ScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/schedules")
public class AdminScheduleController {

    @Autowired
    private ScheduleRepository scheduleRepository;

    @GetMapping
    public List<Schedule> getAllSchedules() {
        return scheduleRepository.findAll();
    }
}