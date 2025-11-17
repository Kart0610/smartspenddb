package com.example.smartspendapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationDto {
    @NotBlank
    private String name;

    @Email @NotBlank
    private String email;

    @NotBlank @Size(min=6)
    private String password;

    // getters & setters
    public String getName(){return name;}
    public void setName(String name){this.name=name;}
    public String getEmail(){return email;}
    public void setEmail(String email){this.email=email;}
    public String getPassword(){return password;}
    public void setPassword(String password){this.password=password;}
}