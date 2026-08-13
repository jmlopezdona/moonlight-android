## Context

Ver `proposal.md` — «Why» para el motivo. Lo que hace falta saber para entender el
enfoque son tres restricciones del código de entrada de mando, que es de upstream y
está lleno de apaños por hardware concreto:

**Los combos existentes ocupan sitio.** `ControllerHandler.handleButtonDown()` ya
reconoce cuatro: `Start+Select+LB+RB` (salir), `Start+LB` (emula Select en los mandos
que no lo tienen), `Start+Select` o `Start+RB` (emula el botón Guide), y `Select+LB`
(emula el clickpad cuando se finge un mando PS4 para el giroscopio). Cualquier gesto
nuevo tiene que esquivarlos.

**Un botón del mando llega por tres caminos distintos.** `handleButtonDown` y
`handleButtonUp` para las teclas, y `handleAxisSet` para los ejes. La cruceta pasa por
uno u otro **según el mando**: en muchos llega como eje HAT, no como tecla. Los tres
reconstruyen el mismo `inputMap`, que es el mapa de bits que se manda al PC.

**Hay una cuarta ruta que no comparte nada.** `reportControllerState()`, la de los
mandos por el driver USB propio, construye el `inputMap` por su cuenta y no pasa por
ninguna de las tres. Tampoco tiene ninguno de los combos existentes.

## Goals / Non-Goals

**Goals:**

- Que el gesto funcione en cualquier mando, reporte la cruceta como reporte.
- Que el cambio sea lo más pequeño y localizado posible: `ControllerHandler.java` es de
  los ficheros más transitados de upstream y cada línea propia se paga en el siguiente
  merge.
- Que no se pueda quedar un botón pegado. Un bug de entrada en mitad de una partida es
  mucho peor que la falta de la función.

**Non-Goals:**

- Cubrir la ruta USB. Ver «Decisions».
- Hacer el gesto configurable. No hay pantalla de mapeo de combos en la app y montarla
  para esto es desproporcionado.

## Decisions

### El gesto es `Start + cruceta`, y la cruceta es lo que decide el modo

La cruceta se eligió porque **no choca con nada**: los cuatro combos existentes usan
botones, ninguno usa direcciones. `Select+RB` y `Select+LB` eran los candidatos
naturales para un par de gestos, pero `Select+LB` ya emula el clickpad al fingir un
mando PS4, así que el par habría quedado cojo justo en los mandos con giroscopio.

Arriba/abajo para completo/lite sale gratis de la misma idea y da dos gestos
hermanos en vez de uno que cicla entre tres estados. Un ciclo obliga a pasar por el
modo intermedio para llegar al otro, que es exactamente la fricción que este cambio
viene a quitar.

**El modificador se movió de Select a Start** después de probarlo. Es el botón que se
pide con el mando en la mano —en la distribución Xbox cae a la derecha del botón
grande— y para algo que se pulsa a menudo la comodidad manda. Tiene un coste real, en
«Risks».

### Se traga la cruceta, no el modificador

Cuando el gesto salta se limpian las direcciones del `inputMap` para que el juego no las
vea. Limpiarlas es seguro y no puede dejar nada pegado: las tres rutas reconstruyen esos
bits desde cero en cada informe, y el `handleButtonUp` correspondiente acaba haciendo un
`&= ~UP_FLAG` sobre un bit que ya está limpio.

**Con Start no se puede hacer lo mismo, y no es por comodidad.** `handleButtonUp`
comprueba que `PLAY_FLAG` esté puesto para distinguir una suelta real de Start de las
espurias que llegan al desconectar un mando (`ControllerHandler.java:2506`). Limpiarlo
al saltar el gesto rompería de paso los dos gestos que cuelgan de Start: el de
emulación de ratón y el de abrir el menú de juego. Así que Start llega al juego.

Se descartó tragárselo restaurando el bit después: hay demasiada contabilidad de tiempos
alrededor de Start (`startDownTime`, `startUpTime`, `backMenuPending`) como para
manipularla desde fuera sin romper algo.

### La comprobación va en las tres rutas, no solo en la de teclas

Es la decisión que más sostiene el cambio. Ponerla solo en `handleButtonDown` habría
dejado el gesto sin funcionar **justo en los mandos más comunes**, los que reportan la
cruceta como eje HAT. Se llama al mismo método desde `handleButtonDown`,
`handleButtonUp` y `handleAxisSet`, siempre justo antes de mandar el paquete.

