package com.earist.ccs.scheduler.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "faculty")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Faculty {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "faculty_id", unique = true, nullable = false)
    private String facultyId;
    
    @Column(name = "first_name", nullable = false)
    private String firstName;
    
    @Column(name = "last_name", nullable = false)
    private String lastName;
    
    @Column(columnDefinition = "TEXT")
    private String specialization;
    
    @Column(columnDefinition = "TEXT")
    private String certifications;
    
    @Column(name = "max_load_units")
    private Integer maxLoadUnits = 24;
}