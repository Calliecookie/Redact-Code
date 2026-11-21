# Limitaciones conocidas — Redact Code v2.0 - Renovacion integral del IDE

Las secciones 1 a 11 describen V1.0 y siguen vigentes salvo donde la sección 12 las reemplaza. La sección 12 enumera lo que V2.0 no hace o no pudo verificarse, y la sección 13 el estado real de la versión.

Este documento describe con honestidad lo que la versión 1.0 no hace, lo que no pudo verificarse y los riesgos técnicos asumidos.

## 1. Verificación en dispositivo

- **La v1.0 no se ejecutó en un dispositivo ni en un emulador** durante su desarrollo. Las pruebas de esa versión fueron de compilación, de empaquetado intermedio (dex, recursos, assets de Python, librerías nativas) y pruebas automatizadas en la JVM (JUnit y Robolectric).
- Funciones que **solo pueden comprobarse en un dispositivo real**: arranque de Python (Chaquopy), validación/ejecución/revisión de Python, `JavaScriptSandbox` (V8 aislado), vista previa en `WebView`, cifrado con Android Keystore, biometría, notificaciones de WorkManager, selector de archivos del sistema (copias de seguridad e importación de retos) y la interacción táctil con el editor Sora.
- **Retos y proyectos guiados en Python**: sus casos de prueba se revisaron manualmente, pero no pudieron ejecutarse en la JVM porque requieren CPython en Android. Los de JavaScript sí se verificaron automáticamente con soluciones de referencia.

## 2. Rendimiento

- Objetivo de la especificación: validar 1 000 líneas en menos de 500 ms en gama media. Medición disponible: en la JVM de escritorio, JSON ≈ 7 ms, CSS ≈ 17 ms, JavaScript con Acorn sobre Rhino interpretado ≈ 520–600 ms. En Android, la validación JavaScript usa V8 (`JavaScriptSandbox`) cuando está disponible, que debería ser mucho más rápido, pero **no está medido**. En dispositivos sin `JavaScriptSandbox`, Rhino interpretado probablemente supere los 500 ms para archivos grandes. La validación es asíncrona, así que no bloquea la escritura en ningún caso.
- El primer uso de Python inicia el intérprete (estimado en 1–3 s según el dispositivo; no medido).

## 3. Ejecución de código y aislamiento

- **Python se ejecuta dentro del proceso de la aplicación. No es un sandbox seguro.** Un programa puede leer o escribir archivos privados de la app, abrir conexiones de red (la app tiene permiso de internet) o consumir mucha memoria. El límite de tiempo y la cancelación usan `sys.settrace`, que no interrumpe llamadas bloqueantes en código C (por ejemplo `time.sleep` largos o lecturas de red); en ese caso la ejecución termina cuando la llamada retorna.
- **JavaScript**: con `JavaScriptSandbox` se ejecuta en un proceso aislado sin acceso a la app. En el respaldo Rhino se ejecuta en el mismo proceso con objetos estándar seguros y límite de instrucciones; Rhino **no admite** parte de la sintaxis moderna (`class`, `async/await`, `for…of` con `const`), lo que se informa al usuario.
- La vista previa web se limita a la carpeta del proyecto, sin acceso a archivos ni contenido del dispositivo, pero el JavaScript de la página sí puede hacer peticiones de red.

## 4. Validadores

- Los analizadores informan el **primer error de sintaxis** (como CPython y Acorn); después de corregirlo aparece el siguiente.
- HTML: jsoup corrige automáticamente muchos errores siguiendo el estándar HTML5; algunos problemas estructurales solo aparecen como advertencias (por ejemplo, elementos sin cerrar).
- CSS: el analizador es propio; no valida que los **valores** de cada propiedad sean correctos (por ejemplo `color: 12px`) y la lista de propiedades conocidas puede no incluir propiedades muy recientes (se muestran como advertencia, no como error).

## 5. Revisor de código

El revisor combina análisis sintáctico real con **reglas heurísticas**. Puede producir falsos positivos (por ejemplo variables usadas solo de forma dinámica) y no detecta errores lógicos. No es una auditoría de seguridad.

## 6. Mentor e IA

