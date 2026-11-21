# Resultados de pruebas — Redact Code V1.0

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
