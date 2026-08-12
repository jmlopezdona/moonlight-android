## Context

Ver `proposal.md` — *Why* para la motivación. Aquí solo el estado del código que
condiciona el enfoque. Números de línea del commit `3397ec7` (`versionName "20.2.6"`):
orientación, no ancla. Todas las rutas son relativas a `app/src/main/java/`.

**Artemis ya tiene el gestor de perfiles completo.** Este cambio no inventa perfiles;
añade la asociación servidor → perfil y su aplicación automática.

| Pieza | Fichero | Qué hace |
|---|---|---|
| `SettingsProfile` | `com/limelight/profiles/SettingsProfile.java` | `uuid`, `name`, `createdUtc`, `modifiedUtc`, `options: Map<String,Object>` |
| `ProfilesManager` | `com/limelight/profiles/ProfilesManager.java` | Singleton. Carga/guarda `files/profiles/profiles.json` con Gson, mantiene `activeProfileId`, notifica listeners |
| Overlay de prefs | `ProfilesManager.getOverlayingSharedPreferences()` :200 | Un `SharedPreferences` que superpone las `options` del perfil activo sobre las globales |
| Consumo | `preferences/PreferenceConfiguration.java:712-719` | **Todas** las lecturas de configuración; sin `SharedPreferences` explícito usa el overlay |
| UI de gestión | `ProfilesActivity`, `EditProfileActivity`, `profiles/ProfilesAdapter` | Crear/editar/borrar y activar uno a mano |
| Arranque | `ArtemisApplication.onCreate()` | `ProfilesManager.getInstance().load(this)` |
| Indicador | FAB `R.id.profilesButton` | `PcView.refreshProfileButton()` :345-359 y `AppView.onResume()` :400-412 |

**Consecuencia clave:** como todo pasa por `readPreferences()`, basta con que el perfil
correcto esté activo **antes** de que se cree `Game` (que lee sus prefs en
`Game.java:354`). No hay que tocar nada del pipeline de streaming.

**El mapa real de escrituras**, que es lo que hace falta entender para no romper nada:

```
      escribe ────────▶┌──────────────────────────────┐◀──────── escribe
  StreamSettings :84   │  PREFS GLOBALES (SharedPrefs) │   overlay.edit()
  (pasa getDefault-    └──────────────────────────────┘   · menú in-stream (Game:4128)
   SharedPreferences                 │ base               · migraciones de readPreferences
   A PROPÓSITO: es el                ▼
   editor de globales)   ┌───────────────────────┐
                         │ OverlaySharedPrefs    │◀── options del perfil activo
                         │ patch → base fallback │
                         └───────────────────────┘
                                     │ lee
                                     ▼
                        PreferenceConfiguration ──▶ Game

  EditProfileActivity ──▶ escribe en el perfil (hoy: snapshot de TODAS las globales, :74)
```

`OverlaySharedPreferences.edit()` devuelve `base.edit()` (:251): el overlay es de solo
lectura hacia el perfil. Eso NO es un descuido en `StreamSettings` —`:84` pasa
`PreferenceManager.getDefaultSharedPreferences(this)` explícitamente—, es que la
pantalla de ajustes es, por diseño de upstream, el editor de las globales. Los perfiles
se editan solo en `EditProfileActivity`.

**Restricciones del entorno.** El servidor de casa no tiene JDK, SDK ni NDK. Cinco
suites Robolectric ya existen en `app/src/test/java/com/limelight/profiles/` con un
`ProfileTestHelper`; son la única verificación automática posible, y hoy tampoco se
pueden ejecutar aquí.

## Goals / Non-Goals

**Goals**

- Que el perfil correcto esté activo antes de cualquier lectura de configuración, por
  todas las rutas, sin tocar el pipeline de streaming.
- Que el cambio sea pequeño y localizado: es un fork y cada línea en un fichero muy
  transitado se paga en el siguiente merge con upstream.
