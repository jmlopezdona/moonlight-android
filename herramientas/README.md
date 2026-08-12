# Compilar e instalar sin JDK ni SDK en la máquina

Este servidor **no puede compilar el proyecto por sí solo**: no tiene JDK, ni SDK de
Android, ni NDK. `./gradlew` no arranca. Todo pasa por un contenedor que trae exactamente
esas tres cosas y nada más.

Es el mismo montaje que la app de la tele (`~/iptv/tele`), del que sale calcado.

```bash
herramientas/compilar          # APK de depuración («Diana»), variante nonRoot_game
herramientas/instalar          # lo instala en la Shield TV del salón
```

## Compilar

```bash
herramientas/compilar                                       # :app:assembleNonRoot_gameDebug
herramientas/compilar :app:testNonRoot_gameDebugUnitTest     # los tests (Robolectric)
herramientas/compilar test                                   # todos los tests unitarios
```

La primera vez construye la imagen (el NDK son un par de GB). Después, lo que Gradle baja
vive en `~/.cache/gradle-artemis`, fuera del contenedor.

**Hay que decir qué variante.** Dos flavors (`nonRoot_game`, `root`) por dos build types,
así que `assembleDebug` no es una tarea. La de casa es `nonRoot_game`.

### Tiempos medidos (2026-08-12, este servidor)

| | |
|---|---|
| Imagen, la primera vez | ~4 min (aprovecha capas de la imagen de `tele`: misma base y mismo `cmdline-tools`) |
| Compilación en limpio | **2 min 05 s** |
| Compilación incremental tras tocar un `.java` | ~55 s |
| Tests unitarios | ~45 s |

### Se compilan las cuatro ABIs, y es a propósito

`app/build.gradle` tiene `splits.abi` con `x86`, `x86_64`, `armeabi-v7a` y `arm64-v8a`, así
que salen cuatro APKs. Para la Shield TV solo hace falta `arm64-v8a` (Tegra X1).

**No intentes filtrarlas con `-Pandroid.injected.build.abi=arm64-v8a`**: probado, y con
`assemble` + `splits.abi` la compilación dice `BUILD SUCCESSFUL` y **no produce ningún
APK**. Ese flag es para las tareas `install` que lanza el IDE. Modo de fallo silencioso, de
los peores. Se compilan las cuatro y ya: cuesta dos minutos.

### OpenSSL no se compila

Viene ya compilado y versionado en el árbol
(`app/src/main/jni/moonlight-core/openssl/<abi>/lib{crypto,ssl}.a`). El `build-openssl.sh`
de al lado no lo lanza Gradle. Solo compilan `moonlight-common-c`, `enet`, `opus` y
`evdev`.

## Instalar

```bash
herramientas/instalar                      # 192.168.1.33:5555, la Shield del salón
herramientas/instalar 192.168.1.50:5555    # otro aparato
```

Usa el `adb` del propio contenedor y **la misma clave ADB que la app de la tele**
(`~/iptv/api/datos/adbkey`), que la Shield ya tiene autorizada: no hay que aceptar ningún
diálogo en la tele. Se monta solo el par `adbkey`/`adbkey.pub`, y de solo lectura — en esa
carpeta está también `iptv-canales.yaml`, que es la credencial del proveedor y no pinta
nada dentro de un contenedor que compila.

Con `SHIELD_ADBKEY` se puede apuntar a otra clave, y con `ARTEMIS_ABI` a otra ABI.

### «Diana» no es «Artemis»: son dos apps distintas

`app/build.gradle` le pone `applicationIdSuffix ".noirdebug"` al build de depuración y lo
llama **Diana**; el release es `".noir"` y se llama **Artemis**. Conviven instaladas:

```
com.limelight.noir       Artemis, la de jugar todos los días
com.limelight.noirdebug  Diana, la de pruebas
com.jmlopezdona.tele     la app de la tele, en el mismo aparato
```

Así que **no hay que desinstalar nada** para probar, no hay conflicto de firma con la app
de diario, y sus emparejamientos y su `profiles.json` no corren ningún riesgo. A cambio,
Diana arranca vacía: hay que emparejar los servidores dentro de ella.

## Dos cosas que costaron un rato

**La clave de firma tiene que sobrevivir al contenedor.** `herramientas/compilar` pasa
`ANDROID_USER_HOME=/gradle/.android` con `/gradle` montado desde la caché. Sin eso, la JVM
saca `user.home` de `/etc/passwd` —con este uid le sale `/home/ubuntu`, que muere con el
contenedor—, cada compilación firma con una clave nueva y la instalación falla con
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, que no explica nada de esto.

**Los scripts van en `herramientas/`, no en `bin/`.** En `tele` están en `bin/`, pero aquí
`.gitignore:17` ignora `bin/` (es la salida de Eclipse, viene del `.gitignore` de upstream)
y no se commitearían nunca.

## Los tests ya vienen rojos

En el árbol limpio fallan **5**, sin tocar nada. Medido y reproducido con y sin cambios
propios, así que el criterio es «no añadir fallos a esta lista», no «pasar la suite»:

| Test | Motivo |
|---|---|
| `ProfilesNavigationTest.clickingProfileButton_launchesProfilesActivity` | `ClassCastException`: el test declara `ImageButton` y el layout tiene un `ExtendedFloatingActionButton` |
| `ProfilesNavigationTest.clickingProfileButton_launchesProfilesActivityFromAppView` | igual |
| `LayoutInflationTest.allLayoutsInflateSuccessfully` | `InflateException` en `activity_app_view:45` |
| `SimpleStartupTest.testApplicationOnCreate` | NPE, `this.mBase` a null |
| `StartupTest.testApplicationStartup` | igual |

Los dos primeros son dos líneas de arreglo, y tocan justo el FAB de perfiles.

## Subir versiones

Si en `app/build.gradle` cambian `compileSdk` o `ndkVersion`, hay que cambiar también las
líneas correspondientes de `Dockerfile.compilar` y reconstruir la imagen
(`docker rmi artemis-compilar`). Gradle se para y lo dice si el `ndkVersion` no coincide.
