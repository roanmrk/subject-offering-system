package com.earist.ccs.scheduler.controller;

import com.earist.ccs.scheduler.model.ActivityLog;
import com.earist.ccs.scheduler.repository.ActivityLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/activity-logs")
public class ActivityLogController {

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @GetMapping
    public List<ActivityLog> getAllLogs() {
        return activityLogRepository.findAll();
    }

    @PostMapping
    public ActivityLog addLog(@RequestBody ActivityLog log) {
        return activityLogRepository.save(log);
    }
}