package com.earist.ccs.scheduler.repository;

import com.earist.ccs.scheduler.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByRoomTypeIgnoreCase(String roomType);
    List<Room> findByCapacityGreaterThanEqual(Integer capacity);
}