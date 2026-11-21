# Resultados de pruebas — Redact Code v2.0 - Renovacion integral del IDE

Fecha de ejecución: 14 de noviembre de 2026. Entorno: Linux, OpenJDK 25, Gradle 9.9.1, AGP 9.5.1, Android SDK 37. Todas las tareas se ejecutaron con `--rerun-tasks`. **No se generó ningún APK ni AAB.**

## A. Pruebas automatizadas (resultados reales)

| Tarea | Resultado |
|---|---|
| `:nucleo:test` | **53/53 superadas** (JUnit 4, JVM) |
| `:aplicacion:compileDebugKotlin` | Superada sin errores |
| `:aplicacion:testDebugUnitTest` | **29/29 superadas** (Robolectric y Compose UI Test en la JVM) |
| `:aplicacion:lintDebug` | **0 errores, 4 avisos** (los mismos de V1.0: `OldTargetApi`, `ObsoleteSdkInt`, 2 × `MonochromeLauncherIcon`) |

Pruebas por clase. Las clases de V1.0 se conservaron sin eliminar ninguna prueba.

| Clase | Módulo | Pruebas | Novedad |
|---|---|---|---|
| `ValidadoresTest` | nucleo | 14 | V1.0 |
| `RetosYProyectosTest` | nucleo | 10 | V1.0 |
| `RevisionMentorAprendizajeTest` | nucleo | 14 | V1.0 |
| `IdeNucleoTest` | nucleo | 7 | **V2.0** |
| `IntegracionesTest` | nucleo | 8 | **V2.0** |
| `BaseDatosYRepositoriosTest` | aplicacion | 9 | V1.0 |
| `EditorYUiTest` | aplicacion | 6 | V1.0 |
| `TemaContrasteTest` | aplicacion | 3 | **V2.0** |
| `AjustesYExtensionesTest` | aplicacion | 8 | **V2.0** |
| `ImportacionZipTest` | aplicacion | 3 | **V2.0** |

### Qué cubren las pruebas nuevas

- **`IdeNucleoTest`**: diferencias de Myers (mínimas, bloques, aplicación parcial, 3 000 líneas, caso clásico de Myers); control de versiones completo (estado, commit parcial, descarte, ramas, bloqueo al cambiar con cambios pendientes, rutas fuera del proyecto, binarios, identificador de blob igual al de Git); búsqueda global y reemplazo con grupos; coincidencia difusa; formateadores JSON, CSS, Python y HTML; símbolos y navegación; terminal real (cd acotado, tuberías, tiempo agotado, comandos internos, autocompletado).
- **`IntegracionesTest`**: extensión `.vsix` (fragmentos, temas, contribuciones no compatibles, ruta maliciosa, expansión de fragmentos); catálogo Open VSX contra servidor simulado (búsqueda, descarga, bloqueo de servidor externo); lectura limitada; extractor ZIP con rutas peligrosas; **sincronización GitHub completa contra un servidor simulado de la API Git Data** (clonar, publicar con eliminación `sha: null`, traer, conflicto, rechazo de publicar con remoto adelantado); intérprete del agente con permisos, proveedor compatible con OpenAI y validación de la dirección del panel de Antigravity.
- **`TemaContrasteTest`**: contraste AA (4,5:1 texto y 3:1 foco y selección) de todos los tokens y colores de sintaxis en las cuatro variantes (oscuro, claro y alto contraste de cada uno). La primera ejecución encontró 22 pares por debajo de 4,5:1 y se ajustaron los colores.
- **`AjustesYExtensionesTest`**: valores predeterminados, persistencia y límites de más de 40 preferencias (releyendo con una instancia nueva), conjuntos de lenguajes y extensiones, restablecer ajustes conservando bienvenida y bloqueo, instalar, desactivar y desinstalar extensiones, tema de extensión, extensiones de archivo permitidas y controlador del editor sin editores.
- **`ImportacionZipTest`**: importar ZIP con carpeta raíz, `.git` y ruta maliciosa; ZIP inválido sin proyectos huérfanos; carpetas ocultas fuera del listado y la búsqueda.

### Defectos encontrados por las pruebas y corregidos