- Que quede en condiciones de mandarse como PR a `ClassicOldSong/moonlight-android`:
  encaja con su diseño de perfiles en vez de sustituirlo.
- Que un usuario que no use la función no note ninguna diferencia.

**Non-Goals (a nivel de diseño; el alcance está en la propuesta)**

- No se cambia el modelo de precedencia de preferencias: sigue siendo un único nivel
  (perfil activo sobre globales). Nada de perfil-de-app-sobre-perfil-de-servidor.
- No se toca la lectura del overlay ni `PreferenceConfiguration`.
- No se añade estado nuevo fuera de `profiles.json`.
- No se persigue reproducibilidad de la compilación: el contenedor es para poder
  compilar y probar en casa, no un artefacto de release.

### Por qué NO las tres ideas que primero se le ocurren a uno

Rescatado del análisis inicial, porque son las que se van a volver a proponer:

- **Perfil por ruta (local vs remota) del mismo servidor.** Es la que más apetece —bajar
  calidad al salir de casa— y la que peor sale con lo que hay: `computer.activeAddress` se
  resuelve por sondeo y no siempre refleja la ruta que acabará usando el stream, así que
  aplicaría el perfil equivocado **justo en el caso que importa**. Pide antes un indicador
  fiable de «estoy en la LAN», que no existe hoy.
- **Perfil por app.** Multiplica la UI (una entrada en el menú de cada juego) y el modelo
  (dos niveles de precedencia). Se puede montar encima de esta base sin rehacerla, así que
  no hay prisa.
- **Activación temporal, restaurada al salir del stream.** `Game` puede morir sin pasar por
  `onDestroy` y dejaría el perfil activo en un estado incoherente. La activación
  persistente es más simple y además se ve: el indicador de perfil la muestra. Está como
  requisito en el spec, con un escenario para el proceso muerto a media partida.

## Decisions

### 1. El perfil guarda el diff, no la fotografía (commit 0)

`EditProfileActivity.saveProfile()` :121 hace
`new HashMap<>(inMemoryPrefs.getAll())`, y un perfil nuevo nace de
`getDefaultSharedPreferences(this).getAll()` :74. Un perfil es hoy una copia completa
de las ~100 preferencias. En ese mismo fichero ya existe `diff(target, newPrefs)` :232,
pero solo se usa para pintar en amarillo lo que cambió (`highlightPreferences` :302).

**Decisión:** guardar el diff contra las globales, reutilizando ese `diff()`.

Por qué va **primero**, antes de todo lo demás: cambia qué *significa* un perfil. Con
activación automática se estaría siempre dentro de un perfil, y por tanto siempre
dentro de una fotografía congelada — cualquier ajuste global posterior dejaría de
llegar, en silencio, para siempre. Hacerlo después obligaría a reeditar a mano los
perfiles ya creados.

Es seguro por la asimetría del overlay: `patch.containsKey(key)` → si la clave no está
en el perfil, cae a `base`. La lectura no se toca y los perfiles completos ya guardados
siguen funcionando igual.

**Pero una línea en `saveProfile()` no basta, y sola deja las cosas peor que hoy.**
Encontrado al implementar: `InMemorySharedPreferences` (:334) es un mapa plano **sin
fallback** —si la clave no está, devuelve el `defValue` del XML— y al editar un perfil
existente se siembra solo con las opciones del perfil (:65):

```java
inMemoryPrefs = new InMemorySharedPreferences(currentProfile.getOptions());
```

Hoy es inocuo porque un perfil tiene todas las claves. Con perfiles parciales, reabrir
un perfil mostraría los defaults del XML para todo lo que no fija —no las globales, que
son lo que de verdad se va a usar— y al guardar, el `diff` metería cada clave donde el
default del XML difiere de la global. El perfil se re-infla y acaba fijando **defaults
del XML por encima de las globales del usuario**: reeditar un perfil pasaría a ser una
operación destructiva.

Así que el commit 0 son tres cambios, todos en `EditProfileActivity.java`:

