# Informe técnico final — Redact Code v2.0 - Renovacion integral del IDE

Las secciones 1 a 12 documentan V1.0. Las secciones 13 a 17 documentan V2.0. El estado real de V2.0 está en la sección 17.

## 1. Resumen ejecutivo

Redact Code V1.0 es un proyecto Android Studio completo, editable y compilable, escrito en Kotlin con Jetpack Compose, que integra un editor de código especializado, validación real de cinco lenguajes con explicaciones en español, ejecución local de Python y JavaScript, vista previa web, mentor de programación adaptativo con proveedor de IA opcional, retos con evaluación automática, proyectos guiados, perfil de aprendizaje, biblioteca de fragmentos con repetición espaciada SM-2, integración con GitHub, seguridad local y copias de seguridad.

**Estado verificado**: el proyecto compila, el código Kotlin de ambos módulos no presenta errores, el empaquetado intermedio (manifiesto, recursos, dex con desugaring, assets de Python y librerías nativas) se completa, y las pruebas automatizadas en la JVM pasan (detalle en `RESULTADOS_DE_PRUEBAS.md`). **La v1.0 no se ejecutó en un dispositivo** (ver `LIMITACIONES_CONOCIDAS.md`).

## 2. Tecnologías

| Área | Tecnología | Versión |
|---|---|---|
| Lenguaje | Kotlin (Kotlin integrado de AGP 9) | 2.4.21 |
| Construcción | Gradle / Android Gradle Plugin | 9.9.1 / 9.5.1 |
| UI | Jetpack Compose (BOM) + Material 3 personalizado | 2026.09.00 |
| Arquitectura | MVVM, StateFlow, Coroutines | 1.11.0 |
| Inyección | Hilt / AndroidX Hilt | 2.60.1 / 1.4.0 |
| Persistencia | Room (SQLite) + KSP | 2.8.5 |
| Preferencias | DataStore | 1.3.0 |
| Tareas | WorkManager | 2.12.0 |
| Serialización | Kotlinx Serialization | 1.13.0 |
| Red | Retrofit / OkHttp | 3.0.0 / 5.5.1 |
| Editor | Sora Editor | 0.24.7 |
| Python | Chaquopy (licencia MIT desde la v15; compatibilidad con AGP 9.5 verificada en su tabla de versiones) | 17.2.0, Python 3.13 |
| JavaScript | Acorn (parser) · AndroidX JavaScriptEngine (V8 aislado) · Rhino (respaldo) | 8.19.0 · 1.1.1 · 1.9.2 |
| HTML | jsoup | 1.24.1 |
| Web | WebView + AndroidX WebKit (`WebViewAssetLoader`) | 1.18.0 |
| Seguridad | Android Keystore (AES-GCM), PBKDF2, AndroidX Biometric | 1.1.0 |
| IA externa opcional | SDK oficial `anthropic-java` (modelo predeterminado `claude-opus-5-5`) | 3.8.0 |
| Pruebas | JUnit 4, Robolectric, Compose UI Test, MockWebServer | 4.13.2 / 4.17 |

Se eligieron las versiones estables más recientes publicadas en los repositorios oficiales al momento del desarrollo. `compileSdk` es 37 porque varias librerías AndroidX lo exigen; `targetSdk` 36 y `minSdk` 26 (Android 8.0).

## 3. Arquitectura

Ver `ARQUITECTURA_DEL_SISTEMA.md`. En resumen: módulo `:nucleo` (Kotlin/JVM puro y reutilizable en escritorio) y módulo `:aplicacion` (Android), con MVVM, repositorios, servicios de dominio e infraestructura separada. Toda la nomenclatura interna está en español sin tildes; se conservan en su idioma original los nombres de tecnologías y APIs. El código fuente no contiene comentarios: la documentación está en esta carpeta.

## 4. Módulos funcionales implementados