| Hallazgo | Detectado por | Corrección |
|---|---|---|
| 22 pares de color con contraste menor a 4,5:1 | `TemaContrasteTest` | Colores ajustados |
| `InputStream.readNBytes(int)` exige API 33 y la app admite API 26 | `lintDebug` | Lectura acotada propia (`leerLimitado`), también en `:nucleo`, donde lint no llega |
| `listarArchivos` y la búsqueda antigua incluían archivos de carpetas ocultas (`.redact/`), que habrían salido en la paleta, en las copias y en el contexto enviado al proveedor de IA | revisión + `ImportacionZipTest` | Se omiten las carpetas ocultas |
| Condición de carrera en una prueba de extensiones (el `StateFlow` se recompone en otro hilo) | `AjustesYExtensionesTest` | La prueba espera la condición con tiempo límite |
| Expectativas erróneas del propio autor en 2 pruebas (hash de «hello world» y conteo de una expresión regular) | `IdeNucleoTest` | Pruebas corregidas |

## B. Lo que NO se probó

| Aspecto | Estado |
|---|---|
| Ejecución en dispositivo o emulador (Xiaomi Redmi Note 14) | **No realizada**. Toda la interfaz de V2.0 es código compilado y probado solo en la JVM |
| Gestos y edición real en Sora (duplicar línea, mover línea, comentar, reemplazo de documento, fragmentos con cursor) | Solo se probó que el controlador no falle sin editores; **no se ejecutó sobre un editor real** |
| Terminal en Android y consola de Python en Chaquopy | La lógica se probó con el shell de Linux y con CPython de escritorio; no en Android |
| GitHub, Open VSX, Claude y proveedores compatibles contra servicios reales | **No se probaron**; solo servidores simulados |
| Animaciones, rendimiento y consumo | No medidos |
| Accesibilidad con TalkBack y tamaños de texto extremos | No verificada; solo contraste por cálculo |
| Importar ZIP y clonar desde el selector de archivos del sistema | No probado en dispositivo (la importación se prueba con flujos de bytes) |
| Panel web de Google Antigravity | No se abrió el panel real: la prueba requiere tu PC con el control remoto activo y tu cuenta de Google |

Las pruebas B se realizan en el dispositivo desde Android Studio con **Run**. Este documento no sustituye esas pruebas.

---

# Resultados de pruebas — Redact Code V1.0 (histórico)


Fecha de ejecución: 13 de noviembre de 2026. Entorno: Windows 11, JetBrains Runtime 25.0.4, Gradle 9.9.1, AGP 9.5.1.

## 1. Qué se probó y qué no

| Tipo | Estado |
|---|---|
| Compilación Kotlin de `:nucleo` y `:aplicacion` | **Superada** sin errores |
| Procesamiento KSP (Room y Hilt: grafo de dependencias validado en compilación) | **Superado** |
| Empaquetado intermedio sin APK: manifiesto combinado, recursos, dex con desugaring (`mergeExtDex`, `mergeLibDex`, `mergeProjectDex`), assets de Python de Chaquopy, librerías nativas y recursos Java | **Superado** |
| Pruebas unitarias JVM de `:nucleo` (JUnit 4) | **38/38 superadas** |
| Pruebas de `:aplicacion` en JVM con Robolectric (Room, archivos, repositorios, evaluación de retos, resaltado, UI de Compose) | **15/15 superadas** |
| Análisis estático Android (lint) | **0 errores**, 4 avisos justificados (sección 5) |
| Ejecución en dispositivo físico o emulador | **No realizada** en la v1.0 |

Las pruebas «de compilación» y «de JVM» no sustituyen las pruebas reales en un teléfono. Ver `LIMITACIONES_CONOCIDAS.md`.

## 2. Pruebas de `:nucleo` (38)

