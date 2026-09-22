package com.earist.ccs.scheduler.controller;

import com.earist.ccs.scheduler.model.Timeslot;
import com.earist.ccs.scheduler.repository.TimeslotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/timeslots")
public class TimeslotController {

    @Autowired
    private TimeslotRepository timeslotRepository;

    // Get all time slots
    @GetMapping
    public List<Timeslot> getAllTimeslots() {
        return timeslotRepository.findAll();
    }

    // Get a single time slot by ID
    @GetMapping("/{id}")
    public ResponseEntity<Timeslot> getTimeslotById(@PathVariable Long id) {
        return timeslotRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Add a new time slot
    @PostMapping
    public Timeslot addTimeslot(@RequestBody Timeslot timeslot) {
        return timeslotRepository.save(timeslot);
    }

    // Update a time slot
    @PutMapping("/{id}")
    public ResponseEntity<Timeslot> updateTimeslot(@PathVariable Long id, @RequestBody Timeslot timeslot) {
        if (!timeslotRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        timeslot.setId(id);
        return ResponseEntity.ok(timeslotRepository.save(timeslot));
    }

    // Delete a time slot
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTimeslot(@PathVariable Long id) {
        if (!timeslotRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        timeslotRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}