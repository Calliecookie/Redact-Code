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


## 14. Limitaciones de V2.5 - Terminal profesional, entornos de ejecucion e identidad visual

Nada de esta sección se probó en un teléfono físico; la verificación fue en JVM, Robolectric y CPython de escritorio. Los componentes que dependen de Android (`TerminalView`, `libtermux.so`, Chaquopy real, `FileObserver`, animaciones) solo se comprobaron compilando.

**Terminal con PTY**
- Se usa `libtermux.so` de las bibliotecas de Termux (0.118.1). Sus bibliotecas nativas **no están alineadas a 16 KB**; en dispositivos con páginas de 16 KB podrían no cargar. Si la carga falla, la pestaña Terminal muestra el aviso y usa la terminal no interactiva de V2.0 (no verificado en dispositivo).
- El shell es `/system/bin/sh` de Android, no `bash`. No hay confinamiento real en la carpeta del proyecto.
- Android 10+ no permite ejecutar binarios descargados: no existen `gcc` ni Node.js reales.
- Las sesiones viven mientras viva el proceso; HyperOS/MIUI puede cerrarlo en segundo plano. No hay servicio en primer plano.
- No se probaron teclados virtuales de Xiaomi con `shouldEnforceCharBasedInput`; TalkBack tiene soporte limitado en la vista de terminal (se ofrece «Ver como texto»).
- **División**: hasta 2 paneles de shell (lado a lado desde 600 dp, apilados en pantallas estrechas); el panel con foco se marca con borde y recibe las teclas extra. El foco por toque se detecta con un observador de punteros que no se verificó en dispositivo. No hay menú `+` por tipo de sesión: Python y JavaScript siguen en sus pestañas.

**Puente de comandos**
- `python`, `node`, `git`, `pip`, `npm` y `redact` son funciones de shell que piden a la app ejecutar la operación mediante archivos en `filesDir/terminal/puente/<proyecto>`. El comportamiento de SELinux sobre ese directorio **no está verificado**. La app consulta la carpeta cada 100 ms (no usa `FileObserver`).
- **El puente no es interactivo**: dentro de la terminal, `python archivo.py` no recibe lo que se escribe (`input()` falla con fin de archivo). La entrada interactiva está en la pestaña **Depuración** (ver abajo), que no usa el PTY: lee de una cola. No se implementó `input()` a través del PTY porque el intérprete corre dentro del proceso de la app (Chaquopy), no como proceso hijo del PTY.
- `git` admite `init`, `status`, `add`, `commit`, `log`, `diff`, `branch`, `checkout`/`switch`, `clone`, `pull` y `push`. `clone` crea un **proyecto nuevo** (no clona en la carpeta actual) y solo acepta `https://github.com/usuario/repo`; `pull`/`push` usan el vínculo y el token de GitHub ya configurados en la app y el mismo sincronizador del panel de Control de código (fusión por archivo; `push` exige commits locales y que el remoto no haya avanzado). Sin token, fallan con el error de GitHub. No hay `merge`, `rebase`, `stash`, `tag`, SSH ni submódulos. Estas operaciones de red se probaron con acciones simuladas, no contra GitHub real.

**pip y npm**
- pip: solo ruedas `py3-none-any` de PyPI, con SHA-256, límite de 50 MB, 15 niveles y 60 paquetes, sin retroceso. Los paquetes con código nativo se rechazan con un mensaje explícito. El entorno es por proyecto (`filesDir/entornos/<id>/python/site-packages`).
- npm: `install`, `list` y `uninstall` descargan del registro público (`registry.npmjs.org`) a `node_modules` del proyecto, con verificación de integridad (sha512 o sha1), límites de tamaño (30 MB por paquete, 100 paquetes, 12 niveles), sin scripts de instalación y sin copias anidadas (un conflicto de versiones se informa y se detiene). No se admiten dependencias git, URL ni archivos locales. **No hay Node.js**: los paquetes sirven para la vista previa web (`<script src="node_modules/...">`); `node archivo.js` no resuelve `require`. `node_modules` se excluye de la búsqueda, del contexto del agente y del control de versiones.
- La resolución de rangos semver es propia (soporta `^`, `~`, comparadores, `x`, `||` y guiones) y puede diferir de npm en casos raros (etiquetas de prelanzamiento).

