package com.earist.ccs.scheduler.controller;

import com.earist.ccs.scheduler.model.*;
import com.earist.ccs.scheduler.repository.*;
import com.earist.ccs.scheduler.service.ScheduleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/schedule")
@Slf4j
public class ScheduleController {
    
    @Autowired
    private ScheduleService scheduleService;
    
    @Autowired
    private ScheduleRepository scheduleRepository;
    
    @Autowired
    private ActivityLogRepository activityLogRepository;
    
    @Autowired
    private SubjectOfferingRepository subjectOfferingRepository;
    
    /**
     * Generate schedule.
     *
     * @param random  If true, uses a fresh random seed → different valid schedule each call.
     *                If false (default), uses deterministic seed = 0 → reproducible.
     * @param seed    Optional explicit seed. If provided, overrides random flag.
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateSchedule(
            @RequestParam(defaultValue = "1st Semester") String semester,
            @RequestParam(defaultValue = "2026-2027") String academicYear,
            @RequestParam(defaultValue = "false") boolean random,
            @RequestParam(required = false) Long seed) {
        
        long effectiveSeed = 0L;
        if (seed != null && seed != 0L) {
            effectiveSeed = seed;
        } else if (random) {
            effectiveSeed = System.nanoTime() & Long.MAX_VALUE;
        }
        
        log.info("Generating schedule for {} - {} (seed={})", 
            semester, academicYear, 
            effectiveSeed == 0L ? "deterministic" : String.valueOf(effectiveSeed));
        
        Map<String, Object> result = scheduleService.generateSchedule(
            semester, academicYear, effectiveSeed);
        
        try {
            ActivityLog activityLog = new ActivityLog();
            activityLog.setUser("admin@earist.edu.ph");
            activityLog.setAction(effectiveSeed == 0L 
                ? "Generated schedule" 
                : "Regenerated schedule");
            activityLog.setDetails(semester + " " + academicYear + " - " + 
                result.getOrDefault("scheduledSections", 0) + " sections" +
                (effectiveSeed != 0L ? " (seed=" + effectiveSeed + ")" : ""));
            activityLog.setTimestamp(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a")));
            activityLogRepository.save(activityLog);
            log.info("📝 Activity logged");
        } catch (Exception e) {
            log.error("Failed to log activity: {}", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Reset schedule — clears all offerings for a semester/year.
     */
    @DeleteMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetSchedule(
            @RequestParam String semester,
            @RequestParam String academicYear) {
        
        Map<String, Object> result = new HashMap<>();
        try {
            List<SubjectOffering> existing = 
                subjectOfferingRepository.findBySemesterAndAcademicYear(semester, academicYear);
            
            int count = existing.size();
            subjectOfferingRepository.deleteAll(existing);
            
            result.put("success", true);
            result.put("cleared", count);
            result.put("message", "Cleared " + count + " offerings");
            
            log.info("🗑️ Reset: cleared {} offerings for {} {}", 
                count, semester, academicYear);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Reset error", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
    
    @GetMapping("/compare-algorithms")
    public ResponseEntity<Map<String, Object>> compareAlgorithms(
            @RequestParam String semester,
            @RequestParam String academicYear) {
        log.info("Comparing algorithms for {} {}", semester, academicYear);
        return ResponseEntity.ok(scheduleService.compareAlgorithms(semester, academicYear));
    }
    
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("✅ Scheduler API is running!");
    }
    
    @GetMapping("/admin/all")
    public ResponseEntity<List<Schedule>> getAllSchedules() {
        return ResponseEntity.ok(scheduleRepository.findAll());
    }
    
    @GetMapping("/view")
    public ResponseEntity<Map<String, Object>> viewSchedule(
            @RequestParam String semester,
            @RequestParam String academicYear) {
        log.info("Viewing schedule for {} - {}", semester, academicYear);
        return ResponseEntity.ok(scheduleService.viewSchedule(semester, academicYear));
    }
    
    @DeleteMapping("/clear")
    public ResponseEntity<String> clearSchedules() {
        scheduleRepository.deleteAll();
        log.info("🗑️ All schedules cleared");
        return ResponseEntity.ok("✅ All schedules cleared!");
    }
}