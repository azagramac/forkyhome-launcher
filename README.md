# launcher-aosp

Launcher Android en Kotlin + Jetpack Compose, estilo Trebuchet, con Material You. minSdk 35 (Android 15).

## Publicar con GitHub Actions
1. Sube el contenido de este zip a un repositorio nuevo (rama `main`). Cada push compila un APK debug (pestaña Actions → artefacto).
2. Para publicar una release: `git tag v0.1.0 && git push origin v0.1.0`. El workflow compila y crea la Release con `dev.azagra.launcher-aosp-0.1.0.apk`.
   También puedes lanzarlo a mano desde Actions → Release → Run workflow.
3. **Firma estable (recomendado)**: sin ella cada APK lleva una clave distinta y Android no deja actualizar encima. Genera una vez:
   `keytool -genkeypair -v -keystore release.jks -alias launcher -keyalg RSA -keysize 2048 -validity 10000`
   y guarda en Settings → Secrets and variables → Actions: `KEYSTORE_BASE64` (`base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. No subas el `.jks` al repo.

## Nombre de los APK
- Debug: `dev.azagra.launcher-aosp-{versionName}-{versionCode}.apk`, p. ej. `dev.azagra.launcher-aosp-0.1.0-12.apk` (el código es el nº de ejecución del workflow).
- Release: `dev.azagra.launcher-aosp-{versionName}.apk`, p. ej. `dev.azagra.launcher-aosp-0.1.0.apk`.

## Compilar en local
1. Abre la carpeta en Android Studio (versión reciente) y deja que sincronice Gradle.
   Si no hay `gradlew`: `gradle wrapper --gradle-version 8.14` en la raíz.
2. Ejecuta en un dispositivo/emulador con Android 15+.
3. Pulsa Home y elige Launcher AOSP como "Siempre", o usa Ajustes → "Establecer como launcher predeterminado".

APK: `./gradlew assembleRelease` (firma de debug por defecto; cambia `signingConfig` para publicar).

## Qué incluye
- Escritorio con páginas (1–5), widgets (AppWidgetHost) y apps colocables
- Dock de máximo 5 iconos
- Cajón con buscador; fondo Sistema (Material You) / Blanco / Negro / Oscuro y opacidad
- Tema claro / oscuro / sistema y colores dinámicos
- Packs de iconos (appfilter.xml) e iconos temáticos
- Número de notificaciones en el icono (requiere acceso a notificaciones)
- Selector de fondo de pantalla
- Soporte multiidioma: Inglés (por defecto) y Español (`values/` y `values-es/`)

## Pendiente (v2)
- Arrastrar y soltar para mover iconos y widgets (ahora: quitar y volver a añadir)
- Redimensionar widgets, carpetas, gestos (doble toque, deslizar abajo)
- Máscara/fondo de iconos para packs que no cubren una app
- Baseline Profile (módulo aparte) para arranque y scroll más rápidos