**Agente y comandos**
- Los comandos propuestos requieren el permiso «Proponer comandos de terminal» (desactivado por defecto) y la aprobación de cada uno. El clasificador es heurístico: bloquea los casos conocidos, pero no puede garantizar que ningún comando ingenioso evada las reglas; los desconocidos se marcan de riesgo alto.
- El registro (`registro_ejecuciones.jsonl`, con secretos censurados, recortado a 512 KB) se ve y se borra en Ajustes › Agente de código. La censura de secretos es por patrones y puede dejar pasar formatos no contemplados.

**Ejecución interactiva y depuración (Python)**
- Pestaña **Depuración** del panel inferior: «Ejecutar» corre el archivo activo con `input()` interactivo (cola) y «Depurar» lo corre bajo `bdb` con puntos de interrupción, Continuar, Paso, Entrar, Salir, Detener, pila de llamadas, variables y evaluación de expresiones. Solo Python, un archivo (`<redact>`), una sesión a la vez; ejecuta el texto del editor aunque no esté guardado, sin importar otros archivos del proyecto salvo paquetes instalados con pip.
- Los puntos de interrupción se alternan con el botón «Punto en la línea del cursor» o el comando «Alternar punto de interrupción»; **no hay icono en el margen del editor** (no se verificó la API `LineSideIcon` de Sora contra el analizador de estilos) y los puntos no se desplazan al editar el texto ni se conservan al salir del proyecto.
- El tiempo límite no cuenta las esperas de entrada ni las pausas. El corte y la cancelación se hacen con una función de rastreo (`sys.settrace`), igual que el botón Ejecutar: enlentece el programa y no interrumpe una llamada nativa larga (por ejemplo `time.sleep(60)`). La salida estándar se redirige a nivel global mientras dura la sesión: no usar la consola Python a la vez.
- No hay depuración de JavaScript (V8 vía `JavaScriptSandbox` no la ofrece) ni depuración remota del WebView.
- No se probó con Chaquopy real ni en el dispositivo: el motor (`redact_sesion.py`) se probó con CPython 3.13 de escritorio y el panel solo se compiló.

**Identidad visual**
- Los derivados del logotipo salen de `recursos_graficos/derivar_logo.py` a partir del original, que no se modifica: baldosa, pantalla de inicio, ícono adaptativo y capa monocromática (silueta de la «R» por umbral de color; vista previa en `build/derivados_logo/`, no versionada). Falta que la usuaria apruebe visualmente el ícono. `logo_redact.png` queda sin uso y no se eliminó (1 aviso `UnusedResources`).
- La pantalla de inicio sigue el tema elegido mediante `UiModeManager` en Android 12+; en versiones anteriores depende del tema del sistema. La animación de salida no se verificó en dispositivo ni con otras capas de fabricante.

**Licencias**
- Se añadieron `terminal-emulator` y `terminal-view` de Termux 0.118.1 (Apache-2.0). No se usa `termux-shared` (GPLv3). Obtenidas desde JitPack: el host `jitpack.io` debe estar permitido al compilar.

**Auditoría de V2.5**
- Corregido y con prueba: ver `RESULTADOS_DE_PRUEBAS.md`, «Auditoría de V2.5».
- Pendiente, sin solución segura sin dispositivo:
  - `UiModeManager.setApplicationNightMode(MODE_NIGHT_AUTO)` se usa para «seguir al sistema»; el comportamiento exacto en HyperOS no está verificado. Si la pantalla de inicio no coincide con el tema, la solución recomendada es fijar siempre el modo explícito (claro u oscuro) calculado al cambiar la preferencia.
  - El indicador del shell se define con `PS1='${PWD##*/} $ '` (sintaxis de mksh). Si el `sh` del dispositivo no fuera mksh, se vería el texto literal; la solución sería un indicador fijo.
  - `Ctrl+C` durante un comando del puente depende de que mksh ejecute la trampa `INT` de la función; solo se probó la cancelación creando el archivo de cancelación con `/bin/sh` de escritorio.
  - Las sesiones de terminal de un proyecto no se cierran al borrar el proyecto ni al salir del editor; se cierran con la X o al terminar el proceso. Límite: 8 por proyecto.
  - Los módulos de Python ya importados de un entorno siguen en memoria al cambiar de proyecto (se quita la ruta, no los módulos cargados).
  - El contador de intentos del PIN ahora se guarda cifrado; no tiene prueba automática porque Android Keystore no existe en Robolectric.
  - El clasificador de riesgo sigue siendo heurístico; leer fuera del proyecto se marca como riesgo alto, no se bloquea.
  - No se midió rendimiento en el teléfono (arranque, memoria, fluidez de la terminal). No hay mediciones nuevas en esta auditoría.
