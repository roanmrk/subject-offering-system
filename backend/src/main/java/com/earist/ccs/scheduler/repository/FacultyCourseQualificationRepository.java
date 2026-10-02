package com.earist.ccs.scheduler.repository;

import com.earist.ccs.scheduler.model.FacultyCourseQualification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FacultyCourseQualificationRepository
        extends JpaRepository<FacultyCourseQualification, Long> {

    List<FacultyCourseQualification> findByCourse_Id(Long courseId);

    List<FacultyCourseQualification> findByFaculty_Id(Long facultyId);

    @Query("SELECT fcq FROM FacultyCourseQualification fcq " +
           "JOIN FETCH fcq.faculty f " +
           "WHERE fcq.course.id = :courseId " +
           "ORDER BY fcq.yearsExperience DESC")
    List<FacultyCourseQualification> findByCourseIdWithFaculty(@Param("courseId") Long courseId);
}