1. `saveProfile()` guarda `diff(inMemoryPrefs.getAll(), globales)` en vez de
   `inMemoryPrefs.getAll()`.
2. Al editar un perfil, sembrar el editor con **globales + opciones del perfil
   superpuestas**, no solo con las opciones. Con eso lo que se ve al editar es lo que se
   va a aplicar, y de paso el resaltado amarillo (`highlightPreferences` :302) empieza a
   significar «difiere de las globales», que es para lo que está.
3. Ojo al orden de los argumentos: `diff(target, newPrefs)` devuelve las entradas de
   **`target`** que difieren de `newPrefs` (o que `newPrefs` no tiene). Para guardar hace
   falta `diff(memoria, globales)`; la llamada que ya existe para el resaltado es la
   contraria, `diff(globales, memoria)`, y da igual porque allí solo se usa el `keySet`.
4. `diff` compara con `equals`, y un perfil releído trae los números como `Double` de
   Gson: `Double(90.0).equals(Integer(90))` es `false`. Sin arreglar eso, un perfil
   heredado no se reduciría nunca —todas sus claves numéricas, incluidas resolución,
   fps y bitrate, seguirían fijadas— y el resaltado amarillo marcaría como cambiada
   cada preferencia numérica al reabrir un perfil tras reiniciar la app. Se añade un
   `sameValue()` que compara los `Number` por valor. Beneficia a los dos usos del `diff`.

**Acoplamiento no obvio que conviene no perder:** el `diff` solo es correcto porque las
globales están **completas** cuando se llega al editor. Lo garantiza
`PcView.java:151`, que llama a `PreferenceManager.setDefaultValues(this, R.xml.preferences,
false)` al arrancar. Sin eso las globales serían dispersas, y como el framework de
preferencias escribe los valores por defecto de las ~90 preferencias en el almacén del
editor al inflar la pantalla, la rama «unknown key, include» del `diff` los tomaría por
overrides deliberados y el perfil volvería a nacer completo. Se descubrió porque el primer
montaje de los tests partía de unas globales casi vacías y el perfil salía con 91 claves en
vez de 1; el arreglo fue hacer el test representativo (llamar a `setDefaultValues`, como la
app), no tocar el `diff`.

### 1b. `getFloat` del editor y los `Double` de Gson (commit propio, antes del 0)

`InMemorySharedPreferences.getFloat` (:371) hace `value instanceof Float`, pero un perfil
releído del JSON trae los números como `Double`. Sus hermanos `getInt` (:353) y `getLong`
(:362) de esa misma clase ya lo hacen bien con `instanceof Number`.

**Corrección respecto a lo que dijo antes este documento:** esto **no** es un bug
observable hoy. Las tres preferencias float que existen —`zoomScale`, `panOffsetX`,
`panOffsetY` (`PreferenceConfiguration:1031-1033`)— **no están en el árbol de ajustes**
(no aparecen en `res/xml/`): las escribe el gesto de zoom del stream directamente sobre
las globales (`Game.java:1727-1729`). Como no hay ninguna preferencia de tipo float en la
pantalla de edición, el framework nunca llama a este `getFloat`, y con perfiles parciales
tampoco entrarían al perfil como diffs. El pipeline de streaming nunca estuvo afectado: el
overlay sí hace `((Number) …).floatValue()` (:236-239).

**Decisión:** hacer el cambio igual, como consistencia defensiva, en un commit suelto y
anterior al 0. Cuatro líneas que dejan los tres getters numéricos de esa clase iguales, y
que evitan una trampa silenciosa el día que alguien añada una preferencia float al árbol
de ajustes. Pero se describe por lo que es —endurecimiento de código hoy inalcanzable—, no
como un arreglo de un fallo real, y **no lleva requisito de spec ni test**: no hay camino
por el que ejercitarlo.

Queda en el mismo cajón que la trampa hermana del overlay: `getStringSet` hace un cast
directo a `Set<String>` y Gson devolvería `ArrayList`. Hoy no hay ninguna
`MultiSelectListPreference` en el árbol (verificado), así que también es inalcanzable. Esa
no se toca, para no ampliar el commit.

