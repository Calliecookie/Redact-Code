# Manual de usuario — Redact Code v2.0 - Renovacion integral del IDE

> Las secciones 1 a 15 describen V1.0. En V2.0 el editor pasó a ser un **espacio de trabajo** (sección 16), Ajustes se reorganizó en 16 secciones (sección 17) y Más funciones están en las secciones 18 a 22. Todo lo descrito en V2.0 está pendiente de prueba en dispositivo; consulta `LIMITACIONES_CONOCIDAS.md`.

*Programa, aprende y crea desde cualquier lugar.*

## 1. Primer inicio

1. Recorre la presentación o pulsa **Omitir**.
2. Crea tu perfil: nombre, experiencia (Principiante, Intermedio, Avanzado), lenguajes que te interesan y tema (oscuro, claro o según el sistema).
3. Opcional: protege la aplicación con un **PIN** de 4 a 8 dígitos y, si tu teléfono lo permite, con **biometría**.
4. Opcional: activa el **recordatorio diario de repaso**.

No necesitas cuenta ni conexión. Todo se guarda en tu teléfono.

## 2. Inicio

Muestra el saludo, el último archivo abierto (toca **Continuar**), accesos rápidos al editor, al mentor y a los retos, tu racha y minutos de práctica de hoy, el gráfico de la semana, retos recomendados según tus dificultades, repasos pendientes y proyectos recientes.

## 3. Proyectos y explorador

- **Proyectos › Nuevo proyecto**: elige nombre, descripción y plantilla (Python, sitio web, JavaScript o vacío).
- Mantén presionado o usa ⋮ para **renombrar** o **eliminar** un proyecto.
- Dentro de un proyecto, el explorador permite **crear archivos y carpetas**, **renombrar**, **mover**, **eliminar**, **buscar** en nombres y contenido y **ordenar** por nombre, tipo o fecha.
- Extensiones admitidas: `.py`, `.js`, `.html`, `.css`, `.json`, `.txt`.

## 4. Editor de código

- **Pestañas**: abre varios archivos; el punto de color indica cambios sin guardar.
- **Barra superior**: deshacer, rehacer, guardar, ejecutar y el menú ⋮ (buscar y reemplazar, archivos del proyecto, nuevo archivo, problemas, revisión de calidad, mentor, guardar como fragmento, publicar en GitHub).
- **Barra de símbolos**: sobre el teclado tienes `{ } ( ) [ ] < > ; : = " ' . , _` y **Tab**, además de símbolos propios de cada lenguaje.
- **Escritura asistida**: cierre automático de paréntesis, llaves, corchetes y comillas; sangría automática después de `:` (Python), `{` o etiquetas HTML; autocompletado de palabras clave e identificadores.
- **Zoom**: pellizca con dos dedos para cambiar el tamaño del texto.
- **Barra de estado**: línea y columna, lenguaje y número de errores y advertencias. Tócala para abrir **Problemas**.

## 5. Problemas y explicaciones

El código se valida automáticamente 400 ms después de que dejas de escribir. Cada problema muestra lenguaje, línea, columna, tipo, código, título, explicación en español, causa probable, ejemplo incorrecto, ejemplo corregido y recomendación. Cuando hay una corrección segura disponible aparece **Aplicar corrección**; el archivo se vuelve a validar al instante. **Preguntar al mentor** abre una explicación adaptada a tu nivel.

## 6. Ejecutar

- **Python**: pulsa ▶. Si tu programa usa `input()`, escribe los datos en **Entrada** (una línea por cada `input()`) y vuelve a ejecutar. Se muestran salida, errores, excepción con la línea y duración. El botón ■ detiene la ejecución.
- **JavaScript**: si el archivo lo usa una página HTML, se abre la vista previa; si no, se ejecuta como script y se muestra la salida de `console.log`. En el menú ⋮ tienes **Ejecutar como script**.
- **HTML y CSS**: ▶ guarda los archivos y abre la **vista previa** con recarga y **consola**.

## 7. Revisión de calidad

Menú ⋮ › **Revisar calidad del código**. Distingue errores confirmados (sintaxis), advertencias, sugerencias y posibles riesgos de seguridad (por ejemplo `eval` o `innerHTML`). Es un análisis automático basado en reglas: no reemplaza una auditoría de seguridad profesional.

## 8. Mentor de programación

Desde **Más › Mentor** o desde el editor. Tareas disponibles: explicar código, explicar línea por línea, explicar un error, sugerir una corrección, explicar un concepto, proponer un ejercicio, recomendar mejoras e identificar malas prácticas. Las explicaciones se adaptan a tu nivel, a tus errores frecuentes y a los conceptos que te cuestan.

Sin conexión, el mentor usa explicaciones locales y análisis del código. Si quieres respuestas generadas por IA, en **Ajustes › Mentor e inteligencia artificial** puedes guardar tu propia clave de API de Anthropic y dar tu consentimiento. Solo se envía el código que consultes y solo cuando pulses **Preguntar al mentor**. El uso de la API tiene costo según la tarifa de Anthropic.

