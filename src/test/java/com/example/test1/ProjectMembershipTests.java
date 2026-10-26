package com.example.test1;

import com.example.test1.entity.*;
import com.example.test1.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:membership;MODE=MySQL;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=update"})
@AutoConfigureMockMvc
class ProjectMembershipTests {
 @Autowired MockMvc mvc;
 @Autowired MemberRepository members;
 @Autowired ProjectRepository projects;
 @Autowired ProjectMembershipRepository memberships;
 @Autowired com.example.test1.service.ProjectMembershipService membershipService;
 @Autowired com.example.test1.service.ProjectService projectService;
 @Autowired javax.sql.DataSource dataSource;
 @Test void creatorBackfillEnablesAccessButDoesNotRestoreRemovedCreator() throws Exception {
  login("legacy-owner@test.com"); login("legacy-newlead@test.com");
  var owner=members.findByEmail("legacy-owner@test.com").orElseThrow();
  var project=new Project(); project.setName("preserved"); project.setCreator(owner);
  long id=projects.save(project).getId();
  assertTrue(projectService.getProjectList(owner.getEmail()).isEmpty());
  migrate();
  assertEquals(ProjectRole.LEAD,projectService.getProject(id,owner.getEmail()).getMyRole());
  var other=members.findByEmail("legacy-newlead@test.com").orElseThrow();
  membershipService.add(id,other.getEmail(),owner.getEmail());
  membershipService.change(id,other.getId(),ProjectRole.LEAD,owner.getEmail());
  membershipService.remove(id,owner.getId(),other.getEmail());
  migrate();
  assertTrue(projectService.getProjectList(owner.getEmail()).isEmpty());
  assertEquals(ProjectRole.LEAD,projectService.getProject(id,other.getEmail()).getMyRole());
  assertTrue(projects.findByIdAndCreator_Id(id,owner.getId()).isPresent());
 }
 private void migrate() throws Exception {
  String sql=java.nio.file.Files.readString(java.nio.file.Path.of("migration/001_project_memberships.sql")).replaceAll("(?m)--.*$", "");
  try(var c=dataSource.getConnection();var s=c.createStatement()) {
   for(String part:sql.split(";")) if(part.trim().equals("START TRANSACTION")) c.setAutoCommit(false); else if(!part.isBlank()) s.execute(part);
  }
 }
 private final ObjectMapper json=new ObjectMapper();
 private MockHttpSession login(String email) throws Exception {
  Member m=new Member(); m.setEmail(email); m.setName(email); m.setPassword(new BCryptPasswordEncoder().encode("password123")); members.save(m);
  var s=new MockHttpSession();
  mvc.perform(withToken(post("/api/auth/login").contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\"password123\"}"),s)).andExpect(status().isOk());
  return s;
 }
 private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder req,MockHttpSession s) throws Exception {
  var token=json.readTree(mvc.perform(get("/api/auth/csrf").session(s)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  return req.session(s).header(token.get("headerName").asText(),token.get("token").asText());
 }
 private long memberId(String email) { return members.findByEmail(email).orElseThrow().getId(); }
 private MockHttpServletRequestBuilder add(String url,String email) { return post(url).contentType("application/json").content("{\"email\":\""+email+"\"}"); }
 private MockHttpServletRequestBuilder role(String url,long id,String role) { return put(url+"/"+id+"/role").contentType("application/json").content("{\"role\":\""+role+"\"}"); }
 @Test void concurrentAddsAndLeadDemotions() throws Exception {
  login("race-a@test.com"); login("race-b@test.com");
  var dto=new com.example.test1.dto.ProjectCreateReqDto(); dto.setName("race");
  long id=projectService.createProject(dto,"race-a@test.com").getId();
  long a=memberId("race-a@test.com"), b=memberId("race-b@test.com");
  var adds=race(()->membershipService.add(id,"race-b@test.com","race-a@test.com"),()->membershipService.add(id,"race-b@test.com","race-a@test.com"));
  assertEquals(java.util.List.of(200,409),adds);
  membershipService.change(id,b,ProjectRole.LEAD,"race-a@test.com");
  var demotions=race(()->membershipService.change(id,a,ProjectRole.MEMBER,"race-a@test.com"),()->membershipService.change(id,b,ProjectRole.MEMBER,"race-b@test.com"));
  assertEquals(java.util.List.of(200,409),demotions);
  assertEquals(1,memberships.countByProject_IdAndRole(id,ProjectRole.LEAD));
 }
 private java.util.List<Integer> race(java.util.concurrent.Callable<?> first,java.util.concurrent.Callable<?> second) throws Exception {
  var ready=new java.util.concurrent.CountDownLatch(2);
  var start=new java.util.concurrent.CountDownLatch(1);
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
   var tasks=new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
   for(var call:java.util.List.of(first,second)) tasks.add(pool.submit(()->{
    ready.countDown(); if(!start.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
    try {call.call();return 200;} catch(org.springframework.web.server.ResponseStatusException e){return e.getStatusCode().value();}
   }));
   assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS));start.countDown();
   var result=new java.util.ArrayList<Integer>();
   for(var task:tasks) result.add(task.get(20,java.util.concurrent.TimeUnit.SECONDS));
   java.util.Collections.sort(result);return result;
  }
 }
 @Test void permissionsAndLifecycle() throws Exception {
  var lead=login("lead@test.com"); var co=login("co@test.com"); var basic=login("basic@test.com"); var outsider=login("out@test.com");
  var created=mvc.perform(withToken(post("/api/projects").contentType("application/json").content("{\"name\":\"membership\"}"),lead)).andExpect(status().isCreated()).andReturn();
  long id=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
  String p="/api/projects/"+id,u=p+"/members";
  long l=memberId("lead@test.com"),c=memberId("co@test.com"),b=memberId("basic@test.com"),o=memberId("out@test.com");
  assertEquals(ProjectRole.LEAD,memberships.findByProject_IdAndMember_Id(id,l).orElseThrow().getRole());
  mvc.perform(get(u)).andExpect(status().isUnauthorized());
  mvc.perform(get(u).session(outsider)).andExpect(status().isNotFound());
  mvc.perform(withToken(add(u,"basic@test.com"),outsider)).andExpect(status().isNotFound());
  mvc.perform(withToken(add(u,"CO@test.com"),lead)).andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("MEMBER")).andExpect(jsonPath("$.password").doesNotExist());
  mvc.perform(withToken(add(u,"co@test.com"),lead)).andExpect(status().isConflict());
  mvc.perform(withToken(add(u,"missing@test.com"),lead)).andExpect(status().isNotFound());
  mvc.perform(withToken(add(u,"invalid"),lead)).andExpect(status().isBadRequest());
  mvc.perform(withToken(role(u,c,"CO_MANAGER"),lead)).andExpect(status().isOk());
  mvc.perform(withToken(add(u,"basic@test.com"),co)).andExpect(status().isCreated());
  mvc.perform(get(p).session(basic)).andExpect(status().isOk());
  mvc.perform(get("/api/projects").session(basic)).andExpect(jsonPath("$[0].id").value(id));
  mvc.perform(get(u).session(basic)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
  mvc.perform(withToken(put(p).contentType("application/json").content("{\"name\":\"co edit\"}"),co)).andExpect(status().isOk());
  mvc.perform(withToken(put(p).contentType("application/json").content("{\"name\":\"bad\"}"),basic)).andExpect(status().isForbidden());
  mvc.perform(withToken(delete(p),co)).andExpect(status().isForbidden());
  mvc.perform(withToken(delete(p),basic)).andExpect(status().isForbidden());
  mvc.perform(withToken(add(u,"out@test.com"),basic)).andExpect(status().isForbidden());
  mvc.perform(withToken(role(u,b,"LEAD"),co)).andExpect(status().isForbidden());
  mvc.perform(withToken(role(u,b,"LEAD"),basic)).andExpect(status().isForbidden());
  mvc.perform(withToken(role(u,l,"MEMBER"),lead)).andExpect(status().isConflict());
  mvc.perform(withToken(role(u,b,"INVALID"),lead)).andExpect(status().isBadRequest());
  mvc.perform(withToken(put(u+"/"+b+"/role").contentType("application/json").content("{}"),lead)).andExpect(status().isBadRequest());
  mvc.perform(add(u,"out@test.com").session(co)).andExpect(status().isForbidden());
  mvc.perform(role(u,b,"LEAD").session(lead)).andExpect(status().isForbidden());
  mvc.perform(delete(u+"/"+b).session(co)).andExpect(status().isForbidden());
  mvc.perform(withToken(delete(u+"/"+l),co)).andExpect(status().isForbidden());
  mvc.perform(withToken(delete(u+"/"+c),basic)).andExpect(status().isForbidden());
  mvc.perform(withToken(delete(u+"/"+b),co)).andExpect(status().isNoContent());
  mvc.perform(get(p).session(basic)).andExpect(status().isNotFound());
  mvc.perform(withToken(add(u,"basic@test.com"),lead)).andExpect(status().isCreated());
  mvc.perform(withToken(role(u,b,"CO_MANAGER"),lead)).andExpect(status().isOk());
  mvc.perform(withToken(delete(u+"/"+b),co)).andExpect(status().isForbidden());
  mvc.perform(withToken(role(u,b,"LEAD"),lead)).andExpect(status().isOk());
  mvc.perform(withToken(role(u,l,"MEMBER"),lead)).andExpect(status().isOk());
  mvc.perform(withToken(delete(p),lead)).andExpect(status().isForbidden());
  mvc.perform(withToken(role(u,b,"MEMBER"),basic)).andExpect(status().isConflict());
  mvc.perform(withToken(delete(u+"/"+b),basic)).andExpect(status().isForbidden());
  mvc.perform(withToken(delete(u+"/"+c),basic)).andExpect(status().isNoContent());
  assertTrue(projects.findByIdAndCreator_Id(id,l).isPresent());
  mvc.perform(withToken(delete(p),basic)).andExpect(status().isNoContent());
  assertFalse(projects.existsById(id)); assertTrue(memberships.findAllByProject_IdOrderByIdAsc(id).isEmpty()); assertTrue(members.existsById(l));
 }
}
