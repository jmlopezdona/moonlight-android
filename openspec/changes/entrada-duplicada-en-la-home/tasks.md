## 1. Conservar el id numérico en el trampolín

- [x] 1.1 En `ShortcutTrampoline.java`, rama del UUID (~línea 444): leer el id numérico de `EXTRA_APP_ID` del propio intent en vez de cablear `-1`, cayendo al centinela solo si el extra no viene o no parsea. El UUID sigue siendo lo que se usa para conectar.
- [x] 1.2 Actualizar el comentario `// App ID is not strictly needed if UUID is present`, que es lo que indujo el fallo: decir que el id sí hace falta aguas abajo, para la entrada del canal y su carátula.
- [x] 1.3 Comprobar que las otras dos ramas de construcción del `NvApp` (por id y por nombre) siguen dejando el `appId` que ya dejaban, y que la rama por nombre sigue escribiendo `EXTRA_APP_ID` de vuelta en el intent tras resolverlo contra la lista de apps.

## 2. Que el canal rechace lo que no puede reconocer

- [x] 2.1 En `TvChannelHelper.addGameToChannel()`, salir sin escribir nada cuando `app.getAppId() <= 0`, cubriendo el `0` de `StreamConfiguration.INVALID_APP_ID` y el `-1` del trampolín.
- [x] 2.2 Dejar constancia en el log de que se descartó la escritura, con el nombre de la app: sin eso, una entrada que no aparece en la home no tiene forma de diagnosticarse.
- [x] 2.3 Verificar que el lanzamiento sigue su curso pese al descarte — la guarda protege la home, no puede impedir jugar.

## 3. Retirar las entradas ya escritas sin identidad válida

- [x] 3.1 Añadir a `TvChannelHelper` un barrido del canal que borre las entradas cuyo `internalProviderId` no sea un entero positivo.
- [x] 3.2 Llamarlo desde `createTvChannel()`, que se ejecuta en cada lanzamiento y en cada emparejamiento nuevo, y **no** desde el camino del sondeo de servidores (ver el aviso de throttling en `ShortcutHelper.createAppViewShortcut()`).
- [x] 3.3 Comprobar que el barrido solo alcanza canales creados por la app —los que se localizan por el uuid del servidor— y que no toca entradas con id válido.

## 4. Compilar e instalar

- [x] 4.1 Construir la imagen de compilación si no está ya en la máquina (`Dockerfile.compilar`).
- [x] 4.2 Compilar la variante `nonRoot_game` con `herramientas/compilar`.
- [x] 4.3 Instalar en la Shield del salón con `herramientas/instalar` (ADB en `192.168.1.33`).

## 5. Verificar en la tele

- [x] 5.1 Abrir la app una vez y comprobar en la home que la entrada gris sin carátula ha desaparecido del canal, y que las demás siguen con la suya.
- [x] 5.2 Lanzar un juego desde su entrada de la home y comprobar que sigue habiendo **una sola** entrada de ese juego, con carátula.
- [x] 5.3 Lanzar un segundo juego desde la home y comprobar que no aparece ningún recuadro nuevo ni se re-titula ninguno — es el síntoma que delataba el fallo.
- [ ] 5.4 Con el PC apagado, lanzar un juego desde la home y comprobar que el canal no gana ninguna entrada pese a que el lanzamiento no llega a conectar.
- [ ] 5.5 Lanzar un juego desde la lista de apps de dentro de la app y comprobar que su entrada se actualiza, no se duplica.

## 6. Cerrar

- [x] 6.1 Commit en la rama `moonlight-noir`, mensaje en español, distinguiendo que el arreglo es propio del fork y no viene de upstream.
- [x] 6.2 Anotar en el cambio qué quedó fuera a propósito: los lanzamientos `.art` que solo llevan UUID dejan de refrescar su entrada, y resolver el id contra el servidor es trabajo aparte.
