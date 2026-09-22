package com.earist.ccs.scheduler.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "sections")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Section {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // Optional: allows section to be associated with a home course.
    // Currently unused by SectionController — can be set manually in DB if needed.
    @ManyToOne
    @JoinColumn(name = "course_id", nullable = true)
    private Course course;
    
    @Column(name = "section_code", unique = true, nullable = false)
    private String sectionCode;
    
    @Column(nullable = false)
    private String program;
    
    @Column(name = "year_level", nullable = false)
    private Integer yearLevel;
    
    @Column(name = "expected_enrollment")
    private Integer expectedEnrollment = 60;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
}