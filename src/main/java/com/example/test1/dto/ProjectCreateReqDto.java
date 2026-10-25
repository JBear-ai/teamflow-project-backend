package com.example.test1.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class ProjectCreateReqDto {
    private String name;
    private String description;
    private LocalDate targetEndDate;
}