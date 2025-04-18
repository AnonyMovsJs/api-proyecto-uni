package com.ronald.proyecto.proyecto_uni.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ronald.proyecto.proyecto_uni.entity.User;
import com.ronald.proyecto.proyecto_uni.models.UserRequest;
import com.ronald.proyecto.proyecto_uni.repository.UserRepository;
import com.ronald.proyecto.proyecto_uni.service.UserService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;

@CrossOrigin(origins = "http://localhost:4200/")
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    UserService userService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping()
    public ResponseEntity<Object> findAll() {
        try {
            List<User> users = userService.findAll();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/page/{page}")
    public ResponseEntity<Object> findAllPageable(@PathVariable Integer page) {
        try {
            /* Pageable pageable = PageRequest.of(page, 4);
            Page<User> usersPage = userService.findAll(pageable); */
            /* return ResponseEntity.ok(usersPage); */
            List<User> users = userService.findAll();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> findById(@PathVariable Integer id) {
        try {
            User user = userService.findById(id);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping()
    public ResponseEntity<Object> save(@Valid @RequestBody User user, BindingResult result) {

        if (result.hasErrors()) {
            return validation(result);
        }

        try {

            User userSave = userService.save(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(userSave);
        } catch (Exception e) {
            /* e.printStackTrace(); */ // 👈 Esto imprimirá el error real en la consola
            /* LOGGER.severe("Error al guardar usuario: " + e.getMessage()); */
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al procesar la solicitud");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> actualizarUsuario(@Valid @RequestBody UserRequest user, BindingResult result,
            @PathVariable Integer id) {

        if (result.hasErrors()) {
            return validation(result);
        }

        try {
            User userActuzalizado = userService.actualizarPagina(user, id);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(userActuzalizado);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); // Error de validación

        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Integer id) {
        try {
            userService.deleteById(id); // No retorna nada
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @GetMapping("/profile")
    public ResponseEntity<?> getCurrentUserProfile(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        return ResponseEntity.ok(user);
    }

    private ResponseEntity<Object> validation(BindingResult result) {
        Map<String, String> errors = new HashMap<>();
        result.getFieldErrors().forEach(error -> {
            errors.put(error.getField(), "El campo " + error.getField() + ' ' + error.getDefaultMessage());
        });

        return ResponseEntity.badRequest().body(errors);
    }

}