| Módulo | Implementación |
|---|---|
| Bienvenida y perfil | Pantalla de inicio (*splash*) con el logo oficial, presentación de 4 diapositivas, perfil local (nombre, nivel, lenguajes), tema, PIN opcional, biometría si está disponible, recordatorios. Sin registro en línea. |
| Inicio | Saludo, continuar último archivo, accesos rápidos, racha, minutos de hoy vs. objetivo, gráfico semanal, retos recomendados, repasos pendientes, proyectos recientes. |
| Explorador | Crear proyectos (4 plantillas), archivos y carpetas; renombrar, eliminar, mover, abrir, guardar, buscar (nombre y contenido), ordenar, mostrar/ocultar extensiones; varios proyectos. Almacenamiento privado con validación de rutas. |
| Editor | Sora Editor con pestañas, números de línea, resaltado propio para 5 lenguajes + texto, cursor, selección, indentación inteligente, autocompletado, deshacer/rehacer por pestaña, buscar y reemplazar (mayúsculas y palabra completa), guardado atómico, indicador de cambios, diagnósticos subrayados, desplazamiento horizontal/vertical, zoom, barra de símbolos (`{ } ( ) [ ] < > ; : = " ' . , _` y Tab) y cierre automático configurable. |
| Validación | Validadores reales asíncronos con *debounce* de 400 ms y explicaciones locales (sección 5). |
| Ejecución | Python (Chaquopy) con salida, errores, excepción, línea, duración, estado, entrada para `input()`, límite de tiempo y cancelación; JavaScript en V8 aislado o Rhino; vista previa web con recarga, consola y alternancia editor/vista previa. |
| Mentor | 8 tipos de solicitud, adaptación por nivel, errores recurrentes y conceptos pendientes, corrección automática verificable, catálogo de 15 conceptos, ejercicios recomendados; proveedor externo opcional desacoplado. |
| Perfil de aprendizaje y mapa de errores | Registro real de errores (editor, ejecución, retos), intentos y actividad; cálculo de dominio por categoría, dificultades, nivel sugerido, lenguajes usados, evolución semanal y recomendaciones. |
| Proyectos guiados | 8 proyectos por niveles con objetivo, descripción, requisitos, archivos iniciales, pasos, pistas, pruebas reales (funciones, selectores HTML, reglas CSS) y resultado esperado. |
| Práctica y retos | 29 retos (17 Python, 12 JavaScript) en 10 categorías y 3 dificultades, código inicial, editor integrado, pruebas visibles y ocultas, evaluación, historial, borrador guardado, pistas e importación JSON. |
| Fragmentos y repaso | Crear, editar, eliminar, buscar, filtrar por lenguaje y etiqueta, copiar, abrir en el editor; SM-2 con historial y próxima fecha; recordatorio diario con WorkManager. |
| Gamificación | Racha diaria, mejor racha, minutos de práctica (solo cuenta tiempo con edición reciente), retos resueltos, actividad semanal, historial y objetivos personales editables. Diseño sobrio. |
| Revisor de código | Duplicados, variables no usadas, funciones complejas/largas, muchos parámetros, anidamiento, malas prácticas por lenguaje y riesgos conocidos (`eval`, `innerHTML`, `document.write`, `os.system`, `pickle`, `target=_blank`), con ejemplos de refactorización y separación entre errores confirmados y advertencias. |
| GitHub | Conectar con token, listar repositorios y ramas, navegar y descargar archivos, publicar cambios creando commits con control de conflictos por SHA. Cliente preparado para *pull requests*. |
| Ajustes | Tema, tamaño de texto, tipografía y tamaño del editor, escritura, animaciones, alto contraste, perfil, PIN/biometría, IA, recordatorios, copias de seguridad, privacidad, versión, licencias y borrado total. |

## 5. Validación y explicaciones

| Lenguaje | Herramienta | Tipo |
|---|---|---|
| Python | `compile()` de CPython 3.13 (Chaquopy) + `SyntaxWarning` | Parser oficial |
| JavaScript | Acorn 8.19 (ECMAScript 2025, *script* y *module*) | Parser completo |
| HTML | jsoup (tokenizador y constructor de árbol HTML5) + revisión de cierres por rango de origen | Parser HTML5 |
| CSS | Analizador propio (CSS Syntax Level 3) | Parser |
| JSON | Analizador propio (RFC 8259) | Parser |

No se usan expresiones regulares como sustituto de análisis. El catálogo local `explicaciones_errores.json` contiene **83 explicaciones** (JSON 15; Python 28: 17 de sintaxis, 1 advertencia y 10 de ejecución; JavaScript 19: 15 de sintaxis y 4 de ejecución; HTML 9; CSS 11; 1 genérica). Cada diagnóstico muestra lenguaje, línea, columna, tipo, código, título, explicación, causa probable, ejemplo incorrecto, ejemplo corregido y recomendación.