- *Alternativa: dejarlo como está y reeditar los tres perfiles a mano cada vez que se
  cambie un ajuste global.* Descartada: es exactamente el trabajo manual que este
  cambio viene a quitar, movido a otro sitio.
- *Alternativa: interfaz para elegir qué claves entran en el perfil.* Descartada: más
  UI para resolver algo que el diff resuelve solo.
- *Alternativa: hacerlo al final, como commit 5.* Descartada por lo dicho arriba.

**Coste de fork:** toca una pieza compartida (afecta a cualquiera que use perfiles hoy),
igual que la objeción que dejó fuera de alcance el redirigir `edit()`. Pero la
superficie es mucho menor: solo la **creación**, no la lectura, y es lo que el propio
diseño del overlay parece esperar. Va en su propio commit para poder mandarse suelto
como PR.

### 2. Las asignaciones viven en `profiles.json`, no en un fichero nuevo

```java
// ProfilesManager.ProfilesData (clase privada, :188)
private static class ProfilesData {
    List<SettingsProfile> profiles;
    UUID activeProfileId;
    Map<String, String> pcBindings;   // NUEVO: pcUuid -> profileUuid | BINDING_NONE
}
```

Así el enlace viaja junto a los perfiles y se guarda con el `save()` que ya existe. Un
fichero antiguo llega con el campo a `null` → inicializar a `LinkedHashMap` vacío.

- *Alternativa: `SharedPreferences` aparte, como los hidden apps de `AppView`.*
  Descartada: separa dos datos que solo tienen sentido juntos, y obliga a un segundo
  camino de borrado al eliminar un perfil.
- *Alternativa: un campo en `ComputerDetails` / la base de datos de PCs.* Descartada:
  esa base la reescribe el servicio de descubrimiento y es territorio de upstream.

### 3. Los tres estados se representan con «clave ausente» vs `BINDING_NONE`

| Estado | Representación | Al usar ese servidor |
|---|---|---|
| Sin asignar (por defecto) | la clave **no está** en el mapa | no se toca nada |
| Perfil X | `pcUuid -> "<uuid>"` | `setActive(uuidX)` |
| Ninguno / globales | `pcUuid -> BINDING_NONE` (`""`) | `setActive(null)` |

`""` como centinela porque el mapa es `Map<String,String>` y Gson lo serializa sin
ceremonia; un `null` como valor sería indistinguible de la clave ausente al releer.

API nueva en `ProfilesManager`:

```java
public static final String BINDING_NONE = "";
public String  getPcBinding(String pcUuid);       // crudo, o null si no hay asignación
public void    bindPc(String pcUuid, UUID profileUuid);   // null => BINDING_NONE
public void    unbindPc(String pcUuid);
public boolean applyProfileForPc(String pcUuid);  // true si el activo cambió
```

Reglas de `applyProfileForPc`, que son el contrato de robustez de los specs:

1. `pcUuid` nulo o vacío → `false`, sin tocar nada.
2. Asignación ausente → `false`.
3. `BINDING_NONE` → `setActive(null)`.
4. Asignación a un perfil inexistente → tratarla como ausente y **limpiarla** con
   `unbindPc`.
5. Si el perfil resultante ya es el activo → **no** llamar a `setActive`.
6. No lanza nunca: cualquier excepción se traga y devuelve `false`.

`bindPc`/`unbindPc` llaman a `saveIfPossible()`, igual que `add`/`update`/`delete`.

La regla 5 no es cosmética: `setActive` hace `notifyListeners()` + `saveIfPossible()`,
y `save()` escribe `profiles.json` **síncrono en el hilo que llame** — y esta ruta se
ejecuta en cada lanzamiento de un juego.

### 4. Enganches: un embudo obligatorio y una confirmación visual

