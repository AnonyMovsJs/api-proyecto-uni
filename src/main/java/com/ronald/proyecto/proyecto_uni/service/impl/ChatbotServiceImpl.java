package com.ronald.proyecto.proyecto_uni.service.impl;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronald.proyecto.proyecto_uni.OpenAIProperties;
import com.ronald.proyecto.proyecto_uni.dto.CreditoDTO;
import com.ronald.proyecto.proyecto_uni.dto.DetalleVentaDTO;
import com.ronald.proyecto.proyecto_uni.dto.VentaDTO;
import com.ronald.proyecto.proyecto_uni.entity.Cuota;
import com.ronald.proyecto.proyecto_uni.entity.Pago;
import com.ronald.proyecto.proyecto_uni.entity.User;
import com.ronald.proyecto.proyecto_uni.entity.Venta;
import com.ronald.proyecto.proyecto_uni.models.ChatRequest;
import com.ronald.proyecto.proyecto_uni.models.ChatResponse;
import com.ronald.proyecto.proyecto_uni.models.ConversationState;
import com.ronald.proyecto.proyecto_uni.models.UserRequest;
import com.ronald.proyecto.proyecto_uni.repository.CuotaRepository;
import com.ronald.proyecto.proyecto_uni.repository.UserRepository;
import com.ronald.proyecto.proyecto_uni.service.ChatbotService;
import com.ronald.proyecto.proyecto_uni.service.PagoService;
import com.ronald.proyecto.proyecto_uni.service.UserService;
import com.ronald.proyecto.proyecto_uni.service.VentaService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ChatbotServiceImpl implements ChatbotService {

    @Autowired
    private CuotaRepository cuotaRepository;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PagoService pagoService;

    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, List<ChatMessage>> userConversations = new HashMap<>();
    // Almacén de estados de conversación por usuario
    private Map<String, ConversationState> conversationStates = new HashMap<>();

    public ChatbotServiceImpl(OpenAIProperties openAIProperties) {
        this.openAiService = new OpenAiService(openAIProperties.getApi().getKey());
    }

    // Sistema de prompt actualizado para alinearse con UserRequest
    private static final String SYSTEM_PROMPT = "Eres un asistente virtual para un sistema de ventas a crédito. " +
            "Tu objetivo es ayudar a los usuarios según su rol:\n\n" +

            "REGLAS CRÍTICAS PARA USUARIOS NORMALES (NO ADMIN):\n" +
            "- Los usuarios normales SOLO pueden consultar sus PROPIOS datos, NUNCA datos de otros usuarios.\n" +
            "- Si un usuario normal pregunta sobre otro cliente o intenta consultar datos que no son suyos, SIEMPRE responde: \"Lo siento, no tienes permisos para consultar información de otros clientes.\"\n"
            +
            "- Si un usuario normal intenta crear usuarios, registrar ventas o realizar cualquier acción administrativa, SIEMPRE responde: \"Lo siento, no tienes permisos para realizar esta acción.\"\n"
            +
            "- NUNCA permitas que un usuario normal acceda a información que no le corresponde, incluso si reformula la pregunta.\n\n"
            +

            "Para USUARIOS NORMALES - CONSULTAS PERMITIDAS:\n" +
            "- Cuando preguntan \"Mis cuotas pendientes\", \"Qué debo pagar\", \"Cuántas cuotas me quedan\", usa CUOTAS_PENDIENTES.\n"
            +
            "- Cuando preguntan \"Mis cuotas pagadas\", \"Cuánto he pagado\", \"Cuántas cuotas pagué\", usa CUOTAS_PAGADAS.\n"
            +
            "- Cuando preguntan \"Mi próxima cuota\", \"Cuándo vence mi pago\", \"Fecha de mi próxima cuota\", usa PROXIMA_CUOTA.\n"
            +
            "- Cuando preguntan \"Cuánto debo en total\", \"Mi deuda total\", \"Monto total pendiente\", usa TOTAL_DEUDA.\n\n"
            +

            "Para ADMINISTRADORES - CONSULTAS DE CLIENTES:\n" +
            "- Cuando consultan sobre un cliente específico, PRIMERO verifica si el cliente existe en el sistema.\n" +
            "- Si el cliente NO existe, responde: \"No se encontró ningún cliente con ese nombre en el sistema.\"\n" +
            "- Si el cliente SÍ existe, continúa con la consulta usando su ID.\n" +
            "- Cuando te pregunten sobre la deuda de un cliente (\"Cuánto debe [nombre]\"), genera una consulta TOTAL_DEUDA.\n"
            +
            "- Cuando te pregunten sobre las cuotas pagadas (\"Cuánto ha pagado [nombre]\", \"Pagos de [nombre]\"), genera CUOTAS_PAGADAS.\n"
            +
            "- Cuando te pregunten por cuotas pendientes (\"Qué debe pagar [nombre]\", \"Cuotas pendientes de [nombre]\"), genera CUOTAS_PENDIENTES.\n"
            +
            "- Cuando te pregunten por la próxima cuota (\"Cuándo vence [nombre]\", \"Próxima cuota de [nombre]\"), genera PROXIMA_CUOTA.\n"
            +
            "- Cuando te pregunten sobre qué clientes tienen deudas o cuántos clientes tienen deudas, usa CLIENTES_CON_DEUDAS.\n\n"
            +

            "Para ADMINISTRADORES - CREACIÓN DE USUARIOS:\n" +
            "- Si el mensaje contiene \"registrar cliente\", \"crear usuario\", \"añadir cliente\", es intención de CREAR_USUARIO.\n"
            +
            "- Al registrar usuarios, solicita los datos uno por uno en este orden: nombre, apellido, DNI (8 dígitos), teléfono (9 dígitos), dirección.\n"
            +
            "- Solo cuando tengas TODOS los datos, genera el JSON para crear el usuario.\n\n" +

            "Para ADMINISTRADORES - REGISTRO DE VENTAS:\n" +
            "- Si el mensaje contiene \"registrar venta\", \"nueva venta\", \"crear venta\", es intención de REGISTRAR_VENTA.\n"
            +
            "- Solicita los datos de la venta paso a paso.\n\n" +

            "FORMATOS DE JSON PARA ACCIONES:\n" +
            "- Para registrar usuarios: ```{\"action\":\"CREATE_USER\",\"data\":{\"name\":\"Juan\",\"lastname\":\"Pérez\",\"dni\":\"12345678\",\"phone\":\"987654321\",\"address\":\"Calle Principal 123\"}}```\n"
            +
            "- Para registrar ventas: ```{\"action\":\"REGISTER_SALE\",\"data\":{\"clienteId\":5,\"descripcion\":\"Compra de electrodomésticos\",\"tipoVenta\":\"CREDITO\",\"detalles\":[{\"nombreProducto\":\"Televisor\",\"cantidad\":1,\"precioUnitario\":1200}],\"creditoDTO\":{\"interes\":5,\"numeroCuotas\":3}}}```\n"
            +
            "- Para consultas: ```{\"action\":\"QUERY\",\"data\":{\"type\":\"CLIENTE_DEUDAS\",\"clienteId\":5}}```\n\n"
            +

            "REGLAS GENERALES:\n" +
            "- SIEMPRE verifica los permisos antes de responder cualquier consulta.\n" +
            "- Si te dicen que ya no quieren hacer esa operación por ejemplo ya no quieren registrar un cliente o venta, entonces olvidas todo e inicias una nueva conversación..\n" +
            "- NUNCA generes un JSON hasta que tengas todos los datos necesarios para la acción.\n" +
            "- SIEMPRE interpreta correctamente las intenciones del usuario, incluso si usa lenguaje informal o natural.\n"
            +
            "- NUNCA pidas ID del cliente cuando un usuario normal consulta sus propios datos.\n";

    @Override
    public ChatResponse processChatMessage(ChatRequest request) {
        try {
            System.out.println("==== NUEVA SOLICITUD DE CHAT ====");
            System.out.println("Mensaje: " + request.getMessage());
            System.out.println("ID Usuario: " + request.getUserId() + ", Rol: " + request.getUserRole());

            String message = request.getMessage().toLowerCase();
            String userRole = request.getUserRole();

            // PARA USUARIOS NORMALES: Verificación de seguridad ESTRICTA
            if (!"ADMIN".equals(userRole)) {
                // Si el mensaje contiene cualquier nombre que no sea "mi", "mis", "mío", "mía",
                // es sospechoso
                if (message.contains("cliente") ||
                        message.contains("datos de") ||
                        message.contains("información de") ||
                        message.contains("deuda de") ||
                        message.contains("cuota de") ||
                        message.contains("usuario") ||
                        message.contains("verificar")) {

                    // Verificar que solo se refiere a sí mismo
                    boolean soloHablaDeSiMismo = message.contains("mi ") ||
                            message.contains("mis ") ||
                            message.contains("yo") ||
                            message.contains("me");

                    if (!soloHablaDeSiMismo) {
                        ChatResponse response = new ChatResponse();
                        response.setSuccess(true);
                        response.setMessage(
                                "Lo siento, no tienes permisos para consultar información de otros clientes o usuarios.");
                        return response;
                    }
                }
            }

            // VERIFICACIÓN RÁPIDA: Usuario normal intenta acciones admin
            if (!"ADMIN".equals(userRole) &&
                    (esIntencionCrearUsuario(message) ||
                            esIntencionRegistrarVenta(message) ||
                            message.contains("cliente") && !message.contains("mi") && !message.contains("yo"))) {

                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage(
                        "Lo siento, no tienes permisos para realizar esta acción o consultar datos de otros clientes.");
                return response;
            }

            // Obtener estado de conversación
            String userId = request.getUserId();
            ConversationState state = getConversationState(userId);

            // Si estamos en una conversación en progreso, continuarla
            if (!"IDLE".equals(state.getCurrentState())) {
                return processStateBasedConversation(request, state);
            }

            // PRIORIDAD ALTA: Detectar acciones especiales
            if ("ADMIN".equals(userRole) && esIntencionCrearUsuario(message)) {
                state.setCurrentState("CREATING_USER");
                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage(
                        "Para crear un usuario necesito algunos datos. Por favor, dime el nombre de la persona:");
                return response;
            }

            if ("ADMIN".equals(userRole) && esIntencionRegistrarVenta(message)) {
                state.setCurrentState("REGISTERING_SALE");
                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage(
                        "Para registrar una venta, necesito algunos datos. ¿Quién es el cliente? (nombre o ID):");
                return response;
            }

            if ("ADMIN".equals(userRole) && esIntencionVerClientesConDeudas(message)) {
                return consultarClientesConDeudas(request);
            }

            // Detección de consultas de cliente específico (ADMIN)
            if ("ADMIN".equals(userRole)) {
                User clienteEncontrado = extraerClienteDeMensaje(message);
                if (clienteEncontrado != null) {
                    String tipoConsulta = determinarTipoConsulta(message);
                    return consultarDatosDeCliente(request, clienteEncontrado, tipoConsulta);
                }
            }

            // Consultas de datos propios (USUARIO)
            if (!"ADMIN".equals(userRole)) {
                String tipoConsulta = determinarTipoConsulta(message);
                return consultarDatosPropios(request, tipoConsulta);
            }

            // REGLA 1: USUARIOS NORMALES - Verificación de seguridad estricta
            if (!"ADMIN".equals(userRole)) {
                // Verificar si intenta acciones administrativas o consultar datos de otros
                if (esIntencionCrearUsuario(message) ||
                        esIntencionRegistrarVenta(message) ||
                        intentaAccederDatosDeOtros(message)) {

                    ChatResponse response = new ChatResponse();
                    response.setSuccess(true);
                    response.setMessage(
                            "Lo siento, no tienes permisos para realizar esta acción o consultar información de otros clientes.");
                    return response;
                }

                // Si pasa la verificación de seguridad, procesar consulta de datos propios
                String tipoConsulta = determinarTipoConsulta(message);
                return consultarDatosPropios(request, tipoConsulta);
            }

            // REGLA 2: ADMIN - Priorizar acciones específicas
            if ("ADMIN".equals(userRole)) {
                // Detectar intención de crear usuario
                if (esIntencionCrearUsuario(message)) {
                    state.setCurrentState("CREATING_USER");
                    ChatResponse response = new ChatResponse();
                    response.setSuccess(true);
                    response.setMessage(
                            "Para crear un usuario necesito algunos datos. Por favor, dime el nombre de la persona:");
                    return response;
                }

                // Detectar intención de registrar venta
                if (esIntencionRegistrarVenta(message)) {
                    state.setCurrentState("REGISTERING_SALE");
                    ChatResponse response = new ChatResponse();
                    response.setSuccess(true);
                    response.setMessage(
                            "Para registrar una venta, necesito algunos datos. ¿Quién es el cliente? (nombre o ID):");
                    return response;
                }

                // Detectar intención de ver clientes con deudas
                if (esIntencionVerClientesConDeudas(message)) {
                    return consultarClientesConDeudas(request);
                }

                // Detectar consulta sobre cliente específico
                String posibleNombreCliente = extraerPosibleNombreCliente(message);
                if (posibleNombreCliente != null) {
                    User cliente = buscarClientePorNombre(posibleNombreCliente);
                    if (cliente != null) {
                        String tipoConsulta = determinarTipoConsulta(message);
                        return consultarDatosDeCliente(request, cliente, tipoConsulta);
                    } else {
                        // Cliente no encontrado
                        ChatResponse response = new ChatResponse();
                        response.setSuccess(true);
                        response.setMessage("No se encontró ningún cliente con el nombre '" + posibleNombreCliente
                                + "' en el sistema. Por favor, verifica el nombre o proporciona el ID si lo conoces.");
                        return response;
                    }
                }
            }

            // Si llegamos aquí, no pudimos determinar la intención directamente
            // Usar OpenAI para intentar entender la intención
            return processWithOpenAI(request, state);

        } catch (Exception e) {
            System.err.println("Error general en processChatMessage: " + e.getMessage());
            e.printStackTrace();

            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setSuccess(false);
            errorResponse
                    .setMessage("Lo siento, ha ocurrido un error al procesar tu mensaje. Por favor, intenta de nuevo.");
            return errorResponse;
        }
    }
    

    private ConversationState getConversationState(String userId) {
        if (!conversationStates.containsKey(userId)) {
            conversationStates.put(userId, new ConversationState());
        }
        return conversationStates.get(userId);
    }

    

    // Método para extraer y verificar cliente del mensaje (para ADMIN)
    /* private ChatResponse procesarConsultaClienteParaAdmin(ChatRequest request) {
        String message = request.getMessage().toLowerCase();

        // Extraer posible nombre del cliente
        String posibleNombreCliente = extraerPosibleNombreCliente(message);

        if (posibleNombreCliente != null) {
            // Buscar el cliente en la base de datos
            User cliente = buscarClientePorNombre(posibleNombreCliente);

            if (cliente != null) {
                // Cliente encontrado, procesar consulta
                String tipoConsulta = determinarTipoConsulta(message);
                return consultarDatosDeCliente(request, cliente, tipoConsulta);
            } else {
                // Cliente NO encontrado, informar al administrador
                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage("No se encontró ningún cliente con el nombre '" + posibleNombreCliente
                        + "'. Por favor, verifica el nombre o proporciona el ID si lo conoces.");
                return response;
            }
        }

        // No se detectó un cliente, usar OpenAI
        return processWithOpenAI(request, getConversationState(request.getUserId()));
    } */

    // Método para verificar si un usuario intenta acceder a datos de otros
    private boolean intentaAccederDatosDeOtros(String mensaje) {
        // Si contiene nombres de otras personas o referencias a otros clientes
        if (mensaje.contains("cliente") ||
                mensaje.contains("usuario") ||
                mensaje.contains("datos de") ||
                mensaje.contains("información de")) {

            // Verificar si solo habla de sí mismo
            return !(mensaje.contains(" mis ") ||
                    mensaje.contains(" mi ") ||
                    mensaje.contains(" yo ") ||
                    mensaje.contains(" me "));
        }

        return false;
    }

    // Extraer posible nombre de cliente del mensaje
    private String extraerPosibleNombreCliente(String mensaje) {
        // Patrones comunes para consultas sobre clientes
        String[] patrones = {
                "(?:de|del)\\s+(?:cliente|usuario)?\\s*([a-zñáéíóúü\\s]+)",
                "(?:cliente|usuario)\\s+([a-zñáéíóúü\\s]+)",
                "(?:cuanto|deuda|debe)\\s+(?:de|el)?\\s*([a-zñáéíóúü\\s]+)",
                "([a-zñáéíóúü\\s]+)\\s+(?:debe|tiene|adeuda)"
        };

        for (String patron : patrones) {
            try {
                Pattern pattern = Pattern.compile(patron, Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(mensaje);
                if (matcher.find()) {
                    String nombre = matcher.group(1).trim();

                    // Filtrar palabras comunes y conectores
                    nombre = nombre.replaceAll("\\b(el|la|los|las|un|una|el cliente|la cliente)\\b", "").trim();

                    // Si después de filtrar queda algo sustancial
                    if (nombre.length() > 2) {
                        System.out.println("Posible nombre de cliente extraído: '" + nombre + "'");
                        return nombre;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error al procesar patrón: " + e.getMessage());
            }
        }

        return null;
    }

    // Método para consultar datos de un cliente específico (ADMIN)
    private ChatResponse consultarDatosDeCliente(ChatRequest request, User cliente, String tipoConsulta) {
        ActionRequest actionRequest = new ActionRequest();
        actionRequest.setAction("QUERY");

        Map<String, Object> queryData = new HashMap<>();
        queryData.put("type", tipoConsulta);
        queryData.put("clienteId", cliente.getId());

        actionRequest.setData(queryData);

        Object result = processAction(actionRequest, request.getUserId());

        ChatResponse response = new ChatResponse();
        response.setSuccess(true);

        String clientFullName = cliente.getName() + " " + cliente.getLastname();
        String messageTxt = "";

        switch (tipoConsulta) {
            case "CUOTAS_PENDIENTES":
                messageTxt = "Cuotas pendientes de " + clientFullName + ":";
                break;
            case "CUOTAS_PAGADAS":
                messageTxt = "Cuotas pagadas por " + clientFullName + ":";
                break;
            case "PROXIMA_CUOTA":
                messageTxt = "Próxima cuota de " + clientFullName + ":";
                break;
            case "TOTAL_DEUDA":
                messageTxt = "Deuda total de " + clientFullName + ":";
                break;
            default:
                messageTxt = "Información de " + clientFullName + ":";
        }

        response.setMessage(messageTxt);
        response.setRequiresAction(true);
        response.setActionType("QUERY");
        response.setActionData(result);

        return response;
    }

    // Método para consultar datos propios (USUARIO NORMAL)
    private ChatResponse consultarDatosPropios(ChatRequest request, String tipoConsulta) {
        ActionRequest actionRequest = new ActionRequest();
        actionRequest.setAction("QUERY");

        Map<String, Object> queryData = new HashMap<>();
        queryData.put("type", tipoConsulta);

        actionRequest.setData(queryData);

        Object result = processAction(actionRequest, request.getUserId());

        ChatResponse response = new ChatResponse();
        response.setSuccess(true);

        String messageTxt = "";
        switch (tipoConsulta) {
            case "CUOTAS_PENDIENTES":
                messageTxt = "Estas son tus cuotas pendientes:";
                break;
            case "CUOTAS_PAGADAS":
                messageTxt = "Estas son las cuotas que has pagado:";
                break;
            case "PROXIMA_CUOTA":
                messageTxt = "Tu próxima cuota a pagar es:";
                break;
            case "TOTAL_DEUDA":
                messageTxt = "Tu deuda total es:";
                break;
            default:
                messageTxt = "Aquí está tu información:";
        }

        response.setMessage(messageTxt);
        response.setRequiresAction(true);
        response.setActionType("QUERY");
        response.setActionData(result);

        return response;
    }

    private boolean esIntencionRegistrarVenta(String mensaje) {
        return (mensaje.contains("registr") && mensaje.contains("venta")) ||
                (mensaje.contains("crea") && mensaje.contains("venta")) ||
                (mensaje.contains("nueva") && mensaje.contains("venta"));
    }

    // Método para consultar clientes con deudas
    private ChatResponse consultarClientesConDeudas(ChatRequest request) {
        String userId = request.getUserId();

        ActionRequest actionRequest = new ActionRequest();
        actionRequest.setAction("QUERY");

        Map<String, Object> queryData = new HashMap<>();
        queryData.put("type", "CLIENTES_CON_DEUDAS");

        actionRequest.setData(queryData);

        Object result = processAction(actionRequest, userId);

        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setMessage("Estos son los clientes que tienen deudas pendientes:");
        response.setRequiresAction(true);
        response.setActionType("QUERY");
        response.setActionData(result);

        return response;
    }

    

    // Verificar intención de crear usuario
    private boolean esIntencionCrearUsuario(String mensaje) {
        // Palabras clave para creación de usuario
        String[] patrones = {
                "registr(a|ar|ame) (un )?(cliente|usuario)",
                "crea(r)? (un )?(cliente|usuario)",
                "añad(e|ir) (un )?(cliente|usuario)",
                "agreg(a|ar) (un )?(cliente|usuario)",
                "nuev(o|a) (cliente|usuario)"
        };

        // Verificar cada patrón
        for (String patron : patrones) {
            if (mensaje.matches(".*" + patron + ".*")) {
                System.out.println("✓ Patrón de creación de usuario detectado: " + patron);
                return true;
            }
        }

        // Si el mensaje contiene palabras clave específicas al inicio
        mensaje = mensaje.trim();
        if (mensaje.startsWith("registrar cliente") ||
                mensaje.startsWith("registra cliente") ||
                mensaje.startsWith("registrame un cliente") ||
                mensaje.startsWith("crear cliente") ||
                mensaje.startsWith("crea cliente") ||
                mensaje.startsWith("nuevo cliente")) {
            System.out.println("✓ Inicio de mensaje de creación de usuario detectado");
            return true;
        }

        // Si contiene nombre, apellido, dni y teléfono juntos, probablemente es
        // registro
        if (mensaje.contains("nombre") &&
                mensaje.contains("apellido") &&
                mensaje.contains("dni") &&
                (mensaje.contains("telefono") || mensaje.contains("teléfono"))) {
            System.out.println("✓ Detectados campos de registro de usuario");
            return true;
        }

        return false;
    }

    // Verificar intención de ver clientes con deudas
    private boolean esIntencionVerClientesConDeudas(String mensaje) {
        return (mensaje.contains("cliente") && mensaje.contains("deuda")) ||
                (mensaje.contains("cliente") && mensaje.contains("moroso")) ||
                (mensaje.contains("cuanto") && mensaje.contains("cliente") && mensaje.contains("debe")) ||
                (mensaje.contains("cliente") && mensaje.contains("falta") && mensaje.contains("pagar"));
    }

    // Determinar tipo de consulta basado en el mensaje
    private String determinarTipoConsulta(String mensaje) {
        mensaje = mensaje.toLowerCase();

        // CUOTAS PENDIENTES - Detectar variaciones
        if (mensaje.contains("pendiente") ||
                mensaje.contains("debo") ||
                mensaje.contains("queda") ||
                mensaje.contains("falta") ||
                (mensaje.contains("cuota") && mensaje.contains("debe")) ||
                (mensaje.contains("falta") && mensaje.contains("pagar"))) {
            return "CUOTAS_PENDIENTES";
        }

        // CUOTAS PAGADAS - Detectar variaciones
        if (mensaje.contains("pagada") ||
                mensaje.contains("pagué") ||
                mensaje.contains("pagué") ||
                mensaje.contains("he pagado") ||
                mensaje.contains("ya pague") ||
                mensaje.contains("ya pagado") ||
                mensaje.contains("abonada") ||
                (mensaje.contains("cuota") && mensaje.contains("pagado")) ||
                (mensaje.contains("cuántas") && mensaje.contains("pagado"))) {
            return "CUOTAS_PAGADAS";
        }

        // PRÓXIMA CUOTA - Detectar variaciones
        if (mensaje.contains("próxima") ||
                mensaje.contains("proxima") ||
                mensaje.contains("siguiente") ||
                mensaje.contains("fecha") ||
                mensaje.contains("vence") ||
                mensaje.contains("vencimiento") ||
                (mensaje.contains("cuándo") && mensaje.contains("pagar")) ||
                (mensaje.contains("cuando") && mensaje.contains("pagar"))) {
            return "PROXIMA_CUOTA";
        }

        // DEUDA TOTAL - Detectar variaciones
        if (mensaje.contains("total") ||
                (mensaje.contains("cuánto") && mensaje.contains("total")) ||
                (mensaje.contains("cuanto") && mensaje.contains("total")) ||
                mensaje.contains("monto total") ||
                (mensaje.contains("monto") && mensaje.contains("deuda"))) {
            return "TOTAL_DEUDA";
        }

        // Análisis de palabras clave y verbos
        String[] palabrasPendientes = { "debo", "debe", "pendiente", "queda", "falta", "pagar" };
        String[] palabrasPagadas = { "pagué", "pagado", "abonado", "completé", "saldado" };
        String[] palabrasProxima = { "próxima", "siguiente", "vence", "fecha", "cuando", "cuándo" };

        // Contar ocurrencias de cada categoría
        int contPendientes = contarPalabras(mensaje, palabrasPendientes);
        int contPagadas = contarPalabras(mensaje, palabrasPagadas);
        int contProxima = contarPalabras(mensaje, palabrasProxima);

        // Determinar la categoría con más peso
        if (contPagadas > contPendientes && contPagadas > contProxima) {
            return "CUOTAS_PAGADAS";
        } else if (contProxima > contPendientes && contProxima > contPagadas) {
            return "PROXIMA_CUOTA";
        } else if (contPendientes > 0) {
            return "CUOTAS_PENDIENTES";
        }

        // Por defecto, devolver TOTAL_DEUDA
        return "TOTAL_DEUDA";
    }

    private int contarPalabras(String mensaje, String[] palabras) {
        int contador = 0;
        for (String palabra : palabras) {
            if (mensaje.contains(palabra)) {
                contador++;
            }
        }
        return contador;
    }

    // Método para procesar conversaciones basadas en estado
    private ChatResponse processStateBasedConversation(ChatRequest request, ConversationState state) {
        String currentState = state.getCurrentState();

        switch (currentState) {
            case "CREATING_USER":
                return processUserCreation(request, state);
            case "CHECKING_CLIENT_DETAILS":
                return processClientDetailsCheck(request, state);
            case "REGISTERING_SALE":
                return processSaleRegistration(request, state);
            default:
                // Estado no reconocido, volver a IDLE
                state.clearContext();
                return processNewIntent(request, state);
        }
    }

    // Método para procesar la creación de usuario paso a paso
    private ChatResponse processUserCreation(ChatRequest request, ConversationState state) {
        Map<String, Object> context = state.getContextData();
        int currentStep = state.getStep();
        String message = request.getMessage().trim();

        // Si es el primer paso, verificar si el mensaje contiene todos los datos
        if (currentStep == 0) {
            // Intentar extraer datos completos del mensaje
            Map<String, String> datosExtraidos = extraerDatosUsuario(message);
            if (datosExtraidos.size() >= 4) { // Si encontramos al menos nombre, apellido, dni y teléfono
                // Usar los datos extraídos
                UserRequest userRequest = new UserRequest();
                userRequest.setName(datosExtraidos.getOrDefault("nombre", ""));
                userRequest.setLastname(datosExtraidos.getOrDefault("apellido", ""));
                userRequest.setDni(datosExtraidos.getOrDefault("dni", ""));
                userRequest.setPhone(datosExtraidos.getOrDefault("telefono", ""));
                userRequest.setAddress(datosExtraidos.getOrDefault("direccion", "Dirección no especificada"));
                userRequest.setAdmin(false);

                try {
                    // Validar datos
                    if (!validarDatosUsuario(userRequest)) {
                        ChatResponse response = new ChatResponse();
                        response.setSuccess(true);
                        response.setMessage(
                                "Los datos proporcionados no son válidos. El DNI debe tener 8 dígitos y el teléfono 9 dígitos. Vamos a comenzar de nuevo.\n\nPor favor, proporciona el nombre del cliente:");
                        return response;
                    }

                    // Procesar la creación si los datos son válidos
                    Object result = processCreateUser(userRequest);

                    // Crear respuesta amigable
                    String userName = userRequest.getName() + " " + userRequest.getLastname();
                    ChatResponse response = new ChatResponse();
                    response.setSuccess(true);
                    response.setMessage(
                            "¡Usuario creado exitosamente! He registrado a " + userName + " en el sistema.");
                    response.setRequiresAction(true);
                    response.setActionType("CREATE_USER");
                    response.setActionData(result);

                    // Limpiar estado
                    state.clearContext();
                    return response;
                } catch (Exception e) {
                    // Si falla la creación, iniciar flujo paso a paso
                    ChatResponse response = new ChatResponse();
                    response.setSuccess(true);
                    response.setMessage("No pude crear el usuario con los datos proporcionados: " + e.getMessage() +
                            ". Vamos a hacerlo paso a paso.\n\nPor favor, proporciona el nombre del cliente:");
                    return response;
                }
            }
        }

        
        

        // Si no se pudieron extraer todos los datos o estamos en medio del flujo,
        // continuar paso a paso
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);

        switch (currentStep) {
            case 0: // Esperando nombre
                if (message.length() < 2) {
                    response.setMessage("El nombre es demasiado corto. Por favor, proporciona un nombre válido:");
                    return response;
                }
                context.put("name", message);
                state.incrementStep();
                response.setMessage("Gracias. Ahora necesito el apellido:");
                break;

            case 1: // Esperando apellido
                if (message.length() < 2) {
                    response.setMessage("El apellido es demasiado corto. Por favor, proporciona un apellido válido:");
                    return response;
                }
                context.put("lastname", message);
                state.incrementStep();
                response.setMessage("Perfecto. Ahora necesito el DNI (8 dígitos numéricos):");
                break;

            case 2: // Esperando DNI
                if (!message.matches("^\\d{8}$")) {
                    response.setMessage("El DNI debe tener exactamente 8 dígitos numéricos. Inténtalo de nuevo:");
                    return response;
                }
                context.put("dni", message);
                state.incrementStep();
                response.setMessage("Ahora necesito el número de teléfono (9 dígitos numéricos):");
                break;

            case 3: // Esperando teléfono
                if (!message.matches("^\\d{9}$")) {
                    response.setMessage("El teléfono debe tener exactamente 9 dígitos numéricos. Inténtalo de nuevo:");
                    return response;
                }
                context.put("phone", message);
                state.incrementStep();
                response.setMessage("Por último, necesito la dirección:");
                break;

            case 4: // Esperando dirección
                if (message.length() < 5) {
                    response.setMessage(
                            "La dirección es demasiado corta. Por favor, proporciona una dirección válida:");
                    return response;
                }
                context.put("address", message);

                // Crear el usuario con todos los datos recopilados
                UserRequest userRequest = new UserRequest();
                userRequest.setName((String) context.get("name"));
                userRequest.setLastname((String) context.get("lastname"));
                userRequest.setDni((String) context.get("dni"));
                userRequest.setPhone((String) context.get("phone"));
                userRequest.setAddress((String) context.get("address"));
                userRequest.setAdmin(false);

                // Procesar la creación
                try {
                    Object result = processCreateUser(userRequest);

                    // Crear respuesta amigable
                    String userName = userRequest.getName() + " " + userRequest.getLastname();
                    response.setMessage(
                            "¡Usuario creado exitosamente! He registrado a " + userName + " en el sistema.");
                    response.setRequiresAction(true);
                    response.setActionType("CREATE_USER");
                    response.setActionData(result);

                    // Limpiar estado
                    state.clearContext();
                } catch (Exception e) {
                    response.setMessage("Lo siento, no pude crear el usuario: " + e.getMessage()
                            + ". Por favor, intenta nuevamente.");
                }
                break;

            default:
                // Limpiar estado si llegamos a un paso desconocido
                state.clearContext();
                response.setMessage("Volvamos a empezar. ¿En qué puedo ayudarte?");
        }

        return response;
    }

    private ChatResponse processSaleRegistration(ChatRequest request, ConversationState state) {
    Map<String, Object> context = state.getContextData();
    int currentStep = state.getStep();
    String message = request.getMessage().trim();
    
    ChatResponse response = new ChatResponse();
    response.setSuccess(true);
    
    switch (currentStep) {
        case 0: // Esperando ID del cliente
            try {
                // Verificar si es un número (ID directo)
                if (message.matches("^\\d+$")) {
                    Integer clienteId = Integer.parseInt(message);
                    User cliente = userRepository.findById(clienteId)
                            .orElse(null);
                    
                    if (cliente == null) {
                        response.setMessage("No se encontró ningún cliente con ID " + clienteId + ". Por favor, proporciona un ID válido o el nombre del cliente:");
                        return response;
                    }
                    
                    context.put("clienteId", clienteId);
                    context.put("clienteNombre", cliente.getName() + " " + cliente.getLastname());
                    state.incrementStep();
                    response.setMessage("Cliente seleccionado: " + cliente.getName() + " " + cliente.getLastname() + ". Ahora necesito una descripción para esta venta:");
                } else {
                    // Buscar por nombre
                    User cliente = buscarClientePorNombre(message);
                    if (cliente != null) {
                        context.put("clienteId", cliente.getId());
                        context.put("clienteNombre", cliente.getName() + " " + cliente.getLastname());
                        state.incrementStep();
                        response.setMessage("Cliente seleccionado: " + cliente.getName() + " " + cliente.getLastname() + ". Ahora necesito una descripción para esta venta:");
                    } else {
                        response.setMessage("No pude encontrar un cliente con ese nombre. Por favor, proporciona un ID válido o el nombre completo del cliente:");
                    }
                }
            } catch (Exception e) {
                response.setMessage("Error al buscar el cliente: " + e.getMessage() + ". Por favor, intenta nuevamente con un ID válido:");
            }
            break;
            
        case 1: // Esperando descripción de la venta
            if (message.length() < 3) {
                response.setMessage("La descripción es demasiado corta. Por favor, proporciona una descripción más detallada:");
                return response;
            }
            
            context.put("descripcion", message);
            state.incrementStep();
            response.setMessage("Excelente. ¿Cuál es el tipo de venta? Escribe CONTADO o CREDITO:");
            break;
            
        case 2: // Esperando tipo de venta
            String tipoVenta = message.toUpperCase();
            if (!tipoVenta.equals("CONTADO") && !tipoVenta.equals("CREDITO")) {
                response.setMessage("Tipo de venta no válido. Por favor, escribe CONTADO o CREDITO:");
                return response;
            }
            
            context.put("tipoVenta", tipoVenta);
            if (tipoVenta.equals("CREDITO")) {
                context.put("detallesProductos", new ArrayList<Map<String, Object>>());
                state.incrementStep();
                response.setMessage("Has seleccionado venta a CRÉDITO. Ahora necesito agregar productos. Por favor, indica el nombre del primer producto:");
            } else {
                context.put("detallesProductos", new ArrayList<Map<String, Object>>());
                state.incrementStep();
                response.setMessage("Has seleccionado venta al CONTADO. Ahora necesito agregar productos. Por favor, indica el nombre del primer producto:");
            }
            break;
            
        case 3: // Esperando nombre del producto
            if (message.length() < 2) {
                response.setMessage("El nombre del producto es demasiado corto. Por favor, proporciona un nombre válido:");
                return response;
            }
            
            context.put("productoActualNombre", message);
            state.incrementStep();
            response.setMessage("Producto: " + message + ". Ahora indica la cantidad:");
            break;
            
        case 4: // Esperando cantidad
            try {
                int cantidad = Integer.parseInt(message);
                if (cantidad <= 0) {
                    response.setMessage("La cantidad debe ser mayor que cero. Por favor, indica una cantidad válida:");
                    return response;
                }
                
                context.put("productoActualCantidad", cantidad);
                state.incrementStep();
                response.setMessage("Cantidad: " + cantidad + ". Ahora indica el precio unitario:");
            } catch (NumberFormatException e) {
                response.setMessage("Cantidad no válida. Por favor, ingresa un número entero positivo:");
            }
            break;
            
        case 5: // Esperando precio unitario
            try {
                BigDecimal precioUnitario = new BigDecimal(message.replace(",", "."));
                if (precioUnitario.compareTo(BigDecimal.ZERO) <= 0) {
                    response.setMessage("El precio debe ser mayor que cero. Por favor, indica un precio válido:");
                    return response;
                }
                
                context.put("productoActualPrecio", precioUnitario);
                
                // Añadir producto a la lista
                String nombreProducto = (String) context.get("productoActualNombre");
                int cantidad = (int) context.get("productoActualCantidad");
                
                Map<String, Object> producto = new HashMap<>();
                producto.put("nombreProducto", nombreProducto);
                producto.put("cantidad", cantidad);
                producto.put("precioUnitario", precioUnitario);
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> detallesProductos = (List<Map<String, Object>>) context.get("detallesProductos");
                detallesProductos.add(producto);
                
                // Calcular total actual
                BigDecimal totalActual = BigDecimal.ZERO;
                for (Map<String, Object> prod : detallesProductos) {
                    BigDecimal precio = (BigDecimal) prod.get("precioUnitario");
                    int cant = (int) prod.get("cantidad");
                    totalActual = totalActual.add(precio.multiply(new BigDecimal(cant)));
                }
                
                context.put("montoTotal", totalActual);
                
                // Preguntar si desea agregar más productos
                state.incrementStep();
                response.setMessage("Producto agregado: " + nombreProducto + ", Cantidad: " + cantidad + 
                                    ", Precio unitario: S/. " + precioUnitario + ".\n\n" +
                                    "Monto actual: S/. " + totalActual + ".\n\n" +
                                    "¿Desea agregar otro producto? (SI/NO)");
            } catch (Exception e) {
                response.setMessage("Precio no válido. Por favor, ingresa un número (puede incluir decimales):");
            }
            break;
            
        case 6: // Preguntar si agregar más productos
            if (message.equalsIgnoreCase("SI") || message.equalsIgnoreCase("SÍ") || 
                message.equalsIgnoreCase("S") || message.equalsIgnoreCase("YES") || 
                message.equalsIgnoreCase("Y")) {
                // Volver al paso 3 para agregar otro producto
                state.setStep(3);
                response.setMessage("Por favor, indica el nombre del siguiente producto:");
                return response;
            } else if (message.equalsIgnoreCase("NO") || message.equalsIgnoreCase("N")) {
                // Verificar si es venta a crédito
                String tipoVentaSeleccionado = (String) context.get("tipoVenta");
                if ("CREDITO".equals(tipoVentaSeleccionado)) {
                    state.incrementStep();
                    response.setMessage("Has terminado de agregar productos. Como es una venta a CRÉDITO, " +
                                       "necesito información adicional. ¿Cuál es el interés? (porcentaje):");
                } else {
                    // Si es al contado, finalizar venta
                    return finalizarVenta(state, context);
                }
            } else {
                response.setMessage("Respuesta no válida. Por favor, responde SI o NO:");
            }
            break;
            
        case 7: // Esperando interés (solo para crédito)
            try {
                BigDecimal interes = new BigDecimal(message.replace(",", "."));
                if (interes.compareTo(BigDecimal.ZERO) < 0) {
                    response.setMessage("El interés no puede ser negativo. Por favor, ingresa un valor válido:");
                    return response;
                }
                
                context.put("interes", interes);
                state.incrementStep();
                response.setMessage("Interés: " + interes + "%. Ahora necesito el número de cuotas:");
            } catch (Exception e) {
                response.setMessage("Valor de interés no válido. Por favor, ingresa un número (puede incluir decimales):");
            }
            break;
            
        case 8: // Esperando número de cuotas (solo para crédito)
            try {
                int numeroCuotas = Integer.parseInt(message);
                if (numeroCuotas <= 0) {
                    response.setMessage("El número de cuotas debe ser mayor que cero. Por favor, indica un valor válido:");
                    return response;
                }
                
                context.put("numeroCuotas", numeroCuotas);
                
                // Finalizar venta
                return finalizarVenta(state, context);
            } catch (NumberFormatException e) {
                response.setMessage("Número de cuotas no válido. Por favor, ingresa un número entero positivo:");
            }
            break;
            
        default:
            // Limpiar estado si llegamos a un paso desconocido
            state.clearContext();
            response.setMessage("Volvamos a empezar. ¿En qué puedo ayudarte?");
    }
    
    return response;
}

// Método para finalizar el registro de venta
private ChatResponse finalizarVenta(ConversationState state, Map<String, Object> context) {
    try {
        // Crear DTO de venta
        VentaDTO ventaDTO = new VentaDTO();
        ventaDTO.setClienteId(Long.valueOf((Integer) context.get("clienteId")));
        ventaDTO.setDescripcion((String) context.get("descripcion"));
        ventaDTO.setTipoVenta(Venta.TipoVenta.valueOf((String) context.get("tipoVenta")));
        ventaDTO.setMontoTotal((BigDecimal) context.get("montoTotal"));
        
        // Agregar detalles
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detallesProductos = (List<Map<String, Object>>) context.get("detallesProductos");
        List<DetalleVentaDTO> detalles = new ArrayList<>();
        
        for (Map<String, Object> producto : detallesProductos) {
            DetalleVentaDTO detalle = new DetalleVentaDTO();
            detalle.setNombreProducto((String) producto.get("nombreProducto"));
            detalle.setCantidad((Integer) producto.get("cantidad"));
            detalle.setPrecioUnitario((BigDecimal) producto.get("precioUnitario"));
            detalles.add(detalle);
        }
        
        ventaDTO.setDetalles(detalles);
        
        // Si es venta a crédito, agregar información de crédito
        if (ventaDTO.getTipoVenta() == Venta.TipoVenta.CREDITO) {
            CreditoDTO creditoDTO = new CreditoDTO();
            creditoDTO.setInteres((BigDecimal) context.get("interes"));
            creditoDTO.setNumeroCuotas((Integer) context.get("numeroCuotas"));
            ventaDTO.setCreditoDTO(creditoDTO);
        }
        
        // Registrar venta
        Venta ventaRegistrada = ventaService.registrarVenta(ventaDTO);
        
        // Crear respuesta
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        
        // Formatear respuesta según tipo de venta
        StringBuilder mensaje = new StringBuilder();
        mensaje.append("¡Venta registrada exitosamente!\n\n");
        mensaje.append("Cliente: ").append(context.get("clienteNombre")).append("\n");
        mensaje.append("Descripción: ").append(ventaDTO.getDescripcion()).append("\n");
        mensaje.append("Monto Total: S/. ").append(ventaDTO.getMontoTotal()).append("\n");
        mensaje.append("Tipo de Venta: ").append(ventaDTO.getTipoVenta());
        
        if (ventaDTO.getTipoVenta() == Venta.TipoVenta.CREDITO) {
            mensaje.append("\nInterés: ").append(context.get("interes")).append("%\n");
            mensaje.append("Número de Cuotas: ").append(context.get("numeroCuotas"));
        }
        
        response.setMessage(mensaje.toString());
        response.setRequiresAction(true);
        response.setActionType("REGISTER_SALE");
        
        // Convertir Venta a Map para respuesta
        Map<String, Object> ventaData = new HashMap<>();
        ventaData.put("id", ventaRegistrada.getId());
        ventaData.put("clienteNombre", context.get("clienteNombre"));
        ventaData.put("descripcion", ventaRegistrada.getDescripcion());
        ventaData.put("montoTotal", ventaRegistrada.getMontoTotal());
        ventaData.put("tipoVenta", ventaRegistrada.getTipoVenta().toString());
        ventaData.put("estado", ventaRegistrada.getEstado().toString());
        
        response.setActionData(ventaData);
        
        // Limpiar estado
        state.clearContext();
        
        return response;
    } catch (Exception e) {
        
        ChatResponse errorResponse = new ChatResponse();
        errorResponse.setSuccess(false);
        errorResponse.setMessage("Error al registrar la venta: " + e.getMessage());
        
        // Limpiar estado
        state.clearContext();
        
        return errorResponse;
    }
}
    // Método para procesar consultas de detalles de cliente
    private ChatResponse processClientDetailsCheck(ChatRequest request, ConversationState state) {
        Map<String, Object> context = state.getContextData();
        String message = request.getMessage().trim();

        System.out.println("Procesando detalles de cliente: " + message);

        // Si el mensaje es un número, interpretar como ID
        if (message.matches("^\\d+$")) {
            int clientId = Integer.parseInt(message);

            // Verificar que existe el cliente
            User cliente = userRepository.findById(clientId).orElse(null);
            if (cliente == null) {
                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage("No encontré ningún cliente con el ID " + clientId +
                        ". ¿Podrías proporcionar otro ID o nombre?");
                return response;
            }

            // Cliente encontrado, ejecutar consulta
            context.put("clientId", clientId);
            context.put("clientName", cliente.getName() + " " + cliente.getLastname());

            String queryType = (String) context.get("queryType");
            return executeClientQuery(request, state, queryType, clientId);
        } else {
            // Intentar buscar por nombre
            User cliente = buscarClientePorNombre(message);
            if (cliente != null) {
                context.put("clientId", cliente.getId());
                context.put("clientName", cliente.getName() + " " + cliente.getLastname());

                String queryType = (String) context.get("queryType");
                return executeClientQuery(request, state, queryType, cliente.getId().longValue());
            } else {
                // No se encontró el cliente
                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage("No encontré ningún cliente con el nombre '" + message +
                        "'. ¿Podrías proporcionar el nombre completo o el ID?");
                return response;
            }
        }
    }

    // Método para ejecutar una consulta específica para un cliente
    private ChatResponse executeClientQuery(ChatRequest request, ConversationState state, String queryType,
            long clientId) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);

        try {
            ActionRequest actionRequest = new ActionRequest();
            actionRequest.setAction("QUERY");

            Map<String, Object> queryData = new HashMap<>();
            queryData.put("type", queryType);
            queryData.put("clienteId", clientId);

            actionRequest.setData(queryData);

            // Ejecutar la consulta
            Object result = processAction(actionRequest, request.getUserId());

            // Crear respuesta amigable según el tipo de consulta
            String clientName = (String) state.getFromContext("clientName");
            String friendlyMessage = "";

            switch (queryType) {
                case "CUOTAS_PENDIENTES":
                    friendlyMessage = "Aquí están las cuotas pendientes de " + clientName + ":";
                    break;
                case "CUOTAS_PAGADAS":
                    friendlyMessage = "Aquí están las cuotas pagadas por " + clientName + ":";
                    break;
                case "PROXIMA_CUOTA":
                    friendlyMessage = "La próxima cuota a pagar de " + clientName + " es:";
                    break;
                case "TOTAL_DEUDA":
                    friendlyMessage = "La deuda total de " + clientName + " es:";
                    break;
                default:
                    friendlyMessage = "Aquí está la información solicitada sobre " + clientName + ":";
            }

            response.setMessage(friendlyMessage);
            response.setRequiresAction(true);
            response.setActionType("QUERY");
            response.setActionData(result);

            // Limpiar estado
            state.clearContext();
        } catch (Exception e) {
            response.setMessage("Lo siento, ocurrió un error al procesar la consulta: " + e.getMessage());

            // Limpiar estado
            state.clearContext();
        }

        return response;
    }

    // Método para procesar nuevas intenciones
    @SuppressWarnings("null")
    private ChatResponse processNewIntent(ChatRequest request, ConversationState state) {
        String message = request.getMessage().toLowerCase();
        String userRole = request.getUserRole();
        String userId = request.getUserId();

        System.out.println("Procesando nueva intención: " + message);
        System.out.println("Rol del usuario: " + userRole);

        // Verificar permisos para acciones de administrador
        boolean isAdmin = "ADMIN".equals(userRole);

        // ---- DETECTAR COMANDOS ADMINISTRATIVOS ----

        // Detectar intención de registrar usuario (solo para admin)
        if (message.contains("registr") && message.contains("cliente") ||
                message.contains("registr") && message.contains("usuario") ||
                message.contains("crea") && message.contains("cliente") ||
                message.contains("crea") && message.contains("usuario") ||
                message.contains("nuevo") && message.contains("cliente") ||
                message.contains("añadir") && message.contains("cliente")) {

            System.out.println("Detectada intención de CREAR USUARIO");

            if (!isAdmin) {
                return createNoPermissionResponse();
            }

            // Iniciar flujo de creación de usuario
            state.setCurrentState("CREATING_USER");

            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setMessage(
                    "Para crear un usuario necesito algunos datos. Por favor, dime el nombre de la persona:");
            return response;
        }

        // Detectar intención de registrar venta (solo para admin)
        if (message.contains("registr") && message.contains("venta") ||
                message.contains("crea") && message.contains("venta") ||
                message.contains("nueva") && message.contains("venta")) {

            System.out.println("Detectada intención de CREAR VENTA");

            if (!isAdmin) {
                return createNoPermissionResponse();
            }

            // Iniciar flujo de creación de venta
            state.setCurrentState("REGISTERING_SALE");

            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setMessage(
                    "Para registrar una venta, primero necesito saber el cliente. ¿Cuál es el nombre o ID del cliente?");
            return response;
        }

        // Consultar clientes con deudas/morosos (solo para admin)
        if (isAdmin && ((message.contains("cliente") && message.contains("deuda")) ||
                (message.contains("cliente") && message.contains("moroso")) ||
                (message.contains("cliente") && message.contains("pendiente")) ||
                (message.contains("cuanto") && message.contains("cliente") && message.contains("debe")) ||
                (message.contains("cliente") && message.contains("falta") && message.contains("pagar")))) {
            System.out.println("Detectada intención de CONSULTAR CLIENTES CON DEUDAS");

            // Consulta directa de clientes con deudas
            ActionRequest actionRequest = new ActionRequest();
            actionRequest.setAction("QUERY");

            Map<String, Object> queryData = new HashMap<>();
            queryData.put("type", "CLIENTES_CON_DEUDAS");

            actionRequest.setData(queryData);

            Object result = processAction(actionRequest, userId);

            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setMessage("Estos son los clientes que tienen deudas pendientes:");
            response.setRequiresAction(true);
            response.setActionType("QUERY");
            response.setActionData(result);

            return response;
        }

        // ---- CONSULTAS ESPECÍFICAS DE CLIENTE ----

        // Si es USUARIO NORMAL, procesar sus propias consultas
        if (!"ADMIN".equals(userRole)) {
            // Cuotas pendientes
            if (message.contains("cuota") && message.contains("pendiente") ||
                    message.contains("que") && message.contains("debo") ||
                    message.contains("cuanto") && message.contains("debo")) {

                return processUserOwnQuery(request, "CUOTAS_PENDIENTES");
            }

            // Cuotas pagadas
            if (message.contains("cuota") && message.contains("pagada") ||
                    message.contains("pago") && message.contains("realizado") ||
                    message.contains("he") && message.contains("pagado")) {

                return processUserOwnQuery(request, "CUOTAS_PAGADAS");
            }

            // Próxima cuota
            if (message.contains("proxim") && message.contains("cuota") ||
                    message.contains("siguiente") && message.contains("pago") ||
                    message.contains("cuando") && message.contains("pagar") ||
                    message.contains("mi") && message.contains("proxim")) {

                return processUserOwnQuery(request, "PROXIMA_CUOTA");
            }

            // Deuda total
            if (message.contains("deuda") && message.contains("total") ||
                    message.contains("cuanto") && message.contains("debo") ||
                    message.contains("total") && message.contains("debe")) {

                return processUserOwnQuery(request, "TOTAL_DEUDA");
            }
        }

        // Si es ADMIN, primero buscar si hay referencia a un cliente específico
        if ("ADMIN".equals(userRole)) {
            // Intentar extraer nombre del cliente
            User cliente = extraerClienteDeMensaje(message);

            if (cliente != null) {
                // Cuotas pendientes de cliente
                if (message.contains("cuota") && message.contains("pendiente") ||
                        message.contains("que") && message.contains("debe") ||
                        message.contains("falta") && message.contains("pagar")) {

                    return processAdminQueryForClient(request, "CUOTAS_PENDIENTES", cliente);
                }

                // Cuotas pagadas de cliente
                if (message.contains("cuota") && message.contains("pagada") ||
                        message.contains("pago") && message.contains("realizado") ||
                        message.contains("ha") && message.contains("pagado")) {

                    return processAdminQueryForClient(request, "CUOTAS_PAGADAS", cliente);
                }

                // Próxima cuota de cliente
                if (message.contains("proxim") && message.contains("cuota") ||
                        message.contains("siguiente") && message.contains("pago") ||
                        message.contains("cuando") && message.contains("pagar") ||
                        message.contains("vence") && message.contains("cuota")) {

                    return processAdminQueryForClient(request, "PROXIMA_CUOTA", cliente);
                }

                // Deuda total de cliente
                if (message.contains("deuda") && message.contains("total") ||
                        message.contains("cuanto") && message.contains("debe") ||
                        message.contains("monto") && message.contains("debe") ||
                        message.contains("total") && message.contains("debe")) {

                    return processAdminQueryForClient(request, "TOTAL_DEUDA", cliente);
                }

                // Si no se detectó un tipo específico, usar deuda total por defecto
                return processAdminQueryForClient(request, "TOTAL_DEUDA", cliente);
            }
        }

        // Si llegamos aquí, procesar con OpenAI
        return processWithOpenAI(request, state);
    }

    // Método para procesar consultas propias del usuario normal
    private ChatResponse processUserOwnQuery(ChatRequest request, String queryType) {
        String userId = request.getUserId();

        ActionRequest actionRequest = new ActionRequest();
        actionRequest.setAction("QUERY");

        Map<String, Object> queryData = new HashMap<>();
        queryData.put("type", queryType);

        actionRequest.setData(queryData);

        Object result = processAction(actionRequest, userId);

        ChatResponse response = new ChatResponse();
        response.setSuccess(true);

        String messageTxt = "";
        switch (queryType) {
            case "CUOTAS_PENDIENTES":
                messageTxt = "Estas son tus cuotas pendientes:";
                break;
            case "CUOTAS_PAGADAS":
                messageTxt = "Estas son las cuotas que has pagado:";
                break;
            case "PROXIMA_CUOTA":
                messageTxt = "Tu próxima cuota a pagar es:";
                break;
            case "TOTAL_DEUDA":
                messageTxt = "Tu deuda total es:";
                break;
        }

        response.setMessage(messageTxt);
        response.setRequiresAction(true);
        response.setActionType("QUERY");
        response.setActionData(result);

        return response;
    }

    // Método para procesar consultas de admin sobre un cliente específico
    private ChatResponse processAdminQueryForClient(ChatRequest request, String queryType, User cliente) {
        String userId = request.getUserId();

        ActionRequest actionRequest = new ActionRequest();
        actionRequest.setAction("QUERY");

        Map<String, Object> queryData = new HashMap<>();
        queryData.put("type", queryType);
        queryData.put("clienteId", cliente.getId());

        actionRequest.setData(queryData);

        Object result = processAction(actionRequest, userId);

        ChatResponse response = new ChatResponse();
        response.setSuccess(true);

        String clientFullName = cliente.getName() + " " + cliente.getLastname();
        String messageTxt = "";

        switch (queryType) {
            case "CUOTAS_PENDIENTES":
                messageTxt = "Cuotas pendientes de " + clientFullName + ":";
                break;
            case "CUOTAS_PAGADAS":
                messageTxt = "Cuotas pagadas por " + clientFullName + ":";
                break;
            case "PROXIMA_CUOTA":
                messageTxt = "Próxima cuota de " + clientFullName + ":";
                break;
            case "TOTAL_DEUDA":
                messageTxt = "Deuda total de " + clientFullName + ":";
                break;
        }

        response.setMessage(messageTxt);
        response.setRequiresAction(true);
        response.setActionType("QUERY");
        response.setActionData(result);

        return response;
    }

    @SuppressWarnings("unused")
    private String extractGroupIfExists(Matcher matcher, int group) {
        try {
            return matcher.group(group);
        } catch (Exception e) {
            return null;
        }
    }

    // Método auxiliar para procesar consultas de cliente
    @SuppressWarnings("unused")
    private ChatResponse processClientQuery(ChatRequest request, ConversationState state, String queryType) {
        String message = request.getMessage().toLowerCase();
        String userRole = request.getUserRole();
        String userId = request.getUserId();

        System.out.println("Procesando consulta de " + queryType + " para mensaje: " + message);

        // Si es un usuario regular, siempre consultamos sus propios datos
        if (!"ADMIN".equals(userRole)) {
            ActionRequest actionRequest = new ActionRequest();
            actionRequest.setAction("QUERY");

            Map<String, Object> queryData = new HashMap<>();
            queryData.put("type", queryType);

            actionRequest.setData(queryData);

            Object result = processAction(actionRequest, userId);

            ChatResponse response = new ChatResponse();
            response.setSuccess(true);

            String messageTxt = "";
            switch (queryType) {
                case "CUOTAS_PENDIENTES":
                    messageTxt = "Estas son tus cuotas pendientes:";
                    break;
                case "CUOTAS_PAGADAS":
                    messageTxt = "Estas son las cuotas que has pagado:";
                    break;
                case "PROXIMA_CUOTA":
                    messageTxt = "Tu próxima cuota a pagar es:";
                    break;
                case "TOTAL_DEUDA":
                    messageTxt = "Tu deuda total es:";
                    break;
            }

            response.setMessage(messageTxt);
            response.setRequiresAction(true);
            response.setActionType("QUERY");
            response.setActionData(result);

            return response;
        }

        // Si es admin, intentar extraer nombre de cliente del mensaje
        if ("ADMIN".equals(userRole)) {
            // Patrón mejorado para extraer nombre del cliente
            Pattern clientePattern = Pattern
                    .compile("(?i)\\b(?:de|del|de la|para|a|por)\\s+(?:cliente|usuario)?\\s*([a-zñáéíóúü\\s]+)\\b");
            Matcher clienteMatcher = clientePattern.matcher(message);

            String clientName = null;
            if (clienteMatcher.find()) {
                clientName = clienteMatcher.group(1).trim();
                System.out.println("Nombre de cliente extraído: " + clientName);
            }

            // Si no se encontró con el patrón anterior, intentar otro
            if (clientName == null || clientName.isEmpty()) {
                Pattern otroPattern = Pattern.compile("(?i)\\b(?:cliente|usuario)\\s+([a-zñáéíóúü\\s]+)\\b");
                Matcher otroMatcher = otroPattern.matcher(message);
                if (otroMatcher.find()) {
                    clientName = otroMatcher.group(1).trim();
                    System.out.println("Nombre de cliente extraído (patrón alternativo): " + clientName);
                }
            }

            // Si se encontró un nombre, buscar el cliente
            if (clientName != null && !clientName.isEmpty()) {
                User cliente = buscarClientePorNombre(clientName);

                if (cliente != null) {
                    // Cliente encontrado, realizar consulta directa
                    ActionRequest actionRequest = new ActionRequest();
                    actionRequest.setAction("QUERY");

                    Map<String, Object> queryData = new HashMap<>();
                    queryData.put("type", queryType);
                    queryData.put("clienteId", cliente.getId());

                    actionRequest.setData(queryData);

                    Object result = processAction(actionRequest, userId);

                    ChatResponse response = new ChatResponse();
                    response.setSuccess(true);

                    String clientFullName = cliente.getName() + " " + cliente.getLastname();
                    String messageTxt = "";

                    switch (queryType) {
                        case "CUOTAS_PENDIENTES":
                            messageTxt = "Cuotas pendientes de " + clientFullName + ":";
                            break;
                        case "CUOTAS_PAGADAS":
                            messageTxt = "Cuotas pagadas por " + clientFullName + ":";
                            break;
                        case "PROXIMA_CUOTA":
                            messageTxt = "Próxima cuota de " + clientFullName + ":";
                            break;
                        case "TOTAL_DEUDA":
                            messageTxt = "Deuda total de " + clientFullName + ":";
                            break;
                    }

                    response.setMessage(messageTxt);
                    response.setRequiresAction(true);
                    response.setActionType("QUERY");
                    response.setActionData(result);

                    return response;
                } else {
                    // Cliente no encontrado, informar al usuario
                    ChatResponse response = new ChatResponse();
                    response.setSuccess(true);
                    response.setMessage("No se pudo encontrar al cliente '" + clientName
                            + "'. Por favor, intenta con otro nombre o proporciona el ID del cliente.");
                    return response;
                }
            } else {
                // No se especificó cliente, iniciar flujo de solicitud
                state.setCurrentState("CHECKING_CLIENT_DETAILS");
                state.addToContext("queryType", queryType);

                ChatResponse response = new ChatResponse();
                response.setSuccess(true);
                response.setMessage(
                        "¿Para qué cliente quieres consultar esta información? Por favor, proporciona el nombre o ID:");
                return response;
            }
        }

        // No debería llegar aquí, pero por si acaso
        return processWithOpenAI(request, state);
    }

    private ChatResponse createNoPermissionResponse() {
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setMessage("Lo siento, no tienes permisos para realizar esta acción.");
        return response;
    }


    

    // Añadir este método para depurar patrones regex
    /* private boolean debugPattern(String mensaje, String patron) {
        try {
            Pattern pattern = Pattern.compile(patron, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(mensaje);
            boolean matches = matcher.find();

            if (matches) {
                System.out.println("MATCH en patrón: " + patron);
                System.out.println(
                        "Grupo encontrado: " + (matcher.groupCount() > 0 ? matcher.group(1) : "ninguno"));
            }

            return matches;
        } catch (Exception e) {
            System.err.println("Error al comprobar patrón " + patron + ": " + e.getMessage());
            return false;
        }
    } */

    // Método para generar mensajes amigables según el tipo de acción y resultado
    // En ChatbotServiceImpl, modifica el método getFriendlyMessageForAction:
    private String getFriendlyMessageForAction(String action, Object result) {
        // Simplificamos este método para mostrar mensajes más concisos
        switch (action) {
            case "CREATE_USER":
                if (result instanceof Map) {
                    Map<?, ?> mapResult = (Map<?, ?>) result;
                    if (mapResult.containsKey("error")) {
                        return "Lo siento, no pude crear el usuario. " +
                                (mapResult.containsKey("mensaje") ? mapResult.get("mensaje")
                                        : "Ocurrió un error desconocido.");
                    } else {
                        String nombre = mapResult.containsKey("nombre") ? mapResult.get("nombre").toString() : "";
                        String apellido = mapResult.containsKey("apellido") ? mapResult.get("apellido").toString() : "";
                        return "¡Usuario creado exitosamente! He registrado a " + nombre + " " + apellido
                                + " en el sistema.";
                    }
                }
                return "He procesado tu solicitud para crear un usuario.";

            case "REGISTER_SALE":
                if (result instanceof Map) {
                    Map<?, ?> mapResult = (Map<?, ?>) result;
                    if (mapResult.containsKey("error")) {
                        return "Lo siento, no pude registrar la venta. " +
                                (mapResult.containsKey("mensaje") ? mapResult.get("mensaje")
                                        : "Ocurrió un error desconocido.");
                    }
                }
                return "¡Venta registrada exitosamente! Los detalles han sido guardados en el sistema.";

            case "QUERY":
                if (result instanceof List) {
                    List<?> list = (List<?>) result;
                    if (list.isEmpty()) {
                        return "No encontré resultados para tu consulta.";
                    } else if (list.size() == 1 && list.get(0) instanceof Map) {
                        Map<?, ?> item = (Map<?, ?>) list.get(0);
                        if (item.containsKey("mensaje")) {
                            return item.get("mensaje").toString();
                        }
                    }
                    // No devolvemos mensaje genérico, ya que se mostrará en la tabla
                    return "";
                } else if (result instanceof Map) {
                    Map<?, ?> mapResult = (Map<?, ?>) result;
                    if (mapResult.containsKey("error")) {
                        return "Lo siento, no pude completar la consulta. " +
                                (mapResult.containsKey("mensaje") ? mapResult.get("mensaje")
                                        : "Ocurrió un error desconocido.");
                    }
                }
                return "";

            default:
                return "He procesado tu solicitud.";
        }
    }

    private boolean hasPermission(String userRole, String action) {
        // Acciones exclusivas para administradores
        List<String> adminActions = Arrays.asList(
                "REGISTER_SALE", "CREATE_USER", "QUERY_ALL_CLIENTS", "QUERY_ALL_DEBTS");

        if (adminActions.contains(action) && !"ADMIN".equals(userRole)) {
            System.out.println("Permiso denegado para acción: " + action + " con rol: " + userRole);
            return false;
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private Object processAction(ActionRequest actionRequest, String userId) {
        try {
            String action = actionRequest.getAction();
            System.out.println("Procesando acción: " + action);

            Object result = null;
            String queryType = null;

            switch (action) {
                case "REGISTER_SALE":
                    result = processRegisterSale(actionRequest.getData());
                    break;

                case "QUERY":
                    // Obtener el tipo de consulta si está disponible
                    if (actionRequest.getData() instanceof Map) {
                        Map<String, Object> queryData = (Map<String, Object>) actionRequest.getData();
                        if (queryData.containsKey("type")) {
                            queryType = (String) queryData.get("type");
                        }
                    }
                    result = processQuery(actionRequest.getData(), userId);
                    break;

                case "CREATE_USER":
                    result = processCreateUser(actionRequest.getData());
                    break;

                default:
                    System.out.println("Acción no reconocida: " + action);
                    return null;
            }

            // Aplicar formateo de números
            result = formatearValoresNumericos(result);

            // Filtrar campos si es una consulta
            if (action.equals("QUERY") && queryType != null) {
                result = filtrarCampos(result, queryType);
            }

            return result;
        } catch (Exception e) {
            System.err.println("Error en processAction: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private Object processRegisterSale(Object data) {
        try {
            System.out.println("Procesando registro de venta con datos: " + data);
            // Convertir data a VentaDTO
            VentaDTO ventaDTO = objectMapper.convertValue(data, VentaDTO.class);

            // Validar datos mínimos
            if (ventaDTO.getClienteId() == null || ventaDTO.getDetalles() == null || ventaDTO.getDetalles().isEmpty()) {
                throw new RuntimeException("Datos incompletos para registrar venta");
            }

            // Llamar al servicio de venta
            return ventaService.registrarVenta(ventaDTO);

        } catch (Exception e) {
            System.err.println("Error al registrar venta: " + e.getMessage());
            e.printStackTrace();

            // Crear respuesta de error estructurada
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", true);
            errorResponse.put("mensaje", "Error al registrar venta: " + e.getMessage());
            return errorResponse;
        }
    }

    @SuppressWarnings({ "null", "unchecked" })
    private Object processQuery(Object data, String userId) {
        try {
            System.out.println("Procesando consulta con datos: " + data);

            // Convertir data a Map primero para evitar problemas de deserialización
            Map<String, Object> dataMap;
            if (data instanceof Map) {
                dataMap = (Map<String, Object>) data;
            } else {
                dataMap = objectMapper.convertValue(data, Map.class);
            }

            // Extraer tipo de consulta de manera segura
            String queryType = (String) dataMap.get("type");
            if (queryType == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", true);
                errorResponse.put("mensaje", "Tipo de consulta no especificado");
                return errorResponse;
            }

            System.out.println("Tipo de consulta: " + queryType);

            // Procesar userId - convertir a número o buscar por email
            Long userIdNumeric = null;
            User userEntity = null;

            try {
                // Intentar convertir a Long si es un número
                userIdNumeric = Long.parseLong(userId);
            } catch (NumberFormatException e) {
                // Si es un email, buscar usuario por email
                if (userId != null && userId.contains("@")) {
                    userEntity = userRepository.findByEmail(userId).orElse(null);
                    if (userEntity != null) {
                        userIdNumeric = userEntity.getId().longValue();
                        System.out.println(
                                "Usuario encontrado por email: " + userEntity.getName() + ", ID: " + userIdNumeric);
                    }
                }
            }

            // Si no se pudo obtener un ID numérico, manejar el error
            if (userIdNumeric == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", true);
                errorResponse.put("mensaje", "No se pudo identificar al usuario. Por favor inicie sesión nuevamente.");
                return errorResponse;
            }

            // Procesar la solicitud según el tipo de consulta
            switch (queryType) {
                case "CLIENTE_DEUDAS":
                    // Determinar clienteId de manera segura
                    Long clienteId = userIdNumeric; // Por defecto, usar el ID del usuario actual

                    // Si hay un clienteId específico en la solicitud, intentar usarlo
                    if (dataMap.containsKey("clienteId")) {
                        Object rawClienteId = dataMap.get("clienteId");
                        try {
                            if (rawClienteId instanceof Number) {
                                clienteId = ((Number) rawClienteId).longValue();
                            } else if (rawClienteId instanceof String) {
                                // Intentar convertir a Long
                                try {
                                    clienteId = Long.parseLong((String) rawClienteId);
                                } catch (NumberFormatException e) {
                                    // Si no es un número, podría ser un nombre de cliente
                                    User clientePorNombre = buscarClientePorNombre((String) rawClienteId);
                                    if (clientePorNombre != null) {
                                        clienteId = clientePorNombre.getId().longValue();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("Error al convertir clienteId: " + e.getMessage());
                            // Mantener el valor predeterminado (userIdNumeric)
                        }
                    }

                    System.out.println("Consultando deudas del cliente: " + clienteId);

                    // Verificar si el cliente existe
                    User clienteDeudas = null;
                    if (clienteId > 0) {
                        clienteDeudas = userRepository.findById(clienteId.intValue()).orElse(null);
                        if (clienteDeudas == null) {
                            Map<String, Object> errorResponse = new HashMap<>();
                            errorResponse.put("error", true);
                            errorResponse.put("mensaje", "Cliente no encontrado con ID: " + clienteId);
                            return errorResponse;
                        }
                    }

                    // Obtener cuotas de la base de datos
                    List<Map<String, Object>> deudas = new ArrayList<>();
                    List<Cuota> cuotas = new ArrayList<>();

                    // Solo buscar en la BD si el cliente existe
                    if (clienteDeudas != null) {
                        cuotas = cuotaRepository.findCuotasByClienteIdOrdered(clienteId);
                    }

                    // Si no hay cuotas, devolver mensaje informativo
                    if (cuotas.isEmpty()) {
                        Map<String, Object> infoResponse = new HashMap<>();
                        infoResponse.put("mensaje", "No se encontraron deudas pendientes para este cliente.");
                        infoResponse.put("clienteNombre",
                                clienteDeudas != null ? clienteDeudas.getName() + " " + clienteDeudas.getLastname()
                                        : "Cliente");
                        return Collections.singletonList(infoResponse);
                    } else {
                        // Convertir cuotas a formato para mostrar
                        for (Cuota cuota : cuotas) {
                            Map<String, Object> deuda = new HashMap<>();
                            deuda.put("ventaId", cuota.getCredito().getVenta().getId());
                            deuda.put("descripcion", cuota.getCredito().getVenta().getDescripcion());
                            deuda.put("numeroCuota", cuota.getNumeroCuota());
                            deuda.put("monto", cuota.getMonto());
                            deuda.put("fechaVencimiento",
                                    cuota.getFechaVencimiento().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                            deuda.put("estado", cuota.getEstado().toString());
                            deuda.put("clienteNombre", clienteDeudas.getName() + " " + clienteDeudas.getLastname());

                            deudas.add(deuda);
                        }
                    }

                    System.out.println("Deudas encontradas: " + deudas.size());
                    return deudas;

                case "MIS_DEUDAS":
                    // Crear un nuevo Map para la consulta de deudas del usuario actual
                    Map<String, Object> misDeudas = new HashMap<>();
                    misDeudas.put("type", "CLIENTE_DEUDAS");
                    misDeudas.put("clienteId", userIdNumeric);

                    // Utilizar el método processQuery recursivamente
                    return processQuery(misDeudas, userId);

                case "CLIENTES_MOROSOS":
                    // Obtener usuarios con cuotas vencidas
                    List<User> clientesMorosos = cuotaRepository.findClientesConCuotasVencidas();
                    List<Map<String, Object>> resultadosMorosos = new ArrayList<>();

                    if (clientesMorosos == null || clientesMorosos.isEmpty()) {
                        // Devolver mensaje informativo
                        Map<String, Object> infoResponse = new HashMap<>();
                        infoResponse.put("mensaje", "No se encontraron clientes con deudas vencidas.");
                        return Collections.singletonList(infoResponse);
                    } else {
                        // Convertir datos a formato para mostrar
                        for (User clienteMoroso : clientesMorosos) {
                            Map<String, Object> moroso = new HashMap<>();
                            moroso.put("id", clienteMoroso.getId());
                            moroso.put("nombre", clienteMoroso.getName() + " " + clienteMoroso.getLastname());

                            // Contar cuotas vencidas y sumar montos
                            List<Cuota> cuotasVencidas = cuotaRepository
                                    .findCuotasVencidasByClienteId(clienteMoroso.getId().intValue());
                            double montoTotal = 0.0;
                            if (cuotasVencidas != null) {
                                montoTotal = cuotasVencidas.stream()
                                        .mapToDouble(c -> c.getMonto().doubleValue())
                                        .sum();
                            }

                            moroso.put("cuotasVencidas", cuotasVencidas != null ? cuotasVencidas.size() : 0);
                            moroso.put("montoTotal", montoTotal);

                            resultadosMorosos.add(moroso);
                        }
                    }

                    return resultadosMorosos;

                case "CLIENTES_CON_VENTAS":
                    // Obtener clientes con ventas
                    List<Map<String, Object>> clientesConVentas = new ArrayList<>();

                    // Consultar ventas de la base de datos
                    List<User> usuarios = userRepository.findAll();
                    for (User usuario : usuarios) {
                        List<Venta> ventas = ventaService.obtenerVentasPorCliente(usuario.getId().longValue());

                        if (ventas != null && !ventas.isEmpty()) {
                            Map<String, Object> clienteVenta = new HashMap<>();
                            clienteVenta.put("id", usuario.getId());
                            clienteVenta.put("nombre", usuario.getName() + " " + usuario.getLastname());
                            clienteVenta.put("ventas", ventas.size());

                            // Calcular monto total de ventas
                            double montoTotal = ventas.stream()
                                    .mapToDouble(v -> v.getMontoTotal().doubleValue())
                                    .sum();

                            clienteVenta.put("montoTotal", montoTotal);
                            clientesConVentas.add(clienteVenta);
                        }
                    }

                    if (clientesConVentas.isEmpty()) {
                        // Devolver mensaje informativo
                        Map<String, Object> infoResponse = new HashMap<>();
                        infoResponse.put("mensaje", "No se encontraron clientes con ventas registradas.");
                        return Collections.singletonList(infoResponse);
                    }

                    return clientesConVentas;

                case "CLIENTES_CON_DEUDAS":
                    List<Map<String, Object>> clientesConDeudas = new ArrayList<>();

                    // Obtener todos los usuarios
                    List<User> todosLosUsuarios = userRepository.findAll();

                    // Para cada usuario, verificar si tiene cuotas pendientes
                    for (User usuario : todosLosUsuarios) {
                        List<Cuota> cuotasUsuario = cuotaRepository
                                .findCuotasByClienteIdOrdered(usuario.getId().longValue());

                        // Filtrar cuotas no pagadas
                        List<Cuota> cuotasNoPagadas = cuotasUsuario.stream()
                                .filter(c -> c.getEstado() != Cuota.EstadoCuota.PAGADO)
                                .collect(Collectors.toList());

                        if (!cuotasNoPagadas.isEmpty()) {
                            Map<String, Object> clienteInfo = new HashMap<>();
                            clienteInfo.put("id", usuario.getId());
                            clienteInfo.put("nombre", usuario.getName() + " " + usuario.getLastname());
                            clienteInfo.put("cuotasPendientes", cuotasNoPagadas.size());

                            // Calcular deuda total
                            BigDecimal deudaTotal = cuotasNoPagadas.stream()
                                    .map(Cuota::getMonto)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                            clienteInfo.put("montoTotal", deudaTotal);

                            clientesConDeudas.add(clienteInfo);
                        }
                    }

                    if (clientesConDeudas.isEmpty()) {
                        Map<String, Object> infoResponse = new HashMap<>();
                        infoResponse.put("mensaje", "No se encontraron clientes con deudas pendientes.");
                        return Collections.singletonList(infoResponse);
                    }

                    return clientesConDeudas;

                case "PAGOS_CLIENTE":
                    // Consultar pagos de un cliente específico
                    List<Pago> pagosCliente = pagoService.obtenerPagosPorCliente(userIdNumeric);
                    List<Map<String, Object>> resultadosPagos = new ArrayList<>();

                    if (pagosCliente == null || pagosCliente.isEmpty()) {
                        // Devolver mensaje informativo
                        Map<String, Object> infoResponse = new HashMap<>();
                        infoResponse.put("mensaje", "No se encontraron pagos registrados.");
                        return Collections.singletonList(infoResponse);
                    } else {
                        // Convertir pagos a formato para mostrar
                        for (Pago pago : pagosCliente) {
                            Map<String, Object> pagoInfo = new HashMap<>();
                            pagoInfo.put("id", pago.getId());
                            pagoInfo.put("cuotaId", pago.getCuota().getId());
                            pagoInfo.put("numeroCuota", pago.getCuota().getNumeroCuota());
                            pagoInfo.put("monto", pago.getMonto());
                            pagoInfo.put("fechaPago",
                                    pago.getFechaPago().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                            pagoInfo.put("descripcionVenta", pago.getCuota().getCredito().getVenta().getDescripcion());

                            resultadosPagos.add(pagoInfo);
                        }
                    }

                    return resultadosPagos;
                case "PROXIMA_CUOTA":
                    // Obtener el ID del cliente (usuario actual o específico)
                    Long clienteIdProximaCuota = userIdNumeric;

                    // Buscar el cliente
                    User clienteProximaCuota = userRepository.findById(clienteIdProximaCuota.intValue()).orElse(null);
                    if (clienteProximaCuota == null) {
                        Map<String, Object> errorResponse = new HashMap<>();
                        errorResponse.put("error", true);
                        errorResponse.put("mensaje", "Cliente no encontrado con ID: " + clienteIdProximaCuota);
                        return errorResponse;
                    }

                    // Obtener todas las cuotas del cliente
                    List<Cuota> todasLasCuotas = cuotaRepository.findCuotasByClienteIdOrdered(clienteIdProximaCuota);

                    // Filtrar solo las pendientes
                    List<Cuota> cuotasPendientes = todasLasCuotas.stream()
                            .filter(c -> c.getEstado() == Cuota.EstadoCuota.PENDIENTE)
                            .collect(Collectors.toList());

                    if (cuotasPendientes.isEmpty()) {
                        Map<String, Object> infoResponse = new HashMap<>();
                        infoResponse.put("mensaje", "No tienes cuotas pendientes por pagar.");
                        return Collections.singletonList(infoResponse);
                    }

                    // Ordenar por fecha de vencimiento y tomar solo la primera (más próxima)
                    Cuota proximaCuota = cuotasPendientes.stream()
                            .min(Comparator.comparing(Cuota::getFechaVencimiento))
                            .get();

                    // Convertir a formato para mostrar
                    Map<String, Object> cuotaInfo = new HashMap<>();
                    cuotaInfo.put("numeroCuota", proximaCuota.getNumeroCuota());
                    cuotaInfo.put("monto", proximaCuota.getMonto());
                    cuotaInfo.put("fechaVencimiento",
                            proximaCuota.getFechaVencimiento().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                    cuotaInfo.put("estado", proximaCuota.getEstado().toString());
                    cuotaInfo.put("descripcion", proximaCuota.getCredito().getVenta().getDescripcion());
                    /* cuotaInfo.put("mensaje", "Tu próxima cuota a pagar"); */

                    return Collections.singletonList(cuotaInfo);

                case "TOTAL_DEUDA":
                    // Obtener el ID del cliente
                    Long clienteIdDeuda = userIdNumeric;

                    // Si es admin y se especificó un cliente
                    if (dataMap.containsKey("clienteId")) {
                        Object rawClienteId = dataMap.get("clienteId");
                        try {
                            if (rawClienteId instanceof Number) {
                                clienteIdDeuda = ((Number) rawClienteId).longValue();
                                System.out.println("ID de cliente específico encontrado (Number): " + clienteIdDeuda);
                            } else if (rawClienteId instanceof String) {
                                clienteIdDeuda = Long.parseLong((String) rawClienteId);
                                System.out.println("ID de cliente específico encontrado (String): " + clienteIdDeuda);
                            }
                        } catch (Exception e) {
                            System.out.println("Error al convertir clienteId: " + e.getMessage());
                        }
                    }

                    // Verificar si el cliente existe
                    User clienteDeuda = userRepository.findById(clienteIdDeuda.intValue()).orElse(null);
                    if (clienteDeuda == null) {
                        Map<String, Object> errorResponse = new HashMap<>();
                        errorResponse.put("error", true);
                        errorResponse.put("mensaje", "Cliente no encontrado con ID: " + clienteIdDeuda);
                        return errorResponse;
                    }

                    // Obtener todas las cuotas del cliente
                    List<Cuota> todasLasCuotasCliente = cuotaRepository.findCuotasByClienteIdOrdered(clienteIdDeuda);

                    // Filtrar solo las pendientes y vencidas
                    List<Cuota> cuotasNoPagadas = todasLasCuotasCliente.stream()
                            .filter(c -> c.getEstado() != Cuota.EstadoCuota.PAGADO)
                            .collect(Collectors.toList());

                    // Calcular total
                    BigDecimal montoTotal = cuotasNoPagadas.stream()
                            .map(Cuota::getMonto)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Formatear respuesta
                    Map<String, Object> deudaInfo = new HashMap<>();
                    deudaInfo.put("clienteNombre", clienteDeuda.getName() + " " + clienteDeuda.getLastname());
                    deudaInfo.put("totalDeuda", montoTotal);
                    deudaInfo.put("cuotasPendientes", cuotasNoPagadas.size());

                    return Collections.singletonList(deudaInfo);

                case "CUOTAS_PENDIENTES":
                    // Obtener el ID del cliente
                    Long clienteIdCuotas = userIdNumeric;

                    // Si es admin y se especificó un cliente
                    if (dataMap.containsKey("clienteId")) {
                        Object rawClienteId = dataMap.get("clienteId");
                        try {
                            if (rawClienteId instanceof Number) {
                                clienteIdCuotas = ((Number) rawClienteId).longValue();
                            } else if (rawClienteId instanceof String) {
                                clienteIdCuotas = Long.parseLong((String) rawClienteId);
                            }
                        } catch (Exception e) {
                            System.out.println("Error al convertir clienteId: " + e.getMessage());
                        }
                    }

                    // Verificar si el cliente existe
                    User clienteCuotas = userRepository.findById(clienteIdCuotas.intValue()).orElse(null);
                    if (clienteCuotas == null) {
                        Map<String, Object> errorResponse = new HashMap<>();
                        errorResponse.put("error", true);
                        errorResponse.put("mensaje", "Cliente no encontrado con ID: " + clienteIdCuotas);
                        return errorResponse;
                    }

                    // Obtener todas las cuotas del cliente
                    List<Cuota> todasLasCuotasDelCliente = cuotaRepository
                            .findCuotasByClienteIdOrdered(clienteIdCuotas);

                    // Filtrar SOLO las pendientes (no pagadas y no vencidas)
                    List<Cuota> soloLasCuotasPendientes = todasLasCuotasDelCliente.stream()
                            .filter(c -> c.getEstado() == Cuota.EstadoCuota.PENDIENTE)
                            .collect(Collectors.toList());

                    if (soloLasCuotasPendientes.isEmpty()) {
                        Map<String, Object> infoResponse = new HashMap<>();
                        infoResponse.put("mensaje", "No se encontraron cuotas pendientes para este cliente.");
                        infoResponse.put("clienteNombre", clienteCuotas.getName() + " " + clienteCuotas.getLastname());
                        return Collections.singletonList(infoResponse);
                    }

                    // Convertir a formato para mostrar
                    List<Map<String, Object>> cuotasPendientesInfo = new ArrayList<>();
                    for (Cuota cuota : soloLasCuotasPendientes) {
                        Map<String, Object> cuotaInfos = new HashMap<>();
                        cuotaInfos.put("ventaId", cuota.getCredito().getVenta().getId());
                        cuotaInfos.put("descripcion", cuota.getCredito().getVenta().getDescripcion());
                        cuotaInfos.put("numeroCuota", cuota.getNumeroCuota());
                        cuotaInfos.put("monto", cuota.getMonto());
                        cuotaInfos.put("fechaVencimiento",
                                cuota.getFechaVencimiento().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                        cuotaInfos.put("estado", cuota.getEstado().toString());
                        cuotaInfos.put("clienteNombre", clienteCuotas.getName() + " " + clienteCuotas.getLastname());

                        cuotasPendientesInfo.add(cuotaInfos);
                    }

                    return cuotasPendientesInfo;

                case "CUOTAS_PAGADAS":
                    // Obtener el ID del cliente
                    Long clienteIdCuotasPagadas = userIdNumeric;

                    // Si es admin y se especificó un cliente
                    if (dataMap.containsKey("clienteId")) {
                        Object rawClienteId = dataMap.get("clienteId");
                        try {
                            if (rawClienteId instanceof Number) {
                                clienteIdCuotasPagadas = ((Number) rawClienteId).longValue();
                            } else if (rawClienteId instanceof String) {
                                clienteIdCuotasPagadas = Long.parseLong((String) rawClienteId);
                            }
                        } catch (Exception e) {
                            System.out.println("Error al convertir clienteId: " + e.getMessage());
                        }
                    }

                    // Verificar si el cliente existe
                    User clienteCuotasPagadas = userRepository.findById(clienteIdCuotasPagadas.intValue()).orElse(null);
                    if (clienteCuotasPagadas == null) {
                        Map<String, Object> errorResponse = new HashMap<>();
                        errorResponse.put("error", true);
                        errorResponse.put("mensaje", "Cliente no encontrado con ID: " + clienteIdCuotasPagadas);
                        return errorResponse;
                    }

                    // Obtener todas las cuotas del cliente
                    List<Cuota> todasCuotasDelCliente = cuotaRepository
                            .findCuotasByClienteIdOrdered(clienteIdCuotasPagadas);

                    // Filtrar SOLO las PAGADAS
                    List<Cuota> soloLasCuotasPagadas = todasCuotasDelCliente.stream()
                            .filter(c -> c.getEstado() == Cuota.EstadoCuota.PAGADO)
                            .collect(Collectors.toList());

                    if (soloLasCuotasPagadas.isEmpty()) {
                        Map<String, Object> infoResponse = new HashMap<>();
                        infoResponse.put("mensaje", "El cliente no ha pagado ninguna cuota hasta el momento.");
                        infoResponse.put("clienteNombre",
                                clienteCuotasPagadas.getName() + " " + clienteCuotasPagadas.getLastname());
                        return Collections.singletonList(infoResponse);
                    }

                    // Convertir a formato para mostrar
                    List<Map<String, Object>> cuotasPagadasInfo = new ArrayList<>();
                    for (Cuota cuota : soloLasCuotasPagadas) {
                        Map<String, Object> cuotaInfos = new HashMap<>();
                        cuotaInfos.put("descripcion", cuota.getCredito().getVenta().getDescripcion());
                        cuotaInfos.put("numeroCuota", cuota.getNumeroCuota());
                        cuotaInfos.put("monto", cuota.getMonto());
                        cuotaInfos.put("fechaVencimiento",
                                cuota.getFechaVencimiento().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                        cuotaInfos.put("estado", cuota.getEstado().toString());

                        // Buscar el pago asociado a esta cuota
                        List<Pago> pagos = pagoService.obtenerPagosPorCuota(cuota.getId());
                        if (!pagos.isEmpty() && pagos.get(0) != null) {
                            cuotaInfos.put("fechaPago",
                                    pagos.get(0).getFechaPago().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                        }

                        cuotasPagadasInfo.add(cuotaInfos);
                    }

                    return cuotasPagadasInfo;

                default:
                    System.out.println("Tipo de consulta no reconocido: " + queryType);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", true);
                    errorResponse.put("mensaje", "Tipo de consulta no reconocido: " + queryType);
                    return errorResponse;
            }

        } catch (Exception e) {
            System.err.println("Error al procesar consulta: " + e.getMessage());
            e.printStackTrace();

            // Crear respuesta de error estructurada
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", true);
            errorResponse.put("mensaje", "Error al procesar consulta: " + e.getMessage());
            return errorResponse;
        }
    }

    private Object processCreateUser(Object data) {
        try {
            System.out.println("Procesando creación de usuario con datos: " + data);

            // Convertir data a UserRequest
            UserRequest userRequest = objectMapper.convertValue(data, UserRequest.class);

            // Validar datos mínimos
            if (userRequest.getName() == null || userRequest.getLastname() == null || userRequest.getDni() == null) {
                throw new RuntimeException("Datos incompletos para crear usuario");
            }

            // Asegurarse de que tenga los campos obligatorios
            if (userRequest.getPhone() == null || userRequest.getPhone().isEmpty()) {
                throw new RuntimeException("El teléfono es obligatorio");
            }

            if (userRequest.getAddress() == null || userRequest.getAddress().isEmpty()) {
                throw new RuntimeException("La dirección es obligatoria");
            }

            // Por defecto, crear como usuario regular (no admin)
            userRequest.setAdmin(false);

            // Llamar al servicio para crear usuario
            User nuevoUsuario = userService.save(userRequest);

            // Crear respuesta con datos del usuario
            Map<String, Object> userResponse = new HashMap<>();
            userResponse.put("id", nuevoUsuario.getId());
            userResponse.put("nombre", nuevoUsuario.getName());
            userResponse.put("apellido", nuevoUsuario.getLastname());
            userResponse.put("email", nuevoUsuario.getEmail());
            userResponse.put("mensaje", "Usuario creado exitosamente");

            return userResponse;

        } catch (Exception e) {
            System.err.println("Error al crear usuario: " + e.getMessage());
            e.printStackTrace();

            // Crear respuesta de error estructurada
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", true);
            errorResponse.put("mensaje", "Error al crear usuario: " + e.getMessage());
            return errorResponse;
        }
    }

    private ChatResponse processWithOpenAI(ChatRequest request, ConversationState state) {
        try {
            // Obtener o crear historial de conversación para este usuario
            String userId = request.getUserId();
            if (!userConversations.containsKey(userId)) {
                userConversations.put(userId, new ArrayList<>());
            }
            List<ChatMessage> conversationHistory = userConversations.get(userId);

            // Añadir mensaje del usuario al historial
            ChatMessage userMessage = new ChatMessage("user", request.getMessage());
            conversationHistory.add(userMessage);

            // Limitar el historial a las últimas 20 interacciones
            if (conversationHistory.size() > 20) {
                conversationHistory = conversationHistory.subList(
                        conversationHistory.size() - 20, conversationHistory.size());
                userConversations.put(userId, conversationHistory);
            }

            // Preparar la lista completa de mensajes
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", SYSTEM_PROMPT));

            // Añadir información contextual sobre el usuario
            String userRole = request.getUserRole();
            if (userRole == null || userRole.isEmpty()) {
                userRole = "USER"; // Rol por defecto
            }

            // Añadir el contexto de conversación actual si existe
            if (!"IDLE".equals(state.getCurrentState())) {
                String contextPrompt = "Estamos en medio de un proceso de " +
                        getContextDescription(state.getCurrentState()) +
                        ". El usuario está respondiendo a la solicitud de datos.";
                messages.add(new ChatMessage("system", contextPrompt));
            }

            // Añadir historial de conversación
            messages.addAll(conversationHistory);

            // Crear la solicitud a OpenAI
            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .model("gpt-3.5-turbo")
                    .messages(messages)
                    .temperature(0.7)
                    .maxTokens(800)
                    .build();

            // Obtener respuesta de OpenAI
            String responseContent = openAiService.createChatCompletion(completionRequest)
                    .getChoices().get(0).getMessage().getContent();

            System.out.println("Respuesta original de OpenAI: " + responseContent);

            // Guardar respuesta en historial
            conversationHistory.add(new ChatMessage("assistant", responseContent));
            userConversations.put(userId, conversationHistory);

            // Verificar si toda la respuesta es un JSON
            if (responseContent.trim().startsWith("{") && responseContent.trim().endsWith("}")) {
                try {
                    ActionRequest actionRequest = objectMapper.readValue(responseContent, ActionRequest.class);

                    // Verificar permisos
                    if (!hasPermission(userRole, actionRequest.getAction())) {
                        ChatResponse response = new ChatResponse();
                        response.setSuccess(true);
                        response.setMessage("Lo siento, no tienes permisos para realizar esta acción.");
                        return response;
                    }

                    // Ejecutar acción
                    Object result = processAction(actionRequest, userId);

                    // Crear respuesta amigable según la acción
                    String friendlyMessage = getFriendlyMessageForAction(actionRequest.getAction(), result);

                    // Configurar respuesta
                    ChatResponse response = new ChatResponse();
                    response.setSuccess(true);
                    response.setMessage(friendlyMessage);

                    // Añadir datos de acción si hay resultados
                    if (result != null) {
                        response.setRequiresAction(true);
                        response.setActionType(actionRequest.getAction());
                        response.setActionData(result);
                    }

                    return response;
                } catch (Exception e) {
                    System.err.println("Error al procesar JSON directo: " + e.getMessage());
                }
            }

            // Buscar acciones en formato JSON con patrones
            String jsonStr = extractJsonFromResponse(responseContent);

            // Inicializar respuesta
            ChatResponse response = new ChatResponse();
            response.setSuccess(true);

            // Si encontramos JSON, procesarlo
            if (jsonStr != null) {
                try {
                    ActionRequest actionRequest = objectMapper.readValue(jsonStr, ActionRequest.class);

                    // Verificar permisos
                    if (!hasPermission(userRole, actionRequest.getAction())) {
                        response.setMessage("Lo siento, no tienes permisos para realizar esta acción.");
                        return response;
                    }

                    // Ejecutar acción
                    Object result = processAction(actionRequest, userId);

                    // Crear respuesta amigable según la acción
                    String friendlyMessage = getFriendlyMessageForAction(actionRequest.getAction(), result);

                    // Limpiar JSON de la respuesta visible
                    String cleanedMessage = cleanJsonFromResponse(responseContent, jsonStr);

                    if (cleanedMessage.isEmpty() && !friendlyMessage.isEmpty()) {
                        response.setMessage(friendlyMessage);
                    } else if (!cleanedMessage.isEmpty() && friendlyMessage.isEmpty()) {
                        response.setMessage(cleanedMessage);
                    } else {
                        response.setMessage(cleanedMessage +
                                (cleanedMessage.isEmpty() ? "" : "\n\n") +
                                friendlyMessage);
                    }

                    // Configurar datos de acción si hay resultados
                    if (result != null) {
                        response.setRequiresAction(true);
                        response.setActionType(actionRequest.getAction());
                        response.setActionData(result);
                    }

                } catch (Exception e) {
                    System.err.println("Error al procesar acción JSON: " + e.getMessage());
                    response.setMessage(responseContent);
                }
            } else {
                // No se encontró JSON, devolver la respuesta original
                response.setMessage(responseContent);
            }

            return response;

        } catch (Exception e) {
            System.err.println("Error general en processWithOpenAI: " + e.getMessage());
            e.printStackTrace();

            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Lo siento, ha ocurrido un error al procesar tu mensaje: " + e.getMessage());
            return errorResponse;
        }
    }

    // Método auxiliar para extraer JSON de la respuesta
    private String extractJsonFromResponse(String responseContent) {
        // Buscar con ```{...}```
        Pattern patternWithBackticks = Pattern.compile("```\\s*\\{.*?\\}\\s*```", Pattern.DOTALL);
        Matcher matcherWithBackticks = patternWithBackticks.matcher(responseContent);

        if (matcherWithBackticks.find()) {
            return matcherWithBackticks.group(0).replaceAll("```\\s*", "").replaceAll("\\s*```", "").trim();
        } else {
            // Buscar {... simplemente como alternativa
            Pattern patternWithoutBackticks = Pattern.compile("\\{\\s*\"action\".*?\\}", Pattern.DOTALL);
            Matcher matcherWithoutBackticks = patternWithoutBackticks.matcher(responseContent);

            if (matcherWithoutBackticks.find()) {
                return matcherWithoutBackticks.group(0).trim();
            }
        }

        return null;
    }

    // Método auxiliar para limpiar JSON de la respuesta
    private String cleanJsonFromResponse(String responseContent, String jsonStr) {
        String cleanedMessage = responseContent.replace(jsonStr, "")
                .replace("```", "").trim();

        // Quitar mensajes específicos que no queremos mostrar
        cleanedMessage = cleanedMessage.replaceAll("Respuesta recibida pero está vacía.", "")
                .replaceAll("(?i)He encontrado \\d+ resultado.*", "")
                .trim();

        return cleanedMessage;
    }

    // Método auxiliar para obtener descripción del contexto
    private String getContextDescription(String state) {
        switch (state) {
            case "CREATING_USER":
                return "creación de usuario";
            case "CHECKING_CLIENT_DETAILS":
                return "consulta de datos de cliente";
            case "REGISTERING_SALE":
                return "registro de venta";
            default:
                return "interacción";
        }
    }

    // Método mejorado para buscar clientes por nombre
    private User buscarClientePorNombre(String nombre) {
        if (nombre == null || nombre.isEmpty()) {
            System.out.println("Nombre vacío en buscarClientePorNombre");
            return null;
        }
        
        System.out.println("Buscando cliente con nombre: '" + nombre + "'");
        
        // Normalizar nombre
        nombre = nombre.toLowerCase().trim();
        
        List<User> usuarios = userRepository.findAll();
        List<User> candidatos = new ArrayList<>();
        
        // Buscar coincidencia exacta (nombre + apellido)
        for (User usuario : usuarios) {
            String nombreCompleto = (usuario.getName() + " " + usuario.getLastname()).toLowerCase();
            if (nombreCompleto.equals(nombre)) {
                System.out.println("Coincidencia exacta: " + usuario.getName() + " " + usuario.getLastname());
                return usuario;
            }
        }
        
        // Buscar coincidencia por nombre o apellido exacto
        for (User usuario : usuarios) {
            if (usuario.getName().toLowerCase().equals(nombre) || 
                usuario.getLastname().toLowerCase().equals(nombre)) {
                System.out.println("Coincidencia por nombre/apellido exacto: " + 
                                  usuario.getName() + " " + usuario.getLastname());
                return usuario;
            }
        }
        
        // Buscar coincidencia por nombre completo parcial
        for (User usuario : usuarios) {
            String nombreCompleto = (usuario.getName() + " " + usuario.getLastname()).toLowerCase();
            if (nombreCompleto.contains(nombre) || nombre.contains(nombreCompleto)) {
                candidatos.add(usuario);
            }
        }
        
        // Buscar coincidencia por nombre o apellido parcial
        if (candidatos.isEmpty()) {
            for (User usuario : usuarios) {
                if (usuario.getName().toLowerCase().contains(nombre) || 
                    usuario.getLastname().toLowerCase().contains(nombre) ||
                    nombre.contains(usuario.getName().toLowerCase()) ||
                    nombre.contains(usuario.getLastname().toLowerCase())) {
                    candidatos.add(usuario);
                }
            }
        }
        
        // Si encontramos candidatos, devolver el primero
        if (!candidatos.isEmpty()) {
            User primerCandidato = candidatos.get(0);
            System.out.println("Coincidencia parcial: " + 
                              primerCandidato.getName() + " " + primerCandidato.getLastname());
            return primerCandidato;
        }
        
        System.out.println("No se encontró ningún cliente con nombre: " + nombre);
        return null;
    }

    /**
     * Procesa el prompt y extrae información sobre el cliente
     */
    private User extraerClienteDeMensaje(String mensaje) {
        if (mensaje == null) {
            return null;
        }

        System.out.println("Analizando mensaje para extraer cliente: " + mensaje);

        // Normalizar mensaje
        mensaje = mensaje.toLowerCase().trim();

        // Detectar patrones comunes
        String clienteNombre = null;

        // Patrón: "cliente [NOMBRE]"
        Pattern pattern1 = Pattern.compile("cliente\\s+([a-zñáéíóúü\\s]+)[\\.,;]?");
        Matcher matcher1 = pattern1.matcher(mensaje);
        if (matcher1.find()) {
            clienteNombre = matcher1.group(1).trim();
        }

        // Patrón: "de [NOMBRE]"
        if (clienteNombre == null) {
            Pattern pattern2 = Pattern.compile("de\\s+([a-zñáéíóúü\\s]+)[\\.,;]?");
            Matcher matcher2 = pattern2.matcher(mensaje);
            if (matcher2.find()) {
                clienteNombre = matcher2.group(1).trim();
            }
        }

        // Patrón: "del cliente [NOMBRE]"
        if (clienteNombre == null) {
            Pattern pattern3 = Pattern.compile("del\\s+cliente\\s+([a-zñáéíóúü\\s]+)[\\.,;]?");
            Matcher matcher3 = pattern3.matcher(mensaje);
            if (matcher3.find()) {
                clienteNombre = matcher3.group(1).trim();
            }
        }

        // Si hemos encontrado un candidato, buscar en la base de datos
        if (clienteNombre != null) {
            System.out.println("Nombre candidato encontrado: " + clienteNombre);

            // Limpiar palabras comunes
            clienteNombre = clienteNombre.replaceAll("\\b(el|la|los|las|un|una|unos|unas)\\b", "").trim();
            clienteNombre = clienteNombre.replaceAll("\\s+", " "); // normalizar espacios

            return buscarClientePorNombre(clienteNombre);
        }

        // Si no encontramos por patrones, intentar extraer nombres directamente
        List<User> usuarios = userRepository.findAll();
        for (User usuario : usuarios) {
            if (mensaje.contains(usuario.getName().toLowerCase()) ||
                    mensaje.contains(usuario.getLastname().toLowerCase())) {
                System.out.println("Cliente encontrado por coincidencia directa: " +
                        usuario.getName() + " " + usuario.getLastname());
                return usuario;
            }
        }

        return null;
    }


    private Map<String, String> extraerDatosUsuario(String mensaje) {
        Map<String, String> datos = new HashMap<>();

        System.out.println("Extrayendo datos de: " + mensaje);

        try {
            // Normalizar mensaje
            mensaje = mensaje.toLowerCase()
                    .replace(",", " ")
                    .replace(".", " ")
                    .replace(";", " ")
                    .replace("con el", " ")
                    .replace("con la", " ")
                    .replace("con", " ")
                    .replace("sea", " ")
                    .replace("es", " ");

            // Extraer nombre
            int nombreIdx = mensaje.indexOf("nombre");
            if (nombreIdx >= 0) {
                int startNombre = nombreIdx + 6;
                // Buscar el fin del nombre (hasta la siguiente palabra clave)
                int endNombre = indexOf(mensaje, startNombre, "apellido", "dni", "telefono", "teléfono", "direccion",
                        "dirección");
                if (endNombre > startNombre) {
                    String nombre = mensaje.substring(startNombre, endNombre).trim();
                    if (nombre.length() >= 2) {
                        datos.put("nombre", nombre);
                        System.out.println("Nombre extraído: '" + nombre + "'");
                    }
                }
            }

            // Extraer apellido
            int apellidoIdx = mensaje.indexOf("apellido");
            if (apellidoIdx >= 0) {
                int startApellido = apellidoIdx + 8;
                // Buscar el fin del apellido
                int endApellido = indexOf(mensaje, startApellido, "dni", "telefono", "teléfono", "direccion",
                        "dirección");
                if (endApellido > startApellido) {
                    String apellido = mensaje.substring(startApellido, endApellido).trim();
                    if (apellido.length() >= 2) {
                        datos.put("apellido", apellido);
                        System.out.println("Apellido extraído: '" + apellido + "'");
                    }
                }
            }

            // Extraer DNI
            int dniIdx = mensaje.indexOf("dni");
            if (dniIdx >= 0) {
                // Buscar 8 dígitos después de "dni"
                String dniPart = mensaje.substring(dniIdx + 3);
                String dni = extraerDigitos(dniPart, 8);
                if (dni != null) {
                    datos.put("dni", dni);
                    System.out.println("DNI extraído: '" + dni + "'");
                }
            }

            // Extraer teléfono
            int telefonoIdx = mensaje.indexOf("telefono");
            if (telefonoIdx < 0) {
                telefonoIdx = mensaje.indexOf("teléfono");
            }
            if (telefonoIdx >= 0) {
                // Buscar 9 dígitos después de "teléfono"
                String telefonoPart = mensaje.substring(telefonoIdx + 8);
                String telefono = extraerDigitos(telefonoPart, 9);
                if (telefono != null) {
                    datos.put("telefono", telefono);
                    System.out.println("Teléfono extraído: '" + telefono + "'");
                }
            }

            // Extraer dirección
            int direccionIdx = mensaje.indexOf("direccion");
            if (direccionIdx < 0) {
                direccionIdx = mensaje.indexOf("dirección");
            }
            if (direccionIdx >= 0) {
                // La dirección es todo lo que queda hasta el final
                String direccion = mensaje.substring(direccionIdx + 9).trim();
                if (!direccion.isEmpty()) {
                    datos.put("direccion", direccion);
                    System.out.println("Dirección extraída: '" + direccion + "'");
                }
            }

        } catch (Exception e) {
            System.err.println("Error al extraer datos: " + e.getMessage());
        }

        return datos;
    }

    // Método auxiliar para encontrar la primera ocurrencia de varias palabras
    private int indexOf(String texto, int inicio, String... palabras) {
        int min = texto.length();
        for (String palabra : palabras) {
            int idx = texto.indexOf(palabra, inicio);
            if (idx >= 0 && idx < min) {
                min = idx;
            }
        }
        return min;
    }

    // Método auxiliar para extraer n dígitos consecutivos de un texto
    private String extraerDigitos(String texto, int cantidad) {
        StringBuilder digitos = new StringBuilder();
        for (char c : texto.toCharArray()) {
            if (Character.isDigit(c)) {
                digitos.append(c);
                if (digitos.length() == cantidad) {
                    return digitos.toString();
                }
            } else if (digitos.length() > 0 && !Character.isWhitespace(c)) {
                // Si ya empezamos a recolectar dígitos y encontramos algo que no es
                // un dígito ni un espacio, reiniciamos
                digitos.setLength(0);
            }
        }
        return digitos.length() == cantidad ? digitos.toString() : null;
    }



    private boolean validarDatosUsuario(UserRequest user) {
        try {
            // Validar nombre y apellido
            if (user.getName() == null || user.getName().length() < 2 ||
                    user.getLastname() == null || user.getLastname().length() < 2) {
                System.out.println("Nombre o apellido inválido");
                return false;
            }

            // Validar DNI - Debe ser exactamente 8 dígitos
            if (user.getDni() == null || user.getDni().length() != 8 || !esNumerico(user.getDni())) {
                System.out.println("DNI inválido: " + user.getDni());
                return false;
            }

            // Validar teléfono - Debe ser exactamente 9 dígitos
            if (user.getPhone() == null || user.getPhone().length() != 9 || !esNumerico(user.getPhone())) {
                System.out.println("Teléfono inválido: " + user.getPhone());
                return false;
            }

            // Validar dirección
            if (user.getAddress() == null || user.getAddress().length() < 5) {
                System.out.println("Dirección inválida");
                return false;
            }

            return true;
        } catch (Exception e) {
            System.err.println("Error validando datos: " + e.getMessage());
            return false;
        }
    }

    private boolean esNumerico(String str) {
        if (str == null) {
            return false;
        }
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }


    // Ayudante para filtrar palabras comunes
    @SuppressWarnings("unused")
    private boolean esComun(String palabra) {
        String[] palabrasComunes = { "cliente", "usuario", "deuda", "cuota", "pagar", "debe", "total", "cuantas",
                "cuanto", "pendiente" };
        for (String comun : palabrasComunes) {
            if (comun.equals(palabra)) {
                return true;
            }
        }
        return false;
    }

    // Método para filtrar campos innecesarios
    @SuppressWarnings("unchecked")
    private Object filtrarCampos(Object data, String queryType) {
        if (!(data instanceof List)) {
            return data;
        }

        List<Object> listaOriginal = (List<Object>) data;
        List<Object> listaFiltrada = new ArrayList<>();

        for (Object item : listaOriginal) {
            if (item instanceof Map) {
                Map<String, Object> mapaOriginal = (Map<String, Object>) item;
                Map<String, Object> mapaFiltrado = new HashMap<>();

                switch (queryType) {
                    case "CUOTAS_PENDIENTES":
                        // Incluir solo campos relevantes para cuotas pendientes
                        if (mapaOriginal.containsKey("descripcion"))
                            mapaFiltrado.put("descripcion", mapaOriginal.get("descripcion"));
                        if (mapaOriginal.containsKey("monto"))
                            mapaFiltrado.put("monto", mapaOriginal.get("monto"));
                        if (mapaOriginal.containsKey("numeroCuota"))
                            mapaFiltrado.put("numeroCuota", mapaOriginal.get("numeroCuota"));
                        if (mapaOriginal.containsKey("fechaVencimiento"))
                            mapaFiltrado.put("fechaVencimiento", mapaOriginal.get("fechaVencimiento"));
                        if (mapaOriginal.containsKey("estado"))
                            mapaFiltrado.put("estado", mapaOriginal.get("estado"));
                        break;

                    case "PROXIMA_CUOTA":
                        // Incluir solo campos relevantes para próxima cuota
                        if (mapaOriginal.containsKey("descripcion"))
                            mapaFiltrado.put("descripcion", mapaOriginal.get("descripcion"));
                        if (mapaOriginal.containsKey("monto"))
                            mapaFiltrado.put("monto", mapaOriginal.get("monto"));
                        if (mapaOriginal.containsKey("numeroCuota"))
                            mapaFiltrado.put("numeroCuota", mapaOriginal.get("numeroCuota"));
                        if (mapaOriginal.containsKey("fechaVencimiento"))
                            mapaFiltrado.put("fechaVencimiento", mapaOriginal.get("fechaVencimiento"));
                        if (mapaOriginal.containsKey("mensaje"))
                            mapaFiltrado.put("mensaje", mapaOriginal.get("mensaje"));
                        break;

                    case "TOTAL_DEUDA":
                        // Incluir solo campos relevantes para total deuda
                        if (mapaOriginal.containsKey("clienteNombre"))
                            mapaFiltrado.put("clienteNombre", mapaOriginal.get("clienteNombre"));
                        if (mapaOriginal.containsKey("totalDeuda"))
                            mapaFiltrado.put("totalDeuda", mapaOriginal.get("totalDeuda"));
                        if (mapaOriginal.containsKey("cuotasPendientes"))
                            mapaFiltrado.put("cuotasPendientes", mapaOriginal.get("cuotasPendientes"));
                        if (mapaOriginal.containsKey("mensaje"))
                            mapaFiltrado.put("mensaje", mapaOriginal.get("mensaje"));
                        break;

                    case "CLIENTES_MOROSOS":
                        // Incluir solo campos relevantes para clientes morosos
                        if (mapaOriginal.containsKey("id"))
                            mapaFiltrado.put("id", mapaOriginal.get("id"));
                        if (mapaOriginal.containsKey("nombre"))
                            mapaFiltrado.put("nombre", mapaOriginal.get("nombre"));
                        if (mapaOriginal.containsKey("cuotasVencidas"))
                            mapaFiltrado.put("cuotasVencidas", mapaOriginal.get("cuotasVencidas"));
                        if (mapaOriginal.containsKey("montoTotal"))
                            mapaFiltrado.put("montoTotal", mapaOriginal.get("montoTotal"));
                        break;

                    default:
                        // Para otras consultas, mantener todos los campos
                        mapaFiltrado = mapaOriginal;
                }

                listaFiltrada.add(mapaFiltrado);
            } else {
                listaFiltrada.add(item);
            }
        }

        return listaFiltrada;
    }

    // Nuevo método para formatear números antes de enviar respuesta
    @SuppressWarnings("unchecked")
    private Object formatearValoresNumericos(Object data) {
        if (data instanceof List) {
            List<Object> listaOriginal = (List<Object>) data;
            List<Object> listaNueva = new ArrayList<>();

            for (Object item : listaOriginal) {
                if (item instanceof Map) {
                    listaNueva.add(formatearMapaNumericos((Map<String, Object>) item));
                } else {
                    listaNueva.add(item);
                }
            }
            return listaNueva;
        } else if (data instanceof Map) {
            return formatearMapaNumericos((Map<String, Object>) data);
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> formatearMapaNumericos(Map<String, Object> mapa) {
        Map<String, Object> nuevoMapa = new HashMap<>();

        for (Map.Entry<String, Object> entry : mapa.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Formatear números para quitar decimales innecesarios
            if (value instanceof Double) {
                Double numeroDouble = (Double) value;
                if (numeroDouble == Math.floor(numeroDouble)) {
                    // Es un entero, quitar decimales
                    nuevoMapa.put(key, numeroDouble.intValue());
                } else {
                    nuevoMapa.put(key, value);
                }
            } else if (value instanceof BigDecimal) {
                BigDecimal numeroBigDecimal = (BigDecimal) value;
                if (numeroBigDecimal.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
                    // Es un entero, quitar decimales
                    nuevoMapa.put(key, numeroBigDecimal.intValue());
                } else {
                    nuevoMapa.put(key, value);
                }
            } else if (value instanceof Map) {
                nuevoMapa.put(key, formatearMapaNumericos((Map<String, Object>) value));
            } else if (value instanceof List) {
                List<Object> listaOriginal = (List<Object>) value;
                List<Object> listaNueva = new ArrayList<>();

                for (Object item : listaOriginal) {
                    if (item instanceof Map) {
                        listaNueva.add(formatearMapaNumericos((Map<String, Object>) item));
                    } else {
                        listaNueva.add(item);
                    }
                }
                nuevoMapa.put(key, listaNueva);
            } else {
                nuevoMapa.put(key, value);
            }
        }

        return nuevoMapa;
    }

    // Clases internas mejoradas
    public static class ActionRequest {
        private String action;
        private Object data;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }

    public static class QueryRequest {
        private String type;
        private Long clienteId;

        public QueryRequest() {
            // Constructor vacío necesario para deserialización
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Long getClienteId() {
            return clienteId;
        }

        public void setClienteId(Long clienteId) {
            this.clienteId = clienteId;
        }
    }
}