## 6. Formato de retos importables (`redactcode.retos` v1)

```json
{
  "formato": "redactcode.retos",
  "version": 1,
  "nombre": "Mi paquete",
  "retos": [{
    "id": "identificador-unico",
    "titulo": "Título",
    "categoria": "BUCLES",
    "dificultad": "basico | intermedio | avanzado",
    "lenguaje": "python | javascript",
    "enunciado": "Texto del enunciado",
    "codigoInicial": "def funcion(x):\n    pass\n",
    "funcion": "funcion",
    "pruebas": [{ "entrada": [1, 2], "esperado": 3, "descripcion": "", "oculto": false }],
    "pistas": ["Pista 1"],
    "conceptos": ["bucles"],
    "minutosEstimados": 10
  }]
}
```

Se validan el formato, la versión, el lenguaje, el nombre de la función, que exista al menos una prueba, que el código inicial declare la función y que no haya identificadores repetidos. Los retos que coinciden con el catálogo oficial se omiten.

## 7. Base de datos

Room sobre SQLite con 12 entidades (Perfil, Proyecto, Archivo, FragmentoCodigo, Repaso, HistorialRepaso, Reto, IntentoReto, ActividadDiaria, RegistroError, PerfilAprendizaje, Preferencia), DAOs con `Flow`, relaciones (`@Relation`) y claves foráneas en cascada, índices, esquema exportado (`aplicacion/esquemas/.../1.json`) y punto de registro de migraciones. Los datos iniciales (catálogo de retos) se sincronizan al iniciar sin perder el progreso. El contenido de los archivos vive en el almacenamiento privado; la base guarda metadatos. Toda escritura es persistente al cerrar la aplicación (Room y escritura atómica de archivos).

## 8. Seguridad

- Datos en almacenamiento privado; `allowBackup=false` y reglas de extracción que excluyen todo de la nube.
- Credenciales (token de GitHub, clave de IA, PIN) cifradas con AES-256-GCM cuya clave reside en Android Keystore; nunca en texto plano ni incluidas en el código.
- PIN derivado con PBKDF2-HMAC-SHA256 (60 000 iteraciones, sal de 16 bytes), comparación en tiempo constante y bloqueo progresivo tras 5 intentos.
- Validación de nombres, extensiones y rutas (bloqueo de *path traversal*), límite de 2 MB por archivo abierto, protección *zip-slip* y límites de tamaño al importar copias.
- Tráfico en texto plano deshabilitado (`network_security_config`).
- El código del usuario solo sale del dispositivo si el usuario conecta GitHub y publica, o activa un proveedor de IA con consentimiento explícito.
- WebView sin acceso a archivos/contenido, con contenido mixto prohibido y navegación externa bloqueada.
- Los riesgos de ejecutar código arbitrario están documentados en `LIMITACIONES_CONOCIDAS.md` (Python no está aislado).

## 9. Decisiones técnicas relevantes

1. **Sora Editor** en lugar de un `TextField`: componente especializado con rendimiento y funciones de editor real.
2. **Acorn en lugar de Rhino como parser**: una sonda inicial demostró que el parser de Rhino 1.9.2 rechaza `class`, `async` y `for…of` con `const`; Acorn acepta ECMAScript moderno completo y se ejecuta en V8 (aislado) o en Rhino.
3. **`JavaScriptSandbox` como motor principal**: ejecuta JavaScript en un proceso aislado, con terminación real por tiempo; Rhino queda como respaldo verificable en la JVM.
4. **Analizadores propios para JSON y CSS**: dan posiciones exactas y códigos de error específicos sin dependencias adicionales.
5. **Sin paquetes PyPI**: la revisión de Python usa `ast` y `symtable` de la biblioteca estándar, evitando dependencias y la necesidad de `buildPython` para `pip`.
6. **SDK oficial de Anthropic** para el proveedor externo; el resto de la app depende solo de la interfaz `ProveedorIA`.
7. **Iconos desde el logo original**: el ícono adaptativo y la pantalla de inicio (*splash*) usan la imagen oficial completa escalada, con un fondo del mismo tono; la bienvenida y el bloqueo usan un recorte de la baldosa del logo. No se rediseñó ni se recoloreó. El logo original se conserva en `recursos_graficos/`. La notificación usa un ícono vectorial neutro (símbolo de código), porque Android exige íconos monocromos para notificaciones.

