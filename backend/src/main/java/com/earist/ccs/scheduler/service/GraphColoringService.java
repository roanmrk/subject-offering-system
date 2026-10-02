package com.earist.ccs.scheduler.service;

import com.earist.ccs.scheduler.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;

/**
 * Module 3: Graph Coloring (faculty-availability-aware)
 *
 * Key improvement: when choosing a slot for an offering, only consider
 * slots where AT LEAST ONE faculty QUALIFIED for that course is
 * available for the whole block.
 *
 * This prevents the "everything at 7 AM" pathology where slots were
 * chosen with no regard for whether anyone was actually available.
 */
@Service
@Slf4j
public class GraphColoringService {

    public static class ConflictGraph {
        public Map<String, Vertex> vertices = new HashMap<>();
        public Map<String, Set<String>> adjacencyList = new HashMap<>();

        public static class Vertex {
            public String id;
            public SubjectOffering offering;
            public Integer color = -1;
            public Set<Integer> occupiedSlots = new HashSet<>();

            public Vertex(String id, SubjectOffering offering) {
                this.id = id;
                this.offering = offering;
            }
        }

        public void addVertex(String id, SubjectOffering offering) {
            vertices.put(id, new Vertex(id, offering));
            adjacencyList.put(id, new HashSet<>());
        }

        public void addEdge(String v1, String v2) {
            if (!v1.equals(v2)) {
                adjacencyList.get(v1).add(v2);
                adjacencyList.get(v2).add(v1);
            }
        }

        public Set<String> getNeighbors(String id) {
            return adjacencyList.getOrDefault(id, new HashSet<>());
        }

        public List<Vertex> getVertices() {
            return new ArrayList<>(vertices.values());
        }

        public Vertex getVertex(String id) {
            return vertices.get(id);
        }

        public int getEdgeCount() {
            int count = 0;
            for (Set<String> n : adjacencyList.values()) count += n.size();
            return count / 2;
        }
    }

    public static class ColoringResult {
        public Map<String, Integer> coloring = new HashMap<>();
        public int vertexCount = 0;
        public int edgeCount = 0;
        public int chromaticNumber = 0;
        public long executionTimeMs = 0;
        public double averageDegree = 0;
        public Map<String, Integer> dayDistribution = new TreeMap<>();
        public long seedUsed = 0L;
    }

