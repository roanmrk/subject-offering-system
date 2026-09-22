package com.earist.ccs.scheduler.controller;

import com.earist.ccs.scheduler.model.*;
import com.earist.ccs.scheduler.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/metrics")
@Slf4j
public class MetricsController {

    @Autowired private SubjectOfferingRepository subjectOfferingRepository;
    @Autowired private FacultyRepository facultyRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SectionRepository sectionRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMetrics(
            @RequestParam String semester,
            @RequestParam String academicYear) {

        List<SubjectOffering> offerings = 
            subjectOfferingRepository.findBySemesterAndAcademicYear(semester, academicYear);

        List<Faculty> allFaculty = facultyRepository.findAll();
        List<Room> allRooms = roomRepository.findAll();
        List<Section> allSections = sectionRepository.findAll();

        Map<String, Object> result = new HashMap<>();

        // Conflict metrics
        long conflicts = offerings.stream()
            .filter(o -> Boolean.FALSE.equals(o.getIsConflictFree())).count();
        result.put("totalOfferings", offerings.size());
        result.put("conflictCount", conflicts);
        result.put("conflictFreeRate", offerings.isEmpty() ? 100.0 
            : Math.round((1.0 - (double) conflicts / offerings.size()) * 10000.0) / 100.0);

        // Faculty workload
        Map<String, Integer> facultyLoad = new HashMap<>();
        for (SubjectOffering o : offerings) {
            if (o.getFaculty() != null) {
                String name = o.getFaculty().getFirstName() + " " + o.getFaculty().getLastName();
                int units = o.getCourse() != null && o.getCourse().getUnits() != null 
                    ? o.getCourse().getUnits() : 3;
                facultyLoad.merge(name, units, Integer::sum);
            }
        }

        List<Map<String, Object>> facultyWorkload = new ArrayList<>();
        for (Faculty f : allFaculty) {
            String name = f.getFirstName() + " " + f.getLastName();
            int load = facultyLoad.getOrDefault(name, 0);
            int maxLoad = f.getMaxLoadUnits() != null ? f.getMaxLoadUnits() : 24;
            Map<String, Object> fw = new HashMap<>();
            fw.put("name", name);
            fw.put("load", load);
            fw.put("maxLoad", maxLoad);
            fw.put("utilization", maxLoad > 0 
                ? Math.round((double) load / maxLoad * 10000.0) / 100.0 : 0);
            facultyWorkload.add(fw);
        }
        facultyWorkload.sort((a, b) -> Integer.compare((int) b.get("load"), (int) a.get("load")));
        result.put("facultyWorkload", facultyWorkload);

        // FWOR
        if (!facultyLoad.isEmpty()) {
            double avg = facultyLoad.values().stream().mapToInt(i -> i).average().orElse(0);
            if (avg > 0) {
                double variance = facultyLoad.values().stream()
                    .mapToDouble(v -> Math.pow(v - avg, 2)).average().orElse(0);
                double stdDev = Math.sqrt(variance);
                double fwor = Math.max(0, (1 - stdDev / avg) * 100);
                result.put("fwor", Math.round(fwor * 100.0) / 100.0);
                result.put("facultyLoadAvg", Math.round(avg * 100.0) / 100.0);
                result.put("facultyLoadStdDev", Math.round(stdDev * 100.0) / 100.0);
            }
        }

        // Room utilization
        Map<String, Integer> roomUsage = new HashMap<>();
        for (SubjectOffering o : offerings) {
            if (o.getRoom() != null) {
                roomUsage.merge(o.getRoom().getRoomCode(), 1, Integer::sum);
            }
        }

        List<Map<String, Object>> roomUtil = new ArrayList<>();
        for (Room r : allRooms) {
            int usage = roomUsage.getOrDefault(r.getRoomCode(), 0);
            Map<String, Object> ru = new HashMap<>();
            ru.put("code", r.getRoomCode());
            ru.put("name", r.getRoomName());
            ru.put("type", r.getRoomType());
            ru.put("usage", usage);
            ru.put("capacity", r.getCapacity());
            roomUtil.add(ru);
        }
        long usedRooms = roomUtil.stream().filter(r -> (int) r.get("usage") > 0).count();
        result.put("rur", allRooms.isEmpty() ? 0 
            : Math.round((double) usedRooms / allRooms.size() * 10000.0) / 100.0);
        result.put("roomUtilization", roomUtil);

        // Section coverage
        Set<String> scheduled = offerings.stream()
            .filter(o -> o.getSection() != null)
            .map(o -> o.getSection().getSectionCode())
            .collect(Collectors.toSet());
        result.put("sectionsWithOfferings", scheduled.size());
        result.put("totalSections", allSections.size());
        result.put("sectionCoverage", allSections.isEmpty() ? 0 
            : Math.round((double) scheduled.size() / allSections.size() * 10000.0) / 100.0);

        return ResponseEntity.ok(result);
    }
}