package com.ronald.proyecto.proyecto_uni.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ronald.proyecto.proyecto_uni.entity.User;


public interface UserRepository extends JpaRepository<User, Integer> {

    /* @SuppressWarnings("null")
    Page<User> findAll(Pageable pageable); */

    
    Optional<User> findByEmail(String email);

    Optional<User> findByDni(String dni);

    Optional<User> findFirstByDni(String dni);

    java.util.List<User> findByNameContainingIgnoreCaseOrLastnameContainingIgnoreCase(String name, String lastname);

}
