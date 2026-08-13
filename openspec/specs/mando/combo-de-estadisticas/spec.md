# combo-de-estadisticas Specification

## Purpose

Define cómo se muestran, se ocultan y se cambian de modo las estadísticas de rendimiento
desde el mando durante una partida, sin pasar por menús, y qué recibe el juego del PC
mientras el usuario hace ese gesto.

## Requirements

### Requirement: Mostrar y ocultar las estadísticas con el mando

Durante una transmisión, el usuario MUST poder mostrar y ocultar el overlay de
estadísticas de rendimiento con una combinación de botones del mando, sin abrir ningún
menú.

El gesto MUST distinguir los dos modos del overlay: uno pide el **completo** y otro el
**lite**. Repetir el gesto del modo que ya está en pantalla MUST ocultar el overlay.

El overlay MUST empezar a mostrar datos aunque la transmisión se haya arrancado con las
estadísticas desactivadas en los Ajustes: no puede quedarse en blanco por no haberlas
pedido antes de empezar.

#### Scenario: Sacar las estadísticas en mitad de la partida

- **GIVEN** una transmisión en curso sin estadísticas en pantalla
- **WHEN** el usuario hace el gesto del modo completo
- **THEN** el overlay completo aparece
- **AND** sus cifras se actualizan mientras siga en pantalla

#### Scenario: Ocultarlas con el mismo gesto

- **GIVEN** el overlay completo en pantalla
- **WHEN** el usuario repite el gesto del modo completo
- **THEN** el overlay desaparece

#### Scenario: La transmisión arrancó con las estadísticas desactivadas

- **GIVEN** una transmisión arrancada con las estadísticas desactivadas en los Ajustes
- **WHEN** el usuario hace el gesto del modo lite
- **THEN** el overlay lite aparece con datos, no vacío

### Requirement: Cambiar de modo sin cortar la partida

El modo del overlay MUST poder cambiarse durante la transmisión. Pedir un modo mientras
el otro está en pantalla MUST cambiar de modo, no ocultar el overlay ni exigir apagarlo
antes.

MUST verse **un solo modo a la vez**: al cambiar de modo, el anterior desaparece de la
pantalla.

#### Scenario: De lite a completo de un gesto

- **GIVEN** el overlay lite en pantalla
- **WHEN** el usuario hace el gesto del modo completo
- **THEN** se ve el overlay completo
- **AND** el overlay lite ya no se ve

#### Scenario: Los dos modos no se apilan

- **GIVEN** el usuario ha alternado varias veces entre los dos modos
- **WHEN** hay un overlay en pantalla
- **THEN** solo se ve uno de los dos, nunca los dos superpuestos

### Requirement: El gesto no se cuela en el juego como movimiento

El juego del PC MUST NOT recibir la dirección de la cruceta que forma parte del gesto.
Consultar las estadísticas no puede mover el cursor de un menú ni al personaje.

Los botones del mando MUST seguir respondiendo con normalidad en cuanto termina el
gesto: ninguno puede quedarse pulsado.

#### Scenario: Consultar las estadísticas dentro de un menú del juego

- **GIVEN** el juego del PC tiene un menú abierto con un cursor
- **WHEN** el usuario hace el gesto para mostrar las estadísticas
- **THEN** el cursor del menú no se mueve

#### Scenario: La cruceta sigue viva después del gesto

- **GIVEN** el usuario acaba de hacer el gesto y ha soltado los botones
- **WHEN** vuelve a usar la cruceta
- **THEN** el juego recibe esas direcciones con normalidad

### Requirement: El gesto funciona sea cual sea la forma de reportar la cruceta

El gesto MUST funcionar en los mandos que reportan la cruceta como botones y en los que
la reportan como eje. Cuál de las dos usa un mando MUST NOT ser observable por el
usuario.

#### Scenario: Mando que reporta la cruceta como eje

- **GIVEN** un mando cuya cruceta llega al sistema como eje y no como botones
- **WHEN** el usuario hace el gesto
- **THEN** las estadísticas responden igual que en un mando que la reporta como botones

### Requirement: Los gestos de mando que ya existían siguen funcionando

Añadir este gesto MUST NOT romper ninguna de las combinaciones de mando que ya
funcionaban: salir de la transmisión, abrir el menú de juego, alternar la emulación de
ratón y las emulaciones de botón de los mandos que no tienen todos los botones físicos.

#### Scenario: El menú de juego sigue abriéndose

- **GIVEN** una transmisión en curso
- **WHEN** el usuario hace el gesto de abrir el menú de juego
- **THEN** el menú de juego se abre

#### Scenario: Salir de la transmisión sigue funcionando

- **GIVEN** una transmisión en curso
- **WHEN** el usuario hace la combinación de salir
- **THEN** la transmisión termina

### Requirement: Lo elegido con el mando no sobrevive a la transmisión

Lo que el usuario muestre, oculte o cambie de modo con el mando MUST valer solo para la
transmisión en curso. Al empezar una transmisión nueva, el overlay MUST volver a lo que
digan los Ajustes.

Este gesto MUST NOT escribir en las preferencias guardadas.

#### Scenario: Una transmisión nueva vuelve a los Ajustes

- **GIVEN** los Ajustes tienen las estadísticas desactivadas
- **AND** el usuario las mostró con el mando durante una partida
- **WHEN** termina esa transmisión y empieza otra
- **THEN** la transmisión nueva empieza sin estadísticas en pantalla
