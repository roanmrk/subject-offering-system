package com.earist.ccs.scheduler.service;

import com.earist.ccs.scheduler.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Module 1: Data Preprocessing
 * Validates and cleans input data before running the algorithm pipeline.
 * Produces a validation report that can be shown to the panel.
 */
@Service
@Slf4j
public class DataPreprocessingService {

    public static class ValidationReport {
        public int totalCourses = 0;
        public int validCourses = 0;
        public int rejectedCourses = 0;
        public List<String> courseErrors = new ArrayList<>();

        public int totalFaculty = 0;
        public int validFaculty = 0;
        public int rejectedFaculty = 0;
        public List<String> facultyErrors = new ArrayList<>();

        public int totalRooms = 0;
        public int validRooms = 0;
        public int rejectedRooms = 0;
        public List<String> roomErrors = new ArrayList<>();

        public int totalSections = 0;
        public int validSections = 0;
        public int rejectedSections = 0;
        public List<String> sectionErrors = new ArrayList<>();

        public int totalTimeslots = 0;
        public int validTimeslots = 0;
        public int rejectedTimeslots = 0;
        public List<String> timeslotErrors = new ArrayList<>();

        public boolean isReady() {
            return validCourses > 0 && validFaculty > 0
                && validRooms > 0 && validSections > 0
                && validTimeslots > 0;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("courses", Map.of("total", totalCourses, "valid", validCourses, 
                "rejected", rejectedCourses));
            map.put("faculty", Map.of("total", totalFaculty, "valid", validFaculty, 
                "rejected", rejectedFaculty));
            map.put("rooms", Map.of("total", totalRooms, "valid", validRooms, 
                "rejected", rejectedRooms));
            map.put("sections", Map.of("total", totalSections, "valid", validSections, 
                "rejected", rejectedSections));
            map.put("timeslots", Map.of("total", totalTimeslots, "valid", validTimeslots, 
                "rejected", rejectedTimeslots));
            map.put("ready", isReady());
            return map;
        }
    }

    public ValidationReport validate(List<Course> courses, List<Faculty> faculty,
                                      List<Room> rooms, List<Section> sections,
                                      List<Timeslot> timeslots) {
        ValidationReport report = new ValidationReport();

        log.info("═══════════════════════════════════════════");
        log.info("📋 [MODULE 1] DATA PREPROCESSING");
        log.info("═══════════════════════════════════════════");

        // ---- Courses ----
        report.totalCourses = courses.size();
        for (Course c : courses) {
            boolean ok = true;
            if (c.getCourseCode() == null || c.getCourseCode().isBlank()) {
                report.courseErrors.add("Course id " + c.getId() + " missing courseCode");
                ok = false;
            }
            if (c.getCourseName() == null || c.getCourseName().isBlank()) {
                report.courseErrors.add("Course " + c.getCourseCode() + " missing name");
                ok = false;
            }
            if (c.getProgram() == null || c.getProgram().isBlank()) {
                report.courseErrors.add("Course " + c.getCourseCode() + " missing program");
                ok = false;
            }
            if (c.getYearLevel() == null || c.getYearLevel() < 1 || c.getYearLevel() > 6) {
                report.courseErrors.add("Course " + c.getCourseCode() + " invalid yearLevel");
                ok = false;
            }
            if (c.getUnits() == null || c.getUnits() < 1 || c.getUnits() > 10) {
                report.courseErrors.add("Course " + c.getCourseCode() + " invalid units");
                ok = false;
            }
            if (ok) report.validCourses++; else report.rejectedCourses++;
        }
        log.info("📚 Courses: {} total, {} valid, {} rejected", 
            report.totalCourses, report.validCourses, report.rejectedCourses);

        // ---- Faculty ----
        report.totalFaculty = faculty.size();
        for (Faculty f : faculty) {
            boolean ok = true;
            if (f.getFacultyId() == null || f.getFacultyId().isBlank()) {
                report.facultyErrors.add("Faculty id " + f.getId() + " missing facultyId");
                ok = false;
            }
            if (f.getFirstName() == null || f.getFirstName().isBlank()) {
                report.facultyErrors.add("Faculty " + f.getFacultyId() + " missing firstName");
                ok = false;
            }
            if (f.getLastName() == null || f.getLastName().isBlank()) {
                report.facultyErrors.add("Faculty " + f.getFacultyId() + " missing lastName");
                ok = false;
            }
            if (f.getSpecialization() == null || f.getSpecialization().isBlank()) {
                report.facultyErrors.add("Faculty " + f.getFacultyId() + " missing specialization");
                ok = false;
            }
            if (ok) report.validFaculty++; else report.rejectedFaculty++;
        }
        log.info("👨‍🏫 Faculty: {} total, {} valid, {} rejected", 
            report.totalFaculty, report.validFaculty, report.rejectedFaculty);

        // ---- Rooms ----
        report.totalRooms = rooms.size();
        for (Room r : rooms) {
            boolean ok = true;
            if (r.getRoomCode() == null || r.getRoomCode().isBlank()) {
                report.roomErrors.add("Room id " + r.getId() + " missing roomCode");
                ok = false;
            }
            if (r.getRoomType() == null || r.getRoomType().isBlank()) {
                report.roomErrors.add("Room " + r.getRoomCode() + " missing roomType");
                ok = false;
            }
            if (r.getCapacity() == null || r.getCapacity() < 1) {
                report.roomErrors.add("Room " + r.getRoomCode() + " invalid capacity");
                ok = false;
            }
            if (ok) report.validRooms++; else report.rejectedRooms++;
        }
        log.info("🏢 Rooms: {} total, {} valid, {} rejected", 
            report.totalRooms, report.validRooms, report.rejectedRooms);

        // ---- Sections ----
        report.totalSections = sections.size();
        for (Section s : sections) {
            boolean ok = true;
            if (s.getSectionCode() == null || s.getSectionCode().isBlank()) {
                report.sectionErrors.add("Section id " + s.getId() + " missing sectionCode");
                ok = false;
            }
            if (s.getProgram() == null || s.getProgram().isBlank()) {
                report.sectionErrors.add("Section " + s.getSectionCode() + " missing program");
                ok = false;
            }
            if (s.getYearLevel() == null || s.getYearLevel() < 1 || s.getYearLevel() > 6) {
                report.sectionErrors.add("Section " + s.getSectionCode() + " invalid yearLevel");
                ok = false;
            }
            if (ok) report.validSections++; else report.rejectedSections++;
        }
        log.info("📦 Sections: {} total, {} valid, {} rejected", 
            report.totalSections, report.validSections, report.rejectedSections);

        // ---- Timeslots ----
        report.totalTimeslots = timeslots.size();
        for (Timeslot t : timeslots) {
            boolean ok = true;
            if (t.getDay() == null || t.getDay().isBlank()) {
                report.timeslotErrors.add("Timeslot id " + t.getId() + " missing day");
                ok = false;
            }
            if (t.getStartTime() == null || t.getEndTime() == null) {
                report.timeslotErrors.add("Timeslot " + t.getSlotCode() + " missing time");
                ok = false;
            }
            if (ok) report.validTimeslots++; else report.rejectedTimeslots++;
        }
        log.info("⏰ Timeslots: {} total, {} valid, {} rejected", 
            report.totalTimeslots, report.validTimeslots, report.rejectedTimeslots);

        log.info("✅ Data preprocessing complete. Ready: {}", report.isReady());
        log.info("═══════════════════════════════════════════");

        return report;
    }
}