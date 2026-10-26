package com.example.test1.repository;

import com.example.test1.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findAllByCreator_IdOrderByIdDesc(Long creatorId);
    Optional<Project> findByIdAndCreator_Id(Long id, Long creatorId);
}