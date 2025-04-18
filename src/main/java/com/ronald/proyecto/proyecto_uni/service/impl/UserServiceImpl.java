package com.ronald.proyecto.proyecto_uni.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.springframework.security.core.Authentication;
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

        if (id == null) {
            throw new IllegalArgumentException("El id no puede ser nulo");
        }

        User userExist = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el usuario con el id: " + id));
        return userExist;
    }

    @Transactional
    @Override
    public User save(User user) {

        String emailName = user.getName().toLowerCase().replaceAll("\\s+", "");
        String emailLastname = user.getLastname().toLowerCase().replaceAll("\\s+", "");
        String generatedEmail = String.format("%s.%s@empresa.com", emailName, emailLastname);
        
        if (userRepository.findByEmail(generatedEmail).isPresent()) {
            Random random = new Random();
            int randomNum = random.nextInt(1000);
            generatedEmail = String.format("%s.%s%d@empresa.com", emailName, emailLastname, randomNum);
        }
        
        String generatedPassword = user.getDni();
        
        User newUser = new User();
        newUser.setName(user.getName());
        newUser.setLastname(user.getLastname());
        newUser.setDni(user.getDni());
        newUser.setPhone(user.getPhone());
        newUser.setAddress(user.getAddress());
        newUser.setEmail(generatedEmail);

        List<Role> roles = getRoles(newUser);
        newUser.setRoles(roles);
        newUser.setPassword(passwordEncoder.encode(generatedPassword));
        return userRepository.save(newUser);
    }

    @Transactional
    @Override
    public User actualizarPagina(UserRequest user, Integer id) {

        if (id == null) {
            throw new IllegalArgumentException("El id no puede ser null");
        }

        Optional<User> userExist = userRepository.findById(id);

        if (!userExist.isPresent()) {
            throw new EntityNotFoundException("El usuario no existe");
        }

        User userActualizado = userExist.get();

        userActualizado.setName(user.getName());
        userActualizado.setLastname(user.getLastname());
        userActualizado.setEmail(user.getEmail());

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


    public User getUserProfile(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return user;
    }

}
