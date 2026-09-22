package com.earist.ccs.scheduler.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalTime;

@Entity
@Table(name = "timeslots")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Timeslot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String day;
    
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    
    @Column(name = "slot_code", unique = true, nullable = false)
    private String slotCode;
    
    @Column(name = "is_available")
    private Boolean isAvailable = true;
}