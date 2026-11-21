# Limitaciones conocidas — Redact Code V1.0

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
