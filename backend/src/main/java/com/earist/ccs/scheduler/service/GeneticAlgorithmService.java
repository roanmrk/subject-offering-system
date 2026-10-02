package com.earist.ccs.scheduler.service;

import com.earist.ccs.scheduler.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Module 4: Genetic Algorithm
 *
 * Fitness = 0.3 * conflict-score + 0.5 * workload-balance + 0.2 * room-utilization
 *
 * The GA is *advisory*: ScheduleService only adopts GA output if it does
 * not regress the number of assigned (faculty, room, timeslot) offerings.
 */
@Service
@Slf4j
public class GeneticAlgorithmService {

    private int populationSize = 50;
    private int maxGenerations = 100;
    private double crossoverRate = 0.8;
    private double mutationRate = 0.05;
    private double elitismRate = 0.1;

    private List<Timeslot> timeSlots;
    private List<Room> rooms;
    private List<Faculty> faculty;
    private List<Section> sections;
    private long currentSeed = 0L;

    public static class Chromosome {
        public Map<String, Integer> timeAssignment = new HashMap<>();
        public Map<String, String> roomAssignment = new HashMap<>();
        public Map<String, String> facultyAssignment = new HashMap<>();
        public double fitness = 0;

        public Chromosome() {}

        public Chromosome(Map<String, Integer> t, Map<String, String> r, Map<String, String> f) {
            this.timeAssignment = new HashMap<>(t);
            this.roomAssignment = new HashMap<>(r);
            this.facultyAssignment = new HashMap<>(f);
        }

        public Map<String, Integer> chromosome() { return timeAssignment; }
        public Map<String, String> roomMap() { return roomAssignment; }
        public Map<String, String> facultyMap() { return facultyAssignment; }
    }

    public static class GAResult {
        public Chromosome best;
        public List<Double> fitnessHistory = new ArrayList<>();
        public long executionTimeMs = 0;
        public int generationsRun = 0;
        public long seedUsed = 0L;
    }

    public void initialize(List<Timeslot> timeSlots, List<Room> rooms,
                           List<Faculty> faculty, List<Section> sections) {
        initialize(timeSlots, rooms, faculty, sections, 0L);
    }

    public void initialize(List<Timeslot> timeSlots, List<Room> rooms,
                           List<Faculty> faculty, List<Section> sections,
                           long seed) {
        this.timeSlots = timeSlots;
        this.rooms = rooms;
        this.faculty = faculty;
        this.sections = sections;
        this.currentSeed = seed;
    }

    public GAResult optimize(Map<String, Integer> seedColoring) {
        return optimize(seedColoring, 0L);
    }

    public GAResult optimize(Map<String, Integer> seedColoring, long seed) {
        GAResult result = new GAResult();
        long start = System.currentTimeMillis();
        result.seedUsed = seed;

        if (sections == null || sections.isEmpty()
            || timeSlots == null || timeSlots.isEmpty()
            || faculty == null || faculty.isEmpty()) {
            log.warn("GA skipped: insufficient data");
            result.best = new Chromosome();
            result.executionTimeMs = System.currentTimeMillis() - start;
            return result;
        }

        final Random rand = (seed != 0L) ? new Random(seed) : new Random();

        log.info("🧬 Starting GA: pop={}, gen={} (seed={})",
            populationSize, maxGenerations,
            seed == 0L ? "random" : String.valueOf(seed));

        List<Chromosome> population = initializePopulation(seedColoring, rand);

        Chromosome best = population.get(0);
        best.fitness = calculateFitness(best);

        for (int gen = 0; gen < maxGenerations; gen++) {
            for (Chromosome c : population) {
                c.fitness = calculateFitness(c);
            }

            population.sort((c1, c2) -> Double.compare(c2.fitness, c1.fitness));

            if (population.get(0).fitness > best.fitness) {
                best = population.get(0);
            }
            result.fitnessHistory.add(best.fitness);

            if (best.fitness >= 0.98) {
                log.info("🧬 GA converged early at gen {}", gen);
                result.generationsRun = gen + 1;
                break;
            }

            population = createNextGeneration(population, rand);
            result.generationsRun = gen + 1;
        }

        result.best = best;
        result.executionTimeMs = System.currentTimeMillis() - start;
        log.info("🧬 GA done. Fitness={}, gens={}, {}ms",
            Math.round(best.fitness * 10000.0) / 10000.0,
            result.generationsRun, result.executionTimeMs);

        return result;
    }

    private List<Chromosome> initializePopulation(Map<String, Integer> seedColoring,
                                                    Random rand) {
        List<Chromosome> pop = new ArrayList<>();

        for (int i = 0; i < populationSize; i++) {
            Chromosome c = new Chromosome();
            for (Section s : sections) {
                String sid = s.getId().toString();

                if (seedColoring != null && seedColoring.containsKey(sid)) {
                    c.timeAssignment.put(sid, seedColoring.get(sid));
                } else {
                    c.timeAssignment.put(sid, rand.nextInt(timeSlots.size()));
                }

                if (!rooms.isEmpty()) {
                    Room r = rooms.get(rand.nextInt(rooms.size()));
                    c.roomAssignment.put(sid, r.getId().toString());
                }
                if (!faculty.isEmpty()) {
                    Faculty f = faculty.get(rand.nextInt(faculty.size()));
                    c.facultyAssignment.put(sid, f.getId().toString());
                }
            }
            pop.add(c);
        }
        return pop;
    }