## 9. Práctica y retos

**Práctica** reúne retos en Python y JavaScript por categoría (Fundamentos, Variables, Condicionales, Bucles, Funciones, Listas, Cadenas, Objetos, Algoritmos, Estructuras de datos), dificultad y lenguaje. En cada reto: lee el **Enunciado** y ejemplos, escribe en **Código**, pulsa ▶ y revisa **Resultado** (pruebas visibles y ocultas). Tienes pistas progresivas e historial de intentos. El ícono de carga permite **importar retos** en formato JSON (ver `INFORME_TECNICO_FINAL.md`, sección 6).

## 10. Aprender creando

**Aprender** contiene 8 proyectos guiados (calculadora, lista de tareas, página personal, conversor de monedas, aplicación de notas, juego de adivinanza, gestor de gastos y formulario interactivo). Pulsa **Comenzar proyecto**, abre el código de cada paso y pulsa **Verificar**: la aplicación ejecuta pruebas reales sobre tus archivos y avanza al siguiente paso cuando todo pasa.

## 11. Progreso

**Más › Progreso** muestra racha, mejor racha, retos resueltos, minutos de la semana, objetivos personales editables, recomendaciones, dominio por concepto, mapa de errores frecuentes, lenguajes más usados y evolución semanal. Todo se calcula con tu actividad real; si aún no hay datos, la pantalla lo indica.

## 12. Fragmentos y repaso

Guarda código desde el editor (⋮ › **Guardar como fragmento**) o crea fragmentos en **Más › Biblioteca de fragmentos**. Puedes buscar, filtrar por lenguaje o etiqueta, copiar, editar, eliminar y abrir en el editor. Los fragmentos se programan para repaso con el algoritmo **SM-2**: al repasar, intenta recordar el código, muéstralo y califica (Olvidé, Difícil, Bien, Fácil).

## 13. GitHub

1. Crea un *fine-grained personal access token* en GitHub con acceso a tus repositorios y permiso **Contents: Read and write**.
2. **Más › GitHub**, pega el token y pulsa **Conectar**. Se guarda cifrado.
3. Elige repositorio y rama, navega por carpetas, marca archivos y pulsa **Descargar** para crear un proyecto local.
4. Para publicar, en el editor usa ⋮ › **Publicar en GitHub**, revisa repositorio, rama y mensaje, y pulsa **Crear commit**. Si el archivo cambió en GitHub desde tu descarga, la aplicación te avisará para no sobrescribir cambios ajenos.

## 14. Ajustes

Perfil, apariencia (tema, tamaño de texto, animaciones, alto contraste), editor (tamaño y tipografía, sangría, cierre automático, ajuste de línea, validación en vivo, extensiones, límite de ejecución), mentor e IA, seguridad (PIN y biometría), recordatorios, GitHub, **copias de seguridad** (exportar e importar un `.zip` con proyectos y fragmentos), información de versión y licencias, y **Borrar todos los datos**.

## 15. Si olvidas tu PIN

Por seguridad no existe recuperación del PIN. Debes borrar los datos de la aplicación desde **Ajustes de Android › Aplicaciones › Redact Code › Almacenamiento**. Exporta copias de seguridad con regularidad.

## 16. El espacio de trabajo (V2.0)

Al abrir un proyecto se abre el espacio de trabajo.

**En teléfono** el editor ocupa toda la pantalla. Toca el ícono ☰ de la barra superior para abrir el **panel lateral** (se cierra al tocar fuera). Arriba del panel están los íconos de la **barra de actividad**: Explorador, Buscar, Control de código, Agente y Extensiones. Deshacer y rehacer están junto a la barra de símbolos.

**En tableta** (o pantalla ancha) la barra de actividad es vertical, el panel lateral se queda fijo y la navegación principal usa un riel lateral.

- **Pestañas**: cada archivo abierto tiene su pestaña; el punto indica cambios sin guardar. Sobre el editor se muestra la ruta del archivo y la función o clase actual (se puede ocultar en Ajustes).
- **Explorador**: crea archivos y carpetas, renombra, mueve, elimina y compara un archivo con el activo desde el menú ⋮ de cada fila. Admite archivos de texto comunes (`.py`, `.js`, `.html`, `.css`, `.json`, `.txt`, `.md`, `.yml`, `.kt`, `.sql`, etc.). Los archivos que no son de texto no se pueden editar.
- **Panel inferior**: pestañas Problemas, Salida, Terminal, Consola, Revisión y Vista previa. Los botones del panel cambian su altura o lo maximizan.
- **Barra de estado**: línea y columna, lenguaje, motor de validación y conteo de errores y advertencias; tócala para abrir Problemas.

### Paleta de comandos

Toca la lupa de la barra superior. Cuatro modos: **Archivos** (ir a archivo), **Comandos** (también con `>`), **Símbolos** (`@`) e **Ir a línea** (`:`). La búsqueda tolera letras salteadas (por ejemplo `fmt` encuentra «Formatear documento»). Desde ahí accedes a guardar, deshacer, duplicar o mover línea, alternar comentario, formatear, comparar con la versión guardada, ir a definición, buscar referencias, ejecutar, mostrar paneles y abrir ajustes.

