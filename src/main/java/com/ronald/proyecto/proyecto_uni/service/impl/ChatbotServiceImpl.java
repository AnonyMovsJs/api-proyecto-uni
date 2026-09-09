package com.ronald.proyecto.proyecto_uni.service.impl;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronald.proyecto.proyecto_uni.OpenAIProperties;
import com.ronald.proyecto.proyecto_uni.entity.Credito;
import com.ronald.proyecto.proyecto_uni.entity.Cuota;
import com.ronald.proyecto.proyecto_uni.entity.User;
import com.ronald.proyecto.proyecto_uni.models.ChatRequest;
import com.ronald.proyecto.proyecto_uni.models.ChatResponse;
import com.ronald.proyecto.proyecto_uni.models.ConversationState;
import com.ronald.proyecto.proyecto_uni.models.UserRequest;
import com.ronald.proyecto.proyecto_uni.repository.CuotaRepository;
import com.ronald.proyecto.proyecto_uni.repository.UserRepository;
import com.ronald.proyecto.proyecto_uni.service.ChatbotService;
import com.ronald.proyecto.proyecto_uni.service.UserService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;

@Service
public class ChatbotServiceImpl implements ChatbotService {

    private final CuotaRepository cuotaRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final OpenAiService openAiService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ConversationState> conversationStates = new ConcurrentHashMap<>();
    private final Map<String, List<ChatMessage>> userConversations = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    public ChatbotServiceImpl(
            CuotaRepository cuotaRepository,
            UserRepository userRepository,
            UserService userService,
            @Autowired(required = false) OpenAIProperties openAIProperties) {
        this.cuotaRepository = cuotaRepository;
        this.userRepository = userRepository;
        this.userService = userService;

        OpenAiService service = null;
        try {
            if (openAIProperties != null && openAIProperties.getApi() != null) {
                String key = openAIProperties.getApi().getKey();
                if (key != null && !key.isBlank() && !key.startsWith("${") && key.startsWith("sk-")) {
                    service = new OpenAiService(key);
                }
            }
        } catch (Exception e) {
            System.err.println("Advertencia: OpenAI Service no disponible inicialmente: " + e.getMessage());
        }
        this.openAiService = service;
    }

    @Override
    public ChatResponse processChatMessage(ChatRequest request) {
        try {
            String rawMessage = request.getMessage() != null ? request.getMessage().trim() : "";
            String message = normalizeText(rawMessage);
            String userRole = request.getUserRole() != null ? request.getUserRole().toUpperCase() : "USER";
            String userId = request.getUserId() != null ? request.getUserId() : "0";

            boolean isAdmin = "ADMIN".equals(userRole);
            ConversationState state = getConversationState(userId);

            // 1. Cancelar operacion actual
            if (esCancelacion(message)) {
                state.clearContext();
                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage("Operacion cancelada con exito. ¿En que mas puedo ayudarte hoy?");
                return response;
            }

            // 2. Wizard de registro en progreso
            if ("CREATING_USER".equals(state.getCurrentState())) {
                if (!isAdmin) {
                    state.clearContext();
                    return buildSecurityDeniedResponse();
                }
                return processClientRegistrationWizard(rawMessage, message, state);
            }

            // 3. Control de Acceso Estricto (RBAC Guard)
            if (!isAdmin) {
                if (esIntencionAdminOAjena(message)) {
                    return buildSecurityDeniedResponse();
                }
            }

            // 4. Saludos y ayuda
            if (esSaludoOAyuda(message)) {
                return buildWelcomeResponse(isAdmin);
            }

            // 5. Iniciar Wizard de Registro de Cliente (ADMIN)
            if (isAdmin && esIntencionRegistrarCliente(message)) {
                state.setCurrentState("CREATING_USER");
                return processClientRegistrationWizard(rawMessage, message, state);
            }

            // 6. Consulta específica: ¿Quién es el próximo a pagar / vencer?
            if (isAdmin && esConsultaProximoAPagar(message)) {
                return consultarProximoAPagar();
            }

            // 6b. Consultas de Deudores / Quien no paga (ADMIN)
            if (isAdmin && esConsultaDeudores(message)) {
                return consultarDeudoresGlobales();
            }

            // 7. Consultas sobre un Cliente Específico (ADMIN)
            if (isAdmin) {
                User cliente = detectarClienteEnMensaje(rawMessage, message);
                if (cliente != null) {
                    return consultarEstadoCliente(cliente);
                }
            }

            // 8. Consultas del Cliente sobre sí mismo (USER)
            if (esConsultaPropia(message) || !isAdmin) {
                if (esConsultaPropia(message)) {
                    return consultarDatosPropiosCliente(userId);
                }
            }

            // 9. Procesamiento Inteligente con LLM (RAG con Contexto de BD)
            return procesarConsultaConInteligenciaArtificial(rawMessage, message, isAdmin, userId);

        } catch (Exception e) {
            System.err.println("Error procesando mensaje en ChatbotServiceImpl: " + e.getMessage());
            e.printStackTrace();
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Ocurrió un inconveniente al procesar tu solicitud. Por favor intenta reformular tu pregunta.");
            return errorResponse;
        }
    }

