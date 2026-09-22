package com.earist.ccs.scheduler.controller;

import com.earist.ccs.scheduler.model.Room;
import com.earist.ccs.scheduler.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/rooms")
public class RoomController {

    @Autowired
    private RoomRepository roomRepository;

    @GetMapping
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Room> getRoomById(@PathVariable Long id) {
        return roomRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/type/{roomType}")
    public List<Room> getRoomsByType(@PathVariable String roomType) {
        return roomRepository.findByRoomTypeIgnoreCase(roomType);
    }

    @PostMapping
    public Room addRoom(@RequestBody Room room) {
        return roomRepository.save(room);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Room> updateRoom(@PathVariable Long id, @RequestBody Room room) {
        if (!roomRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        room.setId(id);
        return ResponseEntity.ok(roomRepository.save(room));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id) {
        if (!roomRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        roomRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}