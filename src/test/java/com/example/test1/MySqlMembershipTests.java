package com.example.test1;

import com.example.test1.dto.ProjectCreateReqDto;
import com.example.test1.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Run only against a dedicated, disposable MySQL schema. Never production. */
@EnabledIfEnvironmentVariable(named="TEAMFLOW_MYSQL_TEST_URL", matches=".+")
class MySqlMembershipTests extends ProjectMembershipTests {
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("TEAMFLOW_MYSQL_TEST_URL"));
        p.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEAMFLOW_MYSQL_TEST_USER", "root"));
        p.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEAMFLOW_MYSQL_TEST_PASSWORD", ""));
        p.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        p.add("spring.datasource.hikari.transaction-isolation", () -> "TRANSACTION_REPEATABLE_READ");
        p.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
    private Connection connection() throws SQLException {
        return DriverManager.getConnection(System.getenv("TEAMFLOW_MYSQL_TEST_URL"),
            System.getenv().getOrDefault("TEAMFLOW_MYSQL_TEST_USER", "root"),
            System.getenv().getOrDefault("TEAMFLOW_MYSQL_TEST_PASSWORD", ""));
    }
    private Member user(String email) {
        var m = new Member(); m.setEmail(email); m.setName(email); m.setPassword("unused");
        return members.save(m);
    }
    @Test void queuedDuplicateAddsReturnConflict() throws Exception {
        var a = user("queued-a@test.com"); var b = user("queued-b@test.com");
        var dto = new ProjectCreateReqDto(); dto.setName("queued writers");
        long id = projectService.createProject(dto, a.getEmail()).getId();
        assertEquals(List.of(200,409), queued(id,
            () -> membershipService.add(id, b.getEmail(), a.getEmail()),
            () -> membershipService.add(id, b.getEmail(), a.getEmail())));
        assertEquals(2, memberships.findAllByProject_IdOrderByIdAsc(id).size());
    }
    @Test void queuedSelfDemotionsKeepOneLead() throws Exception {
        var a = user("demote-a@test.com"); var b = user("demote-b@test.com");
        var dto = new ProjectCreateReqDto(); dto.setName("queued demotions");
        long id = projectService.createProject(dto, a.getEmail()).getId();
        membershipService.add(id, b.getEmail(), a.getEmail());
        membershipService.change(id, b.getId(), ProjectRole.LEAD, a.getEmail());
        assertEquals(List.of(200,409), queued(id,
            () -> membershipService.change(id, a.getId(), ProjectRole.MEMBER, a.getEmail()),
            () -> membershipService.change(id, b.getId(), ProjectRole.MEMBER, b.getEmail())));
        assertEquals(1, memberships.countByProject_IdAndRole(id, ProjectRole.LEAD));
    }
    private List<Integer> queued(long id, Callable<?> first, Callable<?> second) throws Exception {
        try (var blocker = connection(); var monitor = connection();
             var pool = Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("SELECT id FROM projects WHERE id=? FOR UPDATE")) {
                lock.setLong(1,id); lock.executeQuery().close();
            }
            var futures = new ArrayList<Future<Integer>>();
            for (var action : List.of(first,second)) futures.add(pool.submit(() -> {
                try { action.call(); return 200; }
                catch (org.springframework.web.server.ResponseStatusException e) { return e.getStatusCode().value(); }
            }));
            try {
                // Both transactions must actually be waiting, not merely scheduled.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    try (var s = monitor.createStatement(); var r = s.executeQuery(
                        "SELECT COUNT(DISTINCT REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits")) {
                        r.next(); if (r.getInt(1) >= 2) { waiting = true; break; }
                    }
                    Thread.sleep(25);
                }
                assertTrue(waiting, "Two writers must be blocked on the project lock");
            } finally { blocker.commit(); }
            var result = new ArrayList<Integer>();
            for (var f : futures) result.add(f.get(20, TimeUnit.SECONDS));
            Collections.sort(result); return result;
        }
    }
}
