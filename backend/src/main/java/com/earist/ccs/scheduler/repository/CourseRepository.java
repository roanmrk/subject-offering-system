package com.earist.ccs.scheduler.repository;

import com.earist.ccs.scheduler.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByProgramIgnoreCase(String program);
    List<Course> findByIsLaboratoryTrue();
    Optional<Course> findByCourseCode(String courseCode);
}