- Sin proveedor externo, el mentor es **determinista**: explica estructura y errores con catálogos locales y análisis del código, pero no responde preguntas abiertas en lenguaje natural.
- El proveedor Claude requiere que el usuario aporte su clave de API de Anthropic, conexión a internet y aceptar el envío del código consultado. **No se probó contra la API real** porque no hay credenciales disponibles; la integración usa el SDK oficial `anthropic-java` y compila. El uso tiene costo para el titular de la clave.
- Se incluye la cabecera beta de *server-side fallback* recomendada para modelos recientes; si la cuenta no tiene acceso a esa beta, la API podría rechazar la solicitud y el mentor mostrará la explicación local con un aviso.

## 7. GitHub

- La autenticación usa **tokens personales** que el usuario crea en GitHub. No se implementó OAuth (flujo de dispositivo o web) porque requiere registrar una aplicación OAuth propia de Redact Code (un `client_id`), que no existe todavía.
- La API de contenidos de GitHub limita las descargas a archivos de hasta 1 MB y cada publicación crea un commit por archivo (no hay commits de varios archivos en V1.0).
- La revisión de *pull requests* está preparada en el cliente (`pullRequestsAbiertos`, `archivosDePullRequest`) pero **no tiene interfaz** en V1.0.
- No se probó contra GitHub real (sin token disponible); el cliente se verificó con un servidor HTTP simulado (MockWebServer).

## 8. Seguridad y privacidad

- El bloqueo con PIN se solicita al abrir la aplicación (inicio del proceso). No se vuelve a bloquear automáticamente al pasar a segundo plano.
- No hay recuperación de PIN; la única salida es borrar los datos de la app.
- `allowBackup` está desactivado: la protección de los datos depende de las copias manuales (`.zip`), que **no están cifradas**; el usuario es responsable de guardarlas en un lugar seguro.

## 9. Plataforma

- Solo `arm64-v8a` y `x86_64` (Chaquopy con Python 3.13 no soporta 32 bits).
- El tamaño instalado será considerable (intérprete de Python, V8 no se incluye porque lo provee WebView). No se midió.
- `compileSdk` 37 es obligatorio por las versiones actuales de AndroidX utilizadas.

## 10. Licencias

Sora Editor se distribuye bajo **LGPL-2.1**. Para una distribución comercial se debe revisar el cumplimiento de la LGPL (permitir reemplazar la biblioteca) o evaluar una licencia alternativa. Acorn (MIT), Rhino (MPL-2.0), jsoup (MIT), Chaquopy (MIT), JetBrains Mono (OFL-1.1) y AndroidX/Kotlin (Apache-2.0) se usan conforme a sus licencias; los avisos se muestran en **Ajustes › Acerca de** y en `recursos_graficos/`.

## 11. Fuera del alcance de V1.0 (por diseño)

Conexión con PC, ejecución remota de Java/C/C++, terminal de escritorio y sincronización. Solo se prepararon las interfaces en `pe.redactcode.nucleo.escritorio`; no hay botones que aparenten ofrecer estas funciones.

## 12. Limitaciones de V2.0

### 12.1 Verificación en dispositivo

- **Nada de V2.0 se ejecutó en un dispositivo ni en un emulador** durante su desarrollo. Lo verificado es compilación, 52 pruebas JVM de `:nucleo`, 28 pruebas de `:aplicacion` (Robolectric, incluidas pruebas de preferencias, extensiones e importación de ZIP) y lint. La interfaz nueva (espacio de trabajo, paleta, paneles, ajustes, inicio, riel lateral), las animaciones, los gestos sobre el editor Sora, la terminal, el teclado y TalkBack **no se han visto funcionar**.
- No hay capturas de pantalla ni medición de rendimiento de ninguna pantalla de V2.0.

### 12.2 Editor

- **Multicursor: no implementado.** Sora Editor no lo ofrece como función pública y no se simuló.
- **Pliegues de código: no implementados.**
- **Formateo**: JSON (analizador RFC 8259), CSS (por llaves y declaraciones) y HTML (jsoup) se reformatean de verdad. Para Python, JavaScript y texto solo se limpian espacios finales, tabulaciones iniciales y el salto de línea final; no hay un formateador como Black o Prettier. El formateo de HTML puede normalizar el marcado (por ejemplo añadir `html`, `head` y `body` en documentos completos) y el de CSS no conoce todas las reglas de `@`. «Formatear al guardar» solo actúa sobre JSON, CSS y HTML.
- **Navegación de código**: ir a definición, símbolos y referencias se basan en análisis por líneas, no en un servidor de lenguaje. Las definiciones se buscan solo en archivos del mismo lenguaje y las referencias son una búsqueda de palabra completa que incluye comentarios y cadenas.
- **Autocompletado**: palabras clave, identificadores del documento y fragmentos. No hay completado semántico. Al insertar un fragmento, los marcadores `${1}` se resuelven al texto por defecto y el cursor queda en el primer marcador; no existe la navegación con Tab entre marcadores, ni transformaciones con expresiones regulares ni espejos de marcadores.
- Los archivos de más de 2 MB no se abren y los que no son de texto no se pueden editar (el editor lo indica).

