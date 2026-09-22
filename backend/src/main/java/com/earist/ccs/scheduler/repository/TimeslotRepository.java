package com.earist.ccs.scheduler.repository;

import com.earist.ccs.scheduler.model.Timeslot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeslotRepository extends JpaRepository<Timeslot, Long> {
    List<Timeslot> findByDay(String day);
    Optional<Timeslot> findBySlotCode(String slotCode);
}