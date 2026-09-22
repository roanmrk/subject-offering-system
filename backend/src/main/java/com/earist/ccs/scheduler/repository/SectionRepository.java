package com.earist.ccs.scheduler.repository;

import com.earist.ccs.scheduler.model.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SectionRepository extends JpaRepository<Section, Long> {
    Optional<Section> findBySectionCode(String sectionCode);
    List<Section> findByProgram(String program);
    List<Section> findByYearLevel(Integer yearLevel);
}