    // ==========================================
    //  WIZARD INTERACTIVO DE REGISTRO DE CLIENTE
    // ==========================================
    private ChatResponse processClientRegistrationWizard(String rawInput, String normInput, ConversationState state) {
        String input = rawInput.trim();
        Map<String, Object> ctx = state.getContextData();

        extraerEntidadesEnContexto(input, ctx);

        int step = state.getStep();
        String name = (String) ctx.get("name");
        String lastname = (String) ctx.get("lastname");
        String dni = (String) ctx.get("dni");
        String phone = (String) ctx.get("phone");
        String address = (String) ctx.get("address");

        // Si es el inicio del wizard
        if (step == 0) {
            extraerEntidadesEnContexto(input, ctx);

            // Si el usuario en el primer mensaje ya pasó DNI y tenemos nombre
            if (ctx.containsKey("name") && ctx.containsKey("lastname")) {
                if (ctx.containsKey("dni")) {
                    // Validar si el DNI ya existe
                    String dniVal = (String) ctx.get("dni");
                    Optional<User> existing = userRepository.findByDni(dniVal);
                    if (existing.isPresent()) {
                        state.clearContext();
                        ChatResponse response = new ChatResponse();
                        response.setSuccess(true);
                        response.setMessage("El DNI **" + dniVal + "** ya se encuentra registrado a nombre de **" +
                                existing.get().getName() + " " + existing.get().getLastname() + "**.\n" +
                                "Operación detenida. ¿Deseas consultar su estado o registrar otro cliente?");
                        return response;
                    }
                    if (ctx.containsKey("phone")) {
                        state.setStep(4); // Solo falta dirección
                        ChatResponse response = new ChatResponse();
                        response.setSuccess(true);
                        response.setMessage("Datos recibidos para **" + ctx.get("name") + " " + ctx.get("lastname") + "** " +
                                "(DNI: " + dniVal + ", Tel: " + ctx.get("phone") + ").\n" +
                                "Por último, ¿cuál es su **dirección o domicilio**?");
                        return response;
                    } else {
                        state.setStep(3); // Pedir teléfono
                        ChatResponse response = new ChatResponse();
                        response.setSuccess(true);
                        response.setMessage("Excelente. Registrando a **" + ctx.get("name") + " " + ctx.get("lastname") + "** con DNI **" + dniVal + "**.\n" +
                                "Ahora por favor indícame su **teléfono celular** (9 dígitos):");
                        return response;
                    }
                } else {
                    state.setStep(2); // Pedir DNI
                    ChatResponse response = new ChatResponse();
                    response.setSuccess(true);
                    response.setMessage("Muy bien para **" + ctx.get("name") + " " + ctx.get("lastname") + "**.\n" +
                            "Ahora facilítame su número de **DNI** (debe tener exactamente 8 dígitos):");
                    return response;
                }
            }

            state.setStep(1); // Esperando Nombre y Apellidos
            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setMessage("Registro de Nuevo Cliente (Comercial Reyes)\n\n" +
                    "¡Excelente! Vamos a dar de alta al cliente paso a paso.\n" +
                    "Por favor, indícame su **Nombre y Apellidos completos**:");
            return response;
        }

        // Paso 1: Procesar Nombre y Apellidos
        if (step == 1) {
            String[] partes = input.split("\\s+");
            if (partes.length >= 2) {
                ctx.put("name", capitalize(partes[0]));
                ctx.put("lastname", capitalize(String.join(" ", Arrays.copyOfRange(partes, 1, partes.length))));
                state.setStep(2); // Siguiente: DNI
                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage("Muy bien para **" + ctx.get("name") + " " + ctx.get("lastname") + "**.\n" +
                        "Ahora facilítame su número de **DNI** (debe tener exactamente 8 dígitos):");
                return response;
            } else if (partes.length == 1 && partes[0].length() > 2) {
                ctx.put("name", capitalize(partes[0]));
                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage("Por favor, indícame los **Apellidos** del cliente " + partes[0] + ":");
                return response;
            } else {
                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage("Por favor, ingresa un nombre válido (Nombre y Apellidos):");
                return response;
            }
        }

        // Paso 2: Procesar DNI
        if (step == 2) {
            Pattern dniPattern = Pattern.compile("\\b([0-9]{8})\\b");
            Matcher matcher = dniPattern.matcher(input);
            if (matcher.find()) {
                String inputDni = matcher.group(1);
                Optional<User> existingUser = userRepository.findByDni(inputDni);
                if (existingUser.isPresent()) {
                    User u = existingUser.get();
                    state.clearContext();
                    ChatResponse response = new ChatResponse();
                    response.setSuccess(true);
                    response.setMessage("El DNI **" + inputDni + "** ya se encuentra registrado a nombre de **" +
                            u.getName() + " " + u.getLastname() + "**.\n" +
                            "Operacion detenida. ¿Deseas consultar su estado o registrar otro cliente?");
                    return response;
                }
                ctx.put("dni", inputDni);
                state.setStep(3); // Siguiente: Celular
                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage("Perfecto. Ahora por favor indicame su **telefono celular** (9 digitos):");
                return response;
            } else {
                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage("El DNI debe contener exactamente 8 digitos numericos. Por favor ingresalo nuevamente:");
                return response;
            }
        }

        // Paso 3: Procesar Teléfono Celular
        if (step == 3) {
            Pattern phonePattern = Pattern.compile("\\b(9[0-9]{8}|[0-9]{9})\\b");
            Matcher matcher = phonePattern.matcher(input);
            if (matcher.find()) {
                ctx.put("phone", matcher.group(1));
                state.setStep(4); // Siguiente: Dirección
                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage("Ya casi terminamos. ¿Cual es la **direccion o domicilio** del cliente?");
                return response;
            } else {
                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage("El telefono celular debe tener 9 digitos (generalmente inicia con 9). Por favor indicalo:");
                return response;
            }
        }

        // Paso 4: Procesar Dirección
        if (step == 4) {
            if (input.length() >= 4) {
                ctx.put("address", input);
            } else {
                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage("Por favor indica una direccion o referencia valida:");
                return response;
            }
        }

        name = (String) ctx.get("name");
        lastname = (String) ctx.get("lastname");
        dni = (String) ctx.get("dni");
        phone = (String) ctx.get("phone");
        address = (String) ctx.get("address");

        try {
            UserRequest userRequest = new UserRequest();
            userRequest.setName(name);
            userRequest.setLastname(lastname);
            userRequest.setDni(dni);
            userRequest.setPhone(phone);
            userRequest.setAddress(address);
            userRequest.setAdmin(false);

            User savedUser = userService.save(userRequest);
            state.clearContext();

            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setRequiresAction(true);
            response.setActionType("CREATE_USER");

            Map<String, Object> userData = new HashMap<>();
            userData.put("id", savedUser.getId());
            userData.put("nombre", savedUser.getName());
            userData.put("apellido", savedUser.getLastname());
            userData.put("dni", savedUser.getDni());
            userData.put("phone", savedUser.getPhone());
            userData.put("email", savedUser.getEmail());
            userData.put("address", savedUser.getAddress());
            response.setActionData(userData);

            response.setMessage("¡Cliente registrado exitosamente en Comercial Reyes!\n\n" +
                    "• **Nombre:** " + savedUser.getName() + " " + savedUser.getLastname() + "\n" +
                    "• **DNI:** " + savedUser.getDni() + "\n" +
                    "• **Telefono:** " + savedUser.getPhone() + "\n" +
                    "• **Direccion:** " + savedUser.getAddress() + "\n" +
                    "• **Correo generado:** `" + savedUser.getEmail() + "`\n" +
                    "• **Clave inicial:** DNI del cliente.\n\n" +
                    "Ya se encuentra habilitado para realizar compras a credito o fiado.");
            return response;

        } catch (Exception ex) {
            state.clearContext();
            ChatResponse response = new ChatResponse();
            response.setSuccess(false);
            response.setMessage("Error al registrar el cliente en el sistema: " + ex.getMessage());
            return response;
        }
    }

