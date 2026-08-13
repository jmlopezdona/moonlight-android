## Context

Ver `proposal.md` — Why para el fallo y su causa. Lo que hace falta aquí es el estado del
código alrededor, que es lo que decide entre los dos arreglos posibles.

**La caché de carátulas está indexada por id numérico, en disco.**
`DiskAssetLoader.getFile()` devuelve `boxart/<uuid-del-servidor>/<appId>.png`, y
`PosterContentProvider` abre exactamente ese fichero a partir de los segmentos de la uri.
El id numérico no es un detalle del canal: es el nombre del fichero de la carátula.

**El UUID de app es propio del fork.** Lo introdujo el soporte de Apollo (`EXTRA_APP_UUID`,
`NvApp.getAppUUID()`, las comparaciones de `runningGameUUID`), y convive con el id numérico
de upstream. `AppView.java:610` ya contempla que un servidor no dé UUID, así que ninguno de
los dos identificadores se puede dar por garantizado.

**Hay dos centinelas para «no hay id».** `StreamConfiguration.INVALID_APP_ID` vale `0` y es
el que usa `Game.java:571` al leer el intent; `ShortcutTrampoline.java:444` usa `-1` para lo
mismo. Cualquier guarda tiene que cubrir los dos.

**El canal no se puede tocar en el sondeo.** `ShortcutHelper.createAppViewShortcut()` avisa
en un comentario de upstream de que la API de canales *throttlea* si se la llama en cada
sondeo de servidores, y por eso allí solo se crea el canal cuando el emparejamiento es
nuevo. Cualquier trabajo extra sobre el canal tiene que colgar de una acción del usuario,
no del sondeo.

## Goals / Non-Goals

**Goals:**

- Que el id numérico llegue vivo hasta el código del canal por la ruta que hoy lo pierde.
- Que ninguna ruta de lanzamiento pueda escribir en el canal una entrada irreconocible.
- Que la basura ya escrita se vaya sola.

**Non-Goals:**

- **Re-clavar el canal sobre el UUID.** Se descarta abajo, con motivo.
- **Cambiar el formato de la caché de carátulas.** No se toca `DiskAssetLoader` ni
  `PosterContentProvider`.
- **Resolver el id numérico preguntándole al servidor** cuando el intent no lo trae. Es
  otro cambio, más grande y con red de por medio; aquí basta con no ensuciar la home.
- **Arreglar la nota desactualizada de `openspec/config.yaml`** sobre que aquí no se puede
  compilar. Está señalada en la propuesta; corregirla es trabajo aparte.

## Decisions

### D1: Conservar el id numérico en el trampolín, en vez de re-clavar el canal sobre el UUID

`ShortcutTrampoline` lee `EXTRA_APP_ID` del intent también en la rama del UUID, y solo cae
al centinela si de verdad no viene. El UUID sigue siendo lo que manda para conectar.

*Alternativa considerada: usar el UUID como `internalProviderId` del canal.* Es la que
parece obvia al leer `addGameToChannel()`, y es peor por tres motivos, en este orden:

1. **No arregla la carátula.** La imagen la sirve `PosterContentProvider` desde
   `boxart/<uuid>/<appId>.png`. Con el canal clavado en UUID, la uri de la carátula
   seguiría necesitando el id numérico, o habría que reindexar la caché de disco por UUID
   —y entonces las carátulas ya descargadas dejarían de encontrarse—.
2. **El id ya está en el intent.** `ServerHelper.createAppShortcutIntent()` mete las dos
   identidades. Ir a buscar otra clave cuando la buena viaja en el propio intent que se
   está abriendo es trabajo de más.
3. **Coste de fork.** Tocaría `TvChannelHelper`, `PosterContentProvider` y
   `DiskAssetLoader`, los tres de upstream, contra unas líneas en uno solo.

*Alternativa considerada: no llamar a `reportGameLaunched()` cuando el id no es válido.*
Arregla el síntoma en esta ruta y deja el agujero abierto para las demás. Es lo que hace D2,
pero un escalón más abajo, donde cubre a todas.

### D2: La guarda vive en `addGameToChannel()`, no en quien lo llama

Quien sabe qué necesita para escribir una entrada es el código del canal, no cada uno de
sus llamantes. Con la guarda dentro, cualquier ruta futura —un deep link nuevo, un `.art`—
queda cubierta sin acordarse de nada. Condición: `appId <= 0`, que cubre el `0` de
`INVALID_APP_ID` y el `-1` del trampolín.

