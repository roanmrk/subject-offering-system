package com.earist.ccs.scheduler.service;

import com.earist.ccs.scheduler.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Module 5: Backtracking Validation
 * Verifies hard constraints after scheduling.
 * Reports real conflicts with reasons.
 */
@Service
@Slf4j
public class BacktrackingService {

    public static class ValidationResult {
        public List<Map<String, Object>> entries = new ArrayList<>();
        public int totalChecks = 0;
        public int passedChecks = 0;
        public int facultyConflicts = 0;
        public int roomConflicts = 0;
        public int capacityViolations = 0;
        public int sectionConflicts = 0;
        public long executionTimeMs = 0;

        public double hardConstraintRate() {
            if (totalChecks == 0) return 100.0;
            return (double) passedChecks / totalChecks * 100;
        }
    }

    public ValidationResult validateAndFinalize(
            List<SubjectOffering> offerings,
            List<Timeslot> timeSlots,
            List<Room> rooms,
            List<Faculty> faculty,
            List<Section> sections) {

        ValidationResult result = new ValidationResult();
        long start = System.currentTimeMillis();

        log.info("✔️ [MODULE 5] BACKTRACKING VALIDATION");
        log.info("   Validating {} offerings", offerings.size());

        // Track usage: (resource_id + timeslot) → first offering id
        Map<String, String> facultyTimeUsage = new HashMap<>();
        Map<String, String> roomTimeUsage = new HashMap<>();
        Map<String, String> sectionTimeUsage = new HashMap<>();

        for (SubjectOffering o : offerings) {
            if (o.getSection() == null || o.getCourse() == null 
                || o.getTimeslot() == null) continue;

            Section section = o.getSection();
            Course course = o.getCourse();
            Timeslot ts = o.getTimeslot();
            Faculty fac = o.getFaculty();
            Room room = o.getRoom();

            String timeKey = ts.getDay() + "|" + ts.getStartTime();

            boolean rowConflict = false;
            List<String> reasons = new ArrayList<>();

            // Check 1: Faculty conflict
            if (fac != null) {
                result.totalChecks++;
                String key = fac.getId() + "|" + timeKey;
                if (facultyTimeUsage.containsKey(key)) {
                    result.facultyConflicts++;
                    rowConflict = true;
                    reasons.add("Faculty " + fac.getFirstName() + " " + fac.getLastName()
                        + " already booked at " + ts.getDay() + " " + ts.getStartTime());
                } else {
                    facultyTimeUsage.put(key, String.valueOf(o.getId()));
                    result.passedChecks++;
                }
            }

            // Check 2: Room conflict
            if (room != null) {
                result.totalChecks++;
                String key = room.getRoomCode() + "|" + timeKey;
                if (roomTimeUsage.containsKey(key)) {
                    result.roomConflicts++;
                    rowConflict = true;
                    reasons.add("Room " + room.getRoomCode()
                        + " already booked at " + ts.getDay() + " " + ts.getStartTime());
                } else {
                    roomTimeUsage.put(key, String.valueOf(o.getId()));
                    result.passedChecks++;
                }
            }

            // Check 3: Room capacity
            if (room != null && section.getExpectedEnrollment() != null
                && room.getCapacity() != null) {
                result.totalChecks++;
                if (section.getExpectedEnrollment() > room.getCapacity()) {
                    result.capacityViolations++;
                    rowConflict = true;
                    reasons.add("Room " + room.getRoomCode() + " capacity "
                        + room.getCapacity() + " < enrollment " 
                        + section.getExpectedEnrollment());
                } else {
                    result.passedChecks++;
                }
            }

            // Check 4: Section conflict (same section two courses at same time)
            if (section != null) {
                result.totalChecks++;
                String key = section.getId() + "|" + timeKey;
                if (sectionTimeUsage.containsKey(key)) {
                    result.sectionConflicts++;
                    rowConflict = true;
                    reasons.add("Section " + section.getSectionCode()
                        + " has 2 courses at " + ts.getDay() + " " + ts.getStartTime());
                } else {
                    sectionTimeUsage.put(key, String.valueOf(o.getId()));
                    result.passedChecks++;
                }
            }

            Map<String, Object> entry = new HashMap<>();
            entry.put("offeringId", o.getId() != null ? o.getId().toString() : "");
            entry.put("sectionId", section.getId().toString());
            entry.put("courseCode", course.getCourseCode());
            entry.put("courseName", course.getCourseName());
            entry.put("section", section.getSectionCode());
            entry.put("program", section.getProgram());
            entry.put("day", ts.getDay());
            entry.put("time", ts.getStartTime() + " - " + ts.getEndTime());
            entry.put("room", room != null ? room.getRoomCode() : "N/A");
            entry.put("faculty", fac != null 
                ? fac.getFirstName() + " " + fac.getLastName() 
                : "Unassigned");
            entry.put("isConflictFree", !rowConflict);
            entry.put("conflictReason", reasons.isEmpty() ? null : String.join(" | ", reasons));
            entry.put("semester", o.getSemester());
            entry.put("academicYear", o.getAcademicYear());

            result.entries.add(entry);
        }

        result.executionTimeMs = System.currentTimeMillis() - start;
        log.info("✔️ Validation: {}/{} passed ({}%)",
            result.passedChecks, result.totalChecks,
            Math.round(result.hardConstraintRate() * 100.0) / 100.0);
        log.info("   Faculty conflicts: {}", result.facultyConflicts);
        log.info("   Room conflicts: {}", result.roomConflicts);
        log.info("   Capacity violations: {}", result.capacityViolations);
        log.info("   Section conflicts: {}", result.sectionConflicts);

        return result;
    }
}