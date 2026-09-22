package com.earist.ccs.scheduler.service;

import com.earist.ccs.scheduler.model.*;
import com.earist.ccs.scheduler.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    // GENERATE SCHEDULE (5-module pipeline)
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
            log.info("🚀 HYBRID SCHEDULING PIPELINE");
            log.info("   Semester: {} {}", semester, academicYear);
            log.info("   Sections: {} | Faculty: {} | Rooms: {} | Slots: {}",
                sections.size(), allFaculty.size(), rooms.size(), timeSlots.size());
            log.info("   Seed: {}", seed == 0L ? "deterministic" : String.valueOf(seed));
            log.info("═══════════════════════════════════════════");

            // MODULE 1: PREPROCESSING
            long t1 = System.currentTimeMillis();
            DataPreprocessingService.ValidationReport prep = 
                preprocessingService.validate(courses, allFaculty, rooms, sections, timeSlots);
            timings.put("preprocessingMs", System.currentTimeMillis() - t1);

            if (!prep.isReady()) {
                result.put("success", false);
                result.put("error", "Data validation failed");
                return result;
            }

            // MODULE 2: DECISION TREE
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

            // MODULE 3: GRAPH COLORING (seeded)
            long t3 = System.currentTimeMillis();
            GraphColoringService.ConflictGraph graph = 
                graphColoringService.buildConflictGraph(offerings, allFaculty, decisionTreeService);
            GraphColoringService.ColoringResult coloring = 
                graphColoringService.greedyColor(graph, timeSlots, seed);
            timings.put("graphColoringMs", System.currentTimeMillis() - t3);
            log.info("🎨 [MODULE 3] GC: {} vertices, {} edges, chromatic={}",
                coloring.vertexCount, coloring.edgeCount, coloring.chromaticNumber);

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

            // MODULE 4: GENETIC ALGORITHM (seeded)
            long t4 = System.currentTimeMillis();
            geneticAlgorithmService.initialize(timeSlots, rooms, allFaculty, sections, seed);
            GeneticAlgorithmService.GAResult gaResult = 
                geneticAlgorithmService.optimize(coloring.coloring, seed);
            timings.put("geneticAlgorithmMs", System.currentTimeMillis() - t4);
            log.info("🧬 [MODULE 4] GA: fitness={}, gens={}",
                Math.round(gaResult.best.fitness * 10000.0) / 10000.0, gaResult.generationsRun);

            if (seed != 0L && gaResult.best != null) {
                applyGAOptimization(offerings, gaResult.best, timeSlots, rooms, allFaculty);
                log.info("🧬 [MODULE 4] GA output APPLIED to {} offerings",
                    gaResult.best.timeAssignment.size());
            }

            // MODULE 4.5: REPAIR ANY CONFLICTS
            long t45 = System.currentTimeMillis();
            int repaired = repairGAConflicts(offerings, timeSlots, rooms, allFaculty, sections);
            timings.put("repairMs", System.currentTimeMillis() - t45);
            log.info("🔧 [MODULE 4.5] Repaired {} conflicts", repaired);

            // MODULE 5: BACKTRACKING
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
                + validation.capacityViolations + validation.sectionConflicts;
            metrics.put("conflictCount", totalConflicts);
            metrics.put("facultyConflicts", validation.facultyConflicts);
            metrics.put("roomConflicts", validation.roomConflicts);
            metrics.put("capacityViolations", validation.capacityViolations);
            metrics.put("sectionConflicts", validation.sectionConflicts);

            Map<String, Long> dayDist = validation.entries.stream()
                .collect(Collectors.groupingBy(e -> (String) e.get("day"),
                    Collectors.counting()));

            log.info("═══════════════════════════════════════════");
            log.info("✅ PIPELINE COMPLETE in {}ms", totalEnd - totalStart);
            log.info("   CFSR: {}%, FWOR: {}%, RUR: {}%",
                metrics.get("cfsr"), metrics.get("fwor"), metrics.get("rur"));
            log.info("   Conflicts: {}", totalConflicts);
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
                "applied", seed != 0L
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
    // REPAIR CONFLICTS (Module 4.5)
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
        String timeKey = o.getTimeslot().getDay() + "|" + o.getTimeslot().getStartTime();

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
        return false;
    }

    private void registerUsage(SubjectOffering o,
                                Map<String, SubjectOffering> facultyTimeOwner,
                                Map<String, SubjectOffering> roomTimeOwner,
                                Map<String, SubjectOffering> sectionTimeOwner) {
        if (o.getTimeslot() == null) return;
        String timeKey = o.getTimeslot().getDay() + "|" + o.getTimeslot().getStartTime();

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
        if (qualifiedFaculty.isEmpty()) qualifiedFaculty = allFaculty;

        List<Room> candidateRooms = new ArrayList<>(rooms);
        if (Boolean.TRUE.equals(o.getCourse().getIsLaboratory())) {
            candidateRooms.sort(Comparator.comparing((Room r) ->
                !"LABORATORY".equals(r.getRoomType())));
        }

        // Pass 1: qualified faculty
        for (Timeslot ts : shuffledSlots) {
            String timeKey = ts.getDay() + "|" + ts.getStartTime();
            String secKey = o.getSection().getId() + "|" + timeKey;
            if (sectionTimeOwner.containsKey(secKey)) continue;

            for (Faculty f : qualifiedFaculty) {
                String facKey = f.getId() + "|" + timeKey;
                if (facultyTimeOwner.containsKey(facKey)) continue;

                for (Room r : candidateRooms) {
                    String roomKey = r.getRoomCode() + "|" + timeKey;
                    if (roomTimeOwner.containsKey(roomKey)) continue;

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

        // Pass 2: any faculty
        for (Timeslot ts : shuffledSlots) {
            String timeKey = ts.getDay() + "|" + ts.getStartTime();
            String secKey = o.getSection().getId() + "|" + timeKey;
            if (sectionTimeOwner.containsKey(secKey)) continue;

            for (Faculty f : allFaculty) {
                String facKey = f.getId() + "|" + timeKey;
                if (facultyTimeOwner.containsKey(facKey)) continue;

                for (Room r : candidateRooms) {
                    String roomKey = r.getRoomCode() + "|" + timeKey;
                    if (roomTimeOwner.containsKey(roomKey)) continue;

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
    // COMPARE ALGORITHMS (realistic benchmarks)
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

            // GREEDY
            long start = System.currentTimeMillis();
            Map<String, Object> greedy = runComparison(
                allFaculty, rooms, timeSlots, sections, courses, "GREEDY");
            greedy.put("algorithm", "Greedy Round-Robin");
            greedy.put("timeMs", System.currentTimeMillis() - start);
            comparisons.add(greedy);

            // GRAPH COLORING
            start = System.currentTimeMillis();
            Map<String, Object> gc = runComparison(
                allFaculty, rooms, timeSlots, sections, courses, "GRAPH");
            gc.put("algorithm", "Graph Coloring (Welsh-Powell)");
            gc.put("timeMs", System.currentTimeMillis() - start);
            comparisons.add(gc);

            // HYBRID
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

        // ============================================================
    // RUN ONE COMPARISON (per-algorithm simulation)
    // ============================================================
    private Map<String, Object> runComparison(List<Faculty> allFaculty, List<Room> rooms,
                                               List<Timeslot> timeSlots, List<Section> sections,
                                               List<Course> courses, String algorithm) {
        
        List<SubjectOffering> offerings = buildOfferings(sections, courses, "compare", "compare");

        if ("GREEDY".equals(algorithm)) {
            // =========================================================
            // GREEDY: fully naive — no conflict awareness anywhere.
            // Each offering gets a timeslot, faculty, and room by index.
            // =========================================================
            for (int i = 0; i < offerings.size(); i++) {
                SubjectOffering o = offerings.get(i);
                o.setTimeslot(timeSlots.get(i % timeSlots.size()));

                // Naive faculty: pick by index, ignore qualification, ignore load
                if (!allFaculty.isEmpty()) {
                    o.setFaculty(allFaculty.get(i % allFaculty.size()));
                }

                // Naive room: pick by index, ignore type, ignore occupancy
                if (!rooms.isEmpty()) {
                    o.setRoom(rooms.get(i % rooms.size()));
                }
            }

        } else if ("GRAPH".equals(algorithm)) {
            // =========================================================
            // GRAPH COLORING: real Welsh-Powell, smart faculty/room
            // =========================================================
            GraphColoringService.ConflictGraph graph = 
                graphColoringService.buildConflictGraph(offerings, allFaculty, decisionTreeService);
            GraphColoringService.ColoringResult coloring = 
                graphColoringService.greedyColor(graph, timeSlots, 0L);

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
            // =========================================================
            // HYBRID: GC + GA + Repair (full pipeline)
            // =========================================================
            GraphColoringService.ConflictGraph graph = 
                graphColoringService.buildConflictGraph(offerings, allFaculty, decisionTreeService);
            GraphColoringService.ColoringResult coloring = 
                graphColoringService.greedyColor(graph, timeSlots, System.nanoTime());

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

            // Smart assignment
            assignFacultyAndRooms(offerings, allFaculty, rooms);

            // Repair conflicts (final polish)
            repairGAConflicts(offerings, timeSlots, rooms, allFaculty, sections);
        }

        // =========================================================
        // COUNT CONFLICTS (same logic as BacktrackingService)
        // =========================================================
        Map<String, SubjectOffering> facultyTimeOwner = new HashMap<>();
        Map<String, SubjectOffering> roomTimeOwner = new HashMap<>();
        Map<String, SubjectOffering> sectionTimeOwner = new HashMap<>();

        int sectionConflicts = 0;
        int facultyConflicts = 0;
        int roomConflicts = 0;

        for (SubjectOffering o : offerings) {
            if (o.getTimeslot() == null || o.getSection() == null) continue;
            String timeKey = o.getTimeslot().getDay() + "|" + o.getTimeslot().getStartTime();

            // Faculty conflict
            if (o.getFaculty() != null) {
                String key = o.getFaculty().getId() + "|" + timeKey;
                if (facultyTimeOwner.containsKey(key)) {
                    facultyConflicts++;
                } else {
                    facultyTimeOwner.put(key, o);
                }
            }

            // Room conflict
            if (o.getRoom() != null) {
                String key = o.getRoom().getRoomCode() + "|" + timeKey;
                if (roomTimeOwner.containsKey(key)) {
                    roomConflicts++;
                } else {
                    roomTimeOwner.put(key, o);
                }
            }

            // Section conflict
            String secKey = o.getSection().getId() + "|" + timeKey;
            if (sectionTimeOwner.containsKey(secKey)) {
                sectionConflicts++;
            } else {
                sectionTimeOwner.put(secKey, o);
            }
        }

        int totalConflicts = sectionConflicts + facultyConflicts + roomConflicts;
        int totalOfferings = offerings.size();
        double cfsr = totalOfferings == 0 ? 100.0 : 
            Math.round((1.0 - (double) totalConflicts / totalOfferings) * 10000.0) / 100.0;

        Map<String, Object> result = new HashMap<>();
        result.put("totalOfferings", totalOfferings);
        result.put("conflicts", totalConflicts);
        result.put("sectionConflicts", sectionConflicts);
        result.put("facultyConflicts", facultyConflicts);
        result.put("roomConflicts", roomConflicts);
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
        Map<Integer, List<Course>> coursesByYear = courses.stream()
            .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a))
            .values().stream()
            .collect(Collectors.groupingBy(Course::getYearLevel));

        Map<String, Section> uniqueSections = sections.stream()
            .collect(Collectors.toMap(Section::getSectionCode, s -> s, (a, b) -> a));

        Set<String> seen = new HashSet<>();

        for (Section section : uniqueSections.values()) {
            List<Course> eligible = coursesByYear.getOrDefault(section.getYearLevel(), new ArrayList<>());
            List<Course> sectionCourses = eligible.stream()
                .filter(c -> c.getProgram().equals(section.getProgram()))
                .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a))
                .values().stream().limit(6).collect(Collectors.toList());

            if (sectionCourses.isEmpty()) {
                sectionCourses = eligible.stream()
                    .filter(c -> c.getProgram().equals("BSCS"))
                    .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a))
                    .values().stream().limit(6).collect(Collectors.toList());
            }

            for (Course course : sectionCourses) {
                String key = section.getId() + "-" + course.getId();
                if (seen.contains(key)) continue;
                seen.add(key);

                SubjectOffering o = new SubjectOffering();
                o.setSection(section);
                o.setCourse(course);
                o.setSemester(semester);
                o.setAcademicYear(academicYear);
                o.setIsConflictFree(true);
                offerings.add(o);
            }
        }
        return offerings;
    }

    // ============================================================
    // ASSIGN FACULTY AND ROOMS (strictly respects maxLoad)
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

        for (SubjectOffering o : offerings) {
            if (o.getTimeslot() == null) continue;
            Section section = o.getSection();
            Course course = o.getCourse();
            Timeslot ts = o.getTimeslot();
            String timeKey = ts.getDay() + "|" + ts.getStartTime();
            int units = course.getUnits() != null ? course.getUnits() : 3;

            // FACULTY
            List<Faculty> qualified = decisionTreeService.matchFacultyToCourse(course);
            Faculty assignedFaculty = pickLeastLoaded(
                qualified, facultyBusyTimes, facultyLoad, timeKey, units);

            if (assignedFaculty == null) {
                assignedFaculty = pickLeastLoaded(
                    allFaculty, facultyBusyTimes, facultyLoad, timeKey, units);
            }

            if (assignedFaculty == null) {
                unassignedFaculty++;
                log.error("❌ Could not assign faculty for {} (section {}) within maxLoad — leaving unassigned",
                    course.getCourseCode(), section.getSectionCode());
            }

            if (assignedFaculty != null) {
                String fid = assignedFaculty.getId().toString();
                facultyBusyTimes.get(fid).add(timeKey);
                facultyLoad.merge(fid, units, Integer::sum);
            }
            o.setFaculty(assignedFaculty);

            // ROOM
            List<Room> sortedRooms = new ArrayList<>(rooms);
            sortedRooms.sort(Comparator.comparingInt(r -> 
                roomUsage.getOrDefault(r.getRoomCode(), 0)));

            if (Boolean.TRUE.equals(course.getIsLaboratory())) {
                sortedRooms.sort(Comparator.comparing((Room r) -> 
                    !"LABORATORY".equals(r.getRoomType())));
            }

            Room assignedRoom = null;
            for (Room r : sortedRooms) {
                Set<String> busy = roomBusyTimes.get(r.getRoomCode());
                if (busy != null && !busy.contains(timeKey)) {
                    if (section.getExpectedEnrollment() != null
                        && r.getCapacity() != null
                        && section.getExpectedEnrollment() <= r.getCapacity()) {
                        assignedRoom = r;
                        break;
                    }
                }
            }
            if (assignedRoom == null) {
                for (Room r : sortedRooms) {
                    Set<String> busy = roomBusyTimes.get(r.getRoomCode());
                    if (busy != null && !busy.contains(timeKey)) {
                        assignedRoom = r;
                        break;
                    }
                }
            }
            if (assignedRoom == null && !sortedRooms.isEmpty()) {
                assignedRoom = sortedRooms.get(0);
            }

            if (assignedRoom != null) {
                roomBusyTimes.get(assignedRoom.getRoomCode()).add(timeKey);
                roomUsage.merge(assignedRoom.getRoomCode(), 1, Integer::sum);
            }
            o.setRoom(assignedRoom);
        }

        int maxLoadFound = 0;
        int minLoadFound = Integer.MAX_VALUE;
        int overloaded = 0;
        for (Faculty f : allFaculty) {
            String fid = f.getId().toString();
            int load = facultyLoad.getOrDefault(fid, 0);
            int max = f.getMaxLoadUnits() != null ? f.getMaxLoadUnits() : 24;
            if (load > 0) {
                maxLoadFound = Math.max(maxLoadFound, load);
                minLoadFound = Math.min(minLoadFound, load);
                if (load > max) overloaded++;
            }
        }
        log.info("📊 Faculty loads: min={}, max={}, overloaded={}, unassigned-offerings={}",
            minLoadFound == Integer.MAX_VALUE ? 0 : minLoadFound,
            maxLoadFound, overloaded, unassignedFaculty);
    }

    // ============================================================
    // PICK LEAST-LOADED FACULTY (respects maxLoad + availability)
    // ============================================================
    private Faculty pickLeastLoaded(List<Faculty> candidates,
                                     Map<String, Set<String>> facultyBusyTimes,
                                     Map<String, Integer> facultyLoad,
                                     String timeKey,
                                     int unitsNeeded) {
        Faculty best = null;
        int bestLoad = Integer.MAX_VALUE;

        for (Faculty f : candidates) {
            String fid = f.getId().toString();
            int maxLoad = f.getMaxLoadUnits() != null ? f.getMaxLoadUnits() : 24;
            int currentLoad = facultyLoad.getOrDefault(fid, 0);
            Set<String> busy = facultyBusyTimes.get(fid);

            if (busy == null) continue;
            if (busy.contains(timeKey)) continue;
            if (currentLoad + unitsNeeded > maxLoad) continue;

            if (currentLoad < bestLoad) {
                bestLoad = currentLoad;
                best = f;
            }
        }
        return best;
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
    // METRIC CALCULATIONS
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
            e.put("time", off.getTimeslot() != null 
                ? off.getTimeslot().getStartTime() + " - " + off.getTimeslot().getEndTime() 
                : "TBA");
            e.put("room", off.getRoom() != null ? off.getRoom().getRoomCode() : "TBA");
            e.put("faculty", off.getFaculty() != null 
                ? off.getFaculty().getFirstName() + " " + off.getFaculty().getLastName() 
                : "Unassigned");
            e.put("isConflictFree", off.getIsConflictFree());
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
}