Consecuencia aceptada: un lanzamiento que de verdad no traiga id —hoy, un `.art` que solo
guarde UUID— dejará de refrescar su entrada en la home. Es preferible a la alternativa
actual, que es escribir una entrada falsa; y el juego arranca igual, que es lo que importa.

### D3: La limpieza cuelga de `createTvChannel()`

`reportGameLaunched()` llama a `createTvChannel()` antes que a `addGameToChannel()`, así que
ahí hay un punto que se ejecuta **en cada lanzamiento y en cada emparejamiento nuevo**, y no
en el sondeo — que es justo la condición que impone el aviso de throttling de upstream.

La limpieza recorre las entradas del canal y borra aquellas cuyo `internalProviderId` no sea
un entero positivo. Solo mira canales que la app ha creado (se localizan por el uuid del
servidor), así que no puede tocar entradas de otras aplicaciones.

*Alternativa considerada: borrar por título duplicado.* Frágil y peligroso — dos apps
pueden llamarse igual en dos servidores, y el nombre no es identidad.

*Alternativa considerada: no limpiar y que el usuario quite la entrada a mano.* Deja a quien
actualice con la basura de antes y con la impresión de que el arreglo no funciona.

## Risks / Trade-offs

- **El `.art` con solo UUID deja de refrescar su entrada** → Es el precio de D2. Se puede
  levantar más adelante resolviendo el id contra la lista de apps del servidor, que ya se
  hace en `ShortcutTrampoline` para los lanzamientos por nombre.
- **La limpieza añade una consulta al TvProvider por lanzamiento** → Va en el camino de
  `createTvChannel()`, que ya escribe en el proveedor en ese mismo punto; no se añade al
  sondeo, que es donde upstream avisa del throttling.
- **Un `internalProviderId` no numérico podría ser legítimo en el futuro** si algún día se
  clava el canal sobre UUID → Hoy no lo es: todo lo que la app escribe ahí sale de
  `""+app.getAppId()`. Si eso cambia, cambia también el criterio de la limpieza, y ambos
  viven en el mismo fichero.
- **Nada de esto se puede probar sin la tele** → Ver el plan de abajo. No hay test
  automático posible: el estado que falla vive en el TvProvider de un Android TV.

## Migration Plan

1. Compilar el APK con `herramientas/compilar` (variante `nonRoot_game`) e instalarlo en la
   Shield con `herramientas/instalar`.
2. Abrir la app una vez, para que el canal se refresque y se lleve por delante la entrada
   gris que hay hoy.
3. Lanzar un juego **desde su entrada de la home** y comprobar en la tele que sigue habiendo
   una sola entrada de ese juego y que conserva la carátula.
4. Repetir con un segundo juego, que es lo que delataba el fallo: con el código de hoy, el
   recuadro gris se re-titula al del último lanzado.

Vuelta atrás: reinstalar el APK anterior. Las entradas que la versión nueva haya borrado no
se recuperan, pero se vuelven a crear solas al lanzar cada juego.

## Lo que queda fuera, a propósito

Anotado al implementar, para que no se lea como un descuido:

- **Un `.art` que solo lleve UUID deja de refrescar su entrada de la home.** Es la
  consecuencia aceptada de D2: la guarda de `addGameToChannel()` descarta la escritura
  cuando no hay id numérico utilizable. El juego arranca igual; lo único que no pasa es
  que su recuadro se actualice. Antes sí escribía, pero escribía una entrada falsa.
- **Resolver el id numérico contra el servidor cuando el intent no lo trae es otro
  cambio.** `ShortcutTrampoline` ya sabe hacerlo para los lanzamientos por nombre, contra
  la caché de `applist`; extenderlo a la rama del UUID levantaría la limitación de arriba.
  No entra aquí: son más líneas en un fichero de upstream y tiene la red de por medio.
- **La nota desactualizada de `openspec/config.yaml`** —la que dice que aquí no se puede
  compilar— sigue sin corregir. `Dockerfile.compilar` y `herramientas/{compilar,instalar}`
  existen y se han usado en este cambio.

## Estado de la verificación

Comprobado en la Shield del salón sobre **Diana** (`com.limelight.noirdebug`), que es el
paquete donde se observó el fallo — `com.limelight.noir` no está instalado en el aparato:
la entrada gris desapareció del canal, lanzar desde la home no duplica, y un segundo
lanzamiento no crea ni re-titula ningún recuadro. Quedan sin comprobar el caso del PC
apagado y el lanzamiento desde la lista de apps de dentro de la app.