`createStartIntent` es el **único** sitio del árbol que construye un intent a `Game`
(verificado: `ServerHelper.java:101` y `:104` son los dos únicos `new Intent(…,
Game.class)`). Eso lo convierte en una red de seguridad real, no en una esperanza.

**a) `utils/ServerHelper.createStartIntent` :96 — primera línea del método.**

```java
Intent gameIntent = null;
ProfilesManager.getInstance().applyProfileForPc(computer.uuid);   // NUEVO
PreferenceConfiguration prefConfig = PreferenceConfiguration.readPreferences(parent);
```

Debe ir **antes** del `readPreferences`, porque en esa misma función
`prefConfig.enableFullExDisplay` ya decide sobre el display secundario (:99).

**b) `AppView` — en `onResume()`, no en `onCreate()`.**

```java
if (ProfilesManager.getInstance().applyProfileForPc(uuidString)) {
    prefConfig = PreferenceConfiguration.readPreferences(this);
}
// …y luego el refresco del FAB que ya está aquí (:400-412)
```

`onCreate` bastaría para el caso normal, pero no se repite. Secuencia que deja el FAB
mintiendo: lista de apps de `padre` abierta → un atajo del launcher lanza un juego de
`hijo` (activa su perfil) → al volver a `padre`, `onCreate` no corre y `onResume`
refresca el FAB con el perfil de `hijo`. En `onResume` se corrige solo, y por la regla
5 repetirlo es gratis.

- *Alternativa: `onCreate`, como decía el análisis inicial.* Descartada por lo anterior.

**c) `ShortcutTrampoline` — tras resolver `hostUUID`, con relectura.**
Aquí hay una trampa de orden: `prefConfig = readPreferences(this)` corre en `onCreate`
:357, **antes** de que `hostUUID` quede resuelto (:367-428), y ese `prefConfig` se usa
después en `createStartIntent(…, prefConfig.useVirtualDisplay)` (:149, :159, :198).

```java
// después de `uuidString = hostUUID;` (:428)
if (ProfilesManager.getInstance().applyProfileForPc(hostUUID)) {
    prefConfig = PreferenceConfiguration.readPreferences(this);   // releer
}
```

**d) `PcView.doAppList()` :714 — NO se engancha.** Su único efecto sería el FAB de la
lista de servidores, que es global y estaría mostrando el perfil del último servidor
visitado en una pantalla que enseña los tres a la vez: se lee como «esta app está en
1080p», que es falso. No cubre ninguna ruta nueva (a y b ya lo hacen). Lo que sí hace
falta ahí es la decisión 5.

**Hilos.** `ProfilesManager` no es thread-safe (`LinkedHashMap` y lista de listeners sin
sincronizar) y `notifyListeners()` toca UI. Los tres enganches están en el hilo
principal. En `AppView`, `localBinder.getComputer(uuidString)` corre en un hilo del
`ServiceConnection` (:95-110): **no** aplicar asignaciones desde ahí.

### 5. La visibilidad va en la cabecera del menú contextual, no en la rejilla

`PcView.onCreateContextMenu` ya compone `headerTitle` a mano (:409-427: nombre + estado).
Añadir ahí el perfil asignado es una línea y resuelve el problema real: con tres estados
posibles —y dos de ellos («sin asignar» y «ninguno») difíciles de distinguir de
memoria—, si no se ve el estado hay que abrir el diálogo de cada servidor para saber
cómo está configurado.

- *Alternativa: subtítulo en cada tarjeta de la rejilla (`PcGridAdapter`).* Es más
  visible pero toca layout y adapter, más superficie de fork por lo mismo. Queda como
  mejora posterior.

Entrada de menú: `PROFILE_ID = 22` (los ids `12-19` están libres; `20` y `21` ya se
usan), añadida **fuera** de los bloques condicionales de estado, junto a
`TEST_NETWORK_ID`/`DELETE_ID`/`VIEW_DETAILS_ID` (:457-459, órdenes 5/6/7), para poder
configurar el perfil con el servidor apagado o sin emparejar.

