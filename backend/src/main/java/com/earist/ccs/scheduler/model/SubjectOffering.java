package com.earist.ccs.scheduler.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "subject_offerings",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"section_id", "course_id", "semester", "academic_year"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubjectOffering {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;
    
    @ManyToOne
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
    
    @ManyToOne
    @JoinColumn(name = "faculty_id")
    private Faculty faculty;
    
    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;
    
    @ManyToOne
    @JoinColumn(name = "timeslot_id")
    private Timeslot timeslot;
    
    @Column(nullable = false)
    private String semester;
    
    @Column(name = "academic_year", nullable = false)
    private String academicYear;
    
    @Column(name = "is_conflict_free")
    private Boolean isConflictFree = true;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @com.fasterxml.jackson.annotation.JsonProperty("sectionId")
    public Long getSectionId() {
        return section != null ? section.getId() : null;
    }
    
    @com.fasterxml.jackson.annotation.JsonProperty("courseId")
    public Long getCourseId() {
        return course != null ? course.getId() : null;
    }
    
    @com.fasterxml.jackson.annotation.JsonProperty("facultyId")
    public Long getFacultyId() {
        return faculty != null ? faculty.getId() : null;
    }
    
    @com.fasterxml.jackson.annotation.JsonProperty("roomId")
    public Long getRoomId() {
        return room != null ? room.getId() : null;
    }
    
    @com.fasterxml.jackson.annotation.JsonProperty("timeslotId")
    public Long getTimeslotId() {
        return timeslot != null ? timeslot.getId() : null;
    }
}