    private void extraerEntidadesEnContexto(String input, Map<String, Object> ctx) {
        // Extraer DNI (8 dígitos)
        Pattern dniPattern = Pattern.compile("\\b([0-9]{8})\\b");
        Matcher dniMatcher = dniPattern.matcher(input);
        if (dniMatcher.find() && !ctx.containsKey("dni")) {
            ctx.put("dni", dniMatcher.group(1));
        }

        // Extraer teléfono celular (9 dígitos)
        Pattern phonePattern = Pattern.compile("\\b(9[0-9]{8}|[0-9]{9})\\b");
        Matcher phoneMatcher = phonePattern.matcher(input);
        if (phoneMatcher.find() && !ctx.containsKey("phone")) {
            ctx.put("phone", phoneMatcher.group(1));
        }

        // Extraer dirección
        Pattern addrPattern = Pattern.compile("(?:direccion|dirección|vive en|domicilio)\\s*[:=]?\\s*([^,\\.;]+)", Pattern.CASE_INSENSITIVE);
        Matcher addrMatcher = addrPattern.matcher(input);
        if (addrMatcher.find() && !ctx.containsKey("address")) {
            ctx.put("address", addrMatcher.group(1).trim());
        }

        // Extraer nombre cuando viene de dictado de voz como "cliente llamado Juan Perez", "registrar a Juan Perez", etc.
        if (!ctx.containsKey("name")) {
            Pattern namePattern = Pattern.compile("(?:llamado|llamada|cliente|registrar a|agrega a)\\s+([a-zA-ZáéíóúÁÉÍÓÚñÑ]+(?:\\s+[a-zA-ZáéíóúÁÉÍÓÚñÑ]+)+)", Pattern.CASE_INSENSITIVE);
            Matcher nameMatcher = namePattern.matcher(input);
            if (nameMatcher.find()) {
                String fullMatch = nameMatcher.group(1).trim();
                // Limpiar posibles palabras conectoras como "con dni", "telefono", etc.
                fullMatch = fullMatch.replaceAll("(?i)\\s+(?:con|de|dni|telefono|celular|direccion|y)\\b.*", "").trim();
                String[] partes = fullMatch.split("\\s+");
                if (partes.length >= 2) {
                    ctx.put("name", capitalize(partes[0]));
                    ctx.put("lastname", capitalize(String.join(" ", Arrays.copyOfRange(partes, 1, partes.length))));
                }
            }
        }
    }

    // ==========================================
    //  CONSULTA GLOBAL DE DEUDORES (ADMIN)
    // ==========================================
    private ChatResponse consultarDeudoresGlobales() {
        List<Cuota> todasCuotas = cuotaRepository.findAll();
        List<Cuota> cuotasActivas = todasCuotas != null ? todasCuotas.stream()
                .filter(c -> c.getEstado() == Cuota.EstadoCuota.PENDIENTE || c.getEstado() == Cuota.EstadoCuota.VENCIDO)
                .collect(Collectors.toList()) : Collections.emptyList();

        if (cuotasActivas.isEmpty()) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setMessage("¡Excelentes noticias! En este momento no hay clientes con deudas pendientes en Comercial Reyes. Todas las cuentas estan al dia.");
            return response;
        }

        Map<User, List<Cuota>> cuotasPorCliente = new HashMap<>();
        for (Cuota c : cuotasActivas) {
            Credito cr = c.getCredito();
            if (cr != null && cr.getVenta() != null && cr.getVenta().getCliente() != null) {
                User cliente = cr.getVenta().getCliente();
                cuotasPorCliente.computeIfAbsent(cliente, k -> new ArrayList<>()).add(c);
            }
        }

