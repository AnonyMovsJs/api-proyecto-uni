package com.ronald.proyecto.proyecto_uni.service;

import java.util.List;

import com.ronald.proyecto.proyecto_uni.entity.User;
import com.ronald.proyecto.proyecto_uni.models.UserRequest;


public interface UserService {

    List<User> findAll();

    User findById(Integer id);

    User save(User user);

    User actualizarPagina(UserRequest user, Integer id);

    void deleteById(Integer id);

}
