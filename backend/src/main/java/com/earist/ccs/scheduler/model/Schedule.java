package com.earist.ccs.scheduler.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Schedule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "section_id", nullable = false)
    private Long sectionId;
    
    @Column(name = "faculty_id", nullable = false)
    private Long facultyId;
    
    @Column(name = "room_id", nullable = false)
    private Long roomId;
    
    @Column(name = "timeslot_id", nullable = false)
    private Long timeslotId;
    
    @Column(nullable = false)
    private String semester;
    
    @Column(name = "academic_year", nullable = false)
    private String academicYear;
    
    @Column(name = "is_conflict_free")
    private Boolean isConflictFree = false;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}