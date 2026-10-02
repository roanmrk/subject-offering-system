package com.earist.ccs.scheduler.service;

import com.earist.ccs.scheduler.model.*;
import com.earist.ccs.scheduler.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ScheduleService {

    @Autowired private CourseRepository courseRepository;
    @Autowired private FacultyRepository facultyRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private TimeslotRepository timeslotRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private SubjectOfferingRepository subjectOfferingRepository;

    @Autowired private DataPreprocessingService preprocessingService;
    @Autowired private DecisionTreeService decisionTreeService;
    @Autowired private GraphColoringService graphColoringService;
    @Autowired private GeneticAlgorithmService geneticAlgorithmService;
    @Autowired private BacktrackingService backtrackingService;

    // ============================================================
    // GENERATE SCHEDULE
    // ============================================================
    public Map<String, Object> generateSchedule(String semester, String academicYear) {
        return generateSchedule(semester, academicYear, 0L);
    }

    public Map<String, Object> generateSchedule(String semester, String academicYear, long seed) {
        Map<String, Object> result = new HashMap<>();
        long totalStart = System.currentTimeMillis();

        Map<String, Object> metrics = new LinkedHashMap<>();
        Map<String, Long> timings = new LinkedHashMap<>();
        timings.put("seedUsed", seed);

        try {
            List<Faculty> allFaculty = facultyRepository.findAll();
            List<Room> rooms = roomRepository.findAll();
            List<Timeslot> timeSlots = timeslotRepository.findAll();
            List<Section> sections = sectionRepository.findAll();
            List<Course> courses = courseRepository.findAll();

            log.info("═══════════════════════════════════════════");
            log.info("🚀 HYBRID SCHEDULING PIPELINE (block-aware)");
            log.info("   Semester: {} {}", semester, academicYear);
            log.info("   Sections: {} | Faculty: {} | Rooms: {} | Slots: {}",
                sections.size(), allFaculty.size(), rooms.size(), timeSlots.size());
            log.info("   Seed: {}", seed == 0L ? "deterministic" : String.valueOf(seed));
            log.info("═══════════════════════════════════════════");

            // ── MODULE 1: PREPROCESSING ─────────────────────────────
            long t1 = System.currentTimeMillis();
            DataPreprocessingService.ValidationReport prep =
                preprocessingService.validate(courses, allFaculty, rooms, sections, timeSlots);
            timings.put("preprocessingMs", System.currentTimeMillis() - t1);

            if (!prep.isReady()) {
                result.put("success", false);
                result.put("error", "Data validation failed");
                return result;
            }

            // ── MODULE 2: DECISION TREE + OFFERING BUILD ────────────
            long t2 = System.currentTimeMillis();
            List<SubjectOffering> offerings = buildOfferings(sections, courses, semester, academicYear);

            Map<String, Map<String, Object>> prereqReports = new LinkedHashMap<>();
            Map<Long, List<Course>> coursesBySectionId = new HashMap<>();
            for (SubjectOffering o : offerings) {
                coursesBySectionId.computeIfAbsent(o.getSection().getId(), k -> new ArrayList<>())
                    .add(o.getCourse());
            }
            for (Section s : sections) {
                List<Course> sc = coursesBySectionId.getOrDefault(s.getId(), new ArrayList<>());
                prereqReports.put(s.getSectionCode(), decisionTreeService.verifyPrerequisites(s, sc));
            }
            timings.put("decisionTreeMs", System.currentTimeMillis() - t2);
            log.info("🌳 [MODULE 2] DT: {} offerings built", offerings.size());

            // ── MODULE 3: GRAPH COLORING (availability-aware) ───────
            long t3 = System.currentTimeMillis();
            GraphColoringService.ConflictGraph graph =
                graphColoringService.buildConflictGraph(offerings, allFaculty, decisionTreeService);
            GraphColoringService.ColoringResult coloring =
                graphColoringService.greedyColor(graph, timeSlots, seed, allFaculty, decisionTreeService);
            timings.put("graphColoringMs", System.currentTimeMillis() - t3);
            log.info("🎨 [MODULE 3] GC: {} vertices, {} edges, chromatic={}",
                coloring.vertexCount, coloring.edgeCount, coloring.chromaticNumber);

            // Apply coloring → set STARTING timeslot for each offering
            Map<String, SubjectOffering> byId = new HashMap<>();
            for (SubjectOffering o : offerings) {
                byId.put(GraphColoringService.offeringId(o), o);
            }
            for (Map.Entry<String, Integer> e : coloring.coloring.entrySet()) {
                SubjectOffering o = byId.get(e.getKey());
                if (o != null && e.getValue() < timeSlots.size()) {
                    o.setTimeslot(timeSlots.get(e.getValue()));
                }
            }

            assignFacultyAndRooms(offerings, allFaculty, rooms);

            int assignedBefore = countFullyAssigned(offerings);

            // ── MODULE 4: GENETIC ALGORITHM (advisory) ─────────────
            long t4 = System.currentTimeMillis();
            geneticAlgorithmService.initialize(timeSlots, rooms, allFaculty, sections, seed);
            GeneticAlgorithmService.GAResult gaResult =
                geneticAlgorithmService.optimize(coloring.coloring, seed);
            timings.put("geneticAlgorithmMs", System.currentTimeMillis() - t4);
            log.info("🧬 [MODULE 4] GA: fitness={}, gens={}",
                Math.round(gaResult.best.fitness * 10000.0) / 10000.0, gaResult.generationsRun);

            // Always attempt to apply GA output, but ONLY IF it does not
            // reduce the number of fully assigned offerings (safe apply).
            boolean gaApplied = false;
            if (gaResult.best != null && gaResult.best.timeAssignment != null
                && !gaResult.best.timeAssignment.isEmpty()) {

                // Snapshot for rollback
                List<SubjectOffering> snapshot = snapshotOfferings(offerings);

                applyGAOptimization(offerings, gaResult.best, timeSlots, rooms, allFaculty);

                int assignedAfter = countFullyAssigned(offerings);
                if (assignedAfter >= assignedBefore) {
                    gaApplied = true;
                    log.info("🧬 [MODULE 4] GA output APPLIED ({} → {} fully assigned)",
                        assignedBefore, assignedAfter);
                } else {
                    log.warn("🧬 [MODULE 4] GA output REJECTED ({} → {} fully assigned). Rolling back.",
                        assignedBefore, assignedAfter);
                    restoreOfferings(offerings, snapshot);
                }
            } else {
                log.info("🧬 [MODULE 4] GA produced no output — skipping apply");
            }

            // ── MODULE 4.5: REPAIR ─────────────────────────────────
            long t45 = System.currentTimeMillis();
            int repaired = repairGAConflicts(offerings, timeSlots, rooms, allFaculty, sections);
            timings.put("repairMs", System.currentTimeMillis() - t45);
            log.info("🔧 [MODULE 4.5] Repaired {} conflicts", repaired);

            // ── MODULE 5: BACKTRACKING VALIDATION ──────────────────
            long t5 = System.currentTimeMillis();
            BacktrackingService.ValidationResult validation =
                backtrackingService.validateAndFinalize(offerings, timeSlots, rooms,
                    allFaculty, sections);
            timings.put("backtrackingMs", System.currentTimeMillis() - t5);

            saveOfferings(offerings, semester, academicYear);

            long totalEnd = System.currentTimeMillis();
            timings.put("totalMs", totalEnd - totalStart);

            metrics.put("cfsr", calculateCFSR(validation));
            metrics.put("fwor", calculateFWOR(offerings));
            metrics.put("rur", calculateRUR(offerings, rooms));
            metrics.put("hardConstraintRate", validation.hardConstraintRate());
            int totalConflicts = validation.facultyConflicts + validation.roomConflicts
                + validation.capacityViolations + validation.sectionConflicts
                + validation.unassignedOfferings + validation.workloadViolations;
            metrics.put("conflictCount", totalConflicts);
            metrics.put("facultyConflicts", validation.facultyConflicts);
            metrics.put("roomConflicts", validation.roomConflicts);
            metrics.put("capacityViolations", validation.capacityViolations);
            metrics.put("sectionConflicts", validation.sectionConflicts);
            metrics.put("unassignedOfferings", validation.unassignedOfferings);
            metrics.put("workloadViolations", validation.workloadViolations);

            Map<String, Long> dayDist = validation.entries.stream()
                .collect(Collectors.groupingBy(e -> (String) e.get("day"),
                    Collectors.counting()));

            log.info("═══════════════════════════════════════════");
            log.info("✅ PIPELINE COMPLETE in {}ms", totalEnd - totalStart);
            log.info("   CFSR: {}%, FWOR: {}%, RUR: {}%",
                metrics.get("cfsr"), metrics.get("fwor"), metrics.get("rur"));
            log.info("   Conflicts: {}", totalConflicts);
            log.info("   Unassigned: {} | Workload viols: {}",
                validation.unassignedOfferings, validation.workloadViolations);
            log.info("   Day distribution: {}", dayDist);
            log.info("═══════════════════════════════════════════");

            result.put("success", true);
            result.put("entries", validation.entries);
            result.put("totalSections", sections.size());
            result.put("scheduledSections", validation.entries.size());
            result.put("conflictCount", totalConflicts);
            result.put("hasConflicts", totalConflicts > 0);
            result.put("semester", semester);
            result.put("academicYear", academicYear);
            result.put("dayDistribution", dayDist);
            result.put("metrics", metrics);
            result.put("timings", timings);
            result.put("generationTimeMs", totalEnd - totalStart);
            result.put("seedUsed", seed);
            result.put("preprocessing", prep.toMap());
            result.put("graphColoring", Map.of(
                "vertexCount", coloring.vertexCount,
                "edgeCount", coloring.edgeCount,
                "chromaticNumber", coloring.chromaticNumber,
                "averageDegree", Math.round(coloring.averageDegree * 100.0) / 100.0
            ));
            result.put("geneticAlgorithm", Map.of(
                "fitness", Math.round(gaResult.best.fitness * 10000.0) / 10000.0,
                "generations", gaResult.generationsRun,
                "applied", gaApplied
            ));
            result.put("prerequisites", prereqReports);

        } catch (Exception e) {
            log.error("Pipeline error", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ============================================================
    // GA APPLY HELPERS (safe apply)
    // ============================================================
    private int countFullyAssigned(List<SubjectOffering> offerings) {
        int n = 0;
        for (SubjectOffering o : offerings) {
            if (o.getTimeslot() != null && o.getFaculty() != null && o.getRoom() != null) n++;
        }
        return n;
    }

    private List<SubjectOffering> snapshotOfferings(List<SubjectOffering> offerings) {
        List<SubjectOffering> snap = new ArrayList<>();
        for (SubjectOffering o : offerings) {
            SubjectOffering copy = new SubjectOffering();
            copy.setId(o.getId());
            copy.setSection(o.getSection());
            copy.setCourse(o.getCourse());
            copy.setFaculty(o.getFaculty());
            copy.setRoom(o.getRoom());
            copy.setTimeslot(o.getTimeslot());
            copy.setSemester(o.getSemester());
            copy.setAcademicYear(o.getAcademicYear());
            copy.setIsConflictFree(o.getIsConflictFree());
            snap.add(copy);
        }
        return snap;
    }

    private void restoreOfferings(List<SubjectOffering> offerings, List<SubjectOffering> snapshot) {
        for (int i = 0; i < offerings.size() && i < snapshot.size(); i++) {
            SubjectOffering dst = offerings.get(i);
            SubjectOffering src = snapshot.get(i);
            dst.setTimeslot(src.getTimeslot());
            dst.setFaculty(src.getFaculty());
            dst.setRoom(src.getRoom());
        }
    }

    // ============================================================
    // REPAIR CONFLICTS
    // ============================================================
    private int repairGAConflicts(List<SubjectOffering> offerings,
                                   List<Timeslot> timeSlots,
                                   List<Room> rooms,
                                   List<Faculty> allFaculty,
                                   List<Section> sections) {
        if (offerings.isEmpty()) return 0;

        Map<String, SubjectOffering> facultyTimeOwner = new HashMap<>();
        Map<String, SubjectOffering> roomTimeOwner = new HashMap<>();
        Map<String, SubjectOffering> sectionTimeOwner = new HashMap<>();

        int repaired = 0;

        offerings.sort(Comparator.comparing(o ->
            o.getSection() != null ? o.getSection().getSectionCode() : ""));

        for (SubjectOffering o : offerings) {
            if (o.getSection() == null || o.getCourse() == null) continue;

            boolean conflict = hasConflict(o, facultyTimeOwner, roomTimeOwner, sectionTimeOwner);

            if (!conflict) {
                registerUsage(o, facultyTimeOwner, roomTimeOwner, sectionTimeOwner);
                continue;
            }

            boolean fixed = tryReassignToSafe(o, timeSlots, rooms, allFaculty,
                facultyTimeOwner, roomTimeOwner, sectionTimeOwner);

            if (fixed) {
                repaired++;
            } else {
                log.warn("⚠️ Unable to repair conflict for section {} / course {}",
                    o.getSection().getSectionCode(),
                    o.getCourse().getCourseCode());
            }
            registerUsage(o, facultyTimeOwner, roomTimeOwner, sectionTimeOwner);
        }

        return repaired;
    }

    private boolean hasConflict(SubjectOffering o,
                                 Map<String, SubjectOffering> facultyTimeOwner,
                                 Map<String, SubjectOffering> roomTimeOwner,
                                 Map<String, SubjectOffering> sectionTimeOwner) {
        if (o.getTimeslot() == null) return false;

        List<String> blockKeys = buildBlockTimeKeys(o);
        for (String timeKey : blockKeys) {
            if (o.getFaculty() != null) {
                String key = o.getFaculty().getId() + "|" + timeKey;
                if (facultyTimeOwner.containsKey(key)) return true;
            }
            if (o.getRoom() != null) {
                String key = o.getRoom().getRoomCode() + "|" + timeKey;
                if (roomTimeOwner.containsKey(key)) return true;
            }
            if (o.getSection() != null) {
                String key = o.getSection().getId() + "|" + timeKey;
                if (sectionTimeOwner.containsKey(key)) return true;
            }
        }
        return false;
    }

    private void registerUsage(SubjectOffering o,
                                Map<String, SubjectOffering> facultyTimeOwner,
                                Map<String, SubjectOffering> roomTimeOwner,
                                Map<String, SubjectOffering> sectionTimeOwner) {
        if (o.getTimeslot() == null) return;

        List<String> blockKeys = buildBlockTimeKeys(o);
        for (String timeKey : blockKeys) {
            if (o.getFaculty() != null) {
                facultyTimeOwner.put(o.getFaculty().getId() + "|" + timeKey, o);
            }
            if (o.getRoom() != null) {
                roomTimeOwner.put(o.getRoom().getRoomCode() + "|" + timeKey, o);
            }
            if (o.getSection() != null) {
                sectionTimeOwner.put(o.getSection().getId() + "|" + timeKey, o);
            }
        }
    }

    private boolean tryReassignToSafe(SubjectOffering o,
                                       List<Timeslot> timeSlots,
                                       List<Room> rooms,
                                       List<Faculty> allFaculty,
                                       Map<String, SubjectOffering> facultyTimeOwner,
                                       Map<String, SubjectOffering> roomTimeOwner,
                                       Map<String, SubjectOffering> sectionTimeOwner) {
        List<Timeslot> shuffledSlots = new ArrayList<>(timeSlots);
        Collections.shuffle(shuffledSlots, new Random(System.nanoTime() + o.hashCode()));

        List<Faculty> qualifiedFaculty = decisionTreeService.matchFacultyToCourse(o.getCourse());
        int duration = o.getCourse().getDurationHours() != null
            ? o.getCourse().getDurationHours() : 3;

        List<Room> candidateRooms = new ArrayList<>(rooms);

        for (Timeslot ts : shuffledSlots) {
            List<String> blockKeys = new ArrayList<>();
            LocalTime t = ts.getStartTime();
            for (int k = 0; k < duration; k++) {
                blockKeys.add(ts.getDay() + "|" + t);
                t = t.plusHours(1);
            }

            // Section must be free across block
            boolean sectionFree = true;
            for (String bk : blockKeys) {
                if (sectionTimeOwner.containsKey(o.getSection().getId() + "|" + bk)) {
                    sectionFree = false; break;
                }
            }
            if (!sectionFree) continue;

            for (Faculty f : qualifiedFaculty) {
                boolean facAvail = true;
                if (f.getAvailableStartTime() != null && f.getAvailableEndTime() != null) {
                    LocalTime cur = ts.getStartTime();
                    for (int k = 0; k < duration; k++) {
                        if (cur.isBefore(f.getAvailableStartTime())
                            || cur.plusHours(1).isAfter(f.getAvailableEndTime())) {
                            facAvail = false; break;
                        }
                        cur = cur.plusHours(1);
                    }
                }
                if (!facAvail) continue;

                boolean facFree = true;
                for (String bk : blockKeys) {
                    if (facultyTimeOwner.containsKey(f.getId() + "|" + bk)) {
                        facFree = false; break;
                    }
                }
                if (!facFree) continue;

                for (Room r : candidateRooms) {
                    boolean roomFree = true;
                    for (String bk : blockKeys) {
                        if (roomTimeOwner.containsKey(r.getRoomCode() + "|" + bk)) {
                            roomFree = false; break;
                        }
                    }
                    if (!roomFree) continue;

                    if (o.getSection().getExpectedEnrollment() != null
                        && r.getCapacity() != null
                        && o.getSection().getExpectedEnrollment() > r.getCapacity()) {
                        continue;
                    }

                    o.setTimeslot(ts);
                    o.setFaculty(f);
                    o.setRoom(r);
                    return true;
                }
            }
        }

        return false;
    }

    // ============================================================
    // APPLY GA OPTIMIZATION
    // ============================================================
    private void applyGAOptimization(List<SubjectOffering> offerings,
                                     GeneticAlgorithmService.Chromosome best,
                                     List<Timeslot> timeSlots,
                                     List<Room> rooms,
                                     List<Faculty> allFaculty) {
        if (best == null) return;

        Map<Long, Room> roomById = new HashMap<>();
        for (Room r : rooms) roomById.put(r.getId(), r);

        Map<Long, Faculty> facultyById = new HashMap<>();
        for (Faculty f : allFaculty) facultyById.put(f.getId(), f);

        for (SubjectOffering o : offerings) {
            if (o.getSection() == null) continue;
            String sid = o.getSection().getId().toString();

            Integer timeIdx = best.timeAssignment.get(sid);
            if (timeIdx != null && timeIdx >= 0 && timeIdx < timeSlots.size()) {
                o.setTimeslot(timeSlots.get(timeIdx));
            }

            String roomIdStr = best.roomAssignment.get(sid);
            if (roomIdStr != null) {
                try {
                    Long rid = Long.parseLong(roomIdStr);
                    Room r = roomById.get(rid);
                    if (r != null) o.setRoom(r);
                } catch (NumberFormatException ignored) {}
            }

            String facIdStr = best.facultyAssignment.get(sid);
            if (facIdStr != null) {
                try {
                    Long fid = Long.parseLong(facIdStr);
                    Faculty f = facultyById.get(fid);
                    if (f != null) o.setFaculty(f);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    // ============================================================
    // COMPARE ALGORITHMS
    // ============================================================
    public Map<String, Object> compareAlgorithms(String semester, String academicYear) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> comparisons = new ArrayList<>();

        try {
            List<Faculty> allFaculty = facultyRepository.findAll();
            List<Room> rooms = roomRepository.findAll();
            List<Timeslot> timeSlots = timeslotRepository.findAll();
            List<Section> sections = sectionRepository.findAll();
            List<Course> courses = courseRepository.findAll();

            long start = System.currentTimeMillis();
            Map<String, Object> greedy = runComparison(
                allFaculty, rooms, timeSlots, sections, courses, "GREEDY");
            greedy.put("algorithm", "Greedy Round-Robin");
            greedy.put("timeMs", System.currentTimeMillis() - start);
            comparisons.add(greedy);

            start = System.currentTimeMillis();
            Map<String, Object> gc = runComparison(
                allFaculty, rooms, timeSlots, sections, courses, "GRAPH");
            gc.put("algorithm", "Graph Coloring (Welsh-Powell)");
            gc.put("timeMs", System.currentTimeMillis() - start);
            comparisons.add(gc);

            start = System.currentTimeMillis();
            Map<String, Object> hybrid = runComparison(
                allFaculty, rooms, timeSlots, sections, courses, "HYBRID");
            hybrid.put("algorithm", "Hybrid (GC + GA + Repair)");
            hybrid.put("timeMs", System.currentTimeMillis() - start);
            comparisons.add(hybrid);

            result.put("success", true);
            result.put("comparisons", comparisons);
            result.put("semester", semester);
            result.put("academicYear", academicYear);
        } catch (Exception e) {
            log.error("Comparison error", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    private Map<String, Object> runComparison(List<Faculty> allFaculty, List<Room> rooms,
                                               List<Timeslot> timeSlots, List<Section> sections,
                                               List<Course> courses, String algorithm) {

        List<SubjectOffering> offerings = buildOfferings(sections, courses, "1st Semester", "compare");

        if ("GREEDY".equals(algorithm)) {
            for (int i = 0; i < offerings.size(); i++) {
                SubjectOffering o = offerings.get(i);
                o.setTimeslot(timeSlots.get(i % timeSlots.size()));
                if (!allFaculty.isEmpty()) o.setFaculty(allFaculty.get(i % allFaculty.size()));
                if (!rooms.isEmpty()) o.setRoom(rooms.get(i % rooms.size()));
            }
        } else if ("GRAPH".equals(algorithm)) {
            GraphColoringService.ConflictGraph graph =
                graphColoringService.buildConflictGraph(offerings, allFaculty, decisionTreeService);
            GraphColoringService.ColoringResult coloring =
                graphColoringService.greedyColor(graph, timeSlots, 0L, allFaculty, decisionTreeService);

            Map<String, SubjectOffering> byId = new HashMap<>();
            for (SubjectOffering o : offerings) {
                byId.put(GraphColoringService.offeringId(o), o);
            }
            for (Map.Entry<String, Integer> e : coloring.coloring.entrySet()) {
                SubjectOffering o = byId.get(e.getKey());
                if (o != null && e.getValue() < timeSlots.size()) {
                    o.setTimeslot(timeSlots.get(e.getValue()));
                }
            }
            assignFacultyAndRooms(offerings, allFaculty, rooms);
        } else if ("HYBRID".equals(algorithm)) {
            GraphColoringService.ConflictGraph graph =
                graphColoringService.buildConflictGraph(offerings, allFaculty, decisionTreeService);
            GraphColoringService.ColoringResult coloring =
                graphColoringService.greedyColor(graph, timeSlots, System.nanoTime(),
                    allFaculty, decisionTreeService);

            Map<String, SubjectOffering> byId = new HashMap<>();
            for (SubjectOffering o : offerings) {
                byId.put(GraphColoringService.offeringId(o), o);
            }
            for (Map.Entry<String, Integer> e : coloring.coloring.entrySet()) {
                SubjectOffering o = byId.get(e.getKey());
                if (o != null && e.getValue() < timeSlots.size()) {
                    o.setTimeslot(timeSlots.get(e.getValue()));
                }
            }
            assignFacultyAndRooms(offerings, allFaculty, rooms);
            repairGAConflicts(offerings, timeSlots, rooms, allFaculty, sections);
        }

        Map<String, SubjectOffering> facultyTimeOwner = new HashMap<>();
        Map<String, SubjectOffering> roomTimeOwner = new HashMap<>();
        Map<String, SubjectOffering> sectionTimeOwner = new HashMap<>();

        int sectionConflicts = 0;
        int facultyConflicts = 0;
        int roomConflicts = 0;
        int unassigned = 0;

        for (SubjectOffering o : offerings) {
            if (o.getTimeslot() == null || o.getSection() == null
                || o.getFaculty() == null || o.getRoom() == null) {
                unassigned++;
                continue;
            }

            List<String> blockKeys = buildBlockTimeKeys(o);
            for (String timeKey : blockKeys) {
                if (o.getFaculty() != null) {
                    String key = o.getFaculty().getId() + "|" + timeKey;
                    if (facultyTimeOwner.containsKey(key)) {
                        facultyConflicts++;
                    } else {
                        facultyTimeOwner.put(key, o);
                    }
                }
                if (o.getRoom() != null) {
                    String key = o.getRoom().getRoomCode() + "|" + timeKey;
                    if (roomTimeOwner.containsKey(key)) {
                        roomConflicts++;
                    } else {
                        roomTimeOwner.put(key, o);
                    }
                }
                String secKey = o.getSection().getId() + "|" + timeKey;
                if (sectionTimeOwner.containsKey(secKey)) {
                    sectionConflicts++;
                } else {
                    sectionTimeOwner.put(secKey, o);
                }
            }
        }

        int totalConflicts = sectionConflicts + facultyConflicts + roomConflicts + unassigned;
        int totalOfferings = offerings.size();
        double cfsr = totalOfferings == 0 ? 100.0 :
            Math.round((1.0 - (double) totalConflicts / totalOfferings) * 10000.0) / 100.0;

        Map<String, Object> result = new HashMap<>();
        result.put("totalOfferings", totalOfferings);
        result.put("conflicts", totalConflicts);
        result.put("sectionConflicts", sectionConflicts);
        result.put("facultyConflicts", facultyConflicts);
        result.put("roomConflicts", roomConflicts);
        result.put("unassigned", unassigned);
        result.put("cfsr", Math.max(0, cfsr));

        return result;
    }

    // ============================================================
    // BUILD OFFERINGS
    // ============================================================
    private List<SubjectOffering> buildOfferings(List<Section> sections,
                                                  List<Course> courses,
                                                  String semester, String academicYear) {

        List<SubjectOffering> offerings = new ArrayList<>();
        int semesterNum = mapSemesterToNumber(semester);

        Map<String, List<Course>> coursesByKey = new HashMap<>();
        for (Course c : courses) {
            if (c.getSemester() == null) continue;
            String key = c.getYearLevel() + "|" + c.getProgram() + "|" + c.getSemester();
            coursesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }

        Map<String, Section> uniqueSections = new LinkedHashMap<>();
        for (Section s : sections) {
            uniqueSections.putIfAbsent(s.getSectionCode(), s);
        }

        Set<String> seen = new HashSet<>();

        for (Section section : uniqueSections.values()) {
            String key = section.getYearLevel() + "|" + section.getProgram() + "|" + semesterNum;
            List<Course> sectionCourses = coursesByKey.getOrDefault(key, new ArrayList<>());

            if (sectionCourses.isEmpty()) {
                log.warn("⚠️ Section {} (Y{} {} Sem{}) has no matching courses",
                    section.getSectionCode(),
                    section.getYearLevel(),
                    section.getProgram(),
                    semesterNum);
                continue;
            }

            sectionCourses = sectionCourses.stream()
                .sorted(Comparator.comparing(Course::getCourseCode))
                .collect(Collectors.toList());

            for (Course course : sectionCourses) {
                String key2 = section.getId() + "-" + course.getId();
                if (seen.contains(key2)) continue;
                seen.add(key2);

                SubjectOffering o = new SubjectOffering();
                o.setSection(section);
                o.setCourse(course);
                o.setSemester(semester);
                o.setAcademicYear(academicYear);
                o.setIsConflictFree(true);
                offerings.add(o);
            }
        }

        log.info("📚 Built {} offerings for {} sections in semester {}",
            offerings.size(), uniqueSections.size(), semesterNum);

        return offerings;
    }

    private int mapSemesterToNumber(String semester) {
        if (semester == null) return 1;
        String s = semester.toLowerCase();
        if (s.contains("2nd") || s.contains("second")) return 2;
        return 1;
    }

    // ============================================================
    // ASSIGN FACULTY AND ROOMS
    // ============================================================
    private void assignFacultyAndRooms(List<SubjectOffering> offerings,
                                       List<Faculty> allFaculty,
                                       List<Room> rooms) {

        offerings.sort(Comparator.comparing(o ->
            o.getTimeslot() != null
                ? o.getTimeslot().getDay() + o.getTimeslot().getStartTime().toString()
                : ""));

        Map<String, Set<String>> facultyBusyTimes = new HashMap<>();
        Map<String, Set<String>> roomBusyTimes = new HashMap<>();
        Map<String, Integer> facultyLoad = new HashMap<>();
        Map<String, Integer> roomUsage = new HashMap<>();

        for (Faculty f : allFaculty) {
            facultyBusyTimes.put(f.getId().toString(), new HashSet<>());
            facultyLoad.put(f.getId().toString(), 0);
        }
        for (Room r : rooms) {
            roomBusyTimes.put(r.getRoomCode(), new HashSet<>());
            roomUsage.put(r.getRoomCode(), 0);
        }

        int unassignedFaculty = 0;
        int unassignedRoom = 0;

        for (SubjectOffering o : offerings) {
            if (o.getTimeslot() == null) continue;
            Section section = o.getSection();
            Course course = o.getCourse();
            int units = course.getUnits() != null ? course.getUnits() : 3;

            List<String> blockKeys = buildBlockTimeKeys(o);

            // FACULTY
            List<Faculty> qualified = decisionTreeService.matchFacultyToCourse(course);
            Faculty assignedFaculty = pickFairestFacultyForBlock(
                qualified, facultyBusyTimes, facultyLoad, blockKeys, units);

            if (assignedFaculty == null) {
                unassignedFaculty++;
                log.warn("⚠️ No qualified+available faculty for {} (section {})",
                    course.getCourseCode(), section.getSectionCode());
            }

            if (assignedFaculty != null) {
                String fid = assignedFaculty.getId().toString();
                for (String bk : blockKeys) {
                    facultyBusyTimes.get(fid).add(bk);
                }
                facultyLoad.merge(fid, units, Integer::sum);
            }
            o.setFaculty(assignedFaculty);

            // ROOM — enforce type for labs
            boolean isLab = Boolean.TRUE.equals(course.getIsLaboratory());
            String preferredType = isLab ? "LABORATORY" : "LECTURE";
            Integer neededCapacity = section.getExpectedEnrollment();

            Room assignedRoom = pickRoomForBlock(
                rooms, preferredType, neededCapacity,
                roomBusyTimes, roomUsage, blockKeys, isLab);

            if (assignedRoom == null) {
                unassignedRoom++;
                log.warn("⚠️ No available room (any type) for {} (section {})",
                    course.getCourseCode(), section.getSectionCode());
            } else {
                for (String bk : blockKeys) {
                    roomBusyTimes.get(assignedRoom.getRoomCode()).add(bk);
                }
                roomUsage.merge(assignedRoom.getRoomCode(), 1, Integer::sum);
            }
            o.setRoom(assignedRoom);
        }

        log.info("📊 Faculty: unassigned={} | Rooms: unassigned={}",
            unassignedFaculty, unassignedRoom);
    }

    private List<String> buildBlockTimeKeys(SubjectOffering o) {
        List<String> keys = new ArrayList<>();
        if (o.getTimeslot() == null) return keys;

        int duration = o.getCourse() != null && o.getCourse().getDurationHours() != null
            ? o.getCourse().getDurationHours() : 1;
        duration = Math.max(1, Math.min(3, duration));

        LocalTime t = o.getTimeslot().getStartTime();
        String day = o.getTimeslot().getDay();
        for (int i = 0; i < duration; i++) {
            keys.add(day + "|" + t);
            t = t.plusHours(1);
        }
        return keys;
    }

    private Faculty pickFairestFacultyForBlock(List<Faculty> candidates,
                                                Map<String, Set<String>> facultyBusyTimes,
                                                Map<String, Integer> facultyLoad,
                                                List<String> blockKeys,
                                                int unitsNeeded) {
        Faculty best = null;
        double bestRatio = Double.MAX_VALUE;

        for (Faculty f : candidates) {
            String fid = f.getId().toString();
            int maxLoad = f.getMaxLoadUnits() != null ? f.getMaxLoadUnits() : 24;
            int currentLoad = facultyLoad.getOrDefault(fid, 0);
            Set<String> busy = facultyBusyTimes.get(fid);

            if (busy == null) continue;
            if (currentLoad + unitsNeeded > maxLoad) continue;

            boolean allFree = true;
            for (String bk : blockKeys) {
                if (busy.contains(bk)) { allFree = false; break; }
            }
            if (!allFree) continue;

            boolean availOK = true;
            if (f.getAvailableStartTime() != null && f.getAvailableEndTime() != null) {
                for (String bk : blockKeys) {
                    LocalTime t = extractSlotStart(bk);
                    if (t == null) continue;
                    if (t.isBefore(f.getAvailableStartTime())
                        || t.plusHours(1).isAfter(f.getAvailableEndTime())) {
                        availOK = false;
                        break;
                    }
                }
            }
            if (!availOK) continue;

            double ratio = (double) currentLoad / maxLoad;
            if (ratio < bestRatio
                || (ratio == bestRatio && best != null
                    && currentLoad < facultyLoad.getOrDefault(best.getId().toString(), 0))) {
                bestRatio = ratio;
                best = f;
            }
        }
        return best;
    }

    private LocalTime extractSlotStart(String timeKey) {
        if (timeKey == null) return null;
        int pipeIdx = timeKey.indexOf('|');
        if (pipeIdx < 0) return null;
        String timePart = timeKey.substring(pipeIdx + 1).trim();
        try {
            if (timePart.length() == 5) {
                return LocalTime.parse(timePart + ":00");
            }
            return LocalTime.parse(timePart);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Pick room free for ALL blockKeys.
     *
     * If isLab == true, we ONLY return LABORATORY rooms. If none are free,
     * we return null rather than letting a lab land in a lecture hall.
     */
    private Room pickRoomForBlock(List<Room> rooms,
                                   String preferredType,
                                   Integer neededCapacity,
                                   Map<String, Set<String>> roomBusyTimes,
                                   Map<String, Integer> roomUsage,
                                   List<String> blockKeys,
                                   boolean isLab) {

        // Pass 1: preferred type + capacity
        List<Room> pass1 = new ArrayList<>();
        for (Room r : rooms) {
            if (!preferredType.equalsIgnoreCase(r.getRoomType())) continue;
            if (neededCapacity != null && r.getCapacity() != null
                && neededCapacity > r.getCapacity()) continue;
            Set<String> busy = roomBusyTimes.get(r.getRoomCode());
            if (busy == null) continue;
            boolean allFree = true;
            for (String bk : blockKeys) {
                if (busy.contains(bk)) { allFree = false; break; }
            }
            if (allFree) pass1.add(r);
        }
        if (!pass1.isEmpty()) {
            pass1.sort(Comparator.comparingInt(r -> roomUsage.getOrDefault(r.getRoomCode(), 0)));
            return pass1.get(0);
        }

        // Pass 2: preferred type, ignore capacity
        List<Room> pass2 = new ArrayList<>();
        for (Room r : rooms) {
            if (!preferredType.equalsIgnoreCase(r.getRoomType())) continue;
            Set<String> busy = roomBusyTimes.get(r.getRoomCode());
            if (busy == null) continue;
            boolean allFree = true;
            for (String bk : blockKeys) {
                if (busy.contains(bk)) { allFree = false; break; }
            }
            if (allFree) pass2.add(r);
        }
        if (!pass2.isEmpty()) {
            pass2.sort(Comparator.comparingInt(r -> roomUsage.getOrDefault(r.getRoomCode(), 0)));
            return pass2.get(0);
        }

        // For labs, do NOT fall back to lecture rooms. Return null → unassigned.
        if (isLab) {
            return null;
        }

        // Pass 3: any type + capacity
        List<Room> pass3 = new ArrayList<>();
        for (Room r : rooms) {
            if (neededCapacity != null && r.getCapacity() != null
                && neededCapacity > r.getCapacity()) continue;
            Set<String> busy = roomBusyTimes.get(r.getRoomCode());
            if (busy == null) continue;
            boolean allFree = true;
            for (String bk : blockKeys) {
                if (busy.contains(bk)) { allFree = false; break; }
            }
            if (allFree) pass3.add(r);
        }
        if (!pass3.isEmpty()) {
            pass3.sort(Comparator.comparingInt(r -> roomUsage.getOrDefault(r.getRoomCode(), 0)));
            return pass3.get(0);
        }

        // Pass 4: any type, ignore capacity
        List<Room> pass4 = new ArrayList<>();
        for (Room r : rooms) {
            Set<String> busy = roomBusyTimes.get(r.getRoomCode());
            if (busy == null) continue;
            boolean allFree = true;
            for (String bk : blockKeys) {
                if (busy.contains(bk)) { allFree = false; break; }
            }
            if (allFree) pass4.add(r);
        }
        if (!pass4.isEmpty()) {
            pass4.sort(Comparator.comparingInt(r -> roomUsage.getOrDefault(r.getRoomCode(), 0)));
            return pass4.get(0);
        }

        return null;
    }

    // ============================================================
    // SAVE OFFERINGS
    // ============================================================
    private void saveOfferings(List<SubjectOffering> offerings,
                                String semester, String academicYear) {
        List<SubjectOffering> existing =
            subjectOfferingRepository.findBySemesterAndAcademicYear(semester, academicYear);
        if (!existing.isEmpty()) {
            subjectOfferingRepository.deleteAll(existing);
            subjectOfferingRepository.flush();
        }

        int saved = 0, skipped = 0;
        Set<String> seen = new HashSet<>();
        for (SubjectOffering o : offerings) {
            if (o.getSection() == null || o.getCourse() == null) continue;
            String key = o.getSection().getId() + "-" + o.getCourse().getId();
            if (seen.contains(key)) { skipped++; continue; }
            seen.add(key);
            try {
                subjectOfferingRepository.save(o);
                saved++;
            } catch (Exception e) {
                skipped++;
            }
        }
        subjectOfferingRepository.flush();
        log.info("💾 Saved {} offerings (skipped {})", saved, skipped);
    }

    // ============================================================
    // METRICS
    // ============================================================
    private double calculateCFSR(BacktrackingService.ValidationResult v) {
        if (v.entries.isEmpty()) return 0;
        long clean = v.entries.stream()
            .filter(e -> Boolean.TRUE.equals(e.get("isConflictFree")))
            .count();
        return Math.round((double) clean / v.entries.size() * 10000.0) / 100.0;
    }

    private double calculateFWOR(List<SubjectOffering> offerings) {
        Map<String, Integer> load = new HashMap<>();
        for (SubjectOffering o : offerings) {
            if (o.getFaculty() != null) {
                int units = o.getCourse().getUnits() != null ? o.getCourse().getUnits() : 3;
                load.merge(o.getFaculty().getId().toString(), units, Integer::sum);
            }
        }
        if (load.isEmpty()) return 0;
        double avg = load.values().stream().mapToInt(i -> i).average().orElse(0);
        if (avg == 0) return 0;
        double variance = load.values().stream()
            .mapToDouble(v -> Math.pow(v - avg, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        return Math.round(Math.max(0, (1 - (stdDev / avg)) * 100) * 100.0) / 100.0;
    }

    private double calculateRUR(List<SubjectOffering> offerings, List<Room> rooms) {
        if (rooms.isEmpty()) return 0;
        long used = offerings.stream()
            .filter(o -> o.getRoom() != null)
            .map(o -> o.getRoom().getRoomCode())
            .distinct()
            .count();
        return Math.round((double) used / rooms.size() * 10000.0) / 100.0;
    }

    // ============================================================
    // VIEW SCHEDULE
    // ============================================================
    public Map<String, Object> viewSchedule(String semester, String academicYear) {
        List<SubjectOffering> offerings =
            subjectOfferingRepository.findBySemesterAndAcademicYear(semester, academicYear);

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> entries = new ArrayList<>();

        for (SubjectOffering off : offerings) {
            if (off.getSection() == null || off.getCourse() == null) continue;
            Map<String, Object> e = new HashMap<>();
            e.put("offeringId", off.getId() != null ? off.getId().toString() : "");
            e.put("sectionId", off.getSection().getId().toString());
            e.put("courseCode", off.getCourse().getCourseCode());
            e.put("courseName", off.getCourse().getCourseName());
            e.put("section", off.getSection().getSectionCode());
            e.put("program", off.getSection().getProgram());
            e.put("day", off.getTimeslot() != null ? off.getTimeslot().getDay() : "TBA");

            int dur = off.getCourse().getDurationHours() != null
                ? off.getCourse().getDurationHours() : 3;
            String timeDisplay = "TBA";
            if (off.getTimeslot() != null) {
                LocalTime start = off.getTimeslot().getStartTime();
                LocalTime end = start.plusHours(dur);
                timeDisplay = formatTime(start) + " - " + formatTime(end);
            }
            e.put("time", timeDisplay);

            e.put("room", off.getRoom() != null ? off.getRoom().getRoomCode() : "TBA");
            e.put("faculty", off.getFaculty() != null
                ? off.getFaculty().getFirstName() + " " + off.getFaculty().getLastName()
                : "Unassigned");
            e.put("isConflictFree", off.getIsConflictFree());
            e.put("units", off.getCourse().getUnits() != null ? off.getCourse().getUnits() : 3);
            e.put("durationHours", off.getCourse().getDurationHours() != null
                ? off.getCourse().getDurationHours() : 3);
            entries.add(e);
        }

        long conflicts = entries.stream()
            .filter(e -> !(Boolean) e.get("isConflictFree")).count();

        result.put("success", true);
        result.put("entries", entries);
        result.put("totalSections", entries.size());
        result.put("scheduledSections", entries.size());
        result.put("conflictCount", (int) conflicts);
        result.put("hasConflicts", conflicts > 0);
        result.put("semester", semester);
        result.put("academicYear", academicYear);
        return result;
    }

    private String formatTime(LocalTime t) {
        int hour = t.getHour();
        String suffix = hour < 12 ? "AM" : "PM";
        int displayHour = hour % 12;
        if (displayHour == 0) displayHour = 12;
        return String.format("%d:%02d %s", displayHour, t.getMinute(), suffix);
    }
}