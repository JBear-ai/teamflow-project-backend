package com.example.test1.repository;
import com.example.test1.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership,Long> {
 Optional<ProjectMembership> findByProject_IdAndMember_Id(Long projectId,Long memberId);
 List<ProjectMembership> findAllByMember_IdOrderByProject_IdDesc(Long memberId);
 List<ProjectMembership> findAllByProject_IdOrderByIdAsc(Long projectId);
 long countByProject_IdAndRole(Long projectId,ProjectRole role);
 void deleteAllByProject_Id(Long projectId);
}