## 10. Pruebas

Resumen: 38 pruebas en `:nucleo` (JUnit) y 15 en `:aplicacion` (Robolectric y Compose UI Test), todas superadas, más compilación completa y empaquetado intermedio sin APK. Las pruebas detectaron y permitieron corregir dos errores reales del resaltado de sintaxis (un bucle infinito y un acceso fuera de rango). Detalle en `RESULTADOS_DE_PRUEBAS.md`.

## 11. Limitaciones

Ver `LIMITACIONES_CONOCIDAS.md`. Las principales: sin ejecución en dispositivo durante el desarrollo, Python sin aislamiento, rendimiento de validación JavaScript en Android no medido, IA y GitHub no probados contra servicios reales por falta de credenciales, autenticación de GitHub por token (sin OAuth) y licencia LGPL de Sora Editor a revisar para distribución comercial.

## 12. Preparación para la fase PC

Interfaces en `pe.redactcode.nucleo.escritorio` (descubrimiento y emparejamiento, sincronización por instantáneas SHA-256, ejecución remota de Java/C/C++/Python/JavaScript/Kotlin, terminal y transferencia de archivos) y todo el dominio reutilizable en `:nucleo`. Ninguna función de PC aparece en la interfaz de V1.0.

## 13. Alcance de V2.0 y trabajo realizado

V2.0 evoluciona V1.0 hacia un IDE móvil inspirado en VS Code sin reconstruir el proyecto ni eliminar funciones: se conservaron los validadores, el mentor, los retos, los proyectos guiados, los fragmentos, la ejecución y la seguridad.

| Área | Implementación |
|---|---|
| Interfaz | Espacio de trabajo con barra de actividad, explorador con acciones, pestañas, búsqueda global, control de código, agente, extensiones, panel inferior (problemas, salida, terminal, consola, revisión, vista previa), paleta de comandos, paneles plegables y diseño adaptable (cajón en teléfono, paneles fijos y riel en tableta). Pantalla de inicio reorganizada en bloques de trabajo y aprendizaje. |
| Diseño | Tokens de color centralizados, temas oscuro (predeterminado) y claro renovados, alto contraste, colores semánticos y de sintaxis, contraste AA verificado por prueba automática, JetBrains Mono para código, escala de texto que respeta Android. Logo sin modificar. Animaciones de paneles, pestañas y actividad que se desactivan por ajuste. |
| Ajustes | 16 secciones con más de 50 opciones persistentes, todas consumidas por alguna función. |
| Editor | Búsqueda y reemplazo (archivo y proyecto), formateo (JSON, CSS y HTML reales), fragmentos con cursor, símbolos y navegación, duplicar y mover línea, comentar, comparación de archivos, guardado automático y recarga desde disco. |
| Ejecución | Terminal de shell, consola JavaScript, consola Python con sesión, vista previa WebView y ejecución existentes. |
| GitHub | Importar ZIP, clonar, control de versiones local con ramas, sincronización con commits de varios archivos, historial y diferencias. |
| IA | Agente de código con proveedores Claude y compatible con OpenAI, permisos, revisión de diferencias y aceptación por archivo o bloque. |
| Extensiones | Sistema declarativo seguro (sin ejecución de código), catálogo Open VSX, instalación, activación y desinstalación. |

## 14. Investigaciones

### 14.1 Google Antigravity (conexión mediante PC o servicio remoto)

Conclusión: no hay integración de datos; se implementó un acceso al panel web de control remoto, que queda pendiente de probar con tu PC y tu cuenta. Hallazgos (búsqueda web del 14 de noviembre de 2026; las fuentes de terceros no se verificaron contra Google):