Diálogo con `AlertDialog.setSingleChoiceItems`:

```
┌ Perfil de ajustes — padre ──────────────────┐
│ ( ) Sin asignar (usar el perfil activo)     │  → unbindPc()
│ ( ) Ninguno — ajustes globales              │  → bindPc(uuid, null)
│ (•) asiento-1440p                           │  → bindPc(uuid, perfil.getUuid())
│ ( ) consola-4K                              │
│                                   [Cancelar]│
└─────────────────────────────────────────────┘
```

### 6. El aviso en `StreamSettings`, en vez de redirigir `edit()`

El problema: con activación automática se entrará en los ajustes con un perfil activo
casi siempre, se cambiará un valor, se escribirá en las globales, y el perfil seguirá
tapándolo — el cambio *parece* no tener efecto. Ya pasa hoy, pero pasará mucho más.

**Decisión:** avisarlo en esa pantalla cuando haya perfil activo.

- *Alternativa: redirigir `edit()` del overlay al perfil activo.* Es la solución
  correcta, pero cambia el comportamiento de una pieza compartida por todo el que use
  perfiles y merece su propio PR. Fuera de alcance (ya declarado en la propuesta).
- *Alternativa: no avisar nada.* Descartada: es el modo de fallo más probable de este
  cambio, y silencioso.

La decisión 1 lo suaviza mucho de paso: con perfiles que solo contienen lo que cambian,
la mayoría de los ajustes que se toquen no estarán tapados por el perfil y el cambio se
notará.

### 7. Contenedor calcado de `~/iptv/tele`, con tres deltas

Se copia el precedente de la casa (`tele/Dockerfile.compilar`, `tele/bin/compilar`,
`tele/bin/instalar`) y se adapta.

**Los scripts van en `herramientas/`, no en `bin/`.** En `tele` viven en `bin/`, pero aquí
`.gitignore:17` ignora `bin/` —es el directorio de salida de Eclipse que hereda el
`.gitignore` de upstream— y los dos scripts nunca se commitearían: desaparecerían en el
siguiente clon. Se descarta añadir una negación al `.gitignore` (toca un fichero de
upstream por una comodidad propia, y semánticamente `bin/` **sí** es salida generada para
ellos). `herramientas/` está libre y es inconfundiblemente de la casa.

Lo que se hereda tal cual y no hay que redescubrir:

