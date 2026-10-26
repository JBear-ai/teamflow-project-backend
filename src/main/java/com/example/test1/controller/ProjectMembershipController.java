package com.example.test1.controller;
import com.example.test1.entity.ProjectRole;
import com.example.test1.service.ProjectMembershipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/projects/{projectId}/members") @RequiredArgsConstructor
public class ProjectMembershipController {
 private final ProjectMembershipService service;
 public record Add(@NotBlank @Email @Size(max=100) String email) {}
 public record Change(@NotNull ProjectRole role) {}
 @GetMapping public List<ProjectMembershipService.View> list(@PathVariable Long projectId,Authentication auth) { return service.list(projectId,auth.getName()); }
 @PostMapping public ResponseEntity<ProjectMembershipService.View> add(@PathVariable Long projectId,@Valid @RequestBody Add body,Authentication auth) { return ResponseEntity.status(201).body(service.add(projectId,body.email(),auth.getName())); }
 @PutMapping("/{memberId}/role") public ProjectMembershipService.View change(@PathVariable Long projectId,@PathVariable Long memberId,@Valid @RequestBody Change body,Authentication auth) { return service.change(projectId,memberId,body.role(),auth.getName()); }
 @DeleteMapping("/{memberId}") public ResponseEntity<Void> remove(@PathVariable Long projectId,@PathVariable Long memberId,Authentication auth) { service.remove(projectId,memberId,auth.getName()); return ResponseEntity.noContent().build(); }
}
