package com.example.test1;

import com.example.test1.entity.Member;
import com.example.test1.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:security;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@org.springframework.test.context.TestExecutionListeners(listeners = org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class, mergeMode = org.springframework.test.context.TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
@AutoConfigureMockMvc
class SessionAuthenticationTests {
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired com.example.test1.repository.ProjectRepository projects;
    private final ObjectMapper json = new ObjectMapper();
    private JsonNode token(MockHttpSession session) throws Exception {
        return json.readTree(mvc.perform(get("/api/auth/csrf").session(session))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void sessionAndCrudLifecycle() throws Exception {
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"email\":\"old@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isForbidden());
        Member old = new Member(); old.setEmail("old@example.com"); old.setName("기존회원");
        old.setPassword(new BCryptPasswordEncoder().encode("password123")); members.save(old);
        MockHttpSession session = new MockHttpSession();
        JsonNode csrf = token(session);
        mvc.perform(post("/api/auth/signup").session(session).header(csrf.get("headerName").asText(), csrf.get("token").asText())
            .contentType("application/json").content("{\"email\":\"new@example.com\",\"password\":\"password123\",\"name\":\"신규회원\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(post("/api/auth/login").session(session).header(csrf.get("headerName").asText(), csrf.get("token").asText())
            .contentType("application/json").content("{\"email\":\"old@example.com\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
        String oldId = session.getId();
        mvc.perform(post("/api/auth/login").session(session).header(csrf.get("headerName").asText(), csrf.get("token").asText())
            .contentType("application/json").content("{\"email\":\"OLD@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("old@example.com"));
        assertNotEquals(oldId, session.getId());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(post("/api/projects").session(session).contentType("application/json").content("{\"name\":\"test\"}"))
            .andExpect(status().isForbidden());
        // The pre-login token must no longer work.
        mvc.perform(post("/api/projects").session(session).header(csrf.get("headerName").asText(), csrf.get("token").asText())
            .contentType("application/json").content("{\"name\":\"test\"}"))
            .andExpect(status().isForbidden());
        csrf = token(session);
        var result = mvc.perform(post("/api/projects").session(session).header(csrf.get("headerName").asText(), csrf.get("token").asText())
            .contentType("application/json").content("{\"name\":\"test\",\"description\":\"original\"}"))
            .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/projects").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/projects/" + id).session(session)).andExpect(status().isOk());
        mvc.perform(put("/api/projects/" + id).session(session).header(csrf.get("headerName").asText(), csrf.get("token").asText())
            .contentType("application/json").content("{\"name\":\"updated\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("updated"));
        mvc.perform(delete("/api/projects/" + id).session(session).header(csrf.get("headerName").asText(), csrf.get("token").asText()))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).header(csrf.get("headerName").asText(), csrf.get("token").asText()))
            .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
    }

    private MockHttpSession loginAs(String email) throws Exception {
        Member member = new Member(); member.setEmail(email); member.setName(email);
        member.setPassword(new BCryptPasswordEncoder().encode("password123")); members.save(member);
        MockHttpSession session = new MockHttpSession();
        JsonNode csrf = token(session);
        mvc.perform(post("/api/auth/login").session(session)
            .header(csrf.get("headerName").asText(), csrf.get("token").asText())
            .contentType("application/json")
            .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
            .andExpect(status().isOk());
        return session;
    }

    @Test void ownershipAndLegacyIsolation() throws Exception {
        MockHttpSession alice = loginAs("owner-a@example.com");
        MockHttpSession bob = loginAs("owner-b@example.com");
        long bobId = members.findByEmail("owner-b@example.com").orElseThrow().getId();
        JsonNode aToken = token(alice);
        var response = mvc.perform(post("/api/projects").session(alice)
            .header(aToken.get("headerName").asText(), aToken.get("token").asText())
            .contentType("application/json")
            .content("{\"name\":\"private\",\"creatorId\":" + bobId + "}"))
            .andExpect(status().isCreated()).andReturn().getResponse();
        long id = json.readTree(response.getContentAsString()).get("id").asLong();
        long aliceId = members.findByEmail("owner-a@example.com").orElseThrow().getId();
        assertTrue(projects.findByIdAndCreator_Id(id, aliceId).isPresent());
        var legacy = new com.example.test1.entity.Project(); legacy.setName("legacy");
        long legacyId = projects.save(legacy).getId();
        mvc.perform(get("/api/projects").session(alice)).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/projects").session(bob)).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        JsonNode bToken = token(bob);
        for (long hiddenId : new long[]{id, legacyId, Long.MAX_VALUE}) {
            mvc.perform(get("/api/projects/" + hiddenId).session(bob)).andExpect(status().isNotFound());
            mvc.perform(put("/api/projects/" + hiddenId).session(bob)
                .header(bToken.get("headerName").asText(), bToken.get("token").asText())
                .contentType("application/json").content("{\"name\":\"hacked\"}"))
                .andExpect(status().isNotFound());
            mvc.perform(delete("/api/projects/" + hiddenId).session(bob)
                .header(bToken.get("headerName").asText(), bToken.get("token").asText()))
                .andExpect(status().isNotFound());
        }
        assertEquals("private", projects.findById(id).orElseThrow().getName());
        assertTrue(projects.existsById(legacyId));
        mvc.perform(put("/api/projects/" + id).session(alice).contentType("application/json")
            .content("{\"name\":\"changed\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/projects/" + id).session(alice)).andExpect(status().isForbidden());
        mvc.perform(put("/api/projects/" + id).session(alice)
            .header(aToken.get("headerName").asText(), aToken.get("token").asText())
            .contentType("application/json")
            .content("{\"name\":\"changed\",\"creatorId\":" + bobId + "}"))
            .andExpect(status().isOk());
        assertTrue(projects.findByIdAndCreator_Id(id, aliceId).isPresent());
        mvc.perform(get("/api/projects/" + legacyId).session(alice)).andExpect(status().isNotFound());
        members.deleteById(bobId);
        mvc.perform(get("/api/projects").session(bob)).andExpect(status().isUnauthorized());
    }

    @Test void cors() throws Exception {
        for (String origin : new String[]{"http://localhost:5173", "http://localhost:5174"}) {
            mvc.perform(options("/api/projects").header("Origin", origin)
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }
        mvc.perform(options("/api/projects").header("Origin", "http://evil.example")
            .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }
}

