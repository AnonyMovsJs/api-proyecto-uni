package com.ronald.proyecto.proyecto_uni.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ronald.proyecto.proyecto_uni.entity.Role;


public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByName(String name);
}