    private double calculateFitness(Chromosome c) {
        double conflict = calculateConflictScore(c);
        double balance = calculateFacultyBalance(c);
        double util = calculateRoomUtilization(c);
        return (conflict * 0.3) + (balance * 0.5) + (util * 0.2);
    }

    private double calculateConflictScore(Chromosome c) {
        int conflicts = 0;

        Map<Integer, List<Section>> byTime = new HashMap<>();
        for (Section s : sections) {
            Integer t = c.timeAssignment.get(s.getId().toString());
            if (t != null) byTime.computeIfAbsent(t, k -> new ArrayList<>()).add(s);
        }

        for (List<Section> group : byTime.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    Section s1 = group.get(i);
                    Section s2 = group.get(j);
                    if (s1.getYearLevel().equals(s2.getYearLevel())
                        && s1.getProgram().equals(s2.getProgram())) {
                        conflicts++;
                    }
                }
            }
        }

        Map<String, Set<Integer>> facultyTimes = new HashMap<>();
        for (Section s : sections) {
            String sid = s.getId().toString();
            Integer t = c.timeAssignment.get(sid);
            String fid = c.facultyAssignment.get(sid);
            if (t != null && fid != null) {
                facultyTimes.computeIfAbsent(fid, k -> new HashSet<>()).add(t);
            }
        }
        for (Map.Entry<String, Set<Integer>> e : facultyTimes.entrySet()) {
            int total = 0;
            for (Section s : sections) {
                if (e.getKey().equals(c.facultyAssignment.get(s.getId().toString()))) total++;
            }
            conflicts += Math.max(0, total - e.getValue().size());
        }

        int maxConflicts = Math.max(1, sections.size() * 2);
        return Math.max(0, 1.0 - ((double) conflicts / maxConflicts));
    }

    private double calculateFacultyBalance(Chromosome c) {
        Map<String, Integer> load = new HashMap<>();
        for (String fid : c.facultyAssignment.values()) {
            load.merge(fid, 1, Integer::sum);
        }
        if (load.isEmpty()) return 0;

        double avg = load.values().stream().mapToInt(i -> i).average().orElse(0);
        double variance = load.values().stream()
            .mapToDouble(v -> Math.pow(v - avg, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);

        double cv = avg > 0 ? stdDev / avg : 1.0;
        return Math.max(0, 1.0 - cv);
    }

    private double calculateRoomUtilization(Chromosome c) {
        if (rooms.isEmpty()) return 0;
        Set<String> used = new HashSet<>(c.roomAssignment.values());
        return (double) used.size() / rooms.size();
    }

    private List<Chromosome> createNextGeneration(List<Chromosome> pop, Random rand) {
        List<Chromosome> next = new ArrayList<>();
        int elitismCount = (int) (populationSize * elitismRate);
        for (int i = 0; i < elitismCount && i < pop.size(); i++) {
            next.add(pop.get(i));
        }
        while (next.size() < populationSize) {
            Chromosome p1 = tournamentSelection(pop, rand);
            Chromosome p2 = tournamentSelection(pop, rand);
            Chromosome child = crossover(p1, p2, rand);
            mutate(child, rand);
            next.add(child);
        }
        return next;
    }

    private Chromosome crossover(Chromosome p1, Chromosome p2, Random rand) {
        if (rand.nextDouble() > crossoverRate) {
            return new Chromosome(p1.timeAssignment, p1.roomAssignment, p1.facultyAssignment);
        }
        Map<String, Integer> t = new HashMap<>();
        Map<String, String> r = new HashMap<>();
        Map<String, String> f = new HashMap<>();

        Set<String> keys = new HashSet<>(p1.timeAssignment.keySet());
        keys.addAll(p2.timeAssignment.keySet());

        for (String k : keys) {
            if (rand.nextDouble() < 0.5) {
                if (p1.timeAssignment.containsKey(k)) {
                    t.put(k, p1.timeAssignment.get(k));
                    r.put(k, p1.roomAssignment.get(k));
                    f.put(k, p1.facultyAssignment.get(k));
                }
            } else {
                if (p2.timeAssignment.containsKey(k)) {
                    t.put(k, p2.timeAssignment.get(k));
                    r.put(k, p2.roomAssignment.get(k));
                    f.put(k, p2.facultyAssignment.get(k));
                }
            }
        }
        return new Chromosome(t, r, f);
    }

    private void mutate(Chromosome c, Random rand) {
        for (String sid : new ArrayList<>(c.timeAssignment.keySet())) {
            if (rand.nextDouble() < mutationRate) {
                c.timeAssignment.put(sid, rand.nextInt(timeSlots.size()));
            }
            if (rand.nextDouble() < mutationRate && !rooms.isEmpty()) {
                Room r = rooms.get(rand.nextInt(rooms.size()));
                c.roomAssignment.put(sid, r.getId().toString());
            }
            if (rand.nextDouble() < mutationRate && !faculty.isEmpty()) {
                Faculty f = faculty.get(rand.nextInt(faculty.size()));
                c.facultyAssignment.put(sid, f.getId().toString());
            }
        }
    }

    private Chromosome tournamentSelection(List<Chromosome> pop, Random rand) {
        Chromosome best = pop.get(rand.nextInt(pop.size()));
        for (int i = 1; i < 3; i++) {
            Chromosome c = pop.get(rand.nextInt(pop.size()));
            if (c.fitness > best.fitness) best = c;
        }
        return best;
    }
}