package com.earist.ccs.scheduler.service;

import com.earist.ccs.scheduler.model.Course;
import com.earist.ccs.scheduler.model.Faculty;
import com.earist.ccs.scheduler.model.FacultyCourseQualification;
import com.earist.ccs.scheduler.model.Section;
import com.earist.ccs.scheduler.repository.FacultyCourseQualificationRepository;
import com.earist.ccs.scheduler.repository.FacultyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DecisionTreeService {

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired(required = false)
    private FacultyCourseQualificationRepository qualificationRepository;

    /**
     * Match faculty to a course.
     * PRIORITY 1: qualification table (if populated for this course).
     * PRIORITY 2: keyword-based fallback (original behavior).
     */
    public List<Faculty> matchFacultyToCourse(Course course) {
        if (course == null) return new ArrayList<>();

        // PRIORITY 1: qualification table
        if (qualificationRepository != null && course.getId() != null) {
            List<FacultyCourseQualification> quals =
                qualificationRepository.findByCourseIdWithFaculty(course.getId());

            if (!quals.isEmpty()) {
                List<Faculty> result = new ArrayList<>();
                Set<Long> seen = new HashSet<>();
                for (FacultyCourseQualification q : quals) {
                    Faculty f = q.getFaculty();
                    if (f == null || f.getId() == null) continue;
                    if (seen.contains(f.getId())) continue;
                    if (!hasValidSpecialization(f)) continue;
                    seen.add(f.getId());
                    result.add(f);
                }
                if (!result.isEmpty()) {
                    log.debug("Course {} → {} faculty (from qualification table)",
                        course.getCourseCode(), result.size());
                    return result;
                }
            }
        }

        // PRIORITY 2: keyword fallback
        return matchFacultyByKeyword(course);
    }

    private List<Faculty> matchFacultyByKeyword(Course course) {
        List<Faculty> allFaculty = facultyRepository.findAll();
        if (allFaculty.isEmpty()) return new ArrayList<>();

        String domain = classifyCourse(course);
        List<Faculty> qualified = new ArrayList<>();

        for (Faculty f : allFaculty) {
            if (!hasValidSpecialization(f)) continue;
            String spec = f.getSpecialization().toLowerCase();
            if (isQualifiedForDomain(spec, domain)) {
                qualified.add(f);
            }
        }

        qualified.sort((f1, f2) -> Integer.compare(
            getRelevanceScore(f2, domain),
            getRelevanceScore(f1, domain)));

        log.debug("Course {} → {} faculty (keyword, domain={})",
            course.getCourseCode(), qualified.size(), domain);

        return qualified;
    }

    private static final Map<String, List<String>> SPECIALIZATION_MAP = new HashMap<>();

    static {
        SPECIALIZATION_MAP.put("programming", Arrays.asList(
            "programming", "algorithms", "java", "python", "c++", "c#", ".net", "oop",
            "data structures", "discrete", "software"));

        SPECIALIZATION_MAP.put("database", Arrays.asList(
            "database", "sql", "oracle", "mysql", "data"));

        SPECIALIZATION_MAP.put("networking", Arrays.asList(
            "networking", "security", "firewall", "infrastructure", "cisco", "ccna"));

        SPECIALIZATION_MAP.put("web", Arrays.asList(
            "web", "mobile", "ui/ux", "react", "flutter", "node"));

        SPECIALIZATION_MAP.put("ai", Arrays.asList(
            "ai", "machine learning", "data science", "tensorflow", "computer vision",
            "robotics"));

        SPECIALIZATION_MAP.put("software", Arrays.asList(
            "software", "project", "agile", "scrum", "pmp"));

        SPECIALIZATION_MAP.put("systems", Arrays.asList(
            "operating", "computer architecture", "linux", "systems"));

        SPECIALIZATION_MAP.put("math", Arrays.asList(
            "math", "discrete", "algebra", "calculus", "statistics",
            "algorithms", "theory", "data structures", "programming"));

        SPECIALIZATION_MAP.put("research", Arrays.asList(
            "research", "thesis", "capstone", "project", "software",
            "methodology", "algorithms"));

        SPECIALIZATION_MAP.put("graphics", Arrays.asList(
            "graphics", "computer vision", "visual", "ui/ux", "robotics"));
    }

    private boolean hasValidSpecialization(Faculty f) {
        if (f.getSpecialization() == null) return false;
        String spec = f.getSpecialization().trim();
        if (spec.length() < 3) return false;
        return spec.matches(".*[A-Za-z]{3,}.*");
    }

    private String classifyCourse(Course course) {
        String code = course.getCourseCode().toLowerCase();
        String name = course.getCourseName().toLowerCase();
        String combined = code + " " + name;

        if (combined.contains("thesis") || combined.contains("capstone")
            || combined.contains("csthesi") || combined.contains("ite313")
            || combined.contains("ite404") || combined.contains("ite405")
            || combined.contains("ite406") || combined.contains("research")
            || combined.contains("internship") || combined.contains("practicum")
            || combined.contains("indobsft") || combined.contains("csinterm")) {
            return "research";
        }

        if (combined.contains("discrete") || combined.contains("math")
            || combined.contains("calculus") || combined.contains("algebra")
            || combined.contains("statistics")
            || code.equals("disstru1") || code.equals("disstru2")
            || code.equals("calculus") || code.equals("automata")
            || code.equals("ite105") || code.equals("ite203")
            || code.equals("ite206") || code.equals("ite401")) {
            return "math";
        }

        if (combined.contains("programming") || combined.contains("data structures")
            || combined.contains("algorithm") || combined.contains("object-oriented")
            || combined.contains("object oriented")
            || code.equals("ntrocomp") || code.equals("fprog")
            || code.equals("iprog") || code.equals("dsalg")
            || code.equals("ooprg") || code.equals("progl")
            || code.equals("algcmplx") || code.equals("paracomp")
            || code.equals("ite101") || code.equals("ite102")
            || code.equals("ite103") || code.equals("ite201")
            || code.equals("ite204") || code.equals("ite211")
            || code.equals("ite301") || code.equals("ite310")) {
            return "programming";
        }

        if (combined.contains("database") || combined.contains("information management")
            || code.equals("dbmgt") || code.equals("infomgmt")
            || code.equals("ite104") || code.equals("ite208")) {
            return "database";
        }

        if (combined.contains("network") || combined.contains("security")
            || combined.contains("communication")
            || code.equals("netcomms") || code.equals("infoasec")
            || code.equals("ite210") || code.equals("ite402")) {
            return "networking";
        }

        if (combined.contains("web") || combined.contains("mobile")
            || combined.contains("application development")
            || combined.contains("human computer")
            || code.equals("apdev") || code.equals("mobap")
            || code.equals("hucomint")
            || code.equals("ite302") || code.equals("ite304")
            || code.equals("ite308")) {
            return "web";
        }

        if (combined.contains("intelligent") || combined.contains("machine")
            || combined.contains("data science") || combined.contains("artificial")
            || combined.contains("simulation")
            || code.equals("intelsys") || code.equals("geelecds")
            || code.equals("mosim")
            || code.equals("ite305") || code.equals("ite311")
            || code.equals("ite309")) {
            return "ai";
        }

        if (combined.contains("software engineering")
            || combined.contains("social issues")
            || code.equals("softeng1") || code.equals("sofeng2")
            || code.equals("ssupprac")
            || code.equals("ite307") || code.equals("ite312")
            || code.equals("ite403")) {
            return "software";
        }

        if (combined.contains("operating") || combined.contains("architecture")
            || combined.contains("organization") || combined.contains("microprocessor")
            || combined.contains("digital logic")
            || code.equals("opersyst") || code.equals("archiorg")
            || code.equals("microbot") || code.equals("digde")
            || code.equals("ite202") || code.equals("ite209")
            || code.equals("ite303") || code.equals("ite306")) {
            return "systems";
        }

        if (combined.contains("graphics") || combined.contains("visual")
            || code.equals("gpxvc") || code.equals("ite207")) {
            return "graphics";
        }

        return "general";
    }

    private boolean isQualifiedForDomain(String specialization, String domain) {
        if ("general".equals(domain)) {
            if (specialization.length() < 4) return false;
            String[] generalKeywords = {
                "computer", "it", "information", "technology", "science",
                "fundamentals", "intro", "general", "programming",
                "software", "algorithms", "data"
            };
            for (String kw : generalKeywords) {
                if (specialization.contains(kw)) return true;
            }
            return false;
        }

        List<String> keywords = SPECIALIZATION_MAP.getOrDefault(domain, Collections.emptyList());
        for (String kw : keywords) {
            if (specialization.contains(kw)) return true;
        }
        return false;
    }

    private int getRelevanceScore(Faculty f, String domain) {
        if (f.getSpecialization() == null) return 0;
        String spec = f.getSpecialization().toLowerCase();
        List<String> keywords = SPECIALIZATION_MAP.getOrDefault(domain, Collections.emptyList());
        int score = 0;
        for (String kw : keywords) {
            if (spec.contains(kw)) score++;
        }
        return score;
    }

    public Map<String, Object> verifyPrerequisites(Section section, List<Course> sectionCourses) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> chains = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int totalChecks = 0;
        int passedChecks = 0;

        for (Course c : sectionCourses) {
            List<String> chain = new ArrayList<>();
            boolean valid = validatePrerequisiteChain(c, section.getYearLevel(), chain, new HashSet<>());

            totalChecks++;
            if (valid) passedChecks++;

            Map<String, Object> entry = new HashMap<>();
            entry.put("course", c.getCourseCode());
            entry.put("chain", chain);
            entry.put("valid", valid);
            chains.add(entry);

            if (!valid) {
                warnings.add("Prerequisite violation: " + c.getCourseCode()
                    + " → " + String.join(" → ", chain));
            }
        }

        result.put("section", section.getSectionCode());
        result.put("yearLevel", section.getYearLevel());
        result.put("totalChecks", totalChecks);
        result.put("passedChecks", passedChecks);
        result.put("chains", chains);
        result.put("warnings", warnings);
        result.put("valid", passedChecks == totalChecks);

        return result;
    }

    private boolean validatePrerequisiteChain(Course course, int currentYear,
                                               List<String> chain, Set<Long> visited) {
        if (course == null) return true;

        if (visited.contains(course.getId())) {
            chain.add("⚠️ cycle@" + course.getCourseCode());
            return false;
        }
        visited.add(course.getId());
        chain.add(course.getCourseCode());

        Course prereq = course.getPrerequisite();
        if (prereq == null) return true;

        if (prereq.getYearLevel() != null && prereq.getYearLevel() >= currentYear) {
            chain.add("❌" + prereq.getCourseCode() + "(y" + prereq.getYearLevel() + ")");
            return false;
        }

        return validatePrerequisiteChain(prereq, currentYear, chain, visited);
    }
}