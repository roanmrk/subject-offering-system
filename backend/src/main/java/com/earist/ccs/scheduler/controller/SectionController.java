package com.earist.ccs.scheduler.controller;

import com.earist.ccs.scheduler.model.Section;
import com.earist.ccs.scheduler.repository.SectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/admin/sections")
@Slf4j
public class SectionController {

    @Autowired
    private SectionRepository sectionRepository;

    @GetMapping
    public List<Section> getAllSections() {
        return sectionRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Section> getSectionById(@PathVariable Long id) {
        return sectionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> addSection(@RequestBody Map<String, Object> payload) {
        try {
            log.info("📥 Received section payload: {}", payload);
            
            Section section = new Section();
            section.setSectionCode(payload.get("sectionCode").toString());
            section.setYearLevel(Integer.parseInt(payload.get("yearLevel").toString()));
            section.setProgram(payload.get("program").toString());
            
            if (payload.get("expectedEnrollment") != null) {
                section.setExpectedEnrollment(Integer.parseInt(payload.get("expectedEnrollment").toString()));
            }
            
            section.setIsActive(true);
            
            Section saved = sectionRepository.save(section);
            log.info("✅ Section created: {}", saved.getSectionCode());
            return ResponseEntity.ok(saved);
            
        } catch (Exception e) {
            log.error("❌ Error creating section: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateSection(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        try {
            Optional<Section> existingOpt = sectionRepository.findById(id);
            if (!existingOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            Section existing = existingOpt.get();
            
            if (payload.get("sectionCode") != null) {
                existing.setSectionCode(payload.get("sectionCode").toString());
            }
            if (payload.get("yearLevel") != null) {
                existing.setYearLevel(Integer.parseInt(payload.get("yearLevel").toString()));
            }
            if (payload.get("program") != null) {
                existing.setProgram(payload.get("program").toString());
            }
            if (payload.get("expectedEnrollment") != null) {
                existing.setExpectedEnrollment(Integer.parseInt(payload.get("expectedEnrollment").toString()));
            }
            
            Section updated = sectionRepository.save(existing);
            return ResponseEntity.ok(updated);
            
        } catch (Exception e) {
            log.error("❌ Error updating section: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSection(@PathVariable Long id) {
        if (!sectionRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        sectionRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Section deleted"));
    }

    @PostMapping("/import")
    public ResponseEntity<?> importSections(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }

            String content = new String(file.getBytes());
            String[] lines = content.split("\n");
            
            int imported = 0;
            int failed = 0;
            List<String> errors = new ArrayList<>();

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                try {
                    String[] parts = line.split(",");
                    if (parts.length < 4) {
                        failed++;
                        errors.add("Line " + (i+1) + ": invalid format");
                        continue;
                    }

                    Section section = new Section();
                    section.setSectionCode(parts[0].trim());
                    section.setProgram(parts[1].trim());
                    section.setYearLevel(Integer.parseInt(parts[2].trim()));
                    section.setExpectedEnrollment(Integer.parseInt(parts[3].trim()));
                    section.setIsActive(true);

                    sectionRepository.save(section);
                    imported++;

                } catch (Exception e) {
                    failed++;
                    errors.add("Line " + (i+1) + ": " + e.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("imported", imported);
            result.put("failed", failed);
            result.put("errors", errors);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("CSV import error", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}