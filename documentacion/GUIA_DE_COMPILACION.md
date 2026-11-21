# Guía de compilación y ejecución — Redact Code v2.0 - Renovacion integral del IDE

Esta guía explica cómo abrir el proyecto en Android Studio y ejecutarlo en tu celular con el botón **Run**. El proyecto no incluye ni genera instaladores (APK/AAB) de distribución.

## 1. Requisitos

| Componente | Versión usada y verificada |
|---|---|
| Android Studio | Versión con JetBrains Runtime 25 (la instalada en este equipo) |
| JDK | JDK 25 (`gradle/gradle-daemon-jvm.properties` lo exige para el daemon). El JBR 25 de Android Studio lo cumple; en Linux se instaló `openjdk-25-jdk-headless` |
| Gradle | 9.9.1 (se descarga automáticamente con el *wrapper*) |
| Android Gradle Plugin | 9.5.1 |
| Kotlin | 2.4.21 (Kotlin integrado de AGP 9) |
| Android SDK | Plataforma **Android 37** (`compileSdk`), `targetSdk` 36, `minSdk` 26 (Android 8.0) |
| Chaquopy | 17.2.0 con Python 3.13 |
| Conexión a internet | Solo para la primera sincronización (descarga de dependencias) |

ABI incluidas: `arm64-v8a` (celulares actuales) y `x86_64` (emulador). Python 3.13 de Chaquopy no ofrece `armeabi-v7a`, por lo que los teléfonos de 32 bits no son compatibles.

## 2. Abrir el proyecto

1. En Android Studio elige **File › Open** y selecciona la carpeta **RedactCode**.
2. Acepta la sincronización de Gradle. La primera vez descarga Gradle, las dependencias y el entorno de Python de Chaquopy (varios cientos de MB).
3. Si Android Studio pide instalar la plataforma Android 37 o *build-tools*, acepta la instalación desde el SDK Manager.
4. El archivo `local.properties` apunta al SDK de este equipo. Si abres el proyecto en otra computadora, Android Studio lo regenera automáticamente.

## 3. Ejecutar en tu celular por USB

1. En el celular: **Ajustes › Acerca del teléfono** y toca 7 veces **Número de compilación** para activar las opciones de desarrollador.
2. En **Opciones de desarrollador** activa **Depuración por USB**.
3. Conecta el celular por USB y acepta el diálogo «¿Permitir depuración USB?».
4. En Android Studio selecciona tu celular en el selector de dispositivos.
5. Selecciona la configuración de ejecución **aplicacion** (Android Studio la crea al sincronizar) y pulsa **Run ▶**.

Android Studio compila la variante *debug*, la instala y abre Redact Code. Ese paquete temporal lo maneja Android Studio internamente para instalar en el dispositivo; el proyecto no genera ni conserva instaladores de distribución.

## 4. Ejecutar las pruebas

Desde Android Studio: clic derecho sobre `nucleo/src/test` o `aplicacion/src/test` › **Run Tests**.

Desde la terminal de Android Studio:

```
gradlew :nucleo:test
gradlew :aplicacion:testDebugUnitTest
```

Las pruebas de `:aplicacion` usan Robolectric (se ejecutan en la JVM, sin dispositivo). La primera ejecución descarga el entorno de Robolectric.

## 5. Problemas conocidos del entorno de compilación

- **«Unable to establish loopback connection»** al iniciar Gradle desde una terminal: ocurre cuando la carpeta temporal del sistema no admite *sockets* de dominio Unix. Solución: definir la variable de entorno `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\ruta\corta` antes de ejecutar `gradlew`. Android Studio normalmente no presenta este problema.
- **Advertencia de Chaquopy sobre `buildPython`**: si el equipo no tiene Python 3.13 instalado, Chaquopy no precompila el código Python a *bytecode* y lo empaqueta como fuente `.py`. La aplicación funciona igual; solo el primer arranque de Python es algo más lento. Para eliminar la advertencia instala Python 3.13 y agrega `buildPython("ruta\\python.exe")` en el bloque `chaquopy { defaultConfig { } }`.
- **Rutas largas en Windows**: el proyecto funciona con rutas largas cuando `LongPathsEnabled` está activo (lo está en este equipo). Si se copia a otro equipo, se recomienda ubicarlo en una ruta corta como `C:\Proyectos\RedactCode`.

## 6. Estructura del proyecto

```
RedactCode/
├── settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/
├── nucleo/                 Módulo Kotlin/JVM (dominio y lógica reutilizable)
│   └── src/main/resources/pe/redactcode/nucleo/
│       ├── catalogos/      explicaciones_errores.json, retos.json, proyectos_guiados.json, conceptos.json
│       └── js/             acorn.js (MIT), analisis_redact.js
├── aplicacion/             Aplicación Android
│   ├── src/main/java/pe/redactcode/aplicacion/
│   ├── src/main/python/    redact_python.py
│   ├── src/main/res/       iconos generados desde el logo oficial, JetBrains Mono
│   └── esquemas/           esquema Room exportado
├── recursos_graficos/      logo original y licencia de JetBrains Mono
└── documentacion/          esta documentación
```

## 7. Verificación por línea de comandos sin generar APK (V2.0)

Estas son las tareas usadas para verificar V2.0. Ninguna genera APK ni AAB:

```
./gradlew :nucleo:test
./gradlew :aplicacion:compileDebugKotlin
./gradlew :aplicacion:testDebugUnitTest
./gradlew :aplicacion:lintDebug
```

No se ejecutaron `assembleDebug`, `bundle*` ni `installDebug`. La instalación y las pruebas en el dispositivo se hacen desde Android Studio con **Run**.

Requisitos del entorno Linux de verificación: `ANDROID_HOME=/opt/android-sdk`, plataforma `android-37.0`, `build-tools;37.0.0` y un `local.properties` con `sdk.dir`. Las pruebas de Robolectric descargan un artefacto desde `gcp.mirrors.maven.org`; si tu red restringe hosts, debes permitirlo.

## 8. Versión

`versionName` es `2.0` y `versionCode` es 2. Ver `HISTORIAL_DE_VERSIONES.md` para la política de versiones y ramas. Las funciones pendientes y lo no verificado están en `LIMITACIONES_CONOCIDAS.md`, secciones 12 y 13.

## 9. Integración continua

`.github/workflows/verificacion.yml` ejecuta en GitHub Actions, en cada push a `main` o a una rama de versión (`v2`, `v2.1`, etc.) y en cada pull request, las mismas tareas de la sección 7 (sin generar APK) y publica los informes de pruebas y lint.
