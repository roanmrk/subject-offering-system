package com.earist.ccs.scheduler.controller;

import com.earist.ccs.scheduler.model.Faculty;
import com.earist.ccs.scheduler.repository.FacultyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/faculty")
public class FacultyController {

    @Autowired
    private FacultyRepository facultyRepository;

    @GetMapping
    public List<Faculty> getAllFaculty() {
        return facultyRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Faculty> getFacultyById(@PathVariable Long id) {
        return facultyRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/specialization/{specialization}")
    public List<Faculty> getFacultyBySpecialization(@PathVariable String specialization) {
        return facultyRepository.findBySpecializationContainingIgnoreCase(specialization);
    }

    @PostMapping
    public Faculty addFaculty(@RequestBody Faculty faculty) {
        return facultyRepository.save(faculty);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Faculty> updateFaculty(@PathVariable Long id, @RequestBody Faculty faculty) {
        if (!facultyRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        faculty.setId(id);
        return ResponseEntity.ok(facultyRepository.save(faculty));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFaculty(@PathVariable Long id) {
        if (!facultyRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        facultyRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}