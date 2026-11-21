# Historial de versiones — Redact Code

## Política de versiones

| Tipo | Formato | Uso | Rama |
|---|---|---|---|
| Principal | `vX.0 - Titulo` | Nuevas capacidades, rediseños o cambios estructurales | Propia: `v2`, `v3`… |
| Incremental | `vX.Y - Titulo` | Mejoras funcionales o ampliaciones importantes | Propia cuando corresponda: `v2.1`, `v2.2`… |
| Parche | `vX.Y.Z - Titulo` | Correcciones, optimizaciones y ajustes pequeños | Dentro de la rama de desarrollo correspondiente; rama propia solo si se justifica |

- `main` contiene la versión estable y funcional. Una versión solo se integra en `main` después de probarse en dispositivo y con autorización expresa.
- Cada versión tiene un título descriptivo que se usa igual en la rama, la etiqueta (tag), la documentación, este historial y los commits cuando corresponde. La rama usa el número (`v2`) y la etiqueta identifica la entrega (`v2.0`, con el título como mensaje).
- No se crean ramas con nombres automáticos ni aleatorios.
- Ninguna versión se fusiona, publica o elimina sin autorización expresa.
- Una versión solo se declara entregada con compilación verificada, pruebas permitidas superadas, errores corregidos, documentación y limitaciones actualizadas, y la rama completa.
- `versionName` de Android usa el mismo número (`2.0`, `2.1`, `2.1.1`); `versionCode` aumenta en cada entrega.

## Versiones

### v2.0 - Renovacion integral del IDE

- **Rama:** `v2` · **Etiqueta:** `v2.0` · **versionName:** `2.0` · **versionCode:** 2
- **Estado:** entregada para pruebas en dispositivo; **no integrada en `main`**.
- **Principales cambios:** espacio de trabajo tipo IDE (barra de actividad, explorador, pestañas, búsqueda global, control de código, panel inferior, paleta de comandos, diseño adaptable); temas claro y oscuro con contraste WCAG 2.2 AA verificado; ajustes en 16 secciones; inicio reorganizado; terminal y consolas JavaScript y Python; control de versiones local, clonación, importación ZIP y sincronización con GitHub; agente de código con revisión de diferencias y permisos; extensiones declarativas con catálogo Open VSX; acceso al panel web de Google Antigravity; CI en GitHub Actions.
- **Verificación:** `:nucleo` 53/53, `:aplicacion` 29/29, lint 0 errores (4 avisos heredados). Detalle en `RESULTADOS_DE_PRUEBAS.md`.
- **Limitaciones:** `LIMITACIONES_CONOCIDAS.md`, secciones 12 y 13.

### v1.0 - Version inicial

- **Rama:** `main` · **versionName:** `1.0.0` · **versionCode:** 1
- **Estado:** estable en `main`.
- Editor de código, validación de cinco lenguajes con explicaciones, ejecución de Python y JavaScript, vista previa web, mentor, retos, proyectos guiados, fragmentos con repaso, GitHub por archivos, seguridad local y copias de seguridad.
