package com.earist.ccs.scheduler.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String user;
    
    @Column(nullable = false)
    private String action;
    
    @Column(columnDefinition = "TEXT")
    private String details;
    
    @Column(name = "timestamp")
    private String timestamp;
    
    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd hh:mm a")
    private LocalDateTime createdAt = LocalDateTime.now();
}