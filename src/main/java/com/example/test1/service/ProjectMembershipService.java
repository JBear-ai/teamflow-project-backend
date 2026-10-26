package com.example.test1.service;
import com.example.test1.entity.*;
import com.example.test1.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class ProjectMembershipService {
 private final ProjectAccess access;
 private final ProjectMembershipRepository memberships;
 private final MemberRepository members;
 public record View(Long memberId,String email,String name,ProjectRole role,LocalDateTime joinedAt) {}
 private View view(ProjectMembership m) { return new View(m.getMember().getId(),m.getMember().getEmail(),m.getMember().getName(),m.getRole(),m.getJoinedAt()); }
 @Transactional(readOnly=true)
 public List<View> list(Long id,String actor) {
  access.require(id,actor,false);
  return memberships.findAllByProject_IdOrderByIdAsc(id).stream().map(this::view).toList();
 }
 @Transactional
 public View add(Long id,String email,String actor) {
  var a=access.require(id,actor,true);
  ProjectAccess.allowed(a.getRole()!=ProjectRole.MEMBER);
  var member=members.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"가입된 회원이 없습니다."));
  if(memberships.findByProject_IdAndMember_Id(id,member.getId()).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 참여 중입니다.");
  return view(memberships.saveAndFlush(new ProjectMembership(a.getProject(),member,ProjectRole.MEMBER)));
 }
 @Transactional
 public View change(Long id,Long memberId,ProjectRole role,String actor) {
  var a=access.require(id,actor,true);
  ProjectAccess.allowed(a.getRole()==ProjectRole.LEAD);
  var target=target(id,memberId);
  if(role!=ProjectRole.LEAD) protectLastLead(id,target);
  target.changeRole(role);
  return view(target);
 }
 @Transactional
 public void remove(Long id,Long memberId,String actor) {
  var a=access.require(id,actor,true);
  ProjectAccess.allowed(a.getRole()!=ProjectRole.MEMBER);
  var target=target(id,memberId);
  // Self-leave is not part of this API; role changes remain available to LEADs.
  ProjectAccess.allowed(!a.getMember().getId().equals(memberId));
  ProjectAccess.allowed(a.getRole()==ProjectRole.LEAD || target.getRole()==ProjectRole.MEMBER);
  protectLastLead(id,target);
  memberships.delete(target);
 }
 private ProjectMembership target(Long id,Long memberId) { return memberships.findByProject_IdAndMember_Id(id,memberId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"참여자가 없습니다.")); }
 private void protectLastLead(Long id,ProjectMembership target) {
  if(target.getRole()==ProjectRole.LEAD && memberships.countByProject_IdAndRole(id,ProjectRole.LEAD)<=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"마지막 LEAD는 유지해야 합니다.");
 }
}
