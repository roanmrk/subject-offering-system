package com.earist.ccs.scheduler.service;

import com.earist.ccs.scheduler.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;

/**
 * Module 5: Backtracking Validation (block-aware + fully-assigned + workload)
 *
 * Verifies hard constraints after scheduling:
 *   1. Faculty double-booking (block-aware)
 *   2. Room double-booking (block-aware)
 *   3. Room capacity
 *   4. Section double-booking (block-aware)
 *   5. NEW: Offering must be fully assigned (faculty + room + timeslot)
 *   6. NEW: Faculty workload must not exceed maxLoadUnits
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
        public int unassignedOfferings = 0;   // NEW
        public int workloadViolations = 0;    // NEW
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

        log.info("✔️ [MODULE 5] BACKTRACKING VALIDATION (block-aware + full-assign + workload)");
        log.info("   Validating {} offerings", offerings.size());

        // Track usage: (resource_id + timeKey) → first offering id
        Map<String, String> facultyTimeUsage = new HashMap<>();
        Map<String, String> roomTimeUsage = new HashMap<>();
        Map<String, String> sectionTimeUsage = new HashMap<>();

        // Track faculty workload (units)
        Map<Long, Integer> facultyUnits = new HashMap<>();
        Map<Long, Integer> facultyMaxUnits = new HashMap<>();
        for (Faculty f : faculty) {
            if (f.getId() != null) {
                facultyUnits.put(f.getId(), 0);
                facultyMaxUnits.put(f.getId(),
                    f.getMaxLoadUnits() != null ? f.getMaxLoadUnits() : 24);
            }
        }

        // First pass: accumulate faculty units so we can check workload
        for (SubjectOffering o : offerings) {
            if (o.getFaculty() != null && o.getCourse() != null) {
                int units = o.getCourse().getUnits() != null ? o.getCourse().getUnits() : 3;
                facultyUnits.merge(o.getFaculty().getId(), units, Integer::sum);
            }
        }

        for (SubjectOffering o : offerings) {
            // ============================================================
            // NEW CHECK 5: Offering must be fully assigned
            // ============================================================
            result.totalChecks++;
            boolean fullyAssigned = o.getSection() != null
                && o.getCourse() != null
                && o.getTimeslot() != null
                && o.getFaculty() != null
                && o.getRoom() != null;

            if (!fullyAssigned) {
                result.unassignedOfferings++;

                // Still add to entries so the UI shows it, but marked as conflict
                Map<String, Object> entry = new HashMap<>();
                entry.put("offeringId", o.getId() != null ? o.getId().toString() : "");
                entry.put("sectionId", o.getSection() != null
                    ? o.getSection().getId().toString() : "");
                entry.put("courseCode", o.getCourse() != null
                    ? o.getCourse().getCourseCode() : "N/A");
                entry.put("courseName", o.getCourse() != null
                    ? o.getCourse().getCourseName() : "N/A");
                entry.put("section", o.getSection() != null
                    ? o.getSection().getSectionCode() : "N/A");
                entry.put("program", o.getSection() != null
                    ? o.getSection().getProgram() : "N/A");
                entry.put("day", o.getTimeslot() != null
                    ? o.getTimeslot().getDay() : "TBA");
                entry.put("time", formatTimeRange(o));
                entry.put("room", o.getRoom() != null
                    ? o.getRoom().getRoomCode() : "TBA");
                entry.put("faculty", o.getFaculty() != null
                    ? o.getFaculty().getFirstName() + " " + o.getFaculty().getLastName()
                    : "Unassigned");
                entry.put("isConflictFree", false);
                entry.put("conflictReason", buildUnassignedReason(o));
                entry.put("semester", o.getSemester());
                entry.put("academicYear", o.getAcademicYear());
                entry.put("durationHours", o.getCourse() != null
                    && o.getCourse().getDurationHours() != null
                    ? o.getCourse().getDurationHours() : 1);

                result.entries.add(entry);
                continue; // skip remaining checks for this offering
            }

            result.passedChecks++;

            // From here on, we know o.getSection(), getCourse(), getTimeslot(),
            // getFaculty(), getRoom() are all non-null.
            Section section = o.getSection();
            Course course = o.getCourse();
            Timeslot ts = o.getTimeslot();
            Faculty fac = o.getFaculty();
            Room room = o.getRoom();

            int duration = course.getDurationHours() != null
                ? course.getDurationHours() : 1;
            duration = Math.max(1, Math.min(3, duration));

            List<String> blockKeys = new ArrayList<>();
            LocalTime t = ts.getStartTime();
            for (int i = 0; i < duration; i++) {
                blockKeys.add(ts.getDay() + "|" + t);
                t = t.plusHours(1);
            }
            String primaryKey = blockKeys.get(0);

            boolean rowConflict = false;
            List<String> reasons = new ArrayList<>();

            // ============================================================
            // CHECK 1: Faculty conflict
            // ============================================================
            for (String bk : blockKeys) {
                result.totalChecks++;
                String key = fac.getId() + "|" + bk;
                if (facultyTimeUsage.containsKey(key)) {
                    result.facultyConflicts++;
                    rowConflict = true;
                    reasons.add("Faculty " + fac.getFirstName() + " " + fac.getLastName()
                        + " already booked at " + formatKey(bk));
                    break;
                } else {
                    facultyTimeUsage.put(key, String.valueOf(o.getId()));
                    result.passedChecks++;
                }
            }

            // ============================================================
            // CHECK 2: Room conflict
            // ============================================================
            if (!rowConflict) {
                for (String bk : blockKeys) {
                    result.totalChecks++;
                    String key = room.getRoomCode() + "|" + bk;
                    if (roomTimeUsage.containsKey(key)) {
                        result.roomConflicts++;
                        rowConflict = true;
                        reasons.add("Room " + room.getRoomCode()
                            + " already booked at " + formatKey(bk));
                        break;
                    } else {
                        roomTimeUsage.put(key, String.valueOf(o.getId()));
                        result.passedChecks++;
                    }
                }
            }

            // ============================================================
            // CHECK 3: Room capacity
            // ============================================================
            if (!rowConflict && section.getExpectedEnrollment() != null
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

            // ============================================================
            // CHECK 4: Section conflict
            // ============================================================
            if (!rowConflict) {
                boolean sectionClash = false;
                for (String bk : blockKeys) {
                    result.totalChecks++;
                    String key = section.getId() + "|" + bk;
                    if (sectionTimeUsage.containsKey(key)) {
                        sectionClash = true;
                        break;
                    }
                    sectionTimeUsage.put(key, String.valueOf(o.getId()));
                    result.passedChecks++;
                }
                if (sectionClash) {
                    result.sectionConflicts++;
                    rowConflict = true;
                    reasons.add("Section " + section.getSectionCode()
                        + " has 2 courses at " + formatKey(primaryKey));
                }
            }

            // ============================================================
            // NEW CHECK 6: Faculty workload (max load units)
            // ============================================================
            if (!rowConflict) {
                result.totalChecks++;
                int load = facultyUnits.getOrDefault(fac.getId(), 0);
                int max = facultyMaxUnits.getOrDefault(fac.getId(), 24);
                if (load > max) {
                    result.workloadViolations++;
                    rowConflict = true;
                    reasons.add("Faculty " + fac.getFirstName() + " " + fac.getLastName()
                        + " exceeds max load (" + load + " > " + max + " units)");
                } else {
                    result.passedChecks++;
                }
            }

            // ============================================================
            // Build entry
            // ============================================================
            LocalTime blockStart = ts.getStartTime();
            LocalTime blockEnd = blockStart.plusHours(duration);

            Map<String, Object> entry = new HashMap<>();
            entry.put("offeringId", o.getId() != null ? o.getId().toString() : "");
            entry.put("sectionId", section.getId().toString());
            entry.put("courseCode", course.getCourseCode());
            entry.put("courseName", course.getCourseName());
            entry.put("section", section.getSectionCode());
            entry.put("program", section.getProgram());
            entry.put("day", ts.getDay());
            entry.put("time", formatTime(blockStart) + " - " + formatTime(blockEnd));
            entry.put("room", room.getRoomCode());
            entry.put("faculty", fac.getFirstName() + " " + fac.getLastName());
            entry.put("isConflictFree", !rowConflict);
            entry.put("conflictReason", reasons.isEmpty() ? null : String.join(" | ", reasons));
            entry.put("semester", o.getSemester());
            entry.put("academicYear", o.getAcademicYear());
            entry.put("durationHours", duration);

            result.entries.add(entry);
        }

        result.executionTimeMs = System.currentTimeMillis() - start;
        log.info("✔️ Validation: {}/{} passed ({}%)",
            result.passedChecks, result.totalChecks,
            Math.round(result.hardConstraintRate() * 100.0) / 100.0);
        log.info("   Faculty conflicts:  {}", result.facultyConflicts);
        log.info("   Room conflicts:     {}", result.roomConflicts);
        log.info("   Capacity viols:     {}", result.capacityViolations);
        log.info("   Section conflicts:  {}", result.sectionConflicts);
        log.info("   Unassigned:         {}", result.unassignedOfferings);
        log.info("   Workload viols:     {}", result.workloadViolations);

        return result;
    }

    private String buildUnassignedReason(SubjectOffering o) {
        List<String> missing = new ArrayList<>();
        if (o.getTimeslot() == null) missing.add("timeslot");
        if (o.getFaculty() == null) missing.add("faculty");
        if (o.getRoom() == null) missing.add("room");
        return missing.isEmpty() ? "Unassigned" : "Missing: " + String.join(", ", missing);
    }

    private String formatTimeRange(SubjectOffering o) {
        if (o.getTimeslot() == null) return "TBA";
        int dur = o.getCourse() != null && o.getCourse().getDurationHours() != null
            ? o.getCourse().getDurationHours() : 1;
        LocalTime start = o.getTimeslot().getStartTime();
        LocalTime end = start.plusHours(dur);
        return formatTime(start) + " - " + formatTime(end);
    }

    private String formatTime(LocalTime t) {
        int hour = t.getHour();
        String suffix = hour < 12 ? "AM" : "PM";
        int displayHour = hour % 12;
        if (displayHour == 0) displayHour = 12;
        return String.format("%d:%02d %s", displayHour, t.getMinute(), suffix);
    }

    private String formatKey(String key) {
        int idx = key.indexOf('|');
        if (idx < 0) return key;
        return key.substring(0, idx) + " " + key.substring(idx + 1);
    }
}