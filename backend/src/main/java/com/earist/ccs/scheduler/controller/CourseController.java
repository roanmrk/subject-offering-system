package com.earist.ccs.scheduler.controller;

import com.earist.ccs.scheduler.model.Course;
import com.earist.ccs.scheduler.repository.CourseRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/courses")
public class CourseController {

    @Autowired
    private CourseRepository courseRepository;

    @GetMapping
    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Course> getCourseById(@PathVariable Long id) {
        return courseRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> addCourse(@Valid @RequestBody Course course) {
        Course saved = courseRepository.save(course);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCourse(@PathVariable Long id, @Valid @RequestBody Course course) {
        if (!courseRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        course.setId(id);
        return ResponseEntity.ok(courseRepository.save(course));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        if (!courseRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        courseRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}