package com.earist.ccs.scheduler.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "faculty_course_qualifications",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"faculty_id", "course_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FacultyCourseQualification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /**
     * Maps to MySQL ENUM('PRIMARY','SECONDARY','TERTIARY').
     * We declare it as String so Hibernate stores it verbatim.
     * The columnDefinition tells Hibernate to expect ENUM, not VARCHAR.
     */
    @Column(name = "qualification_level", columnDefinition = "ENUM('PRIMARY','SECONDARY','TERTIARY')")
    private String qualificationLevel;

    @Column(name = "years_experience")
    private Integer yearsExperience;
}