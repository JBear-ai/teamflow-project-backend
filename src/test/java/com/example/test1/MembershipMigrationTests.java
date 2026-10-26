package com.example.test1;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class MembershipMigrationTests {
 protected Connection connection() throws SQLException { return DriverManager.getConnection("jdbc:h2:mem:migration;MODE=MySQL;DB_CLOSE_DELAY=-1","sa", ""); }
 @Test void preservesLegacyAndDoesNotRestoreCreatorPrivileges() throws Exception {
  try(var c=connection();var s=c.createStatement()) {
   s.execute("create table members(id bigint primary key)");
   s.execute("create table projects(id bigint primary key, creator_id bigint, created_at timestamp, name varchar(100))");
   s.execute("insert into members values(1),(2)");
   s.execute("insert into projects values(1,1,CURRENT_TIMESTAMP,'preserve'),(2,null,CURRENT_TIMESTAMP,'legacy'),(3,999,CURRENT_TIMESTAMP,'orphan')");
   String sql=Files.readString(Path.of("migration/001_project_memberships.sql")).replaceAll("(?m)--.*$", "");
   for(String part:sql.split(";")) if(part.trim().equals("START TRANSACTION")) c.setAutoCommit(false); else if(!part.isBlank()) s.execute(part);
   try(var rs=s.executeQuery("select count(*) from project_memberships where member_id=1 and role='LEAD'")){rs.next();assertEquals(1,rs.getInt(1));}
   for(String part:sql.split(";")) if(part.trim().equals("START TRANSACTION")) c.setAutoCommit(false); else if(!part.isBlank()) s.execute(part);
   try(var rs=s.executeQuery("select count(*) from project_memberships")){rs.next();assertEquals(1,rs.getInt(1));}
   s.execute("update project_memberships set member_id=2");
   for(String part:sql.split(";")) if(part.trim().equals("START TRANSACTION")) c.setAutoCommit(false); else if(!part.isBlank()) s.execute(part);
   try(var rs=s.executeQuery("select member_id from project_memberships")){rs.next();assertEquals(2,rs.getLong(1));}
   try(var rs=s.executeQuery("select count(*) from projects")){rs.next();assertEquals(3,rs.getInt(1));}
   try(var rs=s.executeQuery("select name,creator_id from projects where id=1")){rs.next();assertEquals("preserve",rs.getString(1));assertEquals(1,rs.getLong(2));}
   assertThrows(SQLException.class,()->s.execute("insert into project_memberships(project_id,member_id,role,joined_at) values(1,2,'MEMBER',CURRENT_TIMESTAMP)"));
  }
 }
}
