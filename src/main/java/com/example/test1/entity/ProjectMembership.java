package com.example.test1.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
@Entity
@Table(name="project_memberships", uniqueConstraints=@UniqueConstraint(name="uk_project_member", columnNames={"project_id","member_id"}))
@Getter @NoArgsConstructor
public class ProjectMembership {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="project_id", nullable=false) private Project project;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="member_id", nullable=false) private Member member;
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ProjectRole role;
 @Column(nullable=false,updatable=false) private LocalDateTime joinedAt;
 public ProjectMembership(Project project, Member member, ProjectRole role) {
  this.project=project; this.member=member; this.role=role; this.joinedAt=LocalDateTime.now();
 }
 public void changeRole(ProjectRole role) { this.role=role; }
}