- La documentación oficial de [Remote Control](https://antigravity.google/docs/remote-control.md) describe un proceso en segundo plano (`agy remote-control start`) accesible desde el navegador mientras la computadora anfitriona esté encendida.
- Un [análisis de terceros](https://www.productcompass.pm/p/antigravity-remote-control) indica que cubre sesiones de la aplicación y la CLI de Antigravity y no los agentes de otros proveedores.
- Varios hilos del [foro de Google](https://discuss.ai.google.dev/t/antigravity-2-0-remote-ssh-solution/187327) informan problemas del Agent Manager con proyectos remotos por SSH.
- No encontré una API o SDK público documentado para controlar Antigravity desde otra aplicación.

Viabilidad: una integración programática directa no es viable sin API documentada. La vía del panel web sí lo es: según la [documentación oficial](https://antigravity.google/docs/remote-control/) y [guías de terceros](https://agentsroom.dev/blog/antigravity-remote-control-from-your-phone), el panel se abre en cualquier navegador, incluido el del teléfono, con la misma cuenta de Google que la PC, y puede instalarse como aplicación web para recibir notificaciones.

**Prueba de la vía del panel web**: se implementó un acceso validado que abre el panel en el navegador del teléfono (sección 12.6 de `LIMITACIONES_CONOCIDAS.md`). No se pudo abrir el panel real ni leer la documentación directamente; la prueba de extremo a extremo requiere tu PC con el control remoto activo y tu cuenta de Google.

### 14.2 Extensiones de VS Code

Se adoptó un modelo **declarativo y acotado**: leer de un `.vsix` solo fragmentos y temas (datos), sin ejecutar su código. Una compatibilidad amplia exigiría implementar la API de extensiones de VS Code y un entorno de ejecución de JavaScript, lo que no es viable ni seguro en esta versión; **no se promete compatibilidad universal**. Sobre el origen de los paquetes, según [fuentes secundarias](https://en.wikipedia.org/wiki/Open_VSX), el Marketplace de Visual Studio limita su uso a productos de Microsoft y [Open VSX](https://www.eclipse.org/community/eclipse_newsletter/2020/march/1.php) es una alternativa abierta, por lo que el catálogo integrado usa Open VSX. Las licencias de cada extensión las define su autor.

## 15. Revisión de licencias de V2.0

V2.0 **no añade dependencias**: `libs.versions.toml` y los `build.gradle.kts` no cambiaron salvo `versionCode` y `versionName`. Las licencias de la sección 9 y de `LIMITACIONES_CONOCIDAS.md` (sección 10) siguen siendo las vigentes. Open VSX es un servicio consultado por red, no se redistribuye ningún contenido suyo. Los fragmentos integrados (`FragmentosBase`) son de autoría propia.

## 16. Decisiones técnicas de V2.0

1. **Control de versiones propio** en lugar de incorporar una biblioteca de Git: evita una dependencia pesada (la implementación en Java sin acceso a procesos de Git es voluminosa) y permite usar la API Git Data de GitHub para commits de varios archivos. Los blobs usan SHA-1 de Git para comparar con el árbol remoto; el costo es que no es compatible con repositorios Git reales.
2. **Fusión de tres vías por archivo** al traer, sin fusión de contenido: es predecible y nunca pierde cambios, a costa de pedir resolución manual.
3. **Agente con JSON de archivos completos** en lugar de parches: el modelo falla menos y las diferencias se calculan localmente con el algoritmo de Myers, así que el usuario ve exactamente lo que se aplicaría.
4. **Tokens de color únicos** y una prueba de contraste: evita que cada pantalla elija colores propios.
5. **Comandos como fuente única** del menú y de la paleta.
6. **Manifiestos normalizados** para extensiones: lo que se instala deja de depender del formato original.
7. **Correcciones halladas con lint y pruebas**: `InputStream.readNBytes` (API 33) fallaría en Android 8 a 12; se reemplazó por una lectura acotada propia. `listarArchivos` y la búsqueda antigua recorrían carpetas ocultas, lo que habría expuesto `.redact/` en la paleta, en las copias y en el contexto enviado a la IA; ahora las omiten.

## 17. Estado real de V2.0

**v2.0** (rama `v2`, etiqueta `v2.0`). Compilación, lint (0 errores, 4 avisos) y pruebas automáticas superadas (ver `RESULTADOS_DE_PRUEBAS.md`). **Sin ninguna prueba en dispositivo.** Pendientes principales: multicursor, pliegues, formateadores reales de Python y JavaScript, resolución de conflictos y fusión de ramas, prueba real del panel de Antigravity y verificación de GitHub, Open VSX y proveedores de IA contra servicios reales.