### ValidadoresTest (14)
- `catalogoDeExplicacionesCompleto`: las 83 explicaciones tienen título, explicación, causa y recomendación.
- `jsonValidoSinDiagnosticos`, `jsonComaFinalConPosicion` (línea 3, columna 9), `jsonErroresFrecuentes` (13 códigos JSON001–JSON014), `jsonClaveDuplicadaEsAdvertencia`.
- `cssValidoIncluyendoMediaYAnidamiento`, `cssPuntoYComaFaltante`, `cssErroresEstructurales` (CSS001–CSS011), `cssPropiedadDesconocidaEsAdvertencia`.
- `htmlValidoSinErrores`, `htmlDetectaProblemas` (elemento sin cerrar, cierre sobrante, falta DOCTYPE, elemento obsoleto).
- `javascriptModernoValido` (clases con campos privados, `async`/`for await`, *spread*, `?.`, `??`, `**`), `javascriptErroresConPosicion` (línea/columna exactas y códigos JS001, JS002, JS005, JS015, JS009; `import` en módulo aceptado).
- `javascriptRendimientoMilLineas` (mediciones en la sección 4).

### RetosYProyectosTest (10)
- `catalogoDeRetosValido`: 29 retos, identificadores únicos, formato válido, Python y JavaScript.
- `todosLosRetosJavaScriptTienenSolucionQueAprueba`: los 12 retos JavaScript se resuelven con soluciones de referencia.
- `codigoInicialNoApruebaLosRetos`: el código inicial nunca aprueba (los retos no se resuelven solos).
- `evaluacionDetectaErroresYBuclesInfinitos`: aprobación parcial, excepciones `TypeError` por caso, bucle infinito detenido por tiempo y función inexistente.
- `ejecucionDeProgramaConConsolaYErrores`: salida de `console.log`, excepción con línea correcta y tiempo agotado.
- `importacionDeRetosValidaFormato`: exportar/importar y rechazo de formatos inválidos.
- `proyectosGuiadosCompletos`, `proyectoListaDeTareasSeVerificaConSolucion`, `proyectosJavaScriptConSoluciones`, `proyectosHtmlYCssSeVerificanConSolucion`: las verificaciones de pasos fallan con los archivos iniciales y pasan con soluciones correctas.

### RevisionMentorAprendizajeTest (14)
- Revisor: prácticas y riesgos de JavaScript (`var`, `==`, `eval`, `innerHTML`, `catch` vacío, variable no usada, preferir `const`), CSS, HTML (alt, `target=_blank`, `id` duplicado, `lang`, `title`, `label`), duplicados y separación de errores confirmados.
- Mentor: explicación línea por línea, adaptación por nivel, explicación de error con historial, correcciones automáticas verificadas (la corrección JSON vuelve a validar sin errores), recurso al mentor local cuando el proveedor externo falla o no hay consentimiento, interpretación de respuestas externas.
- Aprendizaje: SM-2 (factores e intervalos exactos y facilidad mínima 1,3), rachas, perfil y recomendaciones.
- GitHub: listado, descarga Base64, publicación (cuerpo con `sha`, `branch`, contenido) y error 401 contra un servidor HTTP simulado.

## 3. Pruebas de `:aplicacion` con Robolectric (15)

### BaseDatosYRepositoriosTest (9) — Room en memoria + archivos reales en el almacenamiento privado simulado
- `crearProyectoConPlantillaWeb`, `operacionesDeArchivosYSeguridadDeRutas` (crear, guardar, renombrar, mover, buscar, eliminar; rechazo de extensiones no permitidas, de rutas `../` y de mover una carpeta dentro de sí misma), `recientesYEliminacionEnCascada`, `proyectoGuiadoSeCreaUnaVez`.
- `fragmentosConRepeticionEspaciada` (etiquetas normalizadas, próxima fecha, SM-2 persistido, historial, actividad y borrado en cascada).
- `actividadYRacha` (racha de 2 días, minutos de hoy y de la semana).
- `perfilYAprendizajeSinDatosInventados`: sin actividad, el perfil no muestra datos ni errores.
- `evaluarRetoRegistraIntentoYProgreso`: intento fallido, error de sintaxis registrado en el mapa de errores (JS001), reto resuelto, mejor puntaje y actividad diaria.
- `importarRetosDesdeJson`: importa retos nuevos, omite duplicados del catálogo y evalúa el reto importado.

