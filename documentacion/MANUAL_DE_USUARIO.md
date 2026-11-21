# Manual de usuario — Redact Code V1.0

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
