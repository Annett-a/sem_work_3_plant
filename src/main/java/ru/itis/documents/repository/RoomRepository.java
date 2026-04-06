package ru.itis.documents.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.documents.domain.entity.Room;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByUser_IdOrderByNameAsc(Long userId);

    Optional<Room> findByIdAndUser_Id(Long id, Long userId);
}