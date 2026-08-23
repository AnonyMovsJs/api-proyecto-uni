# Sistema de Gestión de Ventas, Créditos y Notificaciones con IA

Este proyecto es una plataforma backend empresarial robusta diseñada para gestionar operaciones de **ventas**, **planes de crédito**, **control de cuotas** y **pagos**. Además, incorpora capacidades modernas como un **chatbot con Inteligencia Artificial (OpenAI)**, **comunicación en tiempo real (WebSockets)** y un **sistema de notificaciones automáticas (Twilio para SMS y WhatsApp)**.

Está diseñado siguiendo las mejores prácticas de desarrollo en Java, utilizando una arquitectura limpia y desacoplada con el ecosistema de **Spring Boot**.

---

## 🚀 Características Principales

*   **Seguridad y Autenticación:** Implementación de **Spring Security** y tokens **JWT (JSON Web Tokens)** para autenticación y autorización segura basada en roles (Admin/User).
*   **Gestión Financiera de Créditos:** Creación de ventas, cálculo automático de planes de financiamiento en cuotas, y amortización de pagos con actualización de estados en tiempo real.
*   **Chatbot Inteligente con IA:** Integración directa con la API de **OpenAI (GPT)** para responder consultas y asistir en la navegación y soporte al usuario.
*   **Notificaciones Multicanal:** Automatización de alertas SMS y mensajes de WhatsApp a través del SDK de **Twilio** para notificar confirmaciones de pagos, vencimientos o alertas de seguridad.
*   **Tiempo Real (Real-time):** Soporte de **WebSockets** para interacción fluida en el chatbot y actualizaciones instantáneas del sistema.
*   **Diseño de API REST:** Endpoints ordenados, validados y con manejo global de excepciones centralizado.

---

## 🛠️ Tecnologías y Herramientas utilizadas

*   **Lenguaje:** Java 17
*   **Framework Principal:** Spring Boot 3.4.4
*   **Persistencia de Datos:** Spring Data JPA / Hibernate
*   **Base de Datos:** MySQL (conector `mysql-connector-j`)
*   **Seguridad:** Spring Security & JWT (`jjwt-api` v0.12.6)
*   **Integraciones de Terceros:**
    *   OpenAI Java Client (`com.theokanning.openai-gpt3-java`)
    *   Twilio SDK (`twilio` v10.0.0)
*   **Mensajería y Tiempo Real:** Spring WebSocket
*   **Gestor de Dependencias:** Maven

---

## 📂 Estructura del Código

El proyecto está organizado bajo el paquete base `com.ronald.proyecto.proyecto_uni` siguiendo el patrón arquitectónico por capas:

```text
├── auth/                 # Filtros JWT y configuraciones de Spring Security
├── controller/           # Controladores REST expuestos al cliente (API Endpoints)
├── dto/                  # Objetos de Transferencia de Datos (DTO) para desacoplar Entidades de la API
├── entity/               # Modelos de datos / Entidades JPA (User, Venta, Credito, Cuota, Pago, etc.)
├── exception/            # Controlador de excepciones global (@ControllerAdvice)
├── models/               # Clases auxiliares para mensajería y estados de conversación
├── repository/           # Interfaces de Spring Data JPA para la interacción con la base de datos
├── service/              # Capa de lógica de negocio (Services e Implementaciones)
└── resources/            # Archivos de configuración (application.properties)
```

---

## ⚙️ Configuración y Ejecución Local

### 1. Clonar el repositorio y preparar las variables de entorno
El proyecto utiliza variables de entorno para proteger datos sensibles. En la raíz encontrarás el archivo `.env.example`. 

1. Duplicá el archivo `.env.example` y renombralo como `.env`:
   ```bash
   cp .env.example .env
   ```
2. Completá las variables con tus credenciales de base de datos, OpenAI API Key y Twilio Credentials:
   ```env
   DB_URL=jdbc:mysql://localhost:3306/tu_base_de_datos
   DB_USERNAME=tu_usuario
   DB_PASSWORD=tu_contraseña
   OPENAI_API_KEY=tu_openai_key
   TWILIO_ACCOUNT_SID=tu_sid
   TWILIO_AUTH_TOKEN=tu_token
   TWILIO_PHONE_NUMBER=tu_numero
   ```

### 2. Compilar y Ejecutar el proyecto

Ejecutá los siguientes comandos desde la terminal en la raíz del proyecto:

*   **Compilar el proyecto:**
    ```bash
    ./mvnw clean compile
    ```
*   **Ejecutar la aplicación:**
    ```bash
    ./mvnw spring-boot:run
    ```

La aplicación se iniciará por defecto en el puerto `8080`.

---

## 🤝 Contribuciones y Contacto

Desarrollado con dedicación por **Ronald Villacorta**.
*   **LinkedIn:** [Tu Perfil de LinkedIn](https://www.linkedin.com/in/tu-perfil) *(¡Recordá actualizar este enlace!)*
*   **GitHub:** [@tu-usuario-github](https://github.com/tu-usuario) *(¡Recordá actualizar este enlace!)*
