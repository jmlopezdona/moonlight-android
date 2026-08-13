# Especificación de entradas-de-juego

## Purpose

Define qué representa una entrada de juego en el canal que la app publica en la home de
Android TV: cuándo se crea, cuándo se actualiza en vez de duplicarse, y qué debe ocurrir
cuando un lanzamiento llega sin una identidad de app utilizable.

## Requirements

### Requirement: Un juego, una sola entrada por servidor

El canal de un servidor MUST tener como mucho **una** entrada por juego. Lanzar un juego
MUST actualizar su entrada existente —título, carátula, destino y orden— en vez de añadir
una segunda.

Esto MUST cumplirse **por cualquiera de las vías de lanzamiento**: desde la lista de apps
dentro de la app, desde la propia entrada de la home, desde un acceso directo anclado o
desde un enlace externo. Que un servidor identifique sus apps con más de un identificador
—hoy Apollo da a cada app un UUID y un id numérico— no MUST ser observable: dos
lanzamientos del mismo juego son el mismo juego, se haya llegado por donde se haya
llegado.

#### Scenario: Lanzar desde la home un juego que ya tiene entrada

- **GIVEN** el juego «Hades II» ya tiene su entrada en el canal del servidor
- **WHEN** el usuario lo lanza pulsando esa misma entrada en la home
- **THEN** el canal sigue teniendo una sola entrada de «Hades II»
- **AND** esa entrada conserva su carátula

#### Scenario: La misma app lanzada por dos vías distintas

- **GIVEN** el usuario lanza «Hades II» desde la lista de apps del servidor
- **WHEN** después lo lanza desde su entrada de la home
- **THEN** el canal sigue teniendo una sola entrada de «Hades II»

#### Scenario: El lanzamiento no llega a conectar

- **GIVEN** el PC del servidor está apagado o no responde
- **WHEN** el usuario lanza un juego desde su entrada de la home
- **THEN** el canal no gana ninguna entrada nueva

### Requirement: Una entrada del canal enseña la carátula de su juego

Toda entrada que la app publique en el canal MUST enseñar la carátula del juego al que
representa siempre que esa carátula esté en la caché del dispositivo. Una entrada
publicada por la app MUST NOT quedarse sin imagen por haber pedido la carátula de una
app que no existe.

#### Scenario: Carátula ya descargada

- **GIVEN** la carátula de «Hades II» está en la caché porque el juego ya se vio en la
  lista de apps
- **WHEN** el juego se lanza desde la home
- **THEN** su entrada del canal enseña esa carátula

### Requirement: Un lanzamiento sin identidad utilizable no escribe en el canal

Cuando un lanzamiento llega sin una identidad de app con la que poder reconocer después
esa misma app, la app MUST NOT crear ni actualizar ninguna entrada del canal. El
lanzamiento en sí MUST seguir su curso: no poder anotarlo en la home no es motivo para
no jugar.

#### Scenario: Lanzamiento sin identidad

- **WHEN** llega un lanzamiento del que no se puede determinar qué app es
- **THEN** el canal del servidor queda exactamente como estaba
- **AND** el juego se lanza igualmente

### Requirement: Las entradas sin identidad válida se retiran

La app MUST retirar del canal las entradas que publicó con una identidad de app no
válida, sin esperar a que el usuario las quite a mano. La retirada MUST ocurrir sin
intervención del usuario tras actualizar la app, y MUST NOT tocar las entradas con
identidad válida ni las publicadas por otras aplicaciones.

#### Scenario: Limpieza al actualizar

- **GIVEN** el canal arrastra una entrada gris con una identidad no válida, escrita por
  una versión anterior de la app
- **WHEN** la app refresca ese canal
- **THEN** la entrada gris desaparece del canal
- **AND** las demás entradas del canal siguen ahí, con sus carátulas
