## Why

Lanzar un juego **desde su entrada de la home de Android TV** duplica esa entrada en el
canal, y la copia nueva sale gris, sin carátula. Observado en producción el 2026-08-13 en
la Shield del salón (SHIELD TV 2017, Android 11), canal `Diana: DESKTOP-5NVFM3T`, juego
Hades II. Se reproduce siempre: no hace falta ni que el PC conteste.

La causa es que en este fork **una app tiene dos identidades** —el UUID que trae Apollo y
el id numérico de toda la vida— y el código del canal, heredado de upstream, solo conoce
la segunda. En `ShortcutTrampoline.java:441-446`, cuando el intent trae UUID se construye
el `NvApp` tirando el id numérico:

```java
app = new NvApp(appName, appUUID,
        -1,   // App ID is not strictly needed if UUID is present
        ...);
```

El comentario es cierto para conectar, pero no para lo que viene después.
`Game.onCreate()` llama a `reportGameLaunched()` (`Game.java:3721`), que acaba en
`TvChannelHelper.addGameToChannel()` (`TvChannelHelper.java:131`), donde las tres
operaciones que importan van por el id numérico:

```java
.setPosterArtUri(PosterContentProvider.createBoxArtUri(computer.uuid, ""+app.getAppId()))
.setInternalProviderId(""+app.getAppId());
Long programId = getProgramId(channelId, ""+app.getAppId());
```

Con `appId = -1`, `getProgramId()` no reconoce la entrada buena —que guarda el id de
verdad— y hace **INSERT en vez de UPDATE**; y la carátula se pide como
`boxart/<uuid>/-1.png`, que `DiskAssetLoader.getFile()` nunca ha escrito. De ahí el
recuadro gris.

Y lo peor no es el duplicado sino lo que hace después: la clave `"-1"` es **una por
canal, no una por juego**, así que ese recuadro no se multiplica — se **re-titula y se
re-apunta al último juego lanzado desde la home**. La home acaba enseñando una entrada
sin carátula cuyo nombre cambia solo, que no se corresponde con ninguna app concreta y
que nadie limpia nunca.

El dato que decide el arreglo: `ServerHelper.createAppShortcutIntent()`
(`ServerHelper.java:54-63`) mete en el intent **las dos identidades**, UUID *e* id
numérico. El id bueno está ahí, en el propio intent que se está abriendo; es
`ShortcutTrampoline` quien lo descarta al elegir la rama del UUID. No hay que ir a
buscarlo a ninguna parte.

## What Changes

**Conservar el id numérico cuando el intent lo trae.** En `ShortcutTrampoline`, la rama
del UUID deja de cablear `-1` y lee `EXTRA_APP_ID` del mismo intent, cayendo al centinela
solo si de verdad no viene. El UUID sigue mandando para conectar —eso no se toca—; lo que
cambia es que el `NvApp` deja de nacer mutilado. Es el arreglo de la causa, y son unas
pocas líneas en un solo fichero.

**Que el canal no acepte entradas sin identidad.** `addGameToChannel()` no escribe nada
cuando el `appId` no es utilizable. Hoy el proyecto ya tiene un centinela para eso
—`StreamConfiguration.INVALID_APP_ID = 0`—, y el `-1` de `ShortcutTrampoline` es un
segundo centinela informal para lo mismo: la guarda debe cubrir los dos (`appId <= 0`).
No sustituye al arreglo de arriba, lo respalda: hay otras rutas de lanzamiento (los
`.art`, los deep links) que podrían llegar sin id, y ninguna tiene por qué poder ensuciar
la home.

**Limpiar lo que ya está escrito.** El recuadro gris que existe hoy no desaparece solo:
vive en el TvProvider y sobrevive a la actualización de la app. Al refrescar un canal se
borran las entradas cuyo `internal_provider_id` no sea un id válido. Sin esto, quien
actualice se queda con la basura de antes y el arreglo parece no funcionar.

Ninguno de los tres es **BREAKING**: las entradas con id correcto —las que se crean
lanzando desde dentro de la app— siguen igual, con su misma clave y su misma carátula.

## Capabilities

### New Capabilities

- `home-de-android-tv/entradas-de-juego`: qué identifica a una entrada de juego en el
  canal de la home, cuándo se crea, cuándo se actualiza en vez de duplicarse, y qué pasa
  cuando la app se lanza sin una identidad utilizable.

### Modified Capabilities

Ninguna. `openspec/specs/` está vacío todavía —`perfil-por-servidor` no se ha archivado—,
así que esto no modifica requisitos de ninguna capacidad existente.

## Impact

**Código.** Tres ficheros, los tres de upstream:

| Fichero | Qué se toca |
|---|---|
| `ShortcutTrampoline.java` | La rama del UUID en `validateAppInput` (~3 líneas) |
| `TvChannelHelper.java` | Guarda de entrada en `addGameToChannel()` y limpieza en el refresco del canal |
| — | `PosterContentProvider` y `DiskAssetLoader` **no se tocan**: con el id numérico recuperado, la carátula vuelve a resolver por el camino de siempre |

**Coste de fork.** Los tres son ficheros de upstream, así que cada línea propia se paga en
el siguiente merge. Es un argumento a favor de este arreglo y en contra de la alternativa
que parecía obvia —re-clavar el canal sobre el UUID—, que obligaría además a que
`PosterContentProvider` y `DiskAssetLoader` supieran servir carátulas por UUID, es decir,
a tocar el formato de la caché de disco. Se descarta por eso; queda razonada en `design.md`.

**Verificación.** No hay test automático posible: esto se ve en el TvProvider de un
Android TV real. Hay que compilar el APK e instalarlo en la Shield, lanzar un juego desde
la home y mirar que la entrada se actualiza en vez de duplicarse.

> El `context` de `openspec/config.yaml` dice que aquí no se puede compilar. **Está
> desactualizado**: `Dockerfile.compilar` y `herramientas/{compilar,instalar}` ya existen
> en el repo. Corregir esa nota es trabajo aparte, no de este cambio.

**Aparato.** La Shield del salón se alcanza por ADB en `192.168.1.33`, y desde este
servidor se le habla con `docker exec iptv-api python -m app.tele shell "..."` (la clave
ADB vive en el contenedor de `iptv-api`). Sirve para comprobar el estado, no para leer el
canal: las filas del TvProvider solo las ve su paquete propietario y el launcher, así que
la comprobación final es mirar la tele.
