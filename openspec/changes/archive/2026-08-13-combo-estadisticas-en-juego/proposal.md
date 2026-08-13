## Why

Las estadísticas de rendimiento son lo que se mira cuando algo va mal: si el juego
tartamudea, saber si la culpa es de la red, del decodificador o del PC empieza por
abrir ese overlay. Es una consulta de diez segundos que se hace en mitad de una
partida.

Y hasta ahora costaba diez segundos **llegar** a ella. El único camino era el menú de
juego, que se abre con una doble pulsación de Start manteniendo la segunda 750 ms
(`ControllerHandler.java:2730-2736`), y desde ahí hay que entrar en «Avanzado» y bajar
hasta la sexta entrada (`GameMenu.java:251`). Para algo que se enciende y se apaga
para echar un vistazo, era más ceremonia que la propia consulta.

Peor: ese menú solo alterna encendido/apagado. **El modo —lite o completo— solo se
elegía en Ajustes, antes de arrancar la transmisión.** Si estabas jugando con el lite
puesto y querías el detalle de latencias del completo, había que cortar la partida.

Esto es propio de este fork. El overlay lite, su variante «bottom» y el diálogo al
tocarlo vienen de Artemis; upstream Moonlight solo tiene el overlay completo.

## What Changes

**Combo de mando para las estadísticas.** `Start + cruceta arriba` saca las
estadísticas completas y `Start + cruceta abajo` las lite. Sin menús: dentro de la
partida, sobre la marcha.

**El combo elige modo, no solo encendido.** Pulsar el combo del modo que ya está en
pantalla lo oculta; pulsar el otro **cambia de modo**. Así se salta de lite a completas
de un toque, que es justo lo que no se podía hacer sin cortar la partida. El modo pasa
a ser una decisión en caliente, no un ajuste previo.

**La cruceta no llega al juego** mientras se hace el combo, para no mover el cursor de
un menú al consultar las estadísticas. Start sí llega — ver el porqué en `design.md`,
porque no es una omisión sino una restricción.

**Se corrige que los dos overlays se apilen.** El `toggleHUD()` del menú de juego nunca
ocultaba el `TextView` del otro modo. No se notaba porque el modo no podía cambiar en
caliente; en cuanto puede, sin arreglarlo se quedan los dos overlays uno encima de
otro. Es un bug preexistente que este cambio hace visible.

**Fuera de alcance:** que el modo elegido con el combo **persista** en las
preferencias. Hoy no persiste, igual que no persistía el `toggleHUD()` del menú: al
reconectar vuelve a lo que digan los Ajustes. Se deja así a propósito, porque un combo
que se pulsa a menudo escribiendo en las preferencias globales interactúa con los
perfiles de ajustes de `perfil-por-servidor` de una forma que no está pensada.

## Capabilities

### New Capabilities
- `mando/combo-de-estadisticas`: mostrar, ocultar y cambiar de modo el overlay de
  estadísticas de rendimiento desde el mando durante la partida, sin pasar por menús,
  y qué se le manda al juego mientras tanto.

### Modified Capabilities
<!-- Ninguna. La única capability principal que hay, `home-de-android-tv/entradas-de-juego`,
     va de las entradas del canal de la home y no la toca este cambio. -->

## Impact

**Código propio del fork que se toca.** Los tres ficheros son de upstream Moonlight y
`ControllerHandler.java` es de los más transitados del árbol, así que el coste de merge
es real; se ha mantenido el cambio lo más localizado posible (un método nuevo y tres
llamadas de una línea, sin tocar nada de la lógica existente):

- `binding/input/ControllerHandler.java` — método `handlePerfOverlayCombo()` nuevo, un
  campo en `InputDeviceContext`, y una llamada en cada una de las tres rutas de
  entrada: `handleButtonDown`, `handleButtonUp` y `handleAxisSet`.
- `Game.java` — `togglePerformanceOverlay(boolean)` nuevo y el reparto de la
  visibilidad a `updatePerformanceOverlayVisibility()`, que ahora comparte con
  `toggleHUD()`.
- `ui/GameGestures.java` — un método `default` nuevo en la interfaz.

**Las tres rutas de entrada no son opcionales.** En muchos mandos el d-pad no llega
como tecla sino como eje HAT, y ahí el `inputMap` se recompone en `handleAxisSet`. Sin
esa tercera llamada el combo no existiría justo en los mandos más comunes.

**Nada del pipeline de vídeo.** El renderer ya consulta `prefs.enablePerfOverlay` y
`prefs.enablePerfOverlayLite` en cada ventana de un segundo
(`MediaCodecDecoderRenderer.java:1774` y `:1879`), y `prefs` es **la misma instancia**
que el `prefConfig` de `Game`. Cambiar el flag en memoria basta para que el texto
empiece o deje de generarse; ya era así para el `toggleHUD()` del menú.

**Ruta USB sin cubrir, a propósito.** `reportControllerState()` —la de los mandos por
el driver USB propio— no pasa por `handleButtonDown` y no tendrá el combo. Tampoco
tiene ninguno de los combos existentes, ni siquiera el de salir, así que no se
introduce una asimetría nueva.

**Sin formato de datos nuevo, sin strings nuevas, sin cambios de layout.**

**Verificación:** compila con `herramientas/compilar` y se instala con
`herramientas/instalar`, el entorno que trajo `perfil-por-servidor`. Los combos hay que
probarlos con el mando en la Shield contra el PC gaming: eso lo hace una persona.