- `-u "$(id -u):$(id -g)"` → el APK y los intermedios no salen de `root`.
- `ANDROID_USER_HOME=/gradle/.android` con `/gradle` montado desde
  `~/.cache/gradle-artemis` → el `debug.keystore` sobrevive al contenedor. Sin esto la
  JVM saca `user.home` de `/etc/passwd`, cada compilación firma con una clave nueva y
  la instalación falla con `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, que no explica nada de
  esto.
- `GRADLE_USER_HOME=/gradle` + `--no-daemon` → la distribución de Gradle y las
  dependencias se bajan una vez.
- `adb` del mismo contenedor con `ADB_VENDOR_KEYS`, montando solo el par de ficheros de
  la clave y en solo lectura.

Deltas frente a `tele`:

| | `tele` | aquí |
|---|---|---|
| SDK | `platforms;android-35`, `build-tools;35.0.0` | `android-36`, `build-tools;36.0.0` |
| NDK | no usa | **`ndk;27.0.12077973`**, exacto (`app/build.gradle:4`); ~2,5 GB de imagen |
| tarea por defecto | `:app:assembleDebug` | `:app:assembleNonRoot_gameDebug` |
| tests | no tiene | `:app:testNonRoot_gameDebugUnitTest` |

JDK 17 se mantiene: AGP 8.13 pide 17 o superior, y es el que ya está probado en casa.
No hace falta `local.properties` — vale el `ANDROID_SDK_ROOT` del contenedor.

**El aparato es la misma Shield TV que la app de la tele**, así que `bin/instalar`
hereda el destino (`192.168.1.33:5555`) y la clave (`~/iptv/api/datos/adbkey`), ya
autorizada en esa Shield desde que esto lo hacía Home Assistant: no hay que aceptar
ningún diálogo en pantalla. Lo que la Shield autoriza es la clave pública, no quién la
presente. Dos consecuencias:

- **La ABI que hace falta es `arm64-v8a`** (Tegra X1). Las otras tres solo cuestan
  tiempo de compilación, de ahí el intento de filtrarlas.
- **La UI se maneja con mando, no con dedo.** El menú contextual del servidor se abre
  con pulsación larga del centro del d-pad —upstream ya se apoya en eso en Shield— y un
  `AlertDialog.setSingleChoiceItems` es navegable con d-pad. Aun así hay que probarlo
  con el mando, no dando por bueno que funciona porque funcione en un móvil.

  Probarlo mereció la pena: **la lista de perfiles era inservible con mando**, y salió al
  intentar activar un perfil a mano durante la verificación. `ProfilesAdapter` hace la fila
  clicable para poder editarla tocándola, y una vista clicable es focusable por defecto;
  como esa fila ocupa todo el ancho, el `FocusFinder` nunca encuentra sus propios hijos «a
  la derecha» —exige que el candidato quede fuera del rectángulo de origen— y salta a la
  fila siguiente. Resultado: el radio de activar, el lápiz y la papelera inalcanzables, y
  OK disparando siempre el lápiz. Arreglado en su propio commit dejando la fila clicable
  pero no focusable. Es un bug de upstream, independiente de esta función, y por eso va
  suelto y es mandable como PR.

  Cómo se comprobó, que sirve para lo que quede: `uiautomator dump` por adb da la
  jerarquía con `focusable`/`focused`, y `input keyevent DPAD_*` permite navegar sin tocar
  el mando. Es la forma de verificar foco en la tele sin fiarse de lo que parece en
  pantalla. Ojo: `ProfilesActivity` no está exportada, así que `am start -n` da
  `SecurityException`; hay que arrancar por `monkey -c LAUNCHER` y llegar navegando o
  tocando por coordenadas.

Dos cosas que salieron a favor al mirar el árbol:

- **OpenSSL viene precompilado y versionado** en
  `app/src/main/jni/moonlight-core/openssl/{arm64-v8a,armeabi-v7a,x86,x86_64}/lib{crypto,ssl}.a`.
  Gradle no lanza `build-openssl.sh`: solo compilan common-c, enet, opus y evdev.
- **El debug es una app aparte.** `applicationIdSuffix ".noirdebug"` con etiqueta
  «Diana», frente al `".noir"` / «Artemis» del release. Son dos paquetes distintos: no
  hay conflicto de firma, no hay que desinstalar nada y los emparejamientos y el
  `profiles.json` reales no corren ningún riesgo. A cambio, «Diana» arranca vacía y hay
  que emparejar en ella los tres endpoints — que para un cambio que reescribe
  `profiles.json` es el aislamiento que se quiere.

## Risks / Trade-offs

- **Escribir en las globales creyendo escribir en el perfil** (el modo de fallo más
  probable, ver decisión 6) → aviso en `StreamSettings`, y la decisión 1 reduce mucho la
  superficie tapada.
- **`profiles.json` se escribe síncrono en el hilo que llame, y ahora en la ruta de
  lanzamiento** → regla 5 de `applyProfileForPc`: no guardar si el perfil no cambia.
- **Un fallo aquí podría impedir lanzar un juego** → `applyProfileForPc` no lanza nunca
  y devuelve `false`; si `load()` falló al arrancar es un no-op silencioso. Hay
  escenarios de spec para ambos.
- **Números en Gson.** `options` se deserializa como `Map<String,Object>` y los números
  llegan como `Double`. El overlay ya lo maneja (`((Number) …).intValue()`, :228-238);
  cualquier código nuevo que lea `options` directamente debe hacer lo mismo, **nunca**
  `(Integer) valor`. Afecta al commit 0 si el diff compara valores leídos del perfil.
- **`getStringSet` del overlay** hace un cast directo a `Set<String>` y Gson devolvería
  `ArrayList`. Hoy no hay ninguna `MultiSelectListPreference` en el árbol de ajustes
  (verificado), así que es inocuo; tenerlo presente si algún día se añade una.
- **Coste de merge con upstream** → siete ficheros, todos ya propios de Artemis (no de
  Moonlight), con cambios de 1-3 líneas salvo `PcView` (menú + diálogo). Sin reordenar
  ni reescribir estilo ajeno. En 4 commits separados para poder mandar el 0 suelto.
- **`minifyEnabled true` también en debug** (upstream lo tiene así) → R8 corre en cada
  compilación de depuración: es más lenta de lo que uno espera de un debug. Conviene
  saberlo antes de culpar al contenedor.
- **`splits.abi` con cuatro ABIs** → el nativo se compila cuatro veces en cada build
  limpia. Se intentará `-Pandroid.injected.build.abi=arm64-v8a` para el bucle de
  pruebas; si no funciona, se acepta la compilación completa antes que tocar
  `app/build.gradle` (churn de fork por una comodidad local).
- **La imagen pesa ~2,5 GB más por el NDK** → se construye una vez y se queda.
- **La suite de tests unitarios ya viene roja del upstream**: 5 fallos en el árbol limpio,
  medidos y reproducidos con y sin cambios propios. Dos son
  `ProfilesNavigationTest.clickingProfileButton_*`, con
  `ClassCastException: ExtendedFloatingActionButton cannot be cast to ImageButton` —los
  tests se quedaron atrás cuando el FAB de perfiles cambió de tipo—. Los otros tres
  (`LayoutInflationTest`, `SimpleStartupTest.testApplicationOnCreate`,
  `StartupTest.testApplicationStartup`) son `InflateException` en `activity_app_view:45` y
  un NPE con `this.mBase` a null. → El criterio no puede ser «pasa la suite» sino **«no
  añade fallos a esa línea base de 5»**. Molesta que los dos primeros toquen justo el FAB
  de perfiles, que es zona de este cambio: son dos líneas de arreglo y se pueden coger de
  paso, pero es alcance aparte y no entra sin decirlo.
- **La Shield TV comparte aparato con la app de la tele** → son paquetes distintos y no
  se estorban, pero el `debug.keystore` de este proyecto vive en
  `~/.cache/gradle-artemis` y el de `tele` en `~/.cache/gradle-iptv-tele`: cachés
  separadas a propósito, para que subir el SDK de un proyecto no arrastre al otro.

## Migration Plan

No hay migración de datos: `profiles.json` gana un campo opcional y es compatible en
ambos sentidos (una versión antigua leyendo un fichero nuevo ignora `pcBindings`).

La decisión 1 no convierte nada: los perfiles completos ya guardados siguen aplicándose
igual, y se reducen al diff solo si el usuario los reedita y guarda. Quien quiera el
comportamiento nuevo en un perfil viejo, lo abre y lo guarda.

**Rollback:** desinstalar el APK de pruebas. La app de uso diario es otro paquete y no
se ha tocado. Si el cambio llegara a la app de uso diario y hubiera que revertirlo, un
`profiles.json` con `pcBindings` lo lee sin problema una versión anterior.

## Open Questions

Ninguna. Las dos que había están resueltas:

- **El aparato es la Shield TV**, la misma que la app de la tele (ver decisión 7).
- **`-Pandroid.injected.build.abi` no sirve aquí.** Probado: con `assemble` y
  `splits.abi` activo la compilación dice `BUILD SUCCESSFUL` y **no produce ningún
  APK** — ese flag es para las tareas `install` que lanza el IDE. Modo de fallo
  silencioso, así que descartado: se compilan las cuatro ABIs. Cuesta poco (medido:
  2m 05s en limpio) y no obliga a tocar `app/build.gradle`.
