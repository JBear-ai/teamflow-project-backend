package com.example.test1.controller;

import com.example.test1.dto.ProjectCreateReqDto;
import com.example.test1.dto.ProjectResDto;
import com.example.test1.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    // 프로젝트 생성
    @PostMapping
    public ResponseEntity<ProjectResDto> createProject(
            @RequestBody ProjectCreateReqDto dto) {

        ProjectResDto result = projectService.createProject(dto);

        return ResponseEntity
                .created(URI.create("/api/projects/" + result.getId()))
                .body(result);
    }

    // 프로젝트 전체 조회
    @GetMapping
    public ResponseEntity<List<ProjectResDto>> getProjectList() {
        return ResponseEntity.ok(projectService.getProjectList());
    }

    // 프로젝트 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResDto> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProject(id));
    }

    // 프로젝트 수정
    @PutMapping("/{id}")
    public ResponseEntity<ProjectResDto> updateProject(
            @PathVariable Long id,
            @RequestBody ProjectCreateReqDto dto) {

        return ResponseEntity.ok(
                projectService.updateProject(id, dto)
        );
    }

    // 프로젝트 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {

        projectService.deleteProject(id);

        return ResponseEntity.noContent().build();
    }
}
