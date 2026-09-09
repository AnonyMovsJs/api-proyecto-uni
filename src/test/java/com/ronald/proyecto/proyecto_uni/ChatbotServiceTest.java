package com.ronald.proyecto.proyecto_uni;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ronald.proyecto.proyecto_uni.entity.Credito;
import com.ronald.proyecto.proyecto_uni.entity.Cuota;
import com.ronald.proyecto.proyecto_uni.entity.User;
import com.ronald.proyecto.proyecto_uni.entity.Venta;
import com.ronald.proyecto.proyecto_uni.models.ChatRequest;
import com.ronald.proyecto.proyecto_uni.models.ChatResponse;
import com.ronald.proyecto.proyecto_uni.models.UserRequest;
import com.ronald.proyecto.proyecto_uni.repository.CuotaRepository;
import com.ronald.proyecto.proyecto_uni.repository.UserRepository;
import com.ronald.proyecto.proyecto_uni.service.UserService;
import com.ronald.proyecto.proyecto_uni.service.impl.ChatbotServiceImpl;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ChatbotServiceTest {

    @Mock
    private CuotaRepository cuotaRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ChatbotServiceImpl chatbotService;

    private User sampleUser;
    private Cuota sampleCuota;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(5);
        sampleUser.setName("Anthony");
        sampleUser.setLastname("RAYAS");
        sampleUser.setDni("12345678");
        sampleUser.setPhone("978676038");
        sampleUser.setAddress("Av. Los Frutales 123");

        Venta venta = new Venta();
        venta.setCliente(sampleUser);

        Credito credito = new Credito();
        credito.setVenta(venta);

        sampleCuota = new Cuota();
        sampleCuota.setId(10L);
        sampleCuota.setCredito(credito);
        sampleCuota.setNumeroCuota(1);
        sampleCuota.setMonto(new BigDecimal("250.00"));
        sampleCuota.setFechaVencimiento(LocalDate.now().minusDays(5));
        sampleCuota.setEstado(Cuota.EstadoCuota.PENDIENTE);
    }

    @Test
    void testAdminConsultarDeudores() {
        when(cuotaRepository.findAll()).thenReturn(List.of(sampleCuota));

        ChatRequest req = new ChatRequest();
        req.setMessage("¿Quién no paga?");
        req.setUserRole("ADMIN");
        req.setUserId("1");

        ChatResponse res = chatbotService.processChatMessage(req);
        assertNotNull(res);
        assertTrue(res.isSuccess());
        assertTrue(res.isRequiresAction());
        assertEquals("QUERY", res.getActionType());
        assertTrue(res.getMessage().contains("Anthony RAYAS"));
        assertTrue(res.getMessage().contains("250.00"));
    }

    @Test
    void testSecurityBlockRegularUserOnAdminQuery() {
        ChatRequest req = new ChatRequest();
        req.setMessage("¿Quién no paga?");
        req.setUserRole("USER");
        req.setUserId("5");

        ChatResponse res = chatbotService.processChatMessage(req);
        assertNotNull(res);
        assertTrue(res.isSuccess());
        assertTrue(res.getMessage().contains("Acceso Restringido"));
        assertFalse(res.isRequiresAction());
    }

    @Test
    void testUserConsultarDatosPropios() {
        when(userRepository.findById(5)).thenReturn(Optional.of(sampleUser));
        when(cuotaRepository.findCuotasByClienteIdOrdered(5L)).thenReturn(List.of(sampleCuota));

        ChatRequest req = new ChatRequest();
        req.setMessage("¿Cuánto debo en total?");
        req.setUserRole("USER");
        req.setUserId("5");

        ChatResponse res = chatbotService.processChatMessage(req);
        assertNotNull(res);
        assertTrue(res.isSuccess());
        assertTrue(res.isRequiresAction());
        assertTrue(res.getMessage().contains("250.00"));
    }

    @Test
    void testAdminConsultarClienteEspecifico() {
        when(userRepository.findByDni("12345678")).thenReturn(Optional.of(sampleUser));
        when(cuotaRepository.findCuotasByClienteIdOrdered(5L)).thenReturn(List.of(sampleCuota));

        ChatRequest req = new ChatRequest();
        req.setMessage("¿Cuánto debe el cliente con DNI 12345678?");
        req.setUserRole("ADMIN");
        req.setUserId("1");

        ChatResponse res = chatbotService.processChatMessage(req);
        assertNotNull(res);
        assertTrue(res.isSuccess());
        assertTrue(res.isRequiresAction());
        assertTrue(res.getMessage().contains("Anthony RAYAS"));
        assertTrue(res.getMessage().contains("250.00"));
    }

    @Test
    void testWizardRegistrarClienteCompleto() {
        when(userRepository.findByDni("87654321")).thenReturn(Optional.empty());
        User nuevo = new User();
        nuevo.setId(99);
        nuevo.setName("Carlos");
        nuevo.setLastname("Gomez");
        nuevo.setDni("87654321");
        nuevo.setPhone("987654321");
        nuevo.setAddress("Calle Lima 450");
        nuevo.setEmail("carlos.gomez@empresa.com");

        when(userService.save(any(UserRequest.class))).thenReturn(nuevo);

        // Paso 1: Inicia
        ChatRequest req1 = new ChatRequest();
        req1.setMessage("Quiero registrar un cliente");
        req1.setUserRole("ADMIN");
        req1.setUserId("admin1");

        ChatResponse res1 = chatbotService.processChatMessage(req1);
        assertTrue(res1.isSuccess());
        assertTrue(res1.getMessage().contains("Nombre y Apellidos"));

        // Paso 2: Envía nombre
        ChatRequest req2 = new ChatRequest();
        req2.setMessage("Carlos Gomez");
        req2.setUserRole("ADMIN");
        req2.setUserId("admin1");

        ChatResponse res2 = chatbotService.processChatMessage(req2);
        assertTrue(res2.isSuccess());
        assertTrue(res2.getMessage().contains("DNI"));

        // Paso 3: Envía DNI
        ChatRequest req3 = new ChatRequest();
        req3.setMessage("87654321");
        req3.setUserRole("ADMIN");
        req3.setUserId("admin1");

        ChatResponse res3 = chatbotService.processChatMessage(req3);
        assertTrue(res3.isSuccess());
        assertTrue(res3.getMessage().contains("celular"));

        // Paso 4: Envía celular
        ChatRequest req4 = new ChatRequest();
        req4.setMessage("987654321");
        req4.setUserRole("ADMIN");
        req4.setUserId("admin1");

        ChatResponse res4 = chatbotService.processChatMessage(req4);
        assertTrue(res4.isSuccess());
        assertTrue(res4.getMessage().contains("direccion") || res4.getMessage().contains("domicilio"));

        // Paso 5: Envía dirección
        ChatRequest req5 = new ChatRequest();
        req5.setMessage("Calle Lima 450");
        req5.setUserRole("ADMIN");
        req5.setUserId("admin1");

        ChatResponse res5 = chatbotService.processChatMessage(req5);
        assertTrue(res5.isSuccess());
        assertTrue(res5.isRequiresAction());
        assertEquals("CREATE_USER", res5.getActionType());
        assertTrue(res5.getMessage().contains("Carlos Gomez"));
        assertTrue(res5.getMessage().contains("carlos.gomez@empresa.com"));
    }
}