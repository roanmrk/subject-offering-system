package com.earist.ccs.scheduler.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "room_code", unique = true, nullable = false)
    private String roomCode;
    
    @Column(name = "room_name", nullable = false)
    private String roomName;
    
    @Column(name = "room_type", nullable = false)
    private String roomType;
    
    @Column(nullable = false)
    private Integer capacity;
    
    @Column(columnDefinition = "TEXT")
    private String equipment;
}