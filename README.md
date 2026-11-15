# AOSP Launcher

Launcher Android moderno, ligero y fluido escrito en **Kotlin + Jetpack Compose**, inspirado en **LineageOS Trebuchet** y diseñado con **Material 3 / Material You**.

- **Paquete**: `dev.azagra.launcher_aosp`
- **SDK**: `minSdk 35` (Android 15) · `targetSdk 36` (Android 16)
- **Licencia**: GPL-3.0

---

## ✨ Características actuales

### 🏠 Pantalla de inicio (Escritorio) y Dock
- **Páginas y cuadrícula configurables**: de `1` a `5` páginas, de `4` a `6` columnas y de `4` a `7` filas.
- **Arrastrar y soltar en tiempo real (estilo Trebuchet)**:
  - Mantén pulsado cualquier icono en el **Cajón de aplicaciones**, **Escritorio**, **Dock** o dentro de una **Carpeta** y arrástralo directamente a la celda o posición del Dock donde quieras soltarlo.
  - **Cambio automático de página** al acercar el icono arrastrado a los bordes laterales de la pantalla.
  - Indicadores visuales Material You de celda destino y creación/fusión de carpetas.
- **Carpetas estilo Trebuchet**:
  - Superposición flotante con cuadrícula de vista previa (`2×2`), edición del nombre en su parte inferior y posibilidad de arrastrar iconos fuera de la carpeta hacia el escritorio o dock.
- **Widgets nativos (`AppWidgetHost`)**:
  - Añade, configura y **redimensiona** (`spanX` × `spanY`) widgets directamente en el escritorio.
- **Dock inferior**: hasta 5 accesos directos fijos con reordenación mediante arrastrar y soltar.
- **Opciones adicionales de escritorio**:
  - Mostrar/ocultar etiquetas de iconos en el escritorio.
  - **Bloquear escritorio** para evitar cambios accidentales.
  - Añadir automáticamente los iconos de nuevas aplicaciones instaladas.
  - Gesto de **deslizar hacia abajo** en cualquier parte del escritorio para desplegar el panel de notificaciones.

### 📱 Cajón de aplicaciones fluido
- **Física de muelle (`spring`) y paralaje**: apertura y cierre suaves siguiendo el dedo con histéresis direccional, curva cúbica y transición de escala/opacidad sin cortes bruscos.
- **Buscador instantáneo** integrado en barra tipo píldora Material 3.
- **Índice rápido alfabético (`A–Z`)** en el lateral derecho para saltar a cualquier letra deslizando el dedo.
- **Personalización visual**:
  - Fondo seleccionable: **Sistema (Material You)**, **Claro**, **Oscuro** o **Negro AMOLED**.
  - Control deslizante de **opacidad del fondo** (`50%` – `100%`).
  - Columnas configurables (`4` – `6`) y opción para mostrar u ocultar etiquetas.

### 🎨 Iconos, Formas y Paquetes de Iconos
- **Tamaño de iconos adaptable e independiente**:
  - Escalado base automático según el número de columnas (`60dp` en 4 columnas, `54dp` en 5 columnas, `48dp` en 6 columnas) con límites seguros para evitar solapamientos.
  - Controles deslizantes independientes (**80% – 120%**) para ajustar el tamaño de los iconos del **Escritorio** y del **Cajón de aplicaciones**.
- **6 formas de icono (`IconShape`)**: Sistema, Círculo, Cuadrado redondeado (*Squircle*), Gota (*Teardrop*), Cilindro (*Cylinder*) y Cuadrado.
- **Soporte avanzado de Paquetes de Iconos**:
  - Compatible con paquetes de iconos estándar (ADW, Nova, Lawnchair, Action, Apex, Go, Smart Launcher).
  - Lectura de `res/xml/appfilter.xml` y `assets/appfilter.xml`.
  - Normalización de `ComponentName`, fallback por paquete y **calendario dinámico (`<calendar>`)** según el día del mes.
  - **Enmascarado automático** (`<iconback>`, `<iconmask>`, `<iconupon>`, `<scale>`) para tematizar de forma uniforme las aplicaciones no incluidas en el pack.
- **Iconos temáticos (Monocromáticos Material You)** para escritorio y dock.
- **Globos de notificación** con contador numérico sobre los iconos (mediante `NotificationListenerService`).
- **App Shortcuts (accesos directos de aplicación)** al mantener pulsado un icono, junto con acceso rápido a información de la app y desinstalación.

### 🔒 Privacidad (Apps Ocultas y Protegidas)
- **Ocultar aplicaciones** del cajón de aplicaciones.
- **Proteger aplicaciones** con **huella dactilar / biometría o PIN/patrón del dispositivo** antes de permitir abrirlas o gestionar su visibilidad.

### 🌐 Idiomas
- **Inglés** (`values/`) por defecto y **Español** (`values-es/`).