Ponerlo en `handleButtonUp` no es simetría decorativa: es lo que suelta el pestillo
cuando el usuario levanta la cruceta por la ruta de teclas.

### Un pestillo por pulsación

Un booleano en el contexto de cada mando, que se pone al saltar el gesto y se quita en
cuanto el `inputMap` deja de coincidir. Sin él, los eventos repetidos de una cruceta
mantenida alternarían el overlay decenas de veces por segundo. Va en el contexto del
mando y no en el handler porque cada mando conectado tiene el suyo.

### El estado vive en memoria, no en las preferencias

El gesto cambia los flags de configuración en memoria y ya. Es lo que ya hacía el
`toggleHUD()` del menú, y funciona porque el renderer de vídeo consulta esos flags en
cada ventana de un segundo sobre **la misma instancia** de configuración que tiene
`Game` (`MediaCodecDecoderRenderer.java:1774` y `:1879`).

Persistirlo se descartó a propósito: un gesto que se pulsa a menudo escribiendo en las
preferencias globales se mete de lleno en los perfiles de ajustes de
`perfil-por-servidor`, donde escribir en las globales mientras un perfil las tapa es
justo el lío del que avisa esa pantalla.

### La visibilidad se decide en un solo sitio

`toggleHUD()` y el gesto nuevo comparten un método que pone la visibilidad de los dos
overlays a la vez. No es limpieza gratuita: `toggleHUD()` nunca ocultaba el del otro
modo, y en cuanto el modo puede cambiar en caliente eso se ve como dos overlays
apilados. Compartir el método arregla el bug preexistente en vez de duplicarlo.

### La ruta USB se queda fuera

`reportControllerState()` no comparte código con las tres rutas y no tiene **ninguno**
de los combos existentes, ni siquiera el de salir. Añadirlo solo para este gesto sería
la primera excepción y habría que replicar allí el pestillo y el tragado. Se deja fuera:
no se introduce una asimetría nueva, se respeta la que ya había.

### Los botones extra del mando GameSir no sirven

Se miró si alguno de los dos botones extra del GameSir de casa podía ser un atajo de un
solo botón, capturando los eventos en crudo del aparato. El resultado, para no repetir
la investigación:

- Ese mando, en modo PS4, se identifica como un **DualShock 4** (`054c:05c4`), con sus
  dispositivos de touchpad y de sensores de movimiento aparte.
- El botón **de debajo del botón grande** sí emite, pero **como clic del touchpad del
  DS4**. Ya tiene trabajo: se manda al PC como botón de touchpad, por el apaño para la
  Shield de `ControllerHandler.java:818`. Usarlo para las estadísticas se lo quitaría a
  los juegos.
- El botón **entre cruceta y stick derecho** no emitió nada en ninguna captura. Se lo
  queda el firmware del mando, que es lo normal en los botones de macro o de cambio de
  modo. Si no llega a Android, no hay nada que la app pueda hacer.

## Risks / Trade-offs

**El juego recibe el Start, y en muchos juegos eso abre el menú de pausa** → Es el precio
de que el modificador sea el botón cómodo, y se acepta a sabiendas. Volver a Select es
cambiar una constante en un sitio; el resto del diseño no depende de cuál sea el
modificador.

**Un juego que use `Start + cruceta` para algo suyo pierde esa dirección** → Es
intrínseco a cualquier combo que se trague teclas. La alternativa —no tragárselas— hace
que consultar las estadísticas mueva el cursor de los menús, que molesta más y más a
menudo.

**El gesto no llega a los mandos por el driver USB propio** → Documentado arriba. Esos
mandos siguen teniendo el menú de juego, que es el camino que había antes de este
cambio.

**Se toca un fichero muy transitado de upstream** → Se mitiga con la forma del cambio:
un método nuevo autocontenido y tres llamadas de una línea, sin tocar la lógica
existente. Un merge conflictivo se resuelve recolocando las tres llamadas.

**En los mandos sin botón Select físico, el gesto convive con la emulación de Select**
(`Start+LB` y su variante por tiempo) → No hay choque: esas emulaciones exigen que el
`inputMap` sea exactamente `Start+LB` o exactamente `Start`, y con una dirección de la
cruceta pulsada ya no lo es. La versión con Select sí tenía aquí una limitación; la de
Start no.

## Open Questions

- Si el menú de pausa que abre el Start resulta molesto en el uso diario. Solo se puede
  responder jugando, y la respuesta no cambia ni las specs ni el diseño: es cambiar qué
  botón es el modificador.