    public ConflictGraph buildConflictGraph(List<SubjectOffering> offerings,
                                             List<Faculty> allFaculty,
                                             DecisionTreeService decisionTreeService) {
        ConflictGraph graph = new ConflictGraph();

        for (SubjectOffering o : offerings) {
            String id = offeringId(o);
            graph.addVertex(id, o);
        }

        Map<Long, Set<Long>> courseToFaculty = new HashMap<>();
        for (SubjectOffering o : offerings) {
            Long cid = o.getCourse().getId();
            if (!courseToFaculty.containsKey(cid)) {
                List<Faculty> qualified = decisionTreeService.matchFacultyToCourse(o.getCourse());
                Set<Long> fids = new HashSet<>();
                for (Faculty f : qualified) fids.add(f.getId());
                courseToFaculty.put(cid, fids);
            }
        }

        int edges = 0;
        List<ConflictGraph.Vertex> list = graph.getVertices();

        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                SubjectOffering a = list.get(i).offering;
                SubjectOffering b = list.get(j).offering;

                boolean conflict = false;

                if (a.getSection().getId().equals(b.getSection().getId())) {
                    conflict = true;
                }

                if (!conflict
                    && a.getSection().getYearLevel().equals(b.getSection().getYearLevel())
                    && a.getSection().getProgram().equals(b.getSection().getProgram())) {
                    conflict = true;
                }

                if (!conflict) {
                    Set<Long> facA = courseToFaculty.getOrDefault(a.getCourse().getId(), new HashSet<>());
                    Set<Long> facB = courseToFaculty.getOrDefault(b.getCourse().getId(), new HashSet<>());
                    Set<Long> shared = new HashSet<>(facA);
                    shared.retainAll(facB);
                    if (facA.size() == 1 && facB.size() == 1 && !shared.isEmpty()) {
                        conflict = true;
                    }
                }

                if (conflict) {
                    graph.addEdge(list.get(i).id, list.get(j).id);
                    edges++;
                }
            }
        }

        log.info("📊 Conflict graph: {} offerings, {} edges", offerings.size(), edges);
        return graph;
    }

    public ColoringResult greedyColor(ConflictGraph graph, List<Timeslot> timeSlots) {
        return greedyColor(graph, timeSlots, 0L, null, null);
    }

    public ColoringResult greedyColor(ConflictGraph graph, List<Timeslot> timeSlots, long seed) {
        return greedyColor(graph, timeSlots, seed, null, null);
    }

    public ColoringResult greedyColor(ConflictGraph graph,
                                       List<Timeslot> timeSlots,
                                       long seed,
                                       List<Faculty> allFaculty) {
        return greedyColor(graph, timeSlots, seed, allFaculty, null);
    }

    /**
     * Availability-aware greedy coloring.
     *
     * @param allFaculty        full faculty list (for availability windows)
     * @param decisionTreeService  used to match faculty to each course
     */
    public ColoringResult greedyColor(ConflictGraph graph,
                                       List<Timeslot> timeSlots,
                                       long seed,
                                       List<Faculty> allFaculty,
                                       DecisionTreeService decisionTreeService) {
        ColoringResult result = new ColoringResult();
        long start = System.currentTimeMillis();
        result.seedUsed = seed;

        List<ConflictGraph.Vertex> vertices = new ArrayList<>(graph.getVertices());
        int totalSlots = timeSlots.size();

        if (totalSlots == 0 || vertices.isEmpty()) {
            result.executionTimeMs = System.currentTimeMillis() - start;
            return result;
        }

        // Group slot indexes by day
        Map<String, List<Integer>> slotsByDay = new LinkedHashMap<>();
        for (int i = 0; i < timeSlots.size(); i++) {
            String day = timeSlots.get(i).getDay();
            slotsByDay.computeIfAbsent(day, k -> new ArrayList<>()).add(i);
        }
        List<String> days = new ArrayList<>(slotsByDay.keySet());

        // ============================================================
        // Precompute: for each course, which slot indices can be used?
        //
        // A slot index is "usable" for a course if AT LEAST ONE faculty
        // qualified for that course is available at that time. Since
        // courses can be 1, 2, or 3 hours long, we ALSO verify the whole
        // block fits within the faculty's availability window.
        // ============================================================
        boolean availabilityAware = allFaculty != null
            && !allFaculty.isEmpty()
            && decisionTreeService != null;

        Map<Long, List<Integer>> courseUsableSlots = new HashMap<>();

        if (availabilityAware) {
            // Cache: courseId → qualified faculty list
            Map<Long, List<Faculty>> courseQualifiedCache = new HashMap<>();

            for (ConflictGraph.Vertex v : vertices) {
                Course course = v.offering.getCourse();
                if (course == null || course.getId() == null) continue;
                if (courseUsableSlots.containsKey(course.getId())) continue;

                List<Faculty> qualified = courseQualifiedCache.computeIfAbsent(
                    course.getId(), k -> decisionTreeService.matchFacultyToCourse(course));

                int duration = getCourseDuration(course);
                List<Integer> usable = new ArrayList<>();

                for (int i = 0; i < timeSlots.size(); i++) {
                    Timeslot ts = timeSlots.get(i);
                    // Verify whole block fits on the same day and no overflow
                    if (!blockFitsOnDay(timeSlots, i, duration)) continue;

                    // Verify at least one qualified faculty is available
                    // for the whole block
                    boolean someoneAvailable = false;
                    for (Faculty f : qualified) {
                        if (facultyAvailableForBlock(f, ts, duration)) {
                            someoneAvailable = true;
                            break;
                        }
                    }
                    if (someoneAvailable) usable.add(i);
                }

                courseUsableSlots.put(course.getId(), usable);
            }

            int totalUsable = courseUsableSlots.values().stream()
                .mapToInt(List::size).sum();
            int totalPossible = courseUsableSlots.size() * timeSlots.size();
            log.info("🎨 [MODULE 3] Availability-aware: {} usable of {} slot-course combinations",
                totalUsable, totalPossible);
        }

        // Sort vertices by degree DESC (Welsh-Powell)
        final Random rand = (seed != 0L) ? new Random(seed) : null;
        final Map<String, Integer> tieBreakKey = new HashMap<>();
        if (rand != null) {
            for (ConflictGraph.Vertex v : vertices) {
                tieBreakKey.put(v.id, rand.nextInt());
            }
        }

        vertices.sort((v1, v2) -> {
            int degDiff = Integer.compare(
                graph.getNeighbors(v2.id).size(),
                graph.getNeighbors(v1.id).size());
            if (degDiff != 0) return degDiff;
            if (rand == null) return v1.id.compareTo(v2.id);
            return Integer.compare(tieBreakKey.get(v1.id), tieBreakKey.get(v2.id));
        });

        Set<Integer> globallyUsed = new HashSet<>();
        Map<String, Integer> dayUsage = new HashMap<>();
        Map<String, Integer> chosenStart = new HashMap<>();

        int totalDegree = 0;

        for (ConflictGraph.Vertex v : vertices) {
            totalDegree += graph.getNeighbors(v.id).size();

            int duration = getCourseDuration(v.offering.getCourse());

            // Gather slots occupied by neighbors (block range)
            Set<Integer> blockedSlots = new HashSet<>();
            for (String nId : graph.getNeighbors(v.id)) {
                ConflictGraph.Vertex n = graph.getVertex(nId);
                if (n != null) blockedSlots.addAll(n.occupiedSlots);
            }

            // Usable slots for this course (availability-filtered if enabled)
            List<Integer> usableSlotsForCourse = null;
            if (availabilityAware && v.offering.getCourse() != null
                && v.offering.getCourse().getId() != null) {
                usableSlotsForCourse = courseUsableSlots.get(v.offering.getCourse().getId());
            }

            // Sort days by usage ASC, so least-used day goes first
            days.sort(Comparator.comparingInt(d -> dayUsage.getOrDefault(d, 0)));

            int chosenColor = -1;

            // Try each day, starting with least-used
            for (String day : days) {
                List<Integer> daySlots = slotsByDay.get(day);
                if (daySlots == null) continue;

                for (int i = 0; i + duration - 1 < daySlots.size(); i++) {
                    int startIdx = daySlots.get(i);
                    boolean fits = true;

                    for (int k = 0; k < duration; k++) {
                        int gIdx = daySlots.get(i + k);

                        // If availability-aware, restrict to usable slots
                        if (usableSlotsForCourse != null
                            && !usableSlotsForCourse.contains(gIdx)) {
                            fits = false; break;
                        }

                        // Slot must not be globally used
                        if (globallyUsed.contains(gIdx)) { fits = false; break; }

                        // Slot must not be blocked by neighbors
                        if (blockedSlots.contains(gIdx)) { fits = false; break; }
                    }

                    if (fits) {
                        chosenColor = startIdx;
                        break;
                    }
                }

                if (chosenColor >= 0) break;
            }

            // Fallback — pick any usable slot that fits
            if (chosenColor < 0) {
                log.warn("⚠️ Section {} / {} could not be placed cleanly — using fallback",
                    v.offering.getSection().getSectionCode(),
                    v.offering.getCourse().getCourseCode());
                chosenColor = findFallbackSlot(days, slotsByDay, duration,
                    timeSlots, usableSlotsForCourse);
            }

            // Mark slots as occupied
            if (chosenColor >= 0 && chosenColor < timeSlots.size()) {
                for (int k = 0; k < duration; k++) {
                    int gIdx = chosenColor + k;
                    if (gIdx < timeSlots.size()
                        && timeSlots.get(gIdx).getDay()
                            .equals(timeSlots.get(chosenColor).getDay())) {
                        globallyUsed.add(gIdx);
                        v.occupiedSlots.add(gIdx);
                    }
                }
                String day = timeSlots.get(chosenColor).getDay();
                dayUsage.merge(day, 1, Integer::sum);
            }

            v.color = chosenColor;
            chosenStart.put(v.id, chosenColor);
        }

        result.coloring = chosenStart;
        result.vertexCount = vertices.size();
        result.edgeCount = graph.getEdgeCount();
        result.averageDegree = vertices.isEmpty() ? 0 : (double) totalDegree / vertices.size();
        result.chromaticNumber = result.coloring.values().stream()
            .max(Integer::compare).orElse(0) + 1;
        result.executionTimeMs = System.currentTimeMillis() - start;

        for (Integer c : result.coloring.values()) {
            if (c >= 0 && c < timeSlots.size()) {
                String day = timeSlots.get(c).getDay();
                result.dayDistribution.merge(day, 1, Integer::sum);
            }
        }

        log.info("🎨 Graph coloring: {} offerings, {} edges, {}ms (seed={})",
            result.vertexCount, result.edgeCount,
            result.executionTimeMs, seed == 0L ? "det" : seed);
        log.info("   Day distribution: {}", result.dayDistribution);

        return result;
    }

    /**
     * Verify a duration-block starting at slotIndex stays within a single day.
     */
    private boolean blockFitsOnDay(List<Timeslot> timeSlots, int startIndex, int duration) {
        if (startIndex + duration > timeSlots.size()) return false;
        String day = timeSlots.get(startIndex).getDay();
        for (int k = 1; k < duration; k++) {
            if (!timeSlots.get(startIndex + k).getDay().equals(day)) return false;
        }
        // Also verify contiguity by time (no gap). In practice the DB is
        // seeded with 1-hour consecutive slots, but let's be defensive.
        LocalTime prev = timeSlots.get(startIndex).getStartTime();
        for (int k = 1; k < duration; k++) {
            LocalTime cur = timeSlots.get(startIndex + k).getStartTime();
            if (!cur.equals(prev.plusHours(1))) return false;
            prev = cur;
        }
        return true;
    }

    /**
     * Verify a faculty member is available for the entire duration block.
     */
    private boolean facultyAvailableForBlock(Faculty f, Timeslot ts, int duration) {
        if (f.getAvailableStartTime() == null || f.getAvailableEndTime() == null) {
            return true; // unrestricted
        }
        LocalTime blockStart = ts.getStartTime();
        LocalTime blockEnd = blockStart.plusHours(duration);

        return !blockStart.isBefore(f.getAvailableStartTime())
            && !blockEnd.isAfter(f.getAvailableEndTime());
    }

    private int findFallbackSlot(List<String> days,
                                  Map<String, List<Integer>> slotsByDay,
                                  int duration,
                                  List<Timeslot> timeSlots,
                                  List<Integer> usableSlotsForCourse) {
        for (String day : days) {
            List<Integer> daySlots = slotsByDay.get(day);
            if (daySlots == null) continue;
            for (int i = 0; i + duration - 1 < daySlots.size(); i++) {
                boolean fits = true;
                for (int k = 0; k < duration; k++) {
                    int gIdx = daySlots.get(i + k);
                    if (usableSlotsForCourse != null
                        && !usableSlotsForCourse.contains(gIdx)) {
                        fits = false; break;
                    }
                }
                if (fits) return daySlots.get(i);
            }
        }
        // Absolute last resort
        if (usableSlotsForCourse != null && !usableSlotsForCourse.isEmpty()) {
            return usableSlotsForCourse.get(0);
        }
        for (String day : days) {
            List<Integer> daySlots = slotsByDay.get(day);
            if (daySlots != null && !daySlots.isEmpty()) return daySlots.get(0);
        }
        return 0;
    }

    private int getCourseDuration(Course course) {
        if (course == null || course.getDurationHours() == null) return 1;
        int d = course.getDurationHours();
        return Math.max(1, Math.min(3, d));
    }

    public static String offeringId(SubjectOffering o) {
        return o.getSection().getId() + "-" + o.getCourse().getId();
    }
}