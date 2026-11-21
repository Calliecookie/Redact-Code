# Arquitectura del sistema — Redact Code V1.0

## 1. Visión general

Redact Code es una aplicación Android nativa escrita en Kotlin con Jetpack Compose. El proyecto se divide en dos módulos Gradle:

| Módulo | Tipo | Responsabilidad |
|---|---|---|
| `:nucleo` | Kotlin/JVM puro (sin dependencias de Android) | Dominio, validadores, catálogos, retos, proyectos guiados, mentor local, revisión de código, algoritmo SM-2, perfil de aprendizaje, cliente de GitHub y puntos de extensión para la fase PC. |
| `:aplicacion` | Aplicación Android | Interfaz (Compose + Material 3 personalizado), base de datos Room, seguridad (Keystore, PIN, biometría), almacenamiento de archivos, ejecución Python (Chaquopy), motores JavaScript (JavaScriptSandbox/Rhino), WebView, WorkManager e inyección de dependencias (Hilt). |

La separación permite reutilizar `:nucleo` sin cambios en una futura aplicación de escritorio (JVM) y probarlo con JUnit puro en segundos.

```
┌─────────────────────────── :aplicacion ───────────────────────────┐
│  funcionalidades/ (pantallas + ViewModels, MVVM, StateFlow)        │
│     bienvenida · inicio · explorador · editor · problemas ·        │
│     ejecucion · practica · aprendizaje · mentor · fragmentos ·     │
│     github · ajustes                                               │
│  navegacion/ (NavHost, control de acceso por PIN/biometría)        │
│  datos/ (repositorios, ServicioCodigo, ProveedorAnthropic,         │
│          GestorCopiasSeguridad)                                    │
│  nucleo/ base_datos · seguridad · archivos · ejecucion ·           │
│          preferencias · diseno                                     │
│  trabajos/ (WorkManager)   di/ (Hilt)   python/ (redact_python.py) │
└──────────────────────────────┬────────────────────────────────────┘
                               │ depende de
┌──────────────────────────────▼──── :nucleo ───────────────────────┐
│ modelo · validacion · javascript · revision · mentor · retos ·     │
│ proyectos · aprendizaje · github · escritorio                      │
│ recursos: catálogos JSON (errores, retos, proyectos, conceptos),   │
│           acorn.js (MIT) y analisis_redact.js                      │
└────────────────────────────────────────────────────────────────────┘
```

## 2. Patrón de presentación