### EditorYUiTest (6)
- `resaltadoPython`, `resaltadoMultilineaJavaScriptYHtml`, `lexerToleraCodigoIncompleto` (cadenas, comentarios y etiquetas sin cerrar en los 6 lenguajes), `pestanaDetectaModificacion`.
- `panelDeProblemasMuestraExplicacionYCorreccion` (Compose UI): el diagnóstico se expande, muestra la causa probable y el botón «Aplicar corrección» ejecuta la corrección.
- `panelDeProblemasVacio`.

## 4. Mediciones de rendimiento (JVM de escritorio, no en teléfono)

| Operación | Tamaño | Tiempo medido |
|---|---|---|
| Validación JSON | 1 000 líneas | 7 ms |
| Validación CSS | 1 000 líneas | 17 ms |
| Validación JavaScript (Acorn sobre Rhino interpretado) | 1 000 líneas | 520–600 ms |
| Resaltado de sintaxis Python | 2 000 líneas | 10 ms |

En Android, la validación JavaScript usa V8 (`JavaScriptSandbox`) cuando está disponible; no se midió en dispositivo. La validación de Python depende de CPython en el teléfono y tampoco se midió.

## 5. Análisis estático (lint)

`gradlew :aplicacion:lintDebug`: **0 errores, 4 avisos**.

La primera ejecución encontró 1 error y 22 avisos, que se corrigieron:
- Error `ContextCastToActivity`: la pantalla de bloqueo ahora obtiene la actividad con `LocalActivity` para la biometría.
- `MissingOnRenderProcessGone`: la vista previa web maneja la caída del proceso del navegador sin cerrar la aplicación.
- Íconos heredados duplicados y sin forma: se eliminaron (con `minSdk` 26 siempre se usa el ícono adaptativo) y la notificación usa un ícono vectorial monocromo propio.
- Dos sugerencias de estado sin *autoboxing* aplicadas.

Avisos que permanecen, con su justificación:
- `OldTargetApi` (targetSdk 36): se mantiene 36 hasta probar en un dispositivo los cambios de comportamiento de Android 17.
- `ObsoleteSdkInt` en `mipmap-anydpi-v26`: al mover los íconos a `mipmap-anydpi` el enlazado de recursos dejó de encontrarlos; se conserva la carpeta `-v26`, que funciona igual con `minSdk` 26.
- `MonochromeLauncherIcon` (2): un ícono temático monocromo requiere una versión de una sola tinta del logo; no se creó para no alterar el logo oficial.

## 6. Errores encontrados y corregidos durante las pruebas

| Hallazgo | Causa | Corrección |
|---|---|---|
| El parser de Rhino rechazaba `class`, `async` y `for…of` con `const` | Soporte ES6 incompleto de Rhino 1.9.2 | Se adoptó Acorn como parser de JavaScript |
| Un `Selector` de Java fallaba al iniciar Gradle en este equipo | La carpeta temporal no admite *sockets* de dominio Unix | `-Djdk.net.unixdomain.tmpdir` (documentado en la guía) |
| Python 3.13 no disponible para `armeabi-v7a` | Limitación de Chaquopy | Se dejaron `arm64-v8a` y `x86_64` |
| Dependencias AndroidX exigían compilar contra API 37 | Versiones recientes de AndroidX | `compileSdk` 37 |
| **Bucle infinito en el resaltado CSS** con valores como `#fff` (y números negativos en JSON) | El lector de números no consumía el primer carácter | Se consume siempre el primer carácter y se añadió una protección de avance en todos los analizadores léxicos |
| **Acceso fuera de rango** en el resaltado con código incompleto (por ejemplo, un atributo HTML sin cerrar al final) | Avance sin comprobar el fin del texto | Avance seguro al final del texto |
| Caracteres con tilde dañados en 6 archivos tras una edición automatizada | Lectura de UTF-8 como Windows-1252 | Se revirtió la doble codificación y se verificó que no queden secuencias dañadas |
| Expectativas incorrectas en 3 pruebas iniciales (un CSS con dos errores, un caso de aprobación parcial y un botón fuera del área visible) | Error en la prueba, no en la aplicación | Pruebas corregidas |
