# Historial de versiones — Redact Code

## Política de versiones

| Tipo | Formato | Uso | Rama |
|---|---|---|---|
| Principal | `vX.0 - Titulo` | Nuevas capacidades, rediseños o cambios estructurales | Propia: `v2`, `v3`… |
| Incremental | `vX.Y - Titulo` | Mejoras funcionales o ampliaciones importantes | Propia cuando corresponda: `v2.1`, `v2.2`… |
| Parche | `vX.Y.Z - Titulo` | Correcciones, optimizaciones y ajustes pequeños | Dentro de la rama de desarrollo correspondiente; rama propia solo si se justifica |

- `main` conserva la v1.0. El proyecto solo avanza: cada versión continúa desde la última.
- Las versiones publicadas se etiquetan en la rama `publicacion`, que contiene solo el producto, con un commit por versión.
- Cada versión tiene un título descriptivo que se usa igual en la rama, la etiqueta (tag), la documentación, este historial y los commits cuando corresponde. La rama usa el número (`v2.5`, `v3`) y la etiqueta identifica la versión publicada (`v2.5.1`, con el título como mensaje).
- No se crean ramas con nombres automáticos ni aleatorios.
- Ninguna versión se fusiona, publica o elimina sin autorización expresa.
- Una versión solo se declara entregada con compilación verificada, pruebas permitidas superadas, errores corregidos, documentación y limitaciones actualizadas, y la rama completa.
- `versionName` de Android usa el mismo número (`2.0`, `2.1`, `2.1.1`); `versionCode` aumenta en cada entrega.

## Versiones

### v2.5.1 - Correcciones del diagnostico de la v2.5

- **Rama:** `v2.5` (parche) · **Etiqueta:** `v2.5.1` (rama `publicacion`) · **versionName:** `2.5.1` · **versionCode:** 4
- **Estado:** cerrada el 2026-11-16. Validada en el emulador (todas las correcciones y regresión completa) y en un Xiaomi Redmi Note 14 (terminal interactiva, huella, PIN, ícono y fluidez sin cuadros lentos).
- **Principales cambios:** la terminal ya no cierra la app y tiene respaldo si la biblioteca nativa no carga; el PIN no se puede quitar ni cambiar sin conocerlo y la espera tras 5 fallos se mantiene al reabrir; arranque más rápido; avisos del depurador visibles; `http://` solo para `localhost`, `127.0.0.1` y `10.0.2.2`; ícono sin bordes negros; licencia de Termux; textos de Ajustes al día; accesibilidad del depurador, chips, botón «Nuevo proyecto» y pestañas en tableta; los comandos de la terminal ya no muestran un aviso falso; el diálogo de cambio de PIN se titula «Cambiar PIN».

### v2.5 - Terminal profesional, entornos de ejecucion e identidad visual

- **Rama:** `v2.5` (creada desde `v2.0-Renovacion-integral-del-IDE`) · **Etiqueta:** ninguna (su versión corregida es la v2.5.1) · **versionName:** `2.5` · **versionCode:** 3
- **Estado:** implementada y verificada en JVM, Robolectric y CPython de escritorio; **sin probar en dispositivo**; **no integrada en `main`** ni publicada.
- **Principales cambios:** terminal con PTY real (Termux `terminal-emulator` y `terminal-view` 0.118.1, Apache-2.0) con sesiones, división en dos paneles, teclas extra y paleta ANSI contrastada, con respaldo no interactivo; puente de comandos `python`, `node`, `git`, `pip`, `npm`, `redact`; `git` local y remoto (clone, pull, push) sobre el control de versiones y el sincronizador de GitHub existentes; `pip install` de paquetes Python puros y `npm install` desde el registro (sin scripts); pestaña Depuración con ejecución interactiva (`input()`), depurador de Python con puntos de interrupción, pasos, pila, variables y evaluación; comandos propuestos por el agente con clasificación de riesgo, aprobación explícita, censura de secretos y registro visible en Ajustes; logotipo derivado del original (baldosa, ícono adaptativo, capa monocromática, pantalla de inicio) con arranque coherente con el tema y animación de entrada.
- **No implementado:** `input()` a través del PTY (la entrada interactiva usa la pestaña Depuración), icono de punto de interrupción en el margen del editor, depuración de JavaScript y del WebView, ejecución de Node.js.
- **Verificación y limitaciones:** `RESULTADOS_DE_PRUEBAS.md` (sección V2.5) y `LIMITACIONES_CONOCIDAS.md` (sección 14).

### v2.0 - Renovacion integral del IDE

- **Rama:** `v2.0-Renovacion-integral-del-IDE` · **Etiqueta:** ninguna · **versionName:** `2.0` · **versionCode:** 2
- **Estado:** entregada para pruebas en dispositivo; **no integrada en `main`**.
- **Principales cambios:** espacio de trabajo tipo IDE (barra de actividad, explorador, pestañas, búsqueda global, control de código, panel inferior, paleta de comandos, diseño adaptable); temas claro y oscuro con contraste WCAG 2.2 AA verificado; ajustes en 16 secciones; inicio reorganizado; terminal y consolas JavaScript y Python; control de versiones local, clonación, importación ZIP y sincronización con GitHub; agente de código con revisión de diferencias y permisos; extensiones declarativas con catálogo Open VSX; acceso al panel web de Google Antigravity; CI en GitHub Actions.
- **Verificación:** `:nucleo` 53/53, `:aplicacion` 29/29, lint 0 errores (4 avisos heredados). Detalle en `RESULTADOS_DE_PRUEBAS.md`.
- **Limitaciones:** `LIMITACIONES_CONOCIDAS.md`, secciones 12 y 13.

### v1.0 - Version inicial

- **Rama:** `main` · **versionName:** `1.0.0` · **versionCode:** 1
- **Estado:** estable en `main`.
- Editor de código, validación de cinco lenguajes con explicaciones, ejecución de Python y JavaScript, vista previa web, mentor, retos, proyectos guiados, fragmentos con repaso, GitHub por archivos, seguridad local y copias de seguridad.
