package com.earist.ccs.scheduler.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "courses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Course code is required")
    @Size(max = 20, message = "Course code must be at most 20 characters")
    @Column(name = "course_code", unique = true, nullable = false)
    private String courseCode;

    @NotBlank(message = "Course name is required")
    @Size(max = 200, message = "Course name must be at most 200 characters")
    @Column(name = "course_name", nullable = false)
    private String courseName;

    @NotBlank(message = "Program is required")
    @Column(nullable = false)
    private String program;

    @NotNull(message = "Year level is required")
    @Min(value = 1, message = "Year level must be at least 1")
    @Max(value = 6, message = "Year level must be at most 6")
    @Column(name = "year_level", nullable = false)
    private Integer yearLevel;

    @NotNull(message = "Semester is required")
    @Min(value = 1, message = "Semester must be 1 or 2")
    @Max(value = 2, message = "Semester must be 1 or 2")
    @Column(nullable = false)
    private Integer semester;

    @NotNull(message = "Units are required")
    @Min(value = 1, message = "Units must be at least 1")
    @Max(value = 10, message = "Units must be at most 10")
    @Column(nullable = false)
    private Integer units;

    @Column(name = "is_laboratory")
    private Boolean isLaboratory = false;

    @ManyToOne
    @JoinColumn(name = "prerequisite_course_id")
    private Course prerequisite;
}