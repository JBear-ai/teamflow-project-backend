package com.example.test1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginReqDto {

    @NotBlank
    private String email;

    @NotBlank
    private String password;
}