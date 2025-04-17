package com.ronald.proyecto.proyecto_uni.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ronald.proyecto.proyecto_uni.entity.Role;
import com.ronald.proyecto.proyecto_uni.entity.User;
import com.ronald.proyecto.proyecto_uni.models.UserIsAdmin;
import com.ronald.proyecto.proyecto_uni.models.UserRequest;
import com.ronald.proyecto.proyecto_uni.repository.RoleRepository;
import com.ronald.proyecto.proyecto_uni.repository.UserRepository;
import com.ronald.proyecto.proyecto_uni.service.UserService;

import jakarta.persistence.EntityNotFoundException;


@Service
public class UserServiceImpl implements UserService {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private RoleRepository roleRepository;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
            RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public List<User> findAll() {

        List<User> users = userRepository.findAll();

        if (users.isEmpty()) {
            throw new EntityNotFoundException("No hay datos disponibles");
        }

        return users;
    }

/*     @Transactional(readOnly = true)
    @Override
    public Page<User> findAll(Pageable pageable) {
        return this.userRepository.findAll(pageable);
    } */

    @Transactional(readOnly = true)
    @Override
    public User findById(Integer id) {

        User userExist = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe el usuario con el id: " + id));
        return userExist;
    }

    @Transactional
    @Override
    public User save(User user) {

        if (user == null) {
            throw new IllegalArgumentException("El usuario no puede ser nulo");
        }

        List<Role> roles = getRoles(user);

        user.setRoles(roles);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    @Transactional
    @Override
    public User actualizarPagina(UserRequest user, Integer id) {

        if (user == null || id == null) {
            throw new IllegalArgumentException("Los datos del usuario no pueden ser nulos");
        }

        Optional<User> userExist = userRepository.findById(id);

        if (!userExist.isPresent()) {
            throw new EntityNotFoundException("El usuario no existe");
        }

        User userActualizado = userExist.get();

        userActualizado.setName(user.getName());
        userActualizado.setLastname(user.getLastname());
        userActualizado.setEmail(user.getEmail());
        userActualizado.setUsername(user.getUsername());

        List<Role> roles = getRoles(user);

        userActualizado.setRoles(roles);
        return userRepository.save(userActualizado);
    }

    @Transactional
    @Override
    public void deleteById(Integer id) {

        if (id == null) {
            throw new IllegalArgumentException("El id no puede ser nulo");
        }

        if (!userRepository.existsById(id)) {
            throw new EntityNotFoundException("No existe un usuario con id: " + id);
        }

        userRepository.deleteById(id);
    }


    private List<Role> getRoles(UserIsAdmin user) {
        List<Role> roles = new ArrayList<>();
        Optional<Role> roleOptionalUser = roleRepository.findByName("ROLE_USER");
        roleOptionalUser.ifPresent(roles::add);

        if (user.isAdmin()) {
            Optional<Role> roleOptionalAdmin = roleRepository.findByName("ROLE_ADMIN");
            roleOptionalAdmin.ifPresent(roles::add);
        }
        return roles;
    }

}