- **MVVM**: cada pantalla tiene un `ViewModel` (`@HiltViewModel`) que expone `StateFlow` inmutables; la UI los observa con `collectAsStateWithLifecycle`.
- **Navegación**: `navigation-compose` con rutas declaradas en `Rutas` (`navegacion/NavegacionRedactCode.kt`). Un `AccesoViewModel` decide si mostrar la bienvenida, el bloqueo por PIN/biometría o la navegación principal.
- **Sistema visual**: `nucleo/diseno` define la paleta (negro #0D0D0F, #151518, gris #202024, #77777F, blanco #F5F5F7, #B8B8BE), el tema oscuro (predeterminado) y claro, tipografía de interfaz del sistema y JetBrains Mono para el código, además de componentes propios (`Superficie`, `EncabezadoPantalla`, `Etiqueta`, `BloqueCodigo`, etc.).

## 3. Capas por responsabilidad

### 3.1 Dominio (`:nucleo`)
- `modelo`: `Lenguaje`, `Diagnostico`, `ExplicacionError`, `ResultadoEjecucion`, `CategoriaConcepto`, `NivelExperiencia`.
- `validacion`: interfaz `Validador` y validadores reales:
  - **JSON**: analizador descendente recursivo propio conforme a RFC 8259 (`AnalizadorJson`).
  - **CSS**: tokenizador y analizador propio según CSS Syntax Level 3 (`AnalizadorCss`), con detección de llaves, punto y coma, dos puntos, cadenas, comentarios y paréntesis sin cerrar, y advertencias de propiedades desconocidas.
  - **HTML**: analizador HTML5 de jsoup con seguimiento de errores y posiciones de origen.
  - **JavaScript**: Acorn (parser ECMAScript completo) ejecutado en un motor JavaScript.
  - **Python** (en `:aplicacion`): `compile()` de CPython vía Chaquopy.
  - `CatalogoExplicaciones` + `ClasificadorMensajes`: traducen el error del analizador a un código propio con explicación en español (título, explicación, causa probable, ejemplo incorrecto, ejemplo corregido, recomendación).
- `javascript`: `MotorJavaScript` (interfaz), `MotorRhino` (interpretado, con límite de instrucciones/tiempo) y `ServicioJavaScript` (validación, revisión, estructura línea por línea, ejecución y arnés de pruebas).
- `revision`: `RevisorCodigo` combina hallazgos por AST (Acorn, `ast`/`symtable` de Python, jsoup, analizador CSS) con un detector de bloques duplicados. Distingue **errores confirmados** de **advertencias/sugerencias/riesgos de seguridad**.
- `mentor`: `ProveedorIA` (interfaz), `MentorLocal` (determinista), `MentorProgramacion` (elige proveedor y recurre al local si falla el externo), `ConstructorPrompt`, `ExplicadorLineas`, `CorrectorAutomatico`, `CatalogoConceptos`.
- `retos`: formato `redactcode.retos` v1 (importable), `CatalogoRetos`, `ComparadorValores`, `InterpreteArnes`.
- `proyectos`: proyectos guiados con verificaciones reales (`funcion`, `selector_html`, `regla_css`).
- `aprendizaje`: `AlgoritmoSm2`, `CalculadoraRacha`, `AnalizadorAprendizaje` (dominio por concepto, dificultades, recomendaciones).
- `github`: `ClienteGitHub` sobre Retrofit/OkHttp contra la API REST oficial.
- `escritorio`: interfaces para la fase PC (ver sección 7).

### 3.2 Datos (`:aplicacion/datos`)
Repositorios que coordinan Room, archivos y servicios: `RepositorioProyectos`, `RepositorioPerfil`, `RepositorioActividad`, `RepositorioAprendizaje`, `RepositorioRetos`, `RepositorioFragmentos`, `RepositorioGitHub`, `GestorCopiasSeguridad`, `ServicioCodigo` (fachada de validación, revisión, ejecución y mentor) y `ProveedorAnthropic` (proveedor externo opcional).

### 3.3 Infraestructura (`:aplicacion/nucleo`)
- `base_datos`: Room (SQLite), 12 tablas, esquema exportado en `aplicacion/esquemas/`.
- `seguridad`: `CifradorKeystore` (AES-256-GCM en Android Keystore), `AlmacenCredenciales`, `GestorPin` (PBKDF2-HMAC-SHA256, 60 000 iteraciones, sal aleatoria, bloqueo progresivo), `AutenticadorBiometrico`.
- `archivos`: `GestorArchivos` (almacenamiento privado `files/proyectos`, validación de nombres y extensiones, bloqueo de rutas fuera del proyecto, guardado atómico, búsqueda).
- `ejecucion`: `PuentePython` (Chaquopy; hilos dedicados con pila de 32 MB) y `MotorJavaScriptSandbox` + `MotorJavaScriptCompuesto`.
- `preferencias`: DataStore.

## 4. Editor de código

Se evaluaron tres opciones: (a) `BasicTextField` de Compose con `VisualTransformation`, descartado por rendimiento y por no ser un componente de edición de código; (b) un editor propio dibujado en `Canvas`, descartado por el riesgo de calidad en selección, IME y accesibilidad; (c) **Sora Editor** (`io.github.rosemoe:editor`, LGPL-2.1), componente especializado mantenido activamente con números de línea, selección, cursor, deshacer/rehacer, búsqueda y reemplazo, desplazamiento horizontal/vertical, diagnósticos, autocompletado y pares de símbolos. Se eligió (c), integrado con `AndroidView`.

Sobre Sora se implementó en Redact Code:
- `LexerResaltado`: analizadores léxicos propios para Python, JavaScript, JSON, HTML y CSS (comentarios y cadenas multilínea incluidos).
- `LenguajeEditor`: autocompletado (palabras clave + identificadores del documento), indentación inteligente por lenguaje, cierre automático configurable y separación de llaves al pulsar Enter.
- `ControladorEditores`: una instancia de editor por pestaña (se conserva el historial de deshacer al cambiar de pestaña), aplicación de diagnósticos subrayados, búsqueda/reemplazo y barra de símbolos táctil.

## 5. Flujo de validación

1. El editor notifica cada cambio de texto al `EditorViewModel`.
2. Tras **400 ms** sin cambios (debounce con cancelación de la tarea anterior) se invoca `ServicioCodigo.validar` fuera del hilo principal.
3. El resultado (`ResultadoValidacion`) se guarda en el estado y la pantalla aplica los diagnósticos al editor y a la barra de estado.
4. Al guardar o ejecutar, los errores se registran en `registro_error` (con deduplicación de 10 minutos por archivo y código) para alimentar el perfil de aprendizaje.

## 6. Ejecución

- **Python**: `redact_python.ejecutar` compila, redirige `stdout`/`stderr` (limitados a 200 000 caracteres), sustituye `input()` por las líneas del panel Entrada y aplica un límite de tiempo y cancelación mediante `sys.settrace`.
- **JavaScript (script)**: `JavaScriptSandbox` (V8 en un proceso aislado) cuando el dispositivo lo admite; si no, Rhino en modo interpretado con límite de instrucciones.
- **HTML/CSS/JS (web)**: `WebView` con `WebViewAssetLoader` que sirve solo la carpeta del proyecto en `https://appassets.androidplatform.net/proyecto/`; sin acceso a archivos ni contenido; enlaces externos bloqueados; consola capturada con `WebChromeClient`.
- **Retos**: un arnés invoca la función del estudiante con cada caso y compara el resultado JSON con `ComparadorValores` (tolerancia numérica relativa de 1e-9).

## 7. Preparación para la fase PC

`pe.redactcode.nucleo.escritorio` define, sin implementación ni botones visibles en V1.0:
- `DescubridorEscritorio` (búsqueda y emparejamiento con verificación),
- `SincronizadorProyectos` (instantáneas con SHA-256 y cálculo de cambios),
- `EjecutorRemoto` (Java, C, C++, Python, JavaScript, Kotlin),
- `TerminalRemota` y `TransferenciaArchivos`,
- `CompatibilidadEscritorio.VERSION_PROTOCOLO`.

Como todo el dominio vive en `:nucleo` (JVM), una aplicación de escritorio puede reutilizar validadores, catálogos, retos, mentor, SM-2 y el cliente de GitHub directamente. Los proyectos se guardan como carpetas normales, lo que simplifica la sincronización futura.

## 8. Modelo de datos (Room)

| Tabla | Propósito |
|---|---|
| `perfil` | Perfil local único (nombre, nivel, lenguajes, objetivos). |
| `proyecto` | Proyectos (carpeta, origen LOCAL/GUIADO/GITHUB, paso del proyecto guiado, repositorio vinculado). |
| `archivo` | Metadatos de archivos (apertura reciente, SHA de GitHub). FK → `proyecto` en cascada. |
| `fragmento_codigo` | Biblioteca personal. |
| `repaso` | Estado SM-2 por fragmento (repeticiones, intervalo, facilidad, próxima fecha). FK en cascada. |
| `historial_repaso` | Cada calificación de repaso. FK en cascada. |
| `reto` | Definición (JSON), origen, resuelto, mejor puntaje, borrador. |
| `intento_reto` | Historial de evaluaciones. FK en cascada. |
| `actividad_diaria` | Segundos de práctica, retos resueltos, ejecuciones, guardados y repasos por día. |
| `registro_error` | Errores reales detectados (código, categoría, lenguaje, origen, fecha). |
| `perfil_aprendizaje` | Instantánea del dominio calculado por categoría. |
| `preferencia` | Pares clave/valor de uso general. |

Las migraciones se declaran en `MigracionesBaseDatos.TODAS` (vacía en la versión 1) y el esquema se exporta para escribir migraciones verificables en versiones futuras.
