package com.earist.ccs.scheduler.repository;

import com.earist.ccs.scheduler.model.SubjectOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SubjectOfferingRepository extends JpaRepository<SubjectOffering, Long> {
    List<SubjectOffering> findBySemesterAndAcademicYear(String semester, String academicYear);
    List<SubjectOffering> findBySection_Id(Long sectionId);
    List<SubjectOffering> findByFaculty_Id(Long facultyId);
    List<SubjectOffering> findByRoom_Id(Long roomId);
    void deleteBySemesterAndAcademicYear(String semester, String academicYear);
}