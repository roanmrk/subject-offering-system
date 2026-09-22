package com.earist.ccs.scheduler.controller;

import com.earist.ccs.scheduler.model.*;
import com.earist.ccs.scheduler.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/subject-offerings")
@Slf4j
public class SubjectOfferingController {

    @Autowired
    private SubjectOfferingRepository subjectOfferingRepository;

    @Autowired
    private SectionRepository sectionRepository;

    @Autowired
    private CourseRepository courseRepository;

    @GetMapping
    public List<SubjectOffering> getAll() {
        return subjectOfferingRepository.findAll();
    }

    @GetMapping("/section/{sectionId}")
    public List<SubjectOffering> getBySection(@PathVariable Long sectionId) {
        return subjectOfferingRepository.findBySection_Id(sectionId);
    }

    @PostMapping
    public ResponseEntity<?> addSubjectOffering(@RequestBody Map<String, Object> payload) {
        try {
            log.info("📥 Received subject offering payload: {}", payload);
            
            SubjectOffering offering = new SubjectOffering();
            
            // Section
            Long sectionId = Long.parseLong(payload.get("sectionId").toString());
            Optional<Section> section = sectionRepository.findById(sectionId);
            if (!section.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Section not found"));
            }
            offering.setSection(section.get());
            
            // Course
            Long courseId = Long.parseLong(payload.get("courseId").toString());
            Optional<Course> course = courseRepository.findById(courseId);
            if (!course.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Course not found"));
            }
            offering.setCourse(course.get());
            
            offering.setSemester(payload.get("semester").toString());
            offering.setAcademicYear(payload.get("academicYear").toString());
            offering.setIsConflictFree(true);
            
            SubjectOffering saved = subjectOfferingRepository.save(offering);
            log.info("✅ Subject offering created: Section={}, Course={}", 
                section.get().getSectionCode(), course.get().getCourseCode());
            
            return ResponseEntity.ok(saved);
            
        } catch (Exception e) {
            log.error("❌ Error creating subject offering: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateSubjectOffering(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        try {
            Optional<SubjectOffering> existingOpt = subjectOfferingRepository.findById(id);
            if (!existingOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            SubjectOffering existing = existingOpt.get();
            
            if (payload.get("sectionId") != null) {
                Long sectionId = Long.parseLong(payload.get("sectionId").toString());
                sectionRepository.findById(sectionId).ifPresent(existing::setSection);
            }
            
            if (payload.get("courseId") != null) {
                Long courseId = Long.parseLong(payload.get("courseId").toString());
                courseRepository.findById(courseId).ifPresent(existing::setCourse);
            }
            
            if (payload.get("semester") != null) {
                existing.setSemester(payload.get("semester").toString());
            }
            if (payload.get("academicYear") != null) {
                existing.setAcademicYear(payload.get("academicYear").toString());
            }
            
            SubjectOffering updated = subjectOfferingRepository.save(existing);
            return ResponseEntity.ok(updated);
            
        } catch (Exception e) {
            log.error("❌ Error updating subject offering: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSubjectOffering(@PathVariable Long id) {
        if (!subjectOfferingRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        subjectOfferingRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Subject offering deleted"));
    }
}