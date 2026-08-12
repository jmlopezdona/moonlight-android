`design.md` ya no tiene preguntas abiertas: el aparato es la Shield TV de siempre, y el
filtro de ABI quedó descartado (probado: con `splits.abi` activo dice `BUILD SUCCESSFUL`
y no produce APK). Se compilan las cuatro ABIs.

Rama sugerida: `feature/perfil-por-servidor`. Mensajes de commit en español.

## 1. Entorno para compilar (va primero: hoy nada de lo demás se puede verificar)

- [x] 1.1 Crear `Dockerfile.compilar` partiendo de `~/iptv/tele/Dockerfile.compilar`:
      `eclipse-temurin:17-jdk-noble`, cmdline-tools, y por `sdkmanager`
      `platform-tools`, `platforms;android-36`, `build-tools;36.0.0` y
      `ndk;27.0.12077973`. Dejar las versiones comentadas como allí, diciendo de qué
      línea de `app/build.gradle` salen, y `chmod -R a+rwX` sobre el SDK.
- [x] 1.2 Crear `herramientas/compilar` calcado de `~/iptv/tele/bin/compilar`: construye la
      imagen si falta, `-u "$(id -u):$(id -g)"`, `HOME`/`GRADLE_USER_HOME`/`GRADLE_OPTS`
      a `/gradle`, `ANDROID_USER_HOME=/gradle/.android`, caché en
      `~/.cache/gradle-artemis`, y tarea por defecto `:app:assembleNonRoot_gameDebug`.
      Conservar el comentario del `debug.keystore`: es lo que evita
      `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- [x] 1.3 Comprobar en `herramientas/compilar`, antes de lanzar Gradle, que
      `app/src/main/jni/moonlight-core/moonlight-common-c` no está vacío, y si lo está
      fallar diciendo que hay que inicializar los submódulos.
- [x] 1.4 Primera compilación completa. Anotar en el `README` del contenedor cuánto
      tarda, y si `-Pandroid.injected.build.abi=arm64-v8a` filtra las ABIs con
      `splits.abi` activo (si no, se compilan las cuatro y se deja constancia).
      `arm64-v8a` es la de la Shield TV: es la única que hace falta instalar.
- [x] 1.5 Comprobar que `herramientas/compilar :app:testNonRoot_gameDebugUnitTest` ejecuta las
      cinco suites Robolectric que ya existen y que un fallo devuelve código de salida
      distinto de cero.
- [x] 1.6 Crear `herramientas/instalar` calcado del de `tele`: `adb` del propio contenedor,
      destino como primer argumento con `192.168.1.33:5555` por defecto (la Shield TV
      del salón), y `ADB_VENDOR_KEYS` a `~/iptv/api/datos/adbkey` —la misma que usa
      `tele`, ya autorizada en esa Shield— con la ruta sobreescribible por variable de
      entorno y montada en **solo lectura**: en esa carpeta vive también
      `iptv-canales.yaml`, que es la credencial del proveedor y no pinta nada dentro de
      un contenedor que compila, así que se monta solo el par `adbkey`/`adbkey.pub`.
      Error claro si no hay APK todavía. No hace falta `--limpio`: el debug es otro
      paquete (`.noirdebug`, «Diana»).
- [ ] 1.7 Instalar «Diana» en la Shield TV con la Artemis de diario instalada, y
      confirmar que aparecen las dos apps, que los emparejamientos y perfiles de la de
      diario están intactos, y que la app de la tele sigue funcionando.
- [x] 1.8 Recompilar sin cambios e instalar encima: debe actualizarse sin pedir
      desinstalar (valida que la firma es estable).
- [ ] 1.9 Emparejar en «Diana» los tres endpoints del PC gaming: la consola y los
      asientos `padre` (:48100) y `hijo` (:48130).
- [x] 1.10 Documentar el entorno donde toque (`CLAUDE.md` del proyecto o un `README`
      junto a los scripts), incluyendo que este servidor no compila sin esto.

## 2. Commit previo — `profiles: leer los decimales del perfil como Number`

Consistencia defensiva, **no** un arreglo de un fallo observable: las tres preferencias
float que existen no están en el árbol de ajustes, así que este `getFloat` hoy es
inalcanzable (ver `design.md`, decisión 1b, con la corrección). Va suelto y primero por eso
mismo: no tiene nada que ver con el cambio de diseño del commit 0. Sin requisito de spec y
sin test, porque no hay camino por el que ejercitarlo.

- [x] 2.1 En `EditProfileActivity.InMemorySharedPreferences.getFloat`, aceptar cualquier
      `Number` en vez de solo `Float`, como ya hacen `getInt` y `getLong` de esa misma
      clase: un perfil releído del JSON trae los números como `Double` de Gson.

## 3. Commit 0 — `profiles: guardar solo lo que cambia respecto a las globales`

- [x] 3.1 En `EditProfileActivity.saveProfile()`, guardar `diff(inMemoryPrefs.getAll(),
      globales)` en vez de `inMemoryPrefs.getAll()`, reutilizando el `diff()` que ya
      existe en el fichero. Ojo al orden: `diff(target, newPrefs)` devuelve las entradas
      de `target` que difieren de `newPrefs`; la llamada que ya existe para el resaltado
      es la contraria y da igual porque allí solo se usa el `keySet`.
- [x] 3.2 Al **editar** un perfil existente, sembrar `inMemoryPrefs` con las globales y
      las opciones del perfil superpuestas, no solo con las opciones. Sin esto el editor
      muestra los defaults del XML para lo que el perfil no fija y guardar re-infla el
      perfil fijando esos defaults por encima de las globales.
- [x] 3.3 Comprobar que el resaltado de `highlightPreferences` pasa a señalar
      exactamente las preferencias que el perfil deja distintas de las globales. Sale
      solo: usa el mismo `diff`, y con la siembra nueva ambos mapas tienen el mismo
      juego de claves, así que el `keySet` es exactamente el de los overrides. Queda
      confirmarlo a ojo en «Diana» (3.6).
- [x] 3.4 Tests: perfil que solo cambia la resolución guarda una sola clave; devolver un
      valor al global lo saca del perfil; guardar sin cambios da un perfil vacío; un
      cambio global posterior se ve a través de un perfil parcial activo; un perfil
      completo heredado sigue aplicándose igual; **reabrir un perfil parcial y guardarlo
      sin tocar nada no lo infla**.
- [ ] 3.5 En «Diana»: crear un perfil cambiando solo la resolución, activarlo, cambiar
      después un ajuste global cualquiera y comprobar que ese ajuste global sí se aplica
      con el perfil activo.
- [ ] 3.6 En «Diana»: reabrir ese perfil parcial y comprobar que el bitrate y los demás
      valores se muestran con los globales, no con los de por defecto, y que el resaltado
      amarillo señala solo la resolución.

## 4. Commit 1 — `profiles: persistir la asignación de perfil por servidor`

- [x] 4.1 Añadir `Map<String, String> pcBindings` a `ProfilesData` y inicializarlo a
      `LinkedHashMap` vacío cuando llegue `null` desde un fichero antiguo.
- [x] 4.2 Escribir `pcBindings` en `save()` junto a los otros dos campos.
- [x] 4.3 Implementar `BINDING_NONE`, `getPcBinding`, `bindPc` y `unbindPc`, con
      `saveIfPossible()` en los dos últimos, igual que `add`/`update`/`delete`.
- [x] 4.4 Implementar `applyProfileForPc` con las seis reglas de `design.md`
      (decisión 3): uuid vacío, asignación ausente, `BINDING_NONE`, asignación huérfana
      que además se limpia, no reactivar lo ya activo, y no lanzar nunca.
- [x] 4.5 En `delete(UUID)`, eliminar todas las asignaciones que apunten al perfil
      borrado.
- [x] 4.6 Tests de los seis casos de `applyProfileForPc`, del borrado de un perfil
      asignado a dos servidores, y de cargar un `profiles.json` sin `pcBindings`.
- [x] 4.7 Test de que lanzar dos veces con el mismo perfil ya activo no reescribe el
      almacén (la regla que evita un `save()` síncrono en cada lanzamiento).

## 5. Commit 2 — `profiles: aplicar el perfil del servidor al usarlo`

- [x] 5.1 `ServerHelper.createStartIntent`: `applyProfileForPc(computer.uuid)` como
      primera línea, **antes** de `readPreferences` (que decide sobre el display
      secundario en esa misma función).
- [x] 5.2 `AppView.onResume()`: aplicar y, si devuelve `true`, releer `prefConfig`.
      Dejarlo antes del refresco del FAB que ya está ahí. **No** engancharlo en el hilo
      del `ServiceConnection`.
- [x] 5.3 `ShortcutTrampoline`: tras `uuidString = hostUUID`, aplicar y releer
      `prefConfig` si cambió — ese `prefConfig` se usa después en las tres llamadas a
      `createStartIntent`.
- [x] 5.4 No enganchar `PcView.doAppList()` (decisión 4d de `design.md`).
- [ ] 5.5 Verificar en «Diana» con los tres endpoints: entrar en `padre` y en la consola
      alternándolos y ver el FAB seguir el cambio; lanzar en cada uno y confirmar en el
      overlay de estadísticas del stream la resolución y el bitrate de su perfil.
- [ ] 5.6 Verificar la ruta del atajo del launcher: atajo directo a un juego de `hijo`
      desde la app cerrada, y confirmar que arranca con el perfil de `hijo`.
- [ ] 5.7 Verificar «Reanudar» desde el menú contextual del servidor.
- [ ] 5.8 Verificar la vuelta desde un stream de otro servidor: lista de apps de `padre`
      abierta, atajo que lanza un juego de `hijo`, volver a `padre` y comprobar que el
      FAB vuelve a mostrar el perfil de `padre` (esto es lo que justifica `onResume`).

## 6. Commit 3 — `pcview: asignar un perfil de ajustes a un servidor`

- [x] 6.1 Añadir `PROFILE_ID = 22` y la entrada de menú en `onCreateContextMenu`, fuera
      de los bloques condicionales de estado, junto a `TEST_NETWORK_ID`/`DELETE_ID`/
      `VIEW_DETAILS_ID`.
- [x] 6.2 Añadir el perfil asignado al `headerTitle` del menú contextual, y no mostrar
      nada cuando el servidor está sin asignar.
- [x] 6.3 Diálogo de selección única con las dos entradas fijas («sin asignar»,
      «ninguno») delante de la lista de perfiles, preseleccionando el estado actual
      según `getPcBinding`.
- [x] 6.4 Al elegir: aplicar, guardar, cerrar el diálogo y confirmar en pantalla qué
      perfil usará ese servidor.
- [x] 6.5 Si no hay ningún perfil creado, avisar y llevar a la pantalla de gestión de
      perfiles en vez de abrir el diálogo.
- [x] 6.6 `case PROFILE_ID` en `onContextItemSelected`.
- [x] 6.7 `unbindPc(details.uuid)` en `removeComputer`, donde ya se borran los assets y
      las apps ocultas. No hace falta un `save()` extra: `unbindPc` ya guarda.
- [x] 6.8 Strings nuevas solo en `res/values/strings.xml`: entrada de menú, título del
      diálogo con el nombre del servidor, «sin asignar», «ninguno», y las confirmaciones.
      Reutilizar `profile_manager_no_profiles_yet`, que ya existe. El resto de idiomas
      cae al inglés y `lint` ya tiene `disable 'MissingTranslation'`.
- [ ] 6.9 Verificar en «Diana»: asignar con un servidor apagado; los tres estados por
      servidor; que la cabecera del menú refleja lo asignado; y que borrar un perfil
      asignado deja el servidor sin asignar sin romper nada al entrar.
- [ ] 6.10 Verificar todo lo anterior **con el mando de la Shield**, no dándolo por
      bueno: abrir el menú contextual con pulsación larga del centro del d-pad, recorrer
      el diálogo y elegir, y comprobar que la cabecera se lee entera en la tele.

## 7. Commit 4 — `settings: avisar de que un perfil está tapando los ajustes globales`

- [x] 7.1 Aviso en `StreamSettings` cuando hay perfil activo, diciendo que lo que se ve
      y se edita son los globales y qué perfil los tapa. Sin perfil activo, nada.
- [x] 7.2 Comprobar que no aparece con el perfil desactivado ni con un servidor asignado
      a «ninguno».

## 8. Repaso final

- [x] 8.1 Pasar los tests unitarios en el contenedor y comprobar que **no hay fallos
      nuevos** respecto a la línea base de 5 que ya trae el upstream (`design.md`,
      Risks): `LayoutInflationTest`, `SimpleStartupTest.testApplicationOnCreate`,
      `StartupTest.testApplicationStartup` y los dos de
      `ProfilesNavigationTest.clickingProfileButton_*`. «Pasa la suite» no es un criterio
      alcanzable hoy.
- [ ] 8.2 Comprobar que un servidor sin asignar se comporta exactamente como antes del
      cambio: perfil activo global, cambios manuales, nada raro.
- [ ] 8.3 Matar la app durante un stream y reabrirla: `profiles.json` coherente, sin
      asignaciones rotas ni perfil activo inconsistente.
- [ ] 8.4 Simular el fallo de carga del almacén de perfiles y comprobar que se puede
      lanzar un juego igual, sin errores en pantalla.
- [ ] 8.5 Borrar un servidor y volver a emparejarlo: sin asignación heredada.
- [ ] 8.6 Instalar sobre una «Diana» anterior con un `profiles.json` sin `pcBindings` y
      confirmar que carga sin error.
- [x] 8.7 Revisar el diff completo contra upstream: 9 ficheros tocados (7 java, el layout
      de ajustes y `strings.xml`), +318/-13. Comprobado que lo único eliminado son las 5
      líneas que se cambian a propósito más el layout de 8 líneas que se reestructura: nada
      reordenado, ningún estilo ajeno reescrito. El commit 0 se sostiene solo.
- [ ] 8.8 Jugar de verdad una sesión en un asiento y otra en la consola sin tocar los
      ajustes en medio, que es la prueba que de verdad importa.