### Buscar en el proyecto

Panel **Buscar**: distingue mayúsculas, palabra completa, expresiones regulares y filtros (`*.js`, `src/**`). Los resultados se agrupan por archivo; tócalos para saltar a la línea. **Reemplazar** actúa en un archivo o en todos (con confirmación) y guarda los cambios en disco.

### Comparar archivos

Menú ⋮ de un archivo del explorador › **Comparar con el archivo activo**, o el comando **Comparar con la versión guardada**.

## 17. Ajustes (V2.0)

Las 16 secciones son: General, Apariencia, Editor, Tipografía, Archivos, Terminal, Lenguajes, Inteligencia artificial, Agentes, Extensiones, Git y GitHub, Notificaciones, Privacidad y seguridad, Accesibilidad, Avanzado y Acerca de. Cada opción se guarda al instante. Algunos puntos útiles:

- **Apariencia**: tema oscuro (predeterminado), claro o del sistema; alto contraste; mostrar u ocultar ruta, barra de estado y barra de símbolos.
- **Tipografía**: la escala de la interfaz se suma al tamaño de fuente de Android. El código usa JetBrains Mono (o la monoespaciada del sistema).
- **Archivos**: guardado automático (desactivado, al cambiar de pestaña o tras una pausa), confirmar antes de eliminar y exclusiones de búsqueda.
- **Accesibilidad**: objetivos táctiles grandes, animaciones y alto contraste.
- **Avanzado**: **Restablecer ajustes** no toca proyectos, PIN ni claves; **Borrar todos los datos** sí lo borra todo y no se puede deshacer.

## 18. Terminal y consolas (V2.0)

- **Terminal** (panel inferior): ejecuta comandos del shell de Android en la carpeta del proyecto. Funcionan `cd`, `pwd`, `clear`, `history`, `help`, la tecla **Tab** completa nombres, y `python archivo.py` o `node archivo.js` ejecutan con los motores integrados. No es interactiva y **no es un entorno aislado** (ver limitaciones).
- **Consola**: elige JavaScript o Python y escribe expresiones. En Python las variables se conservan entre entradas; **Reiniciar sesión** las borra.

## 19. GitHub: ZIP, clonación y sincronización (V2.0)

- **Importar ZIP**: en Proyectos, toca el ícono de importar y elige un `.zip`. Se crea un proyecto nuevo y no se tocan los existentes.
- **Clonar**: Más › GitHub, elige repositorio y rama y toca **Clonar el repositorio completo**.
- **Control de código** (panel lateral): inicializa el control de versiones, revisa cambios, confirma (todos o los marcados), crea ramas, cambia de rama y revisa el historial y las diferencias de cada commit. Para sincronizar, **vincula** el proyecto con `dueño/repositorio`, usa **Traer** para bajar cambios y **Publicar** para subir un solo commit con todos los archivos modificados (opcionalmente a una rama nueva). Si un archivo cambió en ambos lados, Traer informa el conflicto y no aplica nada.

## 20. Agente de código (V2.0)

Primero activa el consentimiento y configura un proveedor en Ajustes › Inteligencia artificial, y revisa los permisos en Ajustes › Agentes. En el panel **Agente** escribe qué quieres cambiar. El agente propone cambios en uno o varios archivos; para cada uno puedes **aceptarlo o rechazarlo**, abrir sus diferencias y aceptar solo algunos bloques. Nada se guarda hasta que pulsas **Aplicar**. Eliminar archivos está desactivado por defecto. Se envía contenido de tu proyecto al proveedor y su uso puede costar dinero.

## 21. Extensiones (V2.0)

Panel **Extensiones** o Ajustes › Extensiones. Instala desde un archivo (`.json` propio o `.vsix`) o desde el **catálogo de Open VSX**. Solo se usan **fragmentos y temas de color**; el código de las extensiones nunca se ejecuta y lo no compatible se informa. Los fragmentos aparecen en el autocompletado al escribir su prefijo; los temas se eligen en el mismo panel. Puedes activar, desactivar o desinstalar cada extensión. Revisa la licencia de cada extensión antes de instalarla.

## 22. Panel de Google Antigravity (V2.0)

Si usas Google Antigravity en tu PC, activa allí el control remoto (en la app: Ajustes › App; en la CLI: `/remote-control` o `agy --remote-control`). En Redact Code ve a Ajustes › Agentes y toca **Abrir el panel en el navegador**, o usa el comando de la paleta. Inicia sesión con la misma cuenta de Google que en la PC. Si tu PC muestra otra dirección, pégala en **Dirección del panel**. Redact Code solo abre el panel: no envía tus proyectos a Antigravity.

## 23. Qué no hace V2.0

No tiene multicursor ni pliegues de código, no intercambia datos con Google Antigravity (solo abre su panel web), no resuelve conflictos de GitHub dentro de los archivos ni fusiona ramas. Ver `LIMITACIONES_CONOCIDAS.md`.
