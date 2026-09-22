package com.earist.ccs.scheduler.repository;

import com.earist.ccs.scheduler.model.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findBySemesterAndAcademicYear(String semester, String academicYear);
}