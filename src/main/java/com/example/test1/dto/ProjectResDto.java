package com.example.test1.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class ProjectResDto {
    private Long id;
    private String name;
    private String description;
    private LocalDate targetEndDate;
    private String status;
    private LocalDateTime createdAt;
}