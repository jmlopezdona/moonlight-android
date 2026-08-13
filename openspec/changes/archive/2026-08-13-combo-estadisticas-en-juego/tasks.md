## 1. Abrir un hueco en la interfaz de gestos

- [x] 1.1 En `ui/GameGestures.java`, añadir `default void togglePerformanceOverlay(boolean lite)`. Que sea `default` evita tocar el resto de implementaciones de la interfaz.

## 2. Decidir la visibilidad de los dos overlays en un solo sitio

- [x] 2.1 En `Game.java`, extraer de `toggleHUD()` un `updatePerformanceOverlayVisibility()` que ponga la visibilidad de los dos overlays a la vez, ocultando **el del modo que no toca**. Es el arreglo del bug de apilado que describe `proposal.md`.
- [x] 2.2 Dejar que `toggleHUD()` —el del menú de juego— use ese método, para que no haya dos sitios que decidan lo mismo.
- [x] 2.3 Implementar `togglePerformanceOverlay(boolean lite)`: si el modo pedido ya está en pantalla, apagar; si no, encender en ese modo. Es lo que da el «cambiar de modo de un gesto» de la spec.
- [x] 2.4 Conservar en el camino nuevo el `setOnClickListener` del overlay lite que ya ponía el arranque, para no perder el atajo de abrir el menú tocándolo.

## 3. Reconocer el gesto en el mando

- [x] 3.1 En `ControllerHandler.java`, añadir a `InputDeviceContext` el booleano del pestillo, junto a los otros campos de estado de gestos.
- [x] 3.2 Escribir `handlePerfOverlayCombo(InputDeviceContext)`: reconocer `Start+arriba` y `Start+abajo`, limpiar las direcciones del `inputMap`, y disparar el aviso una sola vez por pulsación usando el pestillo. Soltar el pestillo en cuanto el `inputMap` deje de coincidir.
- [x] 3.3 No limpiar `PLAY_FLAG`: `handleButtonUp` lo comprueba para distinguir una suelta real de Start (`ControllerHandler.java:2506`), y limpiarlo rompería los gestos de emulación de ratón y de menú de juego.
- [x] 3.4 Llamarlo desde `handleButtonDown`, justo antes de mandar el paquete y **después** de los bloques de emulación de botones, que pueden sintetizar flags.
- [x] 3.5 Llamarlo desde `handleButtonUp`, que es lo que suelta el pestillo por la ruta de teclas.
- [x] 3.6 Llamarlo desde `handleAxisSet`, tras recomponer la cruceta desde el eje HAT. Sin esta llamada el gesto no existe en los mandos que reportan la cruceta como eje.

## 4. Compilar e instalar

- [x] 4.1 Compilar la variante `nonRoot_game` con `herramientas/compilar`.
- [x] 4.2 Pasar los tests unitarios y comprobar que siguen en los 5 fallos que ya vienen rojos del upstream, sin añadir ninguno.
- [x] 4.3 Instalar en la Shield del salón con `herramientas/instalar`.

## 5. Verificar con el mando en la Shield

- [x] 5.1 Con una transmisión en curso, comprobar que `Start + cruceta arriba` saca las estadísticas completas y que repetirlo las oculta.
- [x] 5.2 Comprobar que `Start + cruceta abajo` saca las lite, y que con las completas puestas **cambia de modo** en vez de apilar los dos overlays.
- [x] 5.3 Arrancar una transmisión con las estadísticas **desactivadas** en Ajustes y comprobar que el gesto las saca con datos, no en blanco.
- [x] 5.4 Dentro de un menú del juego, hacer el gesto y comprobar que el cursor no se mueve: la cruceta no debe llegar al PC.
- [x] 5.5 Tras el gesto, comprobar que la cruceta sigue respondiendo con normalidad y que ningún botón se queda pegado.
- [x] 5.6 Comprobar que los gestos de siempre siguen vivos: abrir el menú de juego (doble Start manteniendo el segundo) y salir de la transmisión (`Start+Select+LB+RB`).
- [x] 5.7 Comprobar que al terminar y volver a empezar una transmisión el overlay vuelve a lo que digan los Ajustes.
- [x] 5.8 Valorar en el uso diario cuánto molesta que el Start abra el menú de pausa del juego — la pregunta abierta de `design.md`. Si estorba, cambiar el modificador es una constante.

## 6. Cerrar

- [x] 6.1 Commit en la rama `moonlight-noir`, mensaje en español, dejando dicho que los tres ficheros son de upstream y por qué el cambio se mantiene localizado.
- [x] 6.2 Dejar constancia de lo que quedó fuera a propósito: la persistencia del modo, la ruta USB, y que los dos botones extra del GameSir no sirven (el de debajo del grande ya es el clic de touchpad del DS4; el otro no emite nada).
