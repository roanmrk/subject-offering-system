package com.earist.ccs.scheduler.controller;

import com.earist.ccs.scheduler.model.Faculty;
import com.earist.ccs.scheduler.repository.FacultyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/faculty")
public class FacultyProfileController {

    @Autowired
    private FacultyRepository facultyRepository;

    // Get faculty by ID
    @GetMapping("/{id}")
    public ResponseEntity<Faculty> getFaculty(@PathVariable Long id) {
        return facultyRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Update faculty profile (limited fields)
    @PutMapping("/profile/{id}")
    public ResponseEntity<Faculty> updateProfile(@PathVariable Long id, @RequestBody Faculty faculty) {
        return facultyRepository.findById(id)
                .map(existing -> {
                    // Only allow updating certain fields
                    existing.setFirstName(faculty.getFirstName());
                    existing.setLastName(faculty.getLastName());
                    existing.setSpecialization(faculty.getSpecialization());
                    // Add other fields as needed
                    return ResponseEntity.ok(facultyRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Get faculty schedule
    @GetMapping("/schedule/{facultyId}")
    public ResponseEntity<?> getFacultySchedule(@PathVariable Long facultyId) {
        // Return the faculty's schedule
        // This would query the schedules table
        return ResponseEntity.ok("Faculty schedule data");
    }
}