### 12.3 Terminal y consolas

- La terminal ejecuta el **shell de Android dentro del proceso de la aplicación**. No es una terminal interactiva (sin PTY): los programas que esperan teclado reciben fin de archivo.
- **No es un aislamiento de seguridad.** El comando interno `cd` no permite salir de la carpeta del proyecto, pero un comando del shell puede usar rutas absolutas y acceder a lo que el proceso de la aplicación pueda leer o escribir. Úsala con la misma cautela que la ejecución de Python (sección 3).
- Los comandos disponibles dependen del Android del dispositivo (herramientas de `toybox`). `python` y `node` son comandos internos que usan los motores integrados (CPython vía Chaquopy y V8 aislado o Rhino); `node` **no es Node.js**: no hay `require`, `npm` ni módulos.
- La consola de JavaScript reconstruye el estado volviendo a ejecutar las entradas correctas anteriores, de modo que los efectos secundarios se repiten y no se conserva estado asíncrono. La consola de Python mantiene una sesión real, pero comparte el cerrojo de ejecución con los programas del editor.
- Verificado solo en la JVM de escritorio: la lógica de la terminal (con el shell de Linux) y la sesión de la consola de Python (con CPython de escritorio). Ninguna de las dos en Android.

### 12.4 Control de código y GitHub

- El control de versiones local es **propio** (carpeta `.redact/`), no es Git: los identificadores de blob coinciden con los de Git, pero los de commit no. No hay fusión (merge) entre ramas, rebase, stash ni etiquetas. No se versionan carpetas vacías, permisos de archivo ni archivos de más de 5 MB; los binarios se versionan pero no se comparan.
- **Traer** hace una fusión de tres vías por archivo: si un archivo cambió en ambos lados, se informa el conflicto y **no se aplica ningún cambio**; no hay resolución asistida dentro del archivo.
- **Clonar** usa el archivo zipball de GitHub: se omiten archivos de más de 10 MB, el total descomprimido no puede pasar de 300 MB ni 20 000 archivos, y no se admiten submódulos ni Git LFS.
- El historial remoto es una lista de commits; no hay comparación de cambios de commits remotos. La comparación de cambios existe para el estado local y para commits locales.
- La autenticación sigue siendo por token personal (sin OAuth, sección 7). Publicar crea un único commit con todos los archivos modificados y puede crear la rama remota.
- **Nada de esto se probó contra GitHub real**: solo contra un servidor HTTP simulado (MockWebServer) que implementa la API Git Data. El comportamiento real de la API (límites, rutas con caracteres especiales, repositorios vacíos) puede diferir.

### 12.5 Agentes de IA

- **No se probó contra ninguna API real** (no hay credenciales). La integración con Claude usa el SDK oficial y compila; el proveedor compatible con OpenAI se probó solo con un servidor simulado.
- El agente no es autónomo: no ejecuta comandos ni itera con herramientas. Recibe un contexto limitado (por defecto 30 archivos y 120 000 caracteres), responde con un JSON de archivos completos y tú revisas las diferencias. Si el modelo no respeta el formato, solo se muestra su texto. Si la respuesta se corta por longitud se informa un error.
- Se envía contenido del proyecto al proveedor elegido; tiene costo para el titular de la clave y exige el consentimiento de Ajustes.
- «Proveedores adicionales»: solo Claude y servicios compatibles con la API `/chat/completions`. No se verificó la compatibilidad con ningún servicio concreto.

### 12.6 Google Antigravity: acceso al panel web (sin integración de datos)

Se probó la vía del panel web hasta donde se puede sin tu PC ni tu cuenta:

