package com.ronald.proyecto.proyecto_uni.models;

import jakarta.persistence.Column;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class UserRequest implements UserIsAdmin { // El userIsAdmin para los roles para que no haya problema en el save
                                                  // y update

    @Column(name = "name")
    @NotBlank
    private String name;

    @Column(name = "lastname")
    @NotBlank
    private String lastname;

    @Column(name = "email", unique = true, nullable = false)
    @NotBlank
    @Email
    private String email;

    private boolean admin;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }


    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

}
