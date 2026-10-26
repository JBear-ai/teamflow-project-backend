package com.example.test1.controller;

import com.example.test1.dto.ProjectCreateReqDto;
import com.example.test1.dto.ProjectResDto;
import com.example.test1.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import org.springframework.security.core.Authentication;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    // 프로젝트 생성
    @PostMapping
    public ResponseEntity<ProjectResDto> createProject(
            @RequestBody ProjectCreateReqDto dto, Authentication authentication) {

        ProjectResDto result = projectService.createProject(dto, authentication.getName());

        return ResponseEntity
                .created(URI.create("/api/projects/" + result.getId()))
                .body(result);
    }

    // 프로젝트 전체 조회
    @GetMapping
    public ResponseEntity<List<ProjectResDto>> getProjectList(Authentication authentication) {
        return ResponseEntity.ok(projectService.getProjectList(authentication.getName()));
    }

    // 프로젝트 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResDto> getProject(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(projectService.getProject(id, authentication.getName()));
    }

    // 프로젝트 수정
    @PutMapping("/{id}")
    public ResponseEntity<ProjectResDto> updateProject(
            @PathVariable Long id,
            @RequestBody ProjectCreateReqDto dto, Authentication authentication) {

        return ResponseEntity.ok(
                projectService.updateProject(id, dto, authentication.getName())
        );
    }

    // 프로젝트 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id, Authentication authentication) {

        projectService.deleteProject(id, authentication.getName());

        return ResponseEntity.noContent().build();
    }
}
