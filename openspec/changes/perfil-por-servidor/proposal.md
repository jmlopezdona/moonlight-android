## Why

El PC gaming de casa expone **tres endpoints de Apollo** que Artemis ve como tres
servidores distintos (tres uuid, tres certificados): el Apollo suelto del usuario
*consola* y un Apollo por asiento de MultiSeat (`padre` en :48100, `hijo` en :48130).
Cada uno aguanta una resolución distinta: la consola va bien a 2160p, pero un asiento
tartamudea a 2160p y hay que dejarlo en 1440p o menos — está medido en
`~/multiseat/CLAUDE.md` («Known Constraints», 2026-08-11), y ahí mismo está la frase
que obliga a resolverlo aquí: *«The client picks the resolution, so this is a
Moonlight/Artemis setting, not a server one»*. Apollo no puede imponerlo desde el PC.

Hoy la resolución es un ajuste **global** del cliente: cambiar de asiento a consola
—o de un asiento a otro— significa entrar en Ajustes y cambiarla a mano cada vez. Y
si te olvidas, el síntoma no es un error: es un juego que se congela 1–2 s cada 15 s.

Artemis ya trae un gestor de perfiles completo (`ProfilesManager`, `SettingsProfile`,
`ProfilesActivity`, y un `SharedPreferences` que superpone el perfil activo sobre las
globales). Lo único que falta es que el perfil correcto se active **solo** al usar un
servidor concreto, en vez de ser un interruptor global y manual.

## What Changes

**Commit 0 — un perfil guarda solo lo que cambia.** Hoy `EditProfileActivity`
guarda `inMemoryPrefs.getAll()`: un perfil nuevo nace de una copia de **todas** las
preferencias globales, así que tapa las ~100 claves para siempre. Para este caso de
uso es el pie de foto equivocado — tres asientos que solo deberían diferir en
resolución quedarían como tres fotografías congeladas del día en que se crearon, y
cualquier ajuste global posterior (modo de ratón, deadzones, idioma) no llegaría
nunca a ellos. Se pasa a guardar únicamente las claves que difieren de las globales,
reutilizando el `diff()` que ya existe en ese mismo fichero y que hoy solo sirve para
pintar en amarillo lo que cambiaste. Las claves ausentes ya caen a las globales en el
overlay, así que la lectura no se toca. **No es BREAKING**: los perfiles completos
que ya existan siguen funcionando igual.

**Asociación servidor → perfil.** Un mapa `pcUuid → perfil` persistido en el mismo
`profiles.json`, con tres estados por servidor: *sin asignar* (por defecto, no cambia
nada), *un perfil concreto*, y *ninguno* (usar las globales). El tercero es necesario:
sin él no hay forma de decir «en este servidor quiero las globales» sin arrastrar el
perfil del servidor anterior.

**Aplicación automática.** El perfil asignado se activa al entrar en la lista de apps
de ese servidor y, de nuevo, en el embudo por el que pasan **todos** los lanzamientos
(`ServerHelper.createStartIntent`, el único sitio del árbol que construye un intent a
`Game`). Cubre así atajos del launcher, deep links y el RESUME del menú contextual.

**UI para asignarlo.** Entrada nueva en el menú contextual del servidor en `PcView`
(disponible también con el PC apagado) y un diálogo de selección única. El perfil
asignado se muestra en la cabecera de ese mismo menú, para poder verlo sin abrir el
diálogo.

**Aviso en la pantalla de Ajustes.** `StreamSettings` lee y escribe las globales a
propósito (`:84` pasa `getDefaultSharedPreferences` explícitamente): con activación
automática se entrará ahí casi siempre con un perfil tapando lo que se ve, sin ninguna
pista de que es así. Se añade esa pista.

**Entorno para compilar.** Este servidor no tiene JDK, SDK ni NDK: `./gradlew` no
arranca, así que hoy este cambio no se puede ni compilar ni pasar sus tests. Se monta
un contenedor calcado del precedente de la casa (`~/iptv/tele`), adaptado a SDK 36 y
NDK 27. Es un paso previo, no un extra.

**Fuera de alcance:** perfil por app, perfil según la ruta (local/remota) del mismo
servidor, y redirigir el `edit()` del overlay al perfil activo. Los tres se pueden
añadir encima de esta base sin rehacerla.

## Capabilities

### New Capabilities
- `perfiles/perfil-parcial`: qué guarda un perfil de ajustes — solo las claves que
  difieren de las preferencias globales, de modo que el resto sigue viniendo de las
  globales y no se congela.
- `perfiles/perfil-por-servidor`: asociación persistente entre un servidor y un
  perfil, su aplicación automática en todas las rutas que llevan a `Game`, la UI para
  asignarla y la limpieza de referencias.
- `herramientas/entorno-de-compilacion`: compilar el APK, pasar los tests unitarios e
  instalar en el aparato de juego sin instalar nada en el servidor de casa.

### Modified Capabilities
<!-- Ninguna: openspec/specs/ está vacío, no hay capabilities previas que modificar. -->

## Impact

**Código propio del fork que se toca** (todo son ficheros que ya lleva Artemis, no
Moonlight upstream, así que el coste de merge es contra ClassicOldSong):

- `profiles/ProfilesManager.java` — mapa de bindings en `ProfilesData`, API nueva,
  limpieza en `delete(UUID)`.
- `EditProfileActivity.java` — una línea en `saveProfile()` (commit 0).
- `utils/ServerHelper.java` — una línea al principio de `createStartIntent`.
- `AppView.java`, `ShortcutTrampoline.java` — enganche + relectura de `prefConfig`.
- `PcView.java` — menú contextual, diálogo, cabecera, `unbindPc` en `removeComputer`.
- `preferences/StreamSettings.java` — aviso de perfil activo.
- `res/layout/activity_stream_settings.xml` — el aviso necesita un sitio: el layout pasa de
  un `RelativeLayout` pelado a un `LinearLayout` con el aviso encima y un contenedor que
  conserva el id `stream_settings`, para no tocar el código que sustituye el fragment.
- `res/values/strings.xml` — strings nuevas (solo inglés; el resto de idiomas cae al
  inglés por defecto y `lint` ya tiene `disable 'MissingTranslation'`).

**Nada del pipeline de streaming.** Todas las lecturas de configuración pasan por
`PreferenceConfiguration.readPreferences()`, que sin argumentos usa el overlay del
perfil activo. Basta con que el perfil correcto esté activo antes de que se cree
`Game`.

**Ficheros nuevos:** `Dockerfile.compilar`, `bin/compilar`, `bin/instalar`, y tests
en `app/src/test/java/com/limelight/profiles/` (ya hay cinco suites Robolectric ahí y
un `ProfileTestHelper` que reutilizar).

**Formato de datos:** `profiles.json` gana un campo. Retrocompatible en ambos
sentidos — un fichero viejo llega con el campo a `null`, y una versión vieja leyendo
un fichero nuevo se limita a ignorarlo.

**Dependencia externa:** Docker en el servidor de casa (ya se usa para `~/iptv/tele`).
