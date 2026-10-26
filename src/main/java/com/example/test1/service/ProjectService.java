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

        return convertToDto(savedProject);
    }

    // 프로젝트 전체 조회
    @Transactional(readOnly = true)
    public List<ProjectResDto> getProjectList(String email) {
        return projectRepository.findAllByCreator_IdOrderByIdDesc(currentMember(email).getId())
                .stream()
                .map(this::convertToDto)
                .toList();
    }

    // 프로젝트 상세 조회
    @Transactional(readOnly = true)
    public ProjectResDto getProject(Long id, String email) {
        Project project = ownedProject(id, email);

        return convertToDto(project);
    }

    // 프로젝트 수정
    @Transactional
    public ProjectResDto updateProject(Long id, ProjectCreateReqDto dto, String email) {

        Project project = ownedProject(id, email);

        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        project.setTargetEndDate(dto.getTargetEndDate());

        return convertToDto(project);
    }

    // 프로젝트 삭제
    @Transactional
    public void deleteProject(Long id, String email) {

        Project project = ownedProject(id, email);

        projectRepository.delete(project);
    }


    private Member currentMember(String email) {
        return memberRepository.findByEmail(email).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Project ownedProject(Long id, String email) {
        return projectRepository.findByIdAndCreator_Id(id, currentMember(email).getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "프로젝트가 존재하지 않습니다."));
    }

    // Entity → DTO 변환
    private ProjectResDto convertToDto(Project project) {
        ProjectResDto dto = new ProjectResDto();

        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setTargetEndDate(project.getTargetEndDate());
        dto.setStatus(project.getStatus());
        dto.setCreatedAt(project.getCreatedAt());

        return dto;
    }
}