- **Lo que se implementó**: en Ajustes › Agentes y en la paleta de comandos, un acceso que abre el panel de control remoto de Antigravity **en el navegador del teléfono**, con la dirección configurable. La dirección se valida (solo `https` y los dominios `antigravity.google` y `antigravity.google.com`, sin credenciales en la URL) y la validación tiene prueba automática. No se usa un WebView porque Google no permite iniciar sesión con su cuenta dentro de vistas web incrustadas.
- **Lo que no hace**: Redact Code no intercambia proyectos, archivos ni instrucciones con Antigravity. No existe una API pública documentada para eso (ver `INFORME_TECNICO_FINAL.md`, sección 14.1).
- **Lo que no se pudo verificar**: la documentación oficial de `antigravity.google` se consultó solo a través de fuentes de terceros. La dirección predeterminada (`https://antigravity.google.com`) sale de esas fuentes. **No se abrió el panel real ni se conectó a una PC.** Si tu PC muestra otra dirección al activar el control remoto, pégala en Ajustes › Agentes › Dirección del panel.
- **Requisitos según la documentación consultada**: el control remoto debe estar activo en la PC (en la app, Ajustes › App; en la CLI, `/remote-control` o `agy --remote-control`), la PC debe estar encendida y el navegador del teléfono debe usar la misma cuenta de Google. Una cuenta u organización puede tenerlo desactivado por política.

### 12.7 Extensiones

- Solo se admiten **fragmentos y temas de color**. Un paquete `.vsix` se lee como datos: se ignoran su código (`main`/`browser`), gramáticas TextMate, servidores de lenguaje, comandos, atajos, configuraciones, temas de iconos y cualquier otra contribución, y la interfaz lo informa extensión por extensión. **No hay ni habrá compatibilidad universal con extensiones de VS Code.**
- Un tema se traduce a 15 colores (6 del editor: fondo, texto, línea actual, números de línea, selección y cursor; y 9 de sintaxis); el resto se ignora.
- El catálogo usa **Open VSX** (open-vsx.org). **No se probó contra el servicio real**, solo con un servidor simulado; el formato de respuesta pudo cambiar. No se usa el Marketplace de Visual Studio Code porque, según fuentes secundarias, sus términos limitan el uso a productos de Microsoft; revísalos tú antes de cualquier uso alternativo.
- La licencia de cada extensión la define su autor. Redact Code muestra la licencia cuando el catálogo la informa y no la verifica automáticamente. Antes de instalar o redistribuir una extensión, revisa su licencia.
- No hay firmas ni verificación de integridad de los paquetes más allá del límite de tamaño y de la validación del manifiesto.

### 12.8 Ajustes, seguridad y accesibilidad

- «Bloquear al volver de segundo plano» pide el PIN si la aplicación estuvo más de 30 s fuera; los selectores de archivos del sistema duran menos y no la bloquean.
- Los colores cumplen contraste AA según una prueba automática sobre los valores de color; no se auditó con TalkBack ni con tamaños de texto extremos en dispositivo. Los objetivos táctiles siguen 48 dp (56 dp con la opción) sin auditoría en pantalla.
- Las copias de seguridad siguen sin cifrar y **no incluyen** las extensiones instaladas ni el historial del control de versiones local (la carpeta oculta `.redact/` se excluye, igual que cualquier archivo o carpeta oculta). Al restaurar una copia, los proyectos vuelven sin historial local.

### 12.9 Rendimiento

No se midió nada de V2.0 en dispositivo. Puntos a vigilar en la primera prueba real: la búsqueda global (lee los archivos del proyecto en cada consulta, con tope de 2 000 resultados), la extracción de símbolos (se recalcula 300 ms después de escribir), el escaneo de cambios del control de versiones (calcula el hash de todos los archivos) y el tamaño de repositorios clonados.

### 12.10 Licencias

V2.0 **no añade dependencias nuevas**; se mantienen las de la sección 10 (Sora Editor sigue bajo LGPL-2.1, con la misma salvedad para distribución comercial). Open VSX es un servicio externo, no una dependencia.

## 13. Estado real de V2.0

La versión se publica como **v2.0** en la rama `v2`, pendiente de tus pruebas en dispositivo antes de integrarse en `main`. Siguen pendientes: pruebas en dispositivo, multicursor, pliegues de código, formateadores reales para Python y JavaScript, resolución asistida de conflictos, fusión de ramas, prueba real del panel de Antigravity con tu PC, y verificación de GitHub, Open VSX y los proveedores de IA contra servicios reales.
