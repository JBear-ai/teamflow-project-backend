package com.example.test1.service;

import com.example.test1.dto.ProjectCreateReqDto;
import com.example.test1.dto.ProjectResDto;
import com.example.test1.entity.Project;
import com.example.test1.entity.Member;
import com.example.test1.repository.MemberRepository;
import com.example.test1.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectAccess access;
    private final com.example.test1.repository.ProjectMembershipRepository memberships;
    private final MemberRepository memberRepository;

    // 프로젝트 생성
    @Transactional
    public ProjectResDto createProject(ProjectCreateReqDto dto, String email) {
        Member creator = currentMember(email);
        Project project = new Project();
        project.setCreator(creator);

        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        project.setTargetEndDate(dto.getTargetEndDate());

        Project savedProject = projectRepository.save(project);
        memberships.save(new com.example.test1.entity.ProjectMembership(savedProject, creator, com.example.test1.entity.ProjectRole.LEAD));

        return convertToDto(savedProject, com.example.test1.entity.ProjectRole.LEAD);
    }

    // 프로젝트 전체 조회
    @Transactional(readOnly = true)
    public List<ProjectResDto> getProjectList(String email) {
        return memberships.findAllByMember_IdOrderByProject_IdDesc(currentMember(email).getId())
                .stream()
                .map(m -> convertToDto(m.getProject(), m.getRole()))
                .toList();
    }

    // 프로젝트 상세 조회
    @Transactional(readOnly = true)
    public ProjectResDto getProject(Long id, String email) {
        var membership = access.require(id, email, false);
        Project project = membership.getProject();

        return convertToDto(project, membership.getRole());
    }

    // 프로젝트 수정
    @Transactional
    public ProjectResDto updateProject(Long id, ProjectCreateReqDto dto, String email) {

        var membership = access.require(id, email, true);
        ProjectAccess.allowed(membership.getRole() != com.example.test1.entity.ProjectRole.MEMBER);
        Project project = membership.getProject();

        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        project.setTargetEndDate(dto.getTargetEndDate());

        return convertToDto(project, membership.getRole());
    }

    // 프로젝트 삭제
    @Transactional
    public void deleteProject(Long id, String email) {

        var membership = access.require(id, email, true);
        ProjectAccess.allowed(membership.getRole() == com.example.test1.entity.ProjectRole.LEAD);
        Project project = membership.getProject();
        memberships.deleteAllByProject_Id(id);
        memberships.flush();

        projectRepository.delete(project);
    }


    private Member currentMember(String email) {
        return memberRepository.findByEmail(email).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    // Entity → DTO 변환
    private ProjectResDto convertToDto(Project project, com.example.test1.entity.ProjectRole role) {
        ProjectResDto dto = new ProjectResDto();

        dto.setMyRole(role);
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setTargetEndDate(project.getTargetEndDate());
        dto.setStatus(project.getStatus());
        dto.setCreatedAt(project.getCreatedAt());

        return dto;
    }
}
