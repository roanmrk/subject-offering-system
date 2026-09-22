package com.earist.ccs.scheduler.service;

import com.earist.ccs.scheduler.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Module 3: Graph Coloring
 * 
 * Vertices = (section, course) offerings
 * Edges = conflicts between offerings
 * Colors = timeslots
 * 
 * Supports both deterministic coloring (seed = 0) and randomized
 * tie-breaking (seed != 0) so the pipeline can produce reproducible
 * or exploratory schedules.
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

    /**
     * Build conflict graph where vertices are (section, course) offerings.
     */
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

        log.info("📊 Conflict graph: {} offerings, {} edges",
            offerings.size(), edges);
        return graph;
    }

    /**
     * Deterministic coloring (backward-compatible).
     */
    public ColoringResult greedyColor(ConflictGraph graph, List<Timeslot> timeSlots) {
        return greedyColor(graph, timeSlots, 0L);
    }

    /**
     * Welsh-Powell greedy coloring with day-spreading.
     *
     * @param seed  0  → deterministic tie-breaking
     *              >0 → randomized tie-breaking
     */
    public ColoringResult greedyColor(ConflictGraph graph,
                                       List<Timeslot> timeSlots,
                                       long seed) {
        ColoringResult result = new ColoringResult();
        long start = System.currentTimeMillis();
        result.seedUsed = seed;

        List<ConflictGraph.Vertex> vertices = new ArrayList<>(graph.getVertices());
        int totalSlots = timeSlots.size();

        if (totalSlots == 0 || vertices.isEmpty()) {
            result.executionTimeMs = System.currentTimeMillis() - start;
            return result;
        }

        Map<String, List<Integer>> slotsByDay = new LinkedHashMap<>();
        for (int i = 0; i < timeSlots.size(); i++) {
            String day = timeSlots.get(i).getDay();
            slotsByDay.computeIfAbsent(day, k -> new ArrayList<>()).add(i);
        }
        List<String> days = new ArrayList<>(slotsByDay.keySet());
        int totalDays = days.size();

        // ============================================================
        // VALID COMPARATOR: pre-compute random keys for tie-breaking
        // ============================================================
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

            if (rand == null) {
                // Deterministic tie-break by ID
                return v1.id.compareTo(v2.id);
            } else {
                // Deterministic comparison on pre-computed random keys
                return Integer.compare(
                    tieBreakKey.get(v1.id),
                    tieBreakKey.get(v2.id));
            }
        });

        int totalDegree = 0;

        for (int idx = 0; idx < vertices.size(); idx++) {
            ConflictGraph.Vertex v = vertices.get(idx);
            totalDegree += graph.getNeighbors(v.id).size();

            Set<Integer> usedColors = new HashSet<>();
            for (String nId : graph.getNeighbors(v.id)) {
                ConflictGraph.Vertex n = graph.getVertex(nId);
                if (n != null && n.color >= 0) usedColors.add(n.color);
            }

            int dayStart = idx % totalDays;
            int color = -1;

            outer:
            for (int d = 0; d < totalDays; d++) {
                String day = days.get((dayStart + d) % totalDays);
                List<Integer> daySlots = slotsByDay.get(day);
                for (Integer candidate : daySlots) {
                    if (!usedColors.contains(candidate)) {
                        color = candidate;
                        break outer;
                    }
                }
            }

            if (color < 0) {
                for (int c = 0; c < totalSlots; c++) {
                    if (!usedColors.contains(c)) { color = c; break; }
                }
            }

            if (color < 0) {
                log.warn("Section {} cannot be colored cleanly",
                    v.offering.getSection().getSectionCode());
                color = idx % totalSlots;
            }

            v.color = color;
            result.coloring.put(v.id, color);
        }

        result.vertexCount = vertices.size();
        result.edgeCount = graph.getEdgeCount();
        result.averageDegree = vertices.isEmpty() ? 0 : (double) totalDegree / vertices.size();
        result.chromaticNumber = result.coloring.values().stream()
            .max(Integer::compare).orElse(0) + 1;
        result.executionTimeMs = System.currentTimeMillis() - start;

        for (Integer c : result.coloring.values()) {
            if (c < timeSlots.size()) {
                String day = timeSlots.get(c).getDay();
                result.dayDistribution.merge(day, 1, Integer::sum);
            }
        }

        log.info("🎨 Graph coloring: {} offerings, {} edges, chromatic={}, {}ms (seed={})",
            result.vertexCount, result.edgeCount,
            result.chromaticNumber, result.executionTimeMs,
            seed == 0L ? "deterministic" : seed);
        log.info("   Day distribution: {}", result.dayDistribution);

        return result;
    }

    public static String offeringId(SubjectOffering o) {
        return o.getSection().getId() + "-" + o.getCourse().getId();
    }
}