package com.earist.ccs.scheduler.repository;

import com.earist.ccs.scheduler.model.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    List<Faculty> findBySpecializationContainingIgnoreCase(String specialization);
    List<Faculty> findByLastNameContainingIgnoreCase(String lastName);
}