        if (cuotasPorCliente.isEmpty()) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setMessage("No se encontraron registros activos de deuda asociados a clientes.");
            return response;
        }

        LocalDate hoy = LocalDate.now();
        List<Map<String, Object>> tablaDeudores = new ArrayList<>();
        BigDecimal totalDeudaGlobal = BigDecimal.ZERO;
        int totalClientesDeudores = cuotasPorCliente.size();

        for (Map.Entry<User, List<Cuota>> entry : cuotasPorCliente.entrySet()) {
            User cliente = entry.getKey();
            List<Cuota> cuotas = entry.getValue();

            BigDecimal totalCliente = cuotas.stream()
                    .map(Cuota::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalDeudaGlobal = totalDeudaGlobal.add(totalCliente);

            Cuota cuotaMasAntigua = cuotas.stream()
                    .min(Comparator.comparing(Cuota::getFechaVencimiento))
                    .orElse(cuotas.get(0));

            long diasAtraso = ChronoUnit.DAYS.between(cuotaMasAntigua.getFechaVencimiento(), hoy);
            boolean tieneMora = diasAtraso > 0;
            long cuotasVencidas = cuotas.stream()
                    .filter(c -> c.getEstado() == Cuota.EstadoCuota.VENCIDO || c.getFechaVencimiento().isBefore(hoy))
                    .count();

            String estadoTexto = diasAtraso > 30 ? "CRITICO" : (tieneMora ? "EN MORA" : "POR VENCER");

            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("cliente", cliente.getName() + " " + cliente.getLastname());
            fila.put("dni", cliente.getDni());
            fila.put("telefono", cliente.getPhone());
            fila.put("cuotasPendientes", cuotas.size());
            fila.put("cuotasVencidas", cuotasVencidas);
            fila.put("totalDeuda", "S/. " + totalCliente.setScale(2));
            fila.put("desdeCuando", cuotaMasAntigua.getFechaVencimiento().format(DATE_FORMATTER));
            fila.put("diasAtraso", diasAtraso > 0 ? diasAtraso + " dias" : "Al dia");
            fila.put("estado", estadoTexto);
            tablaDeudores.add(fila);
        }

        long clientesCriticos = tablaDeudores.stream().filter(f -> "CRITICO".equals(f.get("estado"))).count();
        long clientesEnMora = tablaDeudores.stream().filter(f -> "EN MORA".equals(f.get("estado"))).count();
        long clientesPorVencer = tablaDeudores.stream().filter(f -> "POR VENCER".equals(f.get("estado"))).count();

        StringBuilder sb = new StringBuilder();
        sb.append("📊 **Resumen de Cartera Crediticia - Comercial Reyes**\n\n");
        sb.append("Se identificaron **").append(totalClientesDeudores).append(" clientes** con pagos pendientes:\n");
        sb.append("• 🔴 **Críticos (> 30 días atraso):** ").append(clientesCriticos).append(" clientes\n");
        sb.append("• 🟡 **En Mora reciente:** ").append(clientesEnMora).append(" clientes\n");
        sb.append("• 🟢 **Por Vencer:** ").append(clientesPorVencer).append(" clientes\n");
        sb.append("• 💰 **Monto Total por Cobrar:** **S/. ").append(totalDeudaGlobal.setScale(2)).append("**\n\n");
        sb.append("He generado la tabla interactiva de **Datos del Sistema** a continuación para consultar y ordenar el detalle:");

        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setMessage(sb.toString());
        response.setRequiresAction(true);
        response.setActionType("QUERY");
        response.setActionData(tablaDeudores);
        return response;
    }

    // ==========================================
    //  CONSULTA ESPECÍFICA: PRÓXIMO EN PAGAR (ADMIN)
    // ==========================================
    private ChatResponse consultarProximoAPagar() {
        List<Cuota> todasCuotas = cuotaRepository.findAll();
        LocalDate hoy = LocalDate.now();

        // Filtrar todas las cuotas pendientes o vencidas con cliente válido
        List<Cuota> cuotasActivas = todasCuotas != null ? todasCuotas.stream()
                .filter(c -> (c.getEstado() == Cuota.EstadoCuota.PENDIENTE || c.getEstado() == Cuota.EstadoCuota.VENCIDO)
                        && c.getCredito() != null && c.getCredito().getVenta() != null && c.getCredito().getVenta().getCliente() != null)
                .sorted(Comparator.comparing(Cuota::getFechaVencimiento))
                .collect(Collectors.toList()) : Collections.emptyList();

        if (cuotasActivas.isEmpty()) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setMessage("¡No hay pagos pendientes en el sistema! Todas las cuotas se encuentran canceladas al día.");
            return response;
        }

        // Buscar primero las cuotas futuras o de hoy ("próximas a vencer")
        Optional<Cuota> proximaOpt = cuotasActivas.stream()
                .filter(c -> !c.getFechaVencimiento().isBefore(hoy))
                .findFirst();

        Cuota cuotaSeleccionada;
        boolean esVencida = false;

        if (proximaOpt.isPresent()) {
            cuotaSeleccionada = proximaOpt.get();
        } else {
            // Si todas ya están vencidas, la más urgente es la primera de la lista
            cuotaSeleccionada = cuotasActivas.get(0);
            esVencida = true;
        }

        User cliente = cuotaSeleccionada.getCredito().getVenta().getCliente();
        long dias = ChronoUnit.DAYS.between(hoy, cuotaSeleccionada.getFechaVencimiento());

        StringBuilder sb = new StringBuilder();
        sb.append("📅 **Próximo Pago Programado - Comercial Reyes**\n\n");
        sb.append("El próximo cliente con cuota a pagar es:\n\n");
        sb.append("• 👤 **Cliente:** **").append(cliente.getName()).append(" ").append(cliente.getLastname()).append("**\n");
        sb.append("• 🆔 **DNI:** ").append(cliente.getDni()).append("\n");
        sb.append("• 📞 **Teléfono:** ").append(cliente.getPhone() != null ? cliente.getPhone() : "No registrado").append("\n");
        sb.append("• 💵 **Monto de la Cuota:** **S/. ").append(cuotaSeleccionada.getMonto().setScale(2)).append("**\n");
        sb.append("• 🗓️ **Fecha de Vencimiento:** **").append(cuotaSeleccionada.getFechaVencimiento().format(DATE_FORMATTER)).append("**\n");

        if (dias > 0) {
            sb.append("• ⏳ **Tiempo restante:** Faltan **").append(dias).append(" día(s)** para su vencimiento.\n");
        } else if (dias == 0) {
            sb.append("• ⚠️ **Estado:** **¡Vence hoy mismo!**\n");
        } else {
            sb.append("• 🚨 **Estado:** **Vencida hace ").append(Math.abs(dias)).append(" día(s)**.\n");
        }

        // Crear una fila para la tabla del sistema
        List<Map<String, Object>> filas = new ArrayList<>();
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("cliente", cliente.getName() + " " + cliente.getLastname());
        fila.put("dni", cliente.getDni());
        fila.put("telefono", cliente.getPhone());
        fila.put("cuota", "Cuota #" + cuotaSeleccionada.getNumeroCuota());
        fila.put("monto", "S/. " + cuotaSeleccionada.getMonto().setScale(2));
        fila.put("vencimiento", cuotaSeleccionada.getFechaVencimiento().format(DATE_FORMATTER));
        fila.put("estado", cuotaSeleccionada.getEstado().name());
        filas.add(fila);

        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setMessage(sb.toString());
        response.setRequiresAction(true);
        response.setActionType("QUERY");
        response.setActionData(filas);
        return response;
    }

    // ==========================================
    //  CONSULTA DE ESTADO DE UN CLIENTE (ADMIN)
    // ==========================================
    private ChatResponse consultarEstadoCliente(User cliente) {
        Long clienteId = Long.valueOf(cliente.getId());
        List<Cuota> todasCuotas = cuotaRepository.findCuotasByClienteIdOrdered(clienteId);

        if (todasCuotas == null || todasCuotas.isEmpty()) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setMessage("El cliente **" + cliente.getName() + " " + cliente.getLastname() +
                    "** (DNI: " + cliente.getDni() + ") no registra ventas a credito o cuotas en el sistema.");
            return response;
        }

        LocalDate hoy = LocalDate.now();
        List<Cuota> pendientes = todasCuotas.stream()
                .filter(c -> c.getEstado() == Cuota.EstadoCuota.PENDIENTE || c.getEstado() == Cuota.EstadoCuota.VENCIDO)
                .collect(Collectors.toList());

        List<Cuota> pagadas = todasCuotas.stream()
                .filter(c -> c.getEstado() == Cuota.EstadoCuota.PAGADO)
                .collect(Collectors.toList());

        BigDecimal totalDeuda = pendientes.stream()
                .map(Cuota::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPagado = pagadas.stream()
                .map(Cuota::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder sb = new StringBuilder();
        sb.append("Estado de Cuenta: ").append(cliente.getName()).append(" ").append(cliente.getLastname()).append("\n");
        sb.append("• **DNI:** ").append(cliente.getDni()).append(" | **Telefono:** ").append(cliente.getPhone()).append("\n");
        sb.append("• **Direccion:** ").append(cliente.getAddress()).append("\n\n");

        if (pendientes.isEmpty()) {
            sb.append("¡Cliente al dia! No tiene cuotas pendientes.\n");
            sb.append("• Total pagado historicamente: S/. ").append(totalPagado.setScale(2)).append(" (").append(pagadas.size()).append(" cuotas pagadas).\n");
        } else {
            sb.append("• **Deuda Total Pendiente:** S/. ").append(totalDeuda.setScale(2)).append("\n");
            sb.append("• **Cuotas Pendientes:** ").append(pendientes.size()).append(" cuota(s)\n");

            Cuota cuotaMasUrgente = pendientes.get(0);
            long diasAtraso = ChronoUnit.DAYS.between(cuotaMasUrgente.getFechaVencimiento(), hoy);

            if (diasAtraso > 0) {
                sb.append("⚠️ Tiene cuota vencida desde el ")
                  .append(cuotaMasUrgente.getFechaVencimiento().format(DATE_FORMATTER))
                  .append(" (**").append(diasAtraso).append(" dias de atraso**). Monto: S/. ")
                  .append(cuotaMasUrgente.getMonto().setScale(2)).append("\n");
            } else {
                sb.append("📅 **Proximo Vencimiento:** ")
                  .append(cuotaMasUrgente.getFechaVencimiento().format(DATE_FORMATTER))
                  .append(" (S/. ").append(cuotaMasUrgente.getMonto().setScale(2)).append(")\n");
            }

            sb.append("• **Total Pagado:** S/. ").append(totalPagado.setScale(2))
              .append(" (").append(pagadas.size()).append(" cuotas canceladas).\n");
        }

        List<Map<String, Object>> filasDetalle = new ArrayList<>();
        for (Cuota c : todasCuotas) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("cuota", "Cuota #" + c.getNumeroCuota());
            f.put("monto", "S/. " + c.getMonto().setScale(2));
            f.put("vencimiento", c.getFechaVencimiento().format(DATE_FORMATTER));
            f.put("estado", c.getEstado().name());
            filasDetalle.add(f);
        }

        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setMessage(sb.toString());
        response.setRequiresAction(true);
        response.setActionType("QUERY");
        response.setActionData(filasDetalle);
        return response;
    }

    // ==========================================
    //  CONSULTA DE DATOS PROPIOS (USER REGULAR)
    // ==========================================
    private ChatResponse consultarDatosPropiosCliente(String userIdentifier) {
        User cliente = buscarUsuarioPorIdentificador(userIdentifier);
        if (cliente == null) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setMessage("No pudimos asociar tu sesion con tu registro de cliente. Por favor verifica que tu sesion este activa.");
            return response;
        }

        Long clienteId = Long.valueOf(cliente.getId());
        List<Cuota> todasCuotas = cuotaRepository.findCuotasByClienteIdOrdered(clienteId);

        if (todasCuotas == null || todasCuotas.isEmpty()) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setMessage("¡Hola " + cliente.getName() + "! No registras deudas ni cuotas pendientes actualmente en Comercial Reyes.");
            return response;
        }

        LocalDate hoy = LocalDate.now();
        List<Cuota> pendientes = todasCuotas.stream()
                .filter(c -> c.getEstado() == Cuota.EstadoCuota.PENDIENTE || c.getEstado() == Cuota.EstadoCuota.VENCIDO)
                .collect(Collectors.toList());

        List<Cuota> pagadas = todasCuotas.stream()
                .filter(c -> c.getEstado() == Cuota.EstadoCuota.PAGADO)
                .collect(Collectors.toList());

        BigDecimal totalDeuda = pendientes.stream()
                .map(Cuota::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder sb = new StringBuilder();
        sb.append("Tu Estado de Cuenta (Comercial Reyes)\n\n");

        if (pendientes.isEmpty()) {
            sb.append("¡Felicidades, ").append(cliente.getName()).append("! Estas completamente al dia con tus pagos.\n");
            sb.append("• Has completado ").append(pagadas.size()).append(" cuota(s) con exito.\n");
        } else {
            sb.append("• **Tu Deuda Total Pendiente:** S/. ").append(totalDeuda.setScale(2)).append("\n");
            sb.append("• **Cuotas Pendientes por Pagar:** ").append(pendientes.size()).append(" cuota(s)\n");

            Cuota proxima = pendientes.get(0);
            long diasAtraso = ChronoUnit.DAYS.between(proxima.getFechaVencimiento(), hoy);

            if (diasAtraso > 0) {
                sb.append("⚠️ Tienes una cuota vencida desde el ")
                  .append(proxima.getFechaVencimiento().format(DATE_FORMATTER))
                  .append(" por S/. ").append(proxima.getMonto().setScale(2))
                  .append(" (").append(diasAtraso).append(" dias de atraso).\n");
            } else {
                sb.append("📅 **Tu Proxima Cuota Vence:** ")
                  .append(proxima.getFechaVencimiento().format(DATE_FORMATTER))
                  .append(" por S/. ").append(proxima.getMonto().setScale(2)).append("\n");
            }
            sb.append("• Cuotas ya canceladas: ").append(pagadas.size()).append("\n");
        }

        List<Map<String, Object>> filas = new ArrayList<>();
        for (Cuota c : todasCuotas) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("cuota", "Cuota #" + c.getNumeroCuota());
            f.put("monto", "S/. " + c.getMonto().setScale(2));
            f.put("vencimiento", c.getFechaVencimiento().format(DATE_FORMATTER));
            f.put("estado", c.getEstado().name());
            filas.add(f);
        }

        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setMessage(sb.toString());
        response.setRequiresAction(true);
        response.setActionType("QUERY");
        response.setActionData(filas);
        return response;
    }

    // ==========================================
    //  FALLBACK INTELIGENTE
    // ==========================================
    //  PROCESAMIENTO INTELIGENTE CON IA (RAG & REASONING)
    // ==========================================
    private ChatResponse procesarConsultaConInteligenciaArtificial(String rawMessage, String normMessage, boolean isAdmin, String userId) {
        if (openAiService != null) {
            try {
                StringBuilder systemPrompt = new StringBuilder();
                systemPrompt.append("Eres el Asistente Ejecutivo Inteligente de Comercial Reyes (sistema de ventas a crédito y cobranzas en Perú).\n");
                systemPrompt.append("Tu rol es interpretar cualquier pregunta del usuario en lenguaje humano, razonar sobre los datos reales del negocio y responder con precisión, empatía y estilo profesional.\n\n");

                List<Map<String, Object>> tablaDatos = new ArrayList<>();

                if (isAdmin) {
                    systemPrompt.append("ROL ACTUAL: ADMINISTRADOR (Tienes acceso a la cartera crediticia global, deudores, montos y cuotas).\n");
                    systemPrompt.append("A continuación tienes los DATOS REALES EN VIVO extraídos de la base de datos para responder cualquier consulta analítica:\n\n");

                    // Extraer datos vivos de cartera deudora
                    List<Cuota> todasCuotas = cuotaRepository.findAll();
                    LocalDate hoy = LocalDate.now();

                    if (todasCuotas != null && !todasCuotas.isEmpty()) {
                        Map<User, List<Cuota>> cuotasPorCliente = new HashMap<>();
                        for (Cuota c : todasCuotas) {
                            if ((c.getEstado() == Cuota.EstadoCuota.PENDIENTE || c.getEstado() == Cuota.EstadoCuota.VENCIDO)
                                    && c.getCredito() != null && c.getCredito().getVenta() != null && c.getCredito().getVenta().getCliente() != null) {
                                cuotasPorCliente.computeIfAbsent(c.getCredito().getVenta().getCliente(), k -> new ArrayList<>()).add(c);
                            }
                        }

                        BigDecimal totalDeudaGlobal = BigDecimal.ZERO;
                        for (Map.Entry<User, List<Cuota>> entry : cuotasPorCliente.entrySet()) {
                            User cl = entry.getKey();
                            List<Cuota> cuotasCl = entry.getValue();
                            BigDecimal totalCl = cuotasCl.stream().map(Cuota::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
                            totalDeudaGlobal = totalDeudaGlobal.add(totalCl);

                            Cuota primera = cuotasCl.stream().min(Comparator.comparing(Cuota::getFechaVencimiento)).orElse(cuotasCl.get(0));
                            long dias = ChronoUnit.DAYS.between(primera.getFechaVencimiento(), hoy);

                            Map<String, Object> fila = new LinkedHashMap<>();
                            fila.put("cliente", cl.getName() + " " + cl.getLastname());
                            fila.put("dni", cl.getDni());
                            fila.put("telefono", cl.getPhone() != null ? cl.getPhone() : "No registrado");
                            fila.put("cuotasPendientes", cuotasCl.size());
                            fila.put("totalDeuda", "S/. " + totalCl.setScale(2));
                            fila.put("diasAtraso", dias > 0 ? dias + " dias" : "Al dia");
                            fila.put("proximoVencimiento", primera.getFechaVencimiento().format(DATE_FORMATTER));
                            fila.put("estado", dias > 30 ? "CRITICO" : (dias > 0 ? "EN MORA" : "POR VENCER"));
                            tablaDatos.add(fila);

                            systemPrompt.append("- Cliente: ").append(cl.getName()).append(" ").append(cl.getLastname())
                                    .append(" | DNI: ").append(cl.getDni())
                                    .append(" | Deuda Total: S/. ").append(totalCl.setScale(2))
                                    .append(" | Cuotas: ").append(cuotasCl.size())
                                    .append(" | Días de Atraso: ").append(dias > 0 ? dias : 0)
                                    .append(" | Vence/Venció: ").append(primera.getFechaVencimiento().format(DATE_FORMATTER))
                                    .append(" | Estado: ").append(fila.get("estado")).append("\n");
                        }
                        systemPrompt.append("\nTOTAL CARTERA POR COBRAR: S/. ").append(totalDeudaGlobal.setScale(2)).append("\n");
                    } else {
                        systemPrompt.append("No hay registros de deudas activas actualmente en el sistema.\n");
                    }

                    systemPrompt.append("\nINSTRUCCIONES DE RESPUESTA:\n")
                            .append("1. Si te preguntan quién debe más, quién tiene la deuda más grande, quién debe menos, etc., analiza la lista anterior y responde directamente con el nombre, DNI, monto y detalle relevante.\n")
                            .append("2. Usa negritas (**) para resaltar nombres, montos y fechas clave.\n")
                            .append("3. Responde siempre de forma ejecutiva, concisa y amable.");
                } else {
                    systemPrompt.append("ROL ACTUAL: CLIENTE (Solo puede ver sus propias deudas y cuotas).\n");
                }

                List<ChatMessage> messages = new ArrayList<>();
                messages.add(new ChatMessage("system", systemPrompt.toString()));
                messages.add(new ChatMessage("user", rawMessage));

                ChatCompletionRequest compReq = ChatCompletionRequest.builder()
                        .model("gpt-4o-mini")
                        .messages(messages)
                        .maxTokens(450)
                        .temperature(0.4)
                        .build();

                String reply = openAiService.createChatCompletion(compReq)
                        .getChoices().get(0).getMessage().getContent();

                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage(reply);

                if (!tablaDatos.isEmpty() && (normMessage.contains("quien") || normMessage.contains("mas") || normMessage.contains("deuda") || normMessage.contains("deudor") || normMessage.contains("debe"))) {
                    response.setRequiresAction(true);
                    response.setActionType("QUERY");
                    response.setActionData(tablaDatos);
                }

                return response;

            } catch (Exception e) {
                System.err.println("Aviso: OpenAI falló en procesarConsultaConInteligenciaArtificial: " + e.getMessage());
            }
        }

        return fallbackNativo(normMessage, isAdmin);
    }

    private ChatResponse fallbackNativo(String normMessage, boolean isAdmin) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        if (isAdmin) {
            response.setMessage("🤖 **Asistente Comercial Reyes**\n\n" +
                    "Puedes consultarme libremente:\n" +
                    "• *¿Quién es el cliente que más me debe?*\n" +
                    "• *¿Quién es el próximo a pagar?*\n" +
                    "• *¿Quiénes no pagan? o Clientes con deudas*\n" +
                    "• *Registrar a un nuevo cliente*");
        } else {
            response.setMessage("🤖 **Asistente Virtual Comercial Reyes**\n\n" +
                    "Puedes consultarme:\n" +
                    "• *¿Cuánto debo en total?*\n" +
                    "• *¿Cuándo vence mi próxima cuota?*\n" +
                    "• *Mis cuotas pendientes*");
        }
        return response;
    }

    // ==========================================
    //  METODOS AUXILIARES DE NORMALIZACION Y DETECCION
    // ==========================================
    private String normalizeText(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "")
                         .replaceAll("[¿?¡!.,;:]", " ")
                         .toLowerCase()
                         .trim()
                         .replaceAll("\\s+", " ");
    }

    private boolean esCancelacion(String msg) {
        return msg.matches(".*\\b(cancela|cancelar|ya no|olvidalo|salir|reiniciar)\\b.*");
    }

    private boolean esSaludoOAyuda(String msg) {
        return msg.matches("^\\s*(hola|buenos dias|buenas tardes|buenas noches|hey|que tal|ayuda|menu|inicio)\\s*$");
    }

    private boolean esIntencionRegistrarCliente(String msg) {
        return ((msg.contains("registra") || msg.contains("crear") || msg.contains("agrega") || msg.contains("alta")) &&
                (msg.contains("cliente") || msg.contains("usuario"))) || msg.contains("registra a");
    }

    private boolean esConsultaProximoAPagar(String msg) {
        return msg.contains("proximo a pagar") || msg.contains("proximo en pagar") ||
               msg.contains("proxima a pagar") || msg.contains("proximos a pagar") ||
               msg.contains("proximo que le toca") || msg.contains("proximo que debe pagar") ||
               msg.contains("siguiente a pagar") || msg.contains("siguiente en pagar") ||
               msg.contains("quien es el proximo") || msg.contains("quien toca pagar") ||
               msg.contains("quien sigue en pagar") || msg.contains("quien debe pagar proximo");
    }

    private boolean esConsultaDeudores(String msg) {
        return msg.contains("no paga") || msg.contains("no pagan") ||
               msg.contains("moroso") || msg.contains("deudor") ||
               msg.contains("quien debe") || msg.contains("quienes deben") ||
               msg.contains("clientes con deuda") || msg.contains("cuentas por cobrar") ||
               msg.contains("deudas vencidas") || msg.contains("lista de deudas");
    }

    private boolean esConsultaPropia(String msg) {
        return msg.contains("mi deuda") || msg.contains("mis cuotas") ||
               msg.contains("mi proxima cuota") || msg.contains("cuanto debo") ||
               msg.contains("mis pagos") || msg.contains("he pagado") ||
               msg.contains("mi estado de cuenta");
    }

    private boolean esIntencionAdminOAjena(String msg) {
        return msg.contains("registrar") || msg.contains("crear usuario") ||
               msg.contains("quien no paga") || msg.contains("morosos") ||
               msg.contains("deudores") || msg.contains("todos los clientes") ||
               (msg.contains("deuda de") && !msg.contains("mi")) ||
               (msg.contains("cuotas de") && !msg.contains("mis"));
    }

    private ChatResponse buildSecurityDeniedResponse() {
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setMessage("🔒 **Acceso Restringido:**\n\n" +
                "Como cliente, unicamente tienes autorizacion para consultar la informacion de tu propia cuenta personal, cuotas pendientes, fechas de vencimiento y pagos realizados.\n" +
                "Por politicas de seguridad y privacidad de datos, no es posible acceder a informacion de otros clientes ni realizar gestiones administrativas.");
        return response;
    }

    private ChatResponse buildWelcomeResponse(boolean isAdmin) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        if (isAdmin) {
            response.setMessage("👋 ¡Hola! Soy el asistente inteligente de **Comercial Reyes**.\n\n" +
                    "Estoy listo para ayudarte a gestionar la tienda:\n" +
                    "• 📊 **Consultar deudores:** Preguntame *¿Quien no paga?* o *¿Quienes deben?* para ver montos, cuotas vencidas y dias de atraso.\n" +
                    "• 🔍 **Consultar cliente:** Preguntame *¿Cuanto debe [Nombre]?* o *¿Cuantas cuotas debe [Nombre]?*.\n" +
                    "• 📝 **Registrar cliente:** Escribe *Quiero registrar un cliente* y te pedire los datos necesarios paso a paso.\n\n" +
                    "¿Que deseas realizar hoy?");
        } else {
            response.setMessage("👋 ¡Hola! Bienvenido a **Comercial Reyes**.\n\n" +
                    "Puedo ayudarte a revisar tu estado de cuenta:\n" +
                    "• 💳 *¿Cuanto debo en total?*\n" +
                    "• 📅 *¿Cuando vence mi proxima cuota?*\n" +
                    "• 📑 *Mis cuotas pendientes*\n" +
                    "• 💰 *¿Cuanto he pagado?*\n\n" +
                    "¿En que te puedo ayudar?");
        }
        return response;
    }

    private User detectarClienteEnMensaje(String rawMsg, String normMsg) {
        Pattern dniPattern = Pattern.compile("\\b([0-9]{8})\\b");
        Matcher dniMatcher = dniPattern.matcher(rawMsg);
        if (dniMatcher.find()) {
            Optional<User> u = userRepository.findByDni(dniMatcher.group(1));
            if (u.isPresent()) return u.get();
        }

        String posibleNombre = extraerPosibleNombre(normMsg);
        if (posibleNombre != null && posibleNombre.length() >= 3) {
            List<User> users = userRepository.findAll();
            if (users != null && !users.isEmpty()) {
                String busqueda = posibleNombre.trim().toLowerCase();
                String[] terminosBusqueda = busqueda.split("\\s+");

                User mejorCandidato = null;
                int maxCoincidencias = 0;
                int maxScore = 0;

                for (User u : users) {
                    String uNombre = normalizeText(u.getName());
                    String uApellido = normalizeText(u.getLastname());
                    String uCompleto = (uNombre + " " + uApellido).trim();

                    // Coincidencia exacta total: prioridad máxima
                    if (uCompleto.equals(busqueda)) {
                        return u;
                    }

                    // Puntuación por coincidencia de palabras completas
                    int score = 0;
                    int coincidencias = 0;
                    String[] partesUsuario = uCompleto.split("\\s+");

                    for (String termino : terminosBusqueda) {
                        if (termino.length() < 2) continue;
                        for (String parte : partesUsuario) {
                            if (parte.equals(termino)) {
                                score += 10;
                                coincidencias++;
                            } else if (parte.startsWith(termino) || parte.contains(termino)) {
                                score += 4;
                            }
                        }
                    }

                    // Bonificación si el nombre completo del usuario contiene la búsqueda entera o viceversa
                    if (uCompleto.contains(busqueda)) {
                        score += 8;
                    } else if (busqueda.contains(uCompleto)) {
                        score += 8;
                    }

                    if (score > maxScore && score >= 8) {
                        maxScore = score;
                        maxCoincidencias = coincidencias;
                        mejorCandidato = u;
                    }
                }

                if (mejorCandidato != null) {
                    return mejorCandidato;
                }
            }
        }

        return null;
    }

    private String extraerPosibleNombre(String normMsg) {
        String[] patrones = {
            "cuanto debe ([a-z\\s]+)",
            "cuantas cuotas debe ([a-z\\s]+)",
            "desde cuando no paga ([a-z\\s]+)",
            "desde cuando debe ([a-z\\s]+)",
            "deuda de ([a-z\\s]+)",
            "cuotas de ([a-z\\s]+)",
            "estado de ([a-z\\s]+)",
            "cliente ([a-z\\s]+)"
        };

        for (String p : patrones) {
            Pattern pattern = Pattern.compile(p);
            Matcher m = pattern.matcher(normMsg);
            if (m.find()) {
                String match = m.group(1).trim();
                match = match.replaceAll("\\b(el|la|los|las|un|una|sr|sra|don|dona|por favor|dime)\\b", "").trim();
                if (match.length() >= 3) return match;
            }
        }

        return null;
    }

    private User buscarUsuarioPorIdentificador(String idStr) {
        if (idStr == null || idStr.isBlank() || "0".equals(idStr)) {
            return null;
        }
        try {
            Integer id = Integer.parseInt(idStr);
            return userRepository.findById(id).orElse(null);
        } catch (NumberFormatException e) {
            return userRepository.findByEmail(idStr).orElse(null);
        }
    }

    private ConversationState getConversationState(String userId) {
        return conversationStates.computeIfAbsent(userId, k -> new ConversationState());
    }

    private String capitalize(String text) {
        if (text == null || text.isBlank()) return "";
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }
}