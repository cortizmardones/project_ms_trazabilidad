# 🔎 Trazabilidad entre microservicios con `traceId`, `MDC` y OpenFeign

> Documentación del proyecto para implementar correlación de logs y seguimiento de requests entre **MS-A** y **MS-B** mediante un `traceId` propagado por header HTTP.

---

## ✨ Tabla de contenidos

- [1. Descripción general](#1-descripción-general)
- [2. Objetivo del proyecto](#2-objetivo-del-proyecto)
- [3. Arquitectura del ejemplo](#3-arquitectura-del-ejemplo)
- [4. Conceptos clave](#4-conceptos-clave)
- [5. Flujo completo de la trazabilidad](#5-flujo-completo-de-la-trazabilidad)
- [6. Implementación en MS-A](#6-implementación-en-ms-a)
- [7. Implementación en MS-B](#7-implementación-en-ms-b)
- [8. Explicación detallada del log](#8-explicación-detallada-del-log)
- [9. Correlación de logs](#9-correlación-de-logs)
- [10. Header usado para la trazabilidad](#10-header-usado-para-la-trazabilidad)
- [11. Diferencia entre header HTTP y `MDC`](#11-diferencia-entre-header-http-y-mdc)
- [12. Flujo técnico interno](#12-flujo-técnico-interno)
- [13. Ejemplos de uso](#13-ejemplos-de-uso)
- [14. Beneficios](#14-beneficios)
- [15. Consideraciones importantes](#15-consideraciones-importantes)
- [16. Posibles mejoras futuras](#16-posibles-mejoras-futuras)
- [17. Resumen técnico](#17-resumen-técnico)
- [18. Conclusión](#18-conclusión)

---

## 1. Descripción general

Este proyecto implementa una estrategia de **trazabilidad manual entre microservicios** usando un identificador llamado `traceId`.

La idea principal es que una solicitud que entra a **MS-A** quede identificada con un valor único, y que ese mismo valor viaje hacia **MS-B** cuando **MS-A** realiza una llamada HTTP mediante **OpenFeign**.

De esta forma, ambos microservicios pueden registrar en sus logs el mismo `traceId`, permitiendo seguir el recorrido completo de una operación distribuida.

---

## 2. Objetivo del proyecto

El objetivo de esta solución es demostrar cómo lograr trazabilidad entre microservicios sin depender de una plataforma de tracing distribuido como Zipkin, Sleuth o Micrometer Tracing.

### 🎯 Se busca

- identificar cada request con un `traceId`
- almacenar el `traceId` en `MDC`
- incluir el `traceId` en los logs
- propagar el `traceId` entre microservicios mediante headers HTTP
- rechazar requests internas en **MS-B** si no incluyen `traceId`

---

## 3. Arquitectura del ejemplo

El proyecto está compuesto por dos microservicios:

### 🟦 MS-A

Es el microservicio de entrada.

**Responsabilidades:**

- recibe la request inicial
- busca el `traceId` en el header
- si no existe, genera uno nuevo
- lo guarda en `MDC`
- lo agrega al response
- llama a **MS-B** usando OpenFeign
- propaga el `traceId` en el header de la llamada HTTP

### 🟩 MS-B

Es el microservicio receptor de la llamada interna.

**Responsabilidades:**

- recibe la request proveniente de MS-A
- valida que exista el header `traceId`
- si no viene, rechaza la request
- si viene, lo guarda en `MDC`
- lo agrega al response
- registra logs con el mismo `traceId`
- procesa la operación solicitada

### 🧭 Vista general del flujo

```text
Cliente --> MS-A --> OpenFeign --> MS-B
             |                    |
             +---- traceId -------+
```

---

## 4. Conceptos clave

### 4.1 ¿Qué es `traceId`?

`traceId` es un identificador único que representa una transacción o flujo de ejecución.

Ejemplo:

```text
2e7f3f50-63c1-4b84-99cb-2d60d7e4d741
```

Ese mismo valor debe viajar entre microservicios para que los logs puedan relacionarse entre sí.

---

### 4.2 ¿Qué es `MDC`?

`MDC` significa **Mapped Diagnostic Context**.

Es una utilidad de SLF4J/Logback que permite guardar información contextual asociada al hilo actual de ejecución.

En este proyecto se usa para almacenar temporalmente el `traceId`, de modo que cualquier log generado durante la ejecución pueda incluirlo automáticamente.

### ✅ Idea práctica

- entra una request
- se obtiene el `traceId`
- se guarda en `MDC`
- todos los logs del flujo pueden imprimir `traceId`
- al finalizar la request se limpia el `MDC`

---

### 4.3 ¿Qué es `OncePerRequestFilter`?

`OncePerRequestFilter` es un filtro de Spring que garantiza ejecutarse una sola vez por request.

Se usa porque es el lugar ideal para:

- interceptar la request antes de que llegue al controller
- leer headers HTTP
- validar si el `traceId` existe
- generar el `traceId` si corresponde
- guardar el valor en `MDC`
- cortar el flujo si la request no cumple las validaciones

---

### 4.4 ¿Qué es OpenFeign?

OpenFeign permite definir clientes HTTP declarativos en Spring.

En lugar de construir manualmente una llamada REST, se define una interfaz anotada y Spring genera la implementación.

En este proyecto se usa para que **MS-A** llame a **MS-B**.

---

## 5. Flujo completo de la trazabilidad

### ✅ Escenario normal

1. un cliente hace una request a **MS-A**
2. el filtro de **MS-A** revisa si existe header `traceId`
3. si no existe, **MS-A** genera uno nuevo
4. **MS-A** guarda ese valor en `MDC`
5. **MS-A** procesa la request y llama a **MS-B**
6. el interceptor de Feign toma el `traceId` desde `MDC`
7. Feign envía el header `traceId` a **MS-B**
8. el filtro de **MS-B** recibe el header
9. **MS-B** valida que exista
10. **MS-B** guarda el valor en `MDC`
11. **MS-B** procesa la request
12. ambos microservicios escriben logs con el mismo `traceId`

### ❌ Escenario inválido en MS-B

Si **MS-B** recibe una request sin `traceId`:

1. el filtro detecta que el header no existe o está vacío
2. se registra un log de advertencia
3. se responde con HTTP `400`
4. no se continúa al controller
5. no se genera un nuevo `traceId`

> Esto es importante porque el objetivo de MS-B es mantener la trazabilidad de una llamada ya existente, no inventar una nueva.

---

## 6. Implementación en MS-A

### 6.1 Filtro `TraceIdFilter`

```java

@Slf4j
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String traceId = request.getHeader(TRACE_ID);

        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_ID, traceId);

        try {
            log.info("MS-A filter seteó traceId={}", traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}
```

#### 📌 Responsabilidad del filtro en MS-A

El filtro de **MS-A** hace lo siguiente:

- intenta leer el header `traceId`
- si no existe, genera uno nuevo
- guarda el valor en `MDC`
- agrega el `traceId` al response
- deja disponible el contexto para los logs
- limpia el `MDC` al finalizar la request

#### 💡 ¿Por qué MS-A sí genera un `traceId`?

Porque es el punto de entrada al sistema.

Si el cliente externo no envía un `traceId`, el sistema necesita crearlo para iniciar la trazabilidad desde el origen.

---

### 6.2 Controller `ProductController`

```java

@RestController
@RequestMapping("/v1/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final CallExternalService callExternalService;

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@RequestBody ProductRequest productRequest) {

        log.info("Request recibida createProduct() productName = {} , productPrice = {} , companyRut = {}", productRequest.productName(), productRequest.productPrice(), productRequest.companyRut());
        return callExternalService.callExternalService(productRequest);
    }

}
```

#### 📌 Responsabilidad del controller

El controller:

- recibe una request `POST /v1/products`
- registra información del request recibido
- delega la lógica al servicio
- no maneja el `traceId` manualmente porque ya está disponible en `MDC`

---

### 6.3 Interface `CallExternalService`

```java

public interface CallExternalService {

    ResponseEntity<ProductResponse> callExternalService(ProductRequest productRequest);
}
```

#### 📌 Responsabilidad

Define el contrato del servicio que invocará al microservicio externo.

---

### 6.4 Implementación `CallExternalServiceImpl`

```java

@Service
@RequiredArgsConstructor
@Slf4j
public class CallExternalServiceImpl implements CallExternalService {

    private final MsBClient msBClient;

    @Override
    public ResponseEntity<ProductResponse> callExternalService(ProductRequest productRequest) {
        log.info("LLamando a MICROSERVICIO-B con el request : {}", productRequest);
        return msBClient.callExternalService(productRequest);
    }
}
```

#### 📌 Responsabilidad

Este servicio:

- registra el momento en que se llama a MS-B
- utiliza el cliente Feign para hacer la llamada HTTP
- no propaga el `traceId` manualmente porque eso lo hace el interceptor Feign

---

### 6.5 Cliente Feign `MsBClient`

```java


@FeignClient(name = "ms-b-client", url = "http://localhost:8082", configuration = FeignTraceConfig.class)
public interface MsBClient {

    @PostMapping("/v1/products")
    ResponseEntity<ProductResponse> callExternalService(@RequestBody ProductRequest productRequest);

}
```

#### 📌 Responsabilidad

Esta interfaz declara la llamada HTTP desde **MS-A** hacia **MS-B**.

**Puntos importantes:**

- usa `@FeignClient`
- apunta a `http://localhost:8082`
- asocia la configuración `FeignTraceConfig`
- realiza un `POST /v1/products`

---

### 6.6 Configuración Feign `FeignTraceConfig`

```java
package com.java.trazabilidad.config;


// SI APLICAMOS LA ANOTACION @Configuration EL INTERCEPTOR SERA A NIVEL GLOBAL (OJO CON ESO)
public class FeignTraceConfig {

    public static final String TRACE_ID = "traceId";

    @Bean
    public RequestInterceptor requestInterceptor() {

        return new RequestInterceptor() {

            @Override
            public void apply(RequestTemplate template) {

                String traceId = MDC.get(TRACE_ID);

                if (traceId != null) {
                    template.header(TRACE_ID, traceId);
                }
            }
        };
    }
}
```

#### 📌 Responsabilidad

Esta clase crea un `RequestInterceptor` para OpenFeign.

Su función es:

- leer `traceId` desde `MDC`
- agregarlo como header HTTP en cada request saliente a MS-B

#### ⭐ Importancia de esta clase

Sin esta configuración, el `traceId` quedaría solo en el contexto interno de MS-A y no viajaría al siguiente microservicio.

---

### 6.7 Configuración `application.properties` de MS-A

```properties
spring.application.name=MS-A
server.port=8080
logging.pattern.level=%5p [${spring.application.name:},traceId=%X{traceId:-}]
```

#### 📌 Explicación

- `spring.application.name=MS-A`: define el nombre lógico del microservicio
- `server.port=8080`: configura el puerto de ejecución
- `logging.pattern.level=...`: personaliza el patrón del log para incluir nombre del microservicio y `traceId`

---

## 7. Implementación en MS-B

### 7.1 Filtro `TraceIdFilter`

```java

@Slf4j
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String traceId = request.getHeader(TRACE_ID);

        if (traceId == null || traceId.isBlank()) {

            log.warn("Request rechazada: no viene traceId");

            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType("application/json");
            response.getWriter().write(
                """
                {
                    "message":"Header traceId es obligatorio"
                }
                """);
            return;
        }

        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_ID, traceId);

        try {
            log.info("MS-B recibió traceId={}", traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}
```

#### 📌 Responsabilidad del filtro en MS-B

El filtro de **MS-B**:

- lee el header `traceId`
- valida que exista
- si no existe, rechaza la request con HTTP `400`
- si existe, lo guarda en `MDC`
- agrega el valor al response
- deja disponible el `traceId` para los logs
- limpia el `MDC` al finalizar

#### 🚫 ¿Por qué MS-B no genera un nuevo `traceId`?

Porque MS-B participa en una trazabilidad ya iniciada por otro microservicio.

Si generara un nuevo valor:

- se rompería la continuidad de la traza
- dejaría de ser posible correlacionar ambos logs como parte de una misma transacción
- el sentido de la trazabilidad distribuida se perdería

Por eso MS-B debe exigir que el header venga informado.

---

### 7.2 Controller `ProductController`

```java

@RestController
@RequestMapping("/v1/products")
@Slf4j
public class ProductController {

    @PostMapping
    public ResponseEntity<ProductResponse> getProducts(@RequestBody ProductRequest productRequest) {

        log.info("Request recibida createProduct() productName = {} , productPrice = {} , companyRut = {}", productRequest.productName(), productRequest.productPrice(), productRequest.companyRut());

        return ResponseEntity.status(200).body(
                ProductResponse.builder().status("OK")
                        .message("Mensaje recibido exitosamente por el microservicio-B")
                        .build());
    }
}
```

#### 📌 Responsabilidad

Este controller:

- recibe el request de MS-A
- registra información del request
- responde un `ProductResponse`
- utiliza automáticamente el `traceId` que ya fue cargado por el filtro en `MDC`

---

### 7.3 Configuración `application.properties` de MS-B

```properties
spring.application.name=MS-B
server.port=8082
logging.pattern.level=%5p [${spring.application.name:},traceId=%X{traceId:-}]
```

#### 📌 Explicación

- `spring.application.name=MS-B`: nombre del microservicio que aparecerá en los logs
- `server.port=8082`: puerto en el que se ejecuta MS-B
- `logging.pattern.level=...`: incluye el `traceId` del `MDC` dentro del log

---

## 8. Explicación detallada del log

La configuración utilizada es:

```properties
logging.pattern.level=%5p [${spring.application.name:},traceId=%X{traceId:-}]
```

### 🔍 Desglose

#### `%5p`

Muestra el nivel del log con ancho de 5 caracteres.

Ejemplos:

- `INFO`
- `WARN`
- `ERROR`

#### `${spring.application.name:}`

Obtiene el nombre de la aplicación desde `spring.application.name`.

Ejemplos:

- `MS-A`
- `MS-B`

#### `%X{traceId:-}`

Obtiene desde `MDC` el valor asociado a la clave `traceId`.

El `:-` indica que, si no existe valor, se imprimirá vacío.

### 🧪 Ejemplo de salida real

```text
INFO [MS-A,traceId=123e4567-e89b-12d3-a456-426614174000]
INFO [MS-B,traceId=123e4567-e89b-12d3-a456-426614174000]
```

Esto demuestra que ambos microservicios participaron de la misma traza.

---

## 9. Correlación de logs

La correlación se logra porque ambos microservicios escriben en sus logs el mismo `traceId`.

Eso permite:

- buscar una operación específica en todos los logs
- reconstruir el flujo de ejecución
- identificar errores distribuidos
- facilitar debugging entre servicios

Sin `traceId`, cada microservicio tendría logs aislados y sería mucho más difícil relacionarlos.

---

## 10. Header usado para la trazabilidad

En este proyecto se usa el header:

```text
traceId
```

Esto significa que:

- MS-A lo recibe desde el cliente o lo genera
- MS-A lo envía a MS-B
- MS-B lo valida y lo usa

> Es importante que ambos microservicios usen exactamente el mismo nombre de header.

---

## 11. Diferencia entre header HTTP y `MDC`

### Header HTTP

Es el medio por el cual el `traceId` viaja entre microservicios.

Ejemplo:

```text
traceId: 123e4567-e89b-12d3-a456-426614174000
```

### `MDC`

Es el mecanismo local del microservicio para que el `traceId` esté disponible en todos los logs durante la ejecución actual.

### 🧠 En resumen

- el header transporta el dato entre servicios
- `MDC` lo hace visible dentro del servicio para logging

---

## 12. Flujo técnico interno

### En MS-A

#### Paso 1
Llega una request al endpoint `/v1/products`.

#### Paso 2
El filtro `TraceIdFilter` busca el header `traceId`.

#### Paso 3
Si el cliente no lo envió, MS-A genera uno con `UUID`.

#### Paso 4
MS-A guarda el `traceId` en `MDC`.

#### Paso 5
El controller registra logs con ese contexto.

#### Paso 6
El servicio llama a MS-B mediante Feign.

#### Paso 7
El interceptor Feign toma el `traceId` desde `MDC` y lo agrega como header HTTP.

### En MS-B

#### Paso 1
MS-B recibe la request.

#### Paso 2
El filtro `TraceIdFilter` lee el header `traceId`.

#### Paso 3
Si no existe, responde error `400` y termina.

#### Paso 4
Si existe, lo guarda en `MDC`.

#### Paso 5
El controller procesa la request y registra logs con ese mismo valor.

---

## 13. Ejemplos de uso

### 13.1 Request desde el cliente a MS-A sin enviar `traceId`

```bash
curl --location 'http://localhost:8080/v1/products' \
--header 'Content-Type: application/json' \
--data '{
  "productName": "Notebook",
  "productPrice": 1200,
  "companyRut": "12345678-9"
}'
```

En este caso:

- MS-A generará el `traceId`
- lo agregará a `MDC`
- lo enviará a MS-B
- MS-B lo aceptará porque ya viene informado por Feign

---

### 13.2 Request desde el cliente a MS-A enviando `traceId`

```bash
curl --location 'http://localhost:8080/v1/products' \
--header 'Content-Type: application/json' \
--header 'traceId: TRACE-001' \
--data '{
  "productName": "Notebook",
  "productPrice": 1200,
  "companyRut": "12345678-9"
}'
```

En este caso:

- MS-A reutiliza `TRACE-001`
- Feign envía `TRACE-001` a MS-B
- los logs de ambos microservicios mostrarán `TRACE-001`

---

### 13.3 Respuesta esperada de MS-B

```json
{
  "status": "OK",
  "message": "Mensaje recibido exitosamente por el microservicio-B"
}
```

---

### 13.4 Rechazo en MS-B cuando falta `traceId`

Si se invocara MS-B directamente sin `traceId`, respondería algo como:

```json
{
  "message": "Header traceId es obligatorio"
}
```

Y el estado HTTP sería:

```text
400 Bad Request
```

---

### 13.5 Logs esperados

#### Logs de MS-A

```text
INFO [MS-A,traceId=TRACE-001] MS-A filter seteó traceId=TRACE-001
INFO [MS-A,traceId=TRACE-001] Request recibida createProduct() productName = Notebook , productPrice = 1200 , companyRut = 12345678-9
INFO [MS-A,traceId=TRACE-001] LLamando a MICROSERVICIO-B con el request : ProductRequest[productName=Notebook, productPrice=1200, companyRut=12345678-9]
```

#### Logs de MS-B

```text
INFO [MS-B,traceId=TRACE-001] MS-B recibió traceId=TRACE-001
INFO [MS-B,traceId=TRACE-001] Request recibida createProduct() productName = Notebook , productPrice = 1200 , companyRut = 12345678-9
```

---

## 14. Beneficios

### 🚀 Beneficios de esta implementación

- correlación de requests
- debugging distribuido
- simplicidad
- control explícito de la trazabilidad
- validación estricta en servicios internos

---

## 15. Consideraciones importantes

### 15.1 El `traceId` debe ser consistente

Todos los microservicios deben usar el mismo nombre de header:

```text
traceId
```

Si un servicio usa otro nombre, la propagación se rompe.

---

### 15.2 `MDC` es local al hilo

`MDC` funciona dentro del hilo actual de ejecución.

Eso significa que este enfoque funciona bien en flujos sincrónicos tradicionales.

Si hubiera procesamiento asíncrono con otros hilos, habría que propagar manualmente el contexto.

---

### 15.3 Limpiar siempre el `MDC`

Es obligatorio hacer:

```java
MDC.remove("traceId");
```

o equivalente en un bloque `finally`.

Esto evita fugas de contexto entre requests reutilizadas por el servidor.

---

### 15.4 MS-B no debe generar `traceId`

Eso rompería la trazabilidad.

MS-B debe comportarse como consumidor de una traza ya existente.

---

### 15.5 El patrón actual es trazabilidad manual

Este proyecto implementa una solución manual y didáctica.

No reemplaza completamente una plataforma de tracing distribuido empresarial, pero es muy útil para:

- aprendizaje
- pruebas de concepto
- sistemas simples
- control total del mecanismo

---

## 16. Posibles mejoras futuras

### 📦 Centralizar constantes

Mover `TRACE_ID` a una clase común de constantes.

Ejemplo conceptual:

```java
public final class TraceConstants {
    public static final String TRACE_ID = "traceId";
}
```

### 🛡️ Manejo estándar de errores

En lugar de escribir directamente el JSON en el filtro, podría usarse una estructura de error estandarizada.

### 🧾 Agregar más datos al `MDC`

Se podrían incluir otros atributos contextuales, por ejemplo:

- `clientId`
- `userId`
- `transactionId`
- `requestPath`

### 🌐 Incorporar tracing distribuido real

A futuro podría migrarse a:

- Micrometer Tracing
- Zipkin
- Jaeger
- OpenTelemetry

---

## 17. Resumen técnico

Este proyecto implementa trazabilidad distribuida simple entre **MS-A** y **MS-B** usando:

- `traceId` como identificador de correlación
- `OncePerRequestFilter` para interceptar requests
- `MDC` para incluir contexto en logs
- OpenFeign para comunicación entre microservicios
- `RequestInterceptor` para propagar headers

### MS-A

- recibe el request externo
- crea o reutiliza `traceId`
- guarda el valor en `MDC`
- llama a MS-B propagando el header

### MS-B

- recibe el request interno
- exige que venga `traceId`
- lo guarda en `MDC`
- escribe logs correlacionados
- rechaza la request si no viene el identificador

---

## 18. Conclusión

La solución presentada permite implementar trazabilidad entre microservicios de manera clara, controlada y fácil de entender.

El uso conjunto de:

- `OncePerRequestFilter`
- `MDC`
- `traceId`
- OpenFeign

hace posible seguir una misma operación a través de múltiples servicios usando un único identificador visible en todos los logs.

Esto mejora notablemente:

- la observabilidad
- el diagnóstico de problemas
- la auditoría del flujo
- la mantenibilidad de sistemas distribuidos