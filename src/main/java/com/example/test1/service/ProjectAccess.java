package com.example.test1.service;
import com.example.test1.entity.*;
import com.example.test1.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Component @RequiredArgsConstructor
public class ProjectAccess {
 private final MemberRepository members;
 private final ProjectRepository projects;
 private final ProjectMembershipRepository memberships;
 public Member member(String email) { return members.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)); }
 public ProjectMembership require(Long id,String email,boolean write) {
  // Lock before any consistent read: MySQL REPEATABLE READ must not establish
  // a snapshot while another membership writer is still committing.
  if(write) projects.lockById(id).orElseThrow(ProjectAccess::hidden);
  Member actor=member(email);
  return memberships.findByProject_IdAndMember_Id(id,actor.getId()).orElseThrow(ProjectAccess::hidden);
 }
 public static ResponseStatusException hidden() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"프로젝트가 존재하지 않습니다."); }
 public static void allowed(boolean condition) { if(!condition) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"권한이 없습니다."); }
}
