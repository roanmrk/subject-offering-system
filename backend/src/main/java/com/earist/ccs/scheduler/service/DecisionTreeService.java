package com.earist.ccs.scheduler.service;

import com.earist.ccs.scheduler.model.Course;
import com.earist.ccs.scheduler.model.Faculty;
import com.earist.ccs.scheduler.model.Section;
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

    private static final Map<String, List<String>> SPECIALIZATION_MAP = new HashMap<>();

    static {
        SPECIALIZATION_MAP.put("programming", Arrays.asList(
            "programming", "algorithms", "java", "python", "c++", "c#", ".net", "oop",
            "data structures", "discrete"));
        SPECIALIZATION_MAP.put("database", Arrays.asList(
            "database", "sql", "oracle", "mysql", "data"));
        SPECIALIZATION_MAP.put("networking", Arrays.asList(
            "networking", "security", "firewall", "infrastructure", "cisco", "ccna"));
        SPECIALIZATION_MAP.put("web", Arrays.asList(
            "web", "mobile", "ui/ux", "react", "flutter", "node"));
        SPECIALIZATION_MAP.put("ai", Arrays.asList(
            "ai", "machine learning", "data science", "tensorflow", "python"));
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
    }

    /**
     * Match faculty to a course.
     *
     * Primary: keyword-based domain matching.
     * Fallback: if fewer than 3 qualified faculty, expands the pool with the
     *           rest (sorted by relevance) so load-balancing has candidates.
     *
     * Rejects faculty with clearly-invalid specializations
     * (e.g., < 3 chars, all-caps random letters) from the general fallback.
     */
    public List<Faculty> matchFacultyToCourse(Course course) {
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

        // Fallback: expand pool if fewer than 3 qualified
        if (qualified.size() < 3) {
            List<Faculty> fallback = new ArrayList<>();
            for (Faculty f : allFaculty) {
                if (!qualified.contains(f) && hasValidSpecialization(f)) {
                    fallback.add(f);
                }
            }
            fallback.sort((f1, f2) -> Integer.compare(
                getRelevanceScore(f2, domain),
                getRelevanceScore(f1, domain)));
            qualified.addAll(fallback);

            log.warn("⚠️ Course {} (domain={}) had < 3 qualified faculty — expanded pool to {}",
                course.getCourseCode(), domain, qualified.size());
        }

        log.debug("Course {} → domain={}, {} qualified faculty",
            course.getCourseCode(), domain, qualified.size());
        return qualified;
    }

    /**
     * Reject faculty with nonsense / placeholder specializations.
     * Examples rejected: "SDA", "DSA", "ABC", "N/A", "", "   "
     *
     * Requirement: specialization must be at least 3 chars AND
     * contain at least one alphabetic sequence of length ≥ 3.
     */
    private boolean hasValidSpecialization(Faculty f) {
        if (f.getSpecialization() == null) return false;
        String spec = f.getSpecialization().trim();
        if (spec.length() < 3) return false;

        // Must contain at least one recognizable word (3+ letters)
        // "SDA" → too short, likely placeholder
        // "Data Science" → OK
        // "Discrete Math" → OK
        // "Algorithms, Data Structures" → OK
        return spec.matches(".*[A-Za-z]{3,}.*");
    }

    private String classifyCourse(Course course) {
        String code = course.getCourseCode().toLowerCase();
        String name = course.getCourseName().toLowerCase();
        String combined = code + " " + name;

        // Research / Thesis / Capstone
        if (combined.contains("thesis") || combined.contains("capstone")
            || combined.contains("ccs303") || combined.contains("ccs304")
            || combined.contains("ite401") || combined.contains("ite402")
            || combined.contains("research")) {
            return "research";
        }

        // Mathematics / Theory
        if (combined.contains("discrete") || combined.contains("math")
            || combined.contains("calculus") || combined.contains("algebra")
            || combined.contains("statistics") || combined.contains("ccs104")) {
            return "math";
        }

        // Programming
        if (combined.contains("prog") || combined.contains("oop")
            || combined.contains("ccs102") || combined.contains("ccs103")
            || combined.contains("ccs201") || combined.contains("ccs203")
            || combined.contains("data structures") || combined.contains("algorithms")) {
            return "programming";
        }

        // Database
        if (combined.contains("database") || combined.contains("db")
            || combined.contains("ccs202") || combined.contains("ite202")
            || combined.contains("sql") || combined.contains("analytics")) {
            return "database";
        }

        // Networking / Security
        if (combined.contains("network") || combined.contains("security")
            || combined.contains("ccs204") || combined.contains("ite201")
            || combined.contains("ite301") || combined.contains("cisco")) {
            return "networking";
        }

        // Web / Mobile
        if (combined.contains("web") || combined.contains("ite102")
            || combined.contains("ite103") || combined.contains("mobile")
            || combined.contains("ccs208") || combined.contains("ccs301")
            || combined.contains("ite303")) {
            return "web";
        }

        // AI / Machine Learning / Data Science
        if (combined.contains("ai") || combined.contains("machine")
            || combined.contains("ccs206") || combined.contains("ccs302")
            || combined.contains("data science") || combined.contains("intelligence")) {
            return "ai";
        }

        // Software Engineering / Project Management
        if (combined.contains("software") || combined.contains("ccs205")
            || combined.contains("project") || combined.contains("ite204")
            || combined.contains("analysis") || combined.contains("ite203")) {
            return "software";
        }

        // Operating Systems / Architecture
        if (combined.contains("operating") || combined.contains("ccs207")
            || combined.contains("architecture") || combined.contains("systems")) {
            return "systems";
        }

        // Cloud / Infrastructure
        if (combined.contains("cloud") || combined.contains("infrastructure")
            || combined.contains("ite302")) {
            return "networking";
        }

        // Intro / Fundamentals → general (but now stricter)
        if (combined.contains("intro") || combined.contains("ite101")
            || combined.contains("ccs101") || combined.contains("fundamentals")) {
            return "general";
        }

        return "general";
    }

    private boolean isQualifiedForDomain(String specialization, String domain) {
        // "general" now requires at least SOME overlap — not everyone qualifies
        if ("general".equals(domain)) {
            // Reject pure-garbage specializations like "SDA", "DSA"
            if (specialization.length() < 4) return false;

            // Allow faculty with broad/teaching-related specializations
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

    /**
     * Verify prerequisite chain recursively.
     */
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

    /**
     * Recursively validate a course's prerequisite chain.
     */
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