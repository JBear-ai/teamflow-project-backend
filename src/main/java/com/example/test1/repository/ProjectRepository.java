package com.example.test1.repository;

import com.example.test1.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Project p where p.id = :id")
    Optional<Project> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    List<Project> findAllByCreator_IdOrderByIdDesc(Long creatorId);
    Optional<Project> findByIdAndCreator_Id(Long id, Long creatorId);
}