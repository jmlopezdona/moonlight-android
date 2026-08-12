## Purpose

Define qué guarda un perfil de ajustes: solo las preferencias que el usuario dejó
distintas de las globales, para que un perfil signifique «esto es lo que cambio» y no
«una fotografía de todos mis ajustes del día en que lo creé».

## ADDED Requirements

### Requirement: Un perfil guarda solo lo que difiere de las globales

Al guardar un perfil, el sistema MUST persistir únicamente las preferencias cuyo valor
difiere del valor que esa misma preferencia tiene en los ajustes globales. Las
preferencias con el mismo valor que las globales NO se guardan en el perfil.

#### Scenario: Perfil que solo cambia la resolución

- **WHEN** el usuario crea un perfil, cambia únicamente la resolución a 1440p y guarda
- **THEN** el perfil contiene esa única preferencia
- **AND** al activarlo, el resto de los ajustes son los globales

#### Scenario: Devolver un valor al de las globales lo saca del perfil

- **GIVEN** un perfil que cambia resolución y bitrate
- **WHEN** el usuario lo edita, deja el bitrate en el mismo valor que tienen las
  globales y guarda
- **THEN** el perfil ya no contiene el bitrate
- **AND** al activarlo, el bitrate que se usa es el global

#### Scenario: Guardar un perfil sin cambiar nada

- **WHEN** el usuario crea un perfil y lo guarda sin modificar ninguna preferencia
- **THEN** el perfil se guarda sin preferencias
- **AND** activarlo produce exactamente el mismo comportamiento que no tener ningún
  perfil activo

### Requirement: Los ajustes globales posteriores llegan a los perfiles

Una preferencia que no está en un perfil MUST leerse de los ajustes globales, incluso
si su valor global cambia después de haber creado el perfil.

#### Scenario: Cambio global posterior visible con el perfil activo

- **GIVEN** un perfil que solo cambia la resolución, y está activo
- **WHEN** el usuario cambia el modo de ratón en los ajustes globales
- **THEN** con ese perfil activo se usa el modo de ratón nuevo
- **AND** la resolución sigue siendo la del perfil

### Requirement: Editar un perfil muestra los valores que de verdad se aplicarían

Al abrir un perfil para editarlo, cada preferencia MUST mostrarse con el valor que se
usaría al activar ese perfil: el del perfil si lo fija, y el global si no. La pantalla de
edición NO MUST mostrar valores por defecto del programa para las preferencias que el
perfil no fija.

Guardar sin tocar nada MUST NOT añadir al perfil preferencias que no tenía.

#### Scenario: Reabrir un perfil parcial

- **GIVEN** un perfil que solo fija la resolución, y unos ajustes globales con un bitrate
  distinto del valor por defecto del programa
- **WHEN** el usuario abre ese perfil para editarlo
- **THEN** la resolución se muestra con el valor del perfil
- **AND** el bitrate se muestra con el valor global, no con el valor por defecto

#### Scenario: Reabrir y guardar sin cambios no infla el perfil

- **GIVEN** un perfil que solo fija la resolución
- **WHEN** el usuario lo abre para editarlo y lo guarda sin tocar nada
- **THEN** el perfil sigue fijando solo la resolución

#### Scenario: Se distingue lo que el perfil cambia

- **GIVEN** un perfil que solo fija la resolución
- **WHEN** el usuario lo abre para editarlo
- **THEN** la resolución aparece señalada como distinta de las globales
- **AND** las demás preferencias no

### Requirement: Los perfiles completos ya existentes siguen funcionando

El sistema MUST seguir aplicando correctamente los perfiles guardados antes de este
cambio, que contienen todas las preferencias. No se requiere migración ni conversión.

#### Scenario: Perfil heredado con todas las claves

- **GIVEN** un perfil guardado por una versión anterior, con todas las preferencias
- **WHEN** el usuario lo activa
- **THEN** se aplican todos sus valores, igual que antes de este cambio

#### Scenario: Un perfil heredado se reduce al reeditarlo

- **GIVEN** un perfil heredado con todas las preferencias
- **WHEN** el usuario lo abre para editar y lo guarda sin más cambios
- **THEN** el perfil queda reducido a las preferencias que difieren de las globales
- **AND** los valores que se aplican al activarlo son en ese momento los mismos que
  antes de reeditarlo
- **AND** a partir de entonces los cambios globales posteriores sí le llegan
