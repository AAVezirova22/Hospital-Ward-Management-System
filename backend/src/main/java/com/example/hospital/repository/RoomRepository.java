package com.example.hospital.repository;

import com.example.hospital.domain.Room;
import org.springframework.data.jpa.repository.*;

public interface RoomRepository extends JpaRepository<Room, Long> {}
