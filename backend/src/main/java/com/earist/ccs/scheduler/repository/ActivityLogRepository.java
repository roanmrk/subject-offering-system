package com.earist.ccs.scheduler.repository;

import com.earist.ccs.scheduler.model.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findByUser(String user);
    List<ActivityLog> findByAction(String action);
}