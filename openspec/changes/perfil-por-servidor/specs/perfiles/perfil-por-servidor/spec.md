## Purpose

Permite asociar un perfil de ajustes a cada servidor y que ese perfil se active solo al
usarlo, de modo que cada endpoint de Apollo —la consola y cada asiento de MultiSeat—
se transmita con la resolución y el bitrate que aguanta, sin cambiarlos a mano.

## ADDED Requirements

### Requirement: Tres estados de asignación por servidor

Cada servidor MUST estar en uno de tres estados, persistidos entre arranques de la app:
**sin asignar** (el estado inicial de todo servidor), **asignado a un perfil concreto**,
o **asignado a ninguno** (usar los ajustes globales). Los tres estados son distintos y
distinguibles: «sin asignar» no toca el perfil activo, mientras que «ninguno» lo
desactiva.

#### Scenario: Estado inicial de un servidor nuevo

- **WHEN** el usuario empareja o añade un servidor
- **THEN** ese servidor queda sin asignar
- **AND** usarlo no cambia el perfil activo

#### Scenario: Asignar un perfil

- **WHEN** el usuario asigna el perfil «asiento-1440p» al servidor `padre`
- **THEN** la asignación sobrevive a cerrar y reabrir la app

#### Scenario: Asignar «ninguno» para forzar las globales

- **GIVEN** el perfil «consola-4K» está activo porque el usuario acaba de usar la consola
- **AND** el servidor `hijo` está asignado a «ninguno»
- **WHEN** el usuario entra en `hijo`
- **THEN** no queda ningún perfil activo
- **AND** se usan los ajustes globales

### Requirement: El perfil asignado se aplica al usar el servidor

El perfil asignado a un servidor MUST quedar activo antes de que se lea la
configuración de streaming, tanto al entrar en la lista de apps de ese servidor como
al lanzar cualquier app suya. La aplicación MUST cubrir **todas** las rutas que
arrancan un stream, incluidos los atajos del launcher que van directos a un juego, los
deep links y el «Reanudar» del menú contextual del servidor.

#### Scenario: Entrar en la lista de apps

- **GIVEN** el servidor `padre` está asignado al perfil «asiento-1440p»
- **WHEN** el usuario entra en `padre`
- **THEN** «asiento-1440p» queda activo
- **AND** el indicador de perfil de esa pantalla muestra su nombre

#### Scenario: Lanzar un juego

- **GIVEN** el servidor `padre` está asignado al perfil «asiento-1440p»
- **WHEN** el usuario lanza un juego en `padre`
- **THEN** el stream usa la resolución y el bitrate de «asiento-1440p»

#### Scenario: Atajo del launcher directo a un juego

- **GIVEN** el servidor `hijo` está asignado al perfil «asiento-1080p»
- **AND** el usuario tiene un atajo en el launcher a un juego de `hijo`
- **WHEN** pulsa el atajo desde la app cerrada
- **THEN** el stream usa los ajustes de «asiento-1080p»

#### Scenario: Reanudar desde el menú del servidor

- **GIVEN** el servidor `consola` está asignado al perfil «consola-4K»
- **AND** hay un juego en marcha en `consola`
- **WHEN** el usuario elige «Reanudar» en el menú contextual de `consola`
- **THEN** el stream usa los ajustes de «consola-4K»

#### Scenario: Cambiar de servidor sin salir de la app

- **GIVEN** `padre` está asignado a «asiento-1440p» y `consola` a «consola-4K»
- **WHEN** el usuario entra en `padre`, vuelve atrás y entra en `consola`
- **THEN** el indicador de perfil pasa a mostrar «consola-4K»
- **AND** un juego lanzado ahí usa 4K

#### Scenario: Volver a la lista de apps después de un stream de otro servidor

- **GIVEN** el usuario tiene abierta la lista de apps de `padre`
- **WHEN** un atajo del launcher arranca un juego de `hijo` y el usuario vuelve después
  a la lista de apps de `padre`
- **THEN** el indicador de perfil de esa pantalla vuelve a mostrar el perfil de `padre`

### Requirement: La activación es persistente y no se revierte al salir del stream

Al terminar un stream, el perfil que se activó MUST seguir activo. El sistema NO
restaura el perfil anterior.

#### Scenario: Salir del stream

- **GIVEN** el usuario ha lanzado un juego en `padre`, asignado a «asiento-1440p»
- **WHEN** cierra el stream
- **THEN** «asiento-1440p» sigue siendo el perfil activo
- **AND** el indicador de perfil lo muestra

#### Scenario: El proceso muere durante el stream

- **WHEN** el sistema mata la app mientras se juega en `padre`
- **THEN** al reabrirla el perfil activo y las asignaciones siguen siendo coherentes

### Requirement: La asignación tiene prioridad sobre la selección manual

Si el usuario activa un perfil a mano y después usa un servidor con un perfil asignado,
el perfil asignado MUST ganar.

#### Scenario: Selección manual pisada por la asignación

- **GIVEN** el usuario activa a mano el perfil «pruebas» desde la lista de perfiles
- **WHEN** entra en `padre`, que está asignado a «asiento-1440p»
- **THEN** el perfil activo pasa a ser «asiento-1440p»

#### Scenario: Selección manual respetada en un servidor sin asignar

- **GIVEN** el usuario activa a mano el perfil «pruebas»
- **WHEN** entra en un servidor sin asignar
- **THEN** «pruebas» sigue activo

### Requirement: Aplicar una asignación nunca impide usar un servidor

Un fallo al resolver o aplicar la asignación MUST ser silencioso y no bloqueante: la
app MUST poder entrar en el servidor y lanzar el juego igual, con el perfil que hubiera
activo.

#### Scenario: El fichero de perfiles no se pudo cargar al arrancar

- **GIVEN** el almacén de perfiles falló al cargarse al arrancar la app
- **WHEN** el usuario lanza un juego
- **THEN** el juego arranca
- **AND** no se muestra ningún error por esto

#### Scenario: Servidor sin identificador utilizable

- **WHEN** se intenta aplicar la asignación de un servidor cuyo identificador es nulo o
  vacío
- **THEN** no se cambia el perfil activo y la operación no falla

### Requirement: No se reescribe el almacén si el perfil no cambia

Si el perfil que corresponde a un servidor ya es el activo, el sistema MUST NOT
reescribir el almacén de perfiles ni notificar un cambio. Esta ruta se ejecuta en cada
lanzamiento de un juego.

#### Scenario: Lanzar dos juegos seguidos en el mismo servidor

- **GIVEN** `padre` está asignado a «asiento-1440p», ya activo
- **WHEN** el usuario lanza un juego, sale y lanza otro
- **THEN** el almacén de perfiles no se ha reescrito por causa de la asignación

### Requirement: Las asignaciones no quedan huérfanas

El sistema MUST eliminar las asignaciones que apunten a algo que ya no existe.

#### Scenario: Borrar un perfil asignado a varios servidores

- **GIVEN** «asiento-1080p» está asignado a `hijo` y a `invitado`
- **WHEN** el usuario borra ese perfil
- **THEN** `hijo` e `invitado` quedan sin asignar
- **AND** entrar en ellos no cambia el perfil activo ni produce ningún error

#### Scenario: Borrar un servidor

- **GIVEN** `hijo` está asignado a «asiento-1080p»
- **WHEN** el usuario borra el servidor `hijo`
- **THEN** su asignación desaparece
- **AND** si vuelve a emparejarlo, aparece sin asignar

#### Scenario: Asignación que apunta a un perfil inexistente

- **GIVEN** el almacén contiene una asignación a un perfil que ya no existe
- **WHEN** el usuario usa ese servidor
- **THEN** se trata como si estuviera sin asignar
- **AND** la asignación rota se elimina del almacén

### Requirement: Compatibilidad del almacén de perfiles

El almacén MUST leerse sin error tanto si contiene asignaciones como si no.

#### Scenario: Fichero de una versión anterior

- **GIVEN** un almacén de perfiles escrito por una versión sin esta función
- **WHEN** la app arranca
- **THEN** los perfiles se cargan con normalidad
- **AND** todos los servidores aparecen sin asignar

### Requirement: El usuario puede asignar el perfil desde el menú del servidor

El menú contextual de un servidor MUST ofrecer una entrada para elegir su perfil, y
MUST estar disponible independientemente del estado del servidor, incluso apagado o no
emparejado. El diálogo MUST ser de selección única, ofrecer las opciones «sin asignar»
y «ninguno» además de los perfiles existentes, y MUST mostrar preseleccionado el estado
actual.

#### Scenario: Asignar un perfil a un servidor apagado

- **GIVEN** el servidor `padre` está apagado
- **WHEN** el usuario abre su menú contextual
- **THEN** la entrada para elegir el perfil está disponible
- **AND** puede asignar uno

#### Scenario: El diálogo muestra el estado actual

- **GIVEN** `padre` está asignado a «asiento-1440p»
- **WHEN** el usuario abre el diálogo de perfil de `padre`
- **THEN** «asiento-1440p» aparece marcado

#### Scenario: Confirmación al asignar

- **WHEN** el usuario elige un perfil en el diálogo
- **THEN** el diálogo se cierra
- **AND** se confirma en pantalla qué perfil usará ese servidor
- **AND** la asignación queda guardada

#### Scenario: No hay ningún perfil creado todavía

- **GIVEN** el usuario no ha creado ningún perfil
- **WHEN** abre la entrada de perfil de un servidor
- **THEN** se le indica que no hay perfiles
- **AND** se le lleva a la pantalla de gestión de perfiles

### Requirement: El perfil asignado se ve sin abrir el diálogo

El menú contextual de un servidor MUST indicar, en su cabecera, qué perfil tiene
asignado, para poder revisarlo sin entrar en el diálogo de cada servidor.

#### Scenario: Cabecera de un servidor con perfil

- **GIVEN** `padre` está asignado a «asiento-1440p»
- **WHEN** el usuario abre su menú contextual
- **THEN** la cabecera nombra «asiento-1440p» junto al nombre y el estado del servidor

#### Scenario: Cabecera de un servidor sin asignar

- **WHEN** el usuario abre el menú contextual de un servidor sin asignar
- **THEN** la cabecera es la de siempre, sin mención de ningún perfil

### Requirement: La pantalla de ajustes avisa si un perfil los está tapando

Los ajustes de streaming leen y escriben los valores globales. Cuando hay un perfil
activo, esa pantalla MUST avisar de que lo que se está viendo y editando son los
globales y que el perfil activo los está tapando.

#### Scenario: Abrir los ajustes con un perfil activo

- **GIVEN** el perfil «asiento-1440p» está activo
- **WHEN** el usuario abre los ajustes de streaming
- **THEN** se le indica que esos valores son los globales y que «asiento-1440p» los
  tapa

#### Scenario: Abrir los ajustes sin perfil activo

- **GIVEN** no hay ningún perfil activo
- **WHEN** el usuario abre los ajustes de streaming
- **THEN** no aparece ningún aviso
