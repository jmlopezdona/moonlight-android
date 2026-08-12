## Purpose

Permite compilar el APK, pasar los tests unitarios e instalarlo en el aparato de juego
desde el servidor de casa, que no tiene JDK ni SDK ni NDK de Android, sin instalarle
nada de eso.

## ADDED Requirements

### Requirement: Compilar sin instalar herramientas en la máquina

El proyecto MUST poder compilarse con un único comando desde un árbol recién clonado,
sin que la máquina tenga JDK, SDK ni NDK de Android instalados. Las versiones exigidas
por el proyecto (JDK 17 o superior, SDK de compilación 36, NDK 27.0.12077973) MUST
estar declaradas en un solo sitio, para que subirlas sea editar ahí.

#### Scenario: Primera compilación

- **GIVEN** una máquina con Docker y sin JDK ni SDK de Android
- **WHEN** se lanza el comando de compilar
- **THEN** el entorno se prepara solo la primera vez
- **AND** se produce el APK de depuración de la variante que se usa en casa

#### Scenario: Se elige la variante explícitamente

- **WHEN** se lanza el comando de compilar sin argumentos
- **THEN** compila la variante de depuración del flavor que se usa en casa, no una
  tarea ambigua que valga para cualquier flavor

#### Scenario: Segunda compilación

- **WHEN** se vuelve a compilar sin haber cambiado nada
- **THEN** no se descargan de nuevo ni la distribución de Gradle ni las dependencias

### Requirement: El APK de pruebas no pone en riesgo la app real

El APK de depuración MUST poder convivir instalado a la vez que la Artemis de uso
diario, sin sustituirla, sin exigir desinstalarla y sin tocar sus emparejamientos ni
sus perfiles.

#### Scenario: Instalar la de pruebas con la real instalada

- **GIVEN** el aparato tiene instalada la Artemis de uso diario, con sus servidores
  emparejados
- **WHEN** se instala el APK de depuración
- **THEN** aparecen las dos apps por separado
- **AND** los emparejamientos y los perfiles de la app de uso diario quedan intactos

### Requirement: La firma de depuración es estable entre compilaciones

Compilaciones sucesivas MUST firmar el APK con la misma clave, de modo que instalar
encima de una versión anterior de la app de pruebas funcione sin desinstalarla.

#### Scenario: Reinstalar encima

- **GIVEN** el APK de depuración ya está instalado en el aparato
- **WHEN** se compila de nuevo y se instala encima
- **THEN** la instalación se actualiza sin pedir desinstalar
- **AND** no falla por firma incompatible

### Requirement: Los ficheros producidos pertenecen al usuario

El APK y los intermedios de la compilación MUST quedar con el usuario que lanzó el
comando como propietario, no con `root`.

#### Scenario: Borrar los intermedios

- **WHEN** el usuario que compiló quiere borrar los directorios de salida
- **THEN** puede hacerlo sin privilegios de administrador

### Requirement: Pasar los tests unitarios

Los tests unitarios del proyecto MUST poder ejecutarse con el mismo entorno, sin
dispositivo ni emulador.

#### Scenario: Ejecutar la suite

- **WHEN** se pide ejecutar los tests unitarios de la variante que se usa en casa
- **THEN** se ejecutan y su resultado se reporta
- **AND** un test que falla hace fallar el comando

### Requirement: Instalar en el aparato de juego

MUST existir un comando que instale el APK ya compilado en el aparato desde el que se
juega, por red, usando el mismo entorno. El destino MUST poder indicarse al invocarlo y
MUST tener un valor por defecto.

#### Scenario: Instalar en el aparato por defecto

- **GIVEN** hay un APK compilado
- **WHEN** se lanza el comando de instalar sin argumentos
- **THEN** se instala en el aparato de juego habitual

#### Scenario: Instalar en otro aparato

- **WHEN** se lanza el comando de instalar indicando otra dirección
- **THEN** se instala en ese aparato

#### Scenario: No hay APK todavía

- **WHEN** se lanza el comando de instalar sin haber compilado antes
- **THEN** falla con un mensaje que dice que hay que compilar primero

### Requirement: Fallos de entorno con diagnóstico claro

Cuando falte algo del entorno, el comando MUST fallar con un mensaje que diga qué falta
y cómo arreglarlo, en vez de con un error de compilación que no lo explique.

#### Scenario: Submódulos sin inicializar

- **GIVEN** el árbol se clonó sin los submódulos
- **WHEN** se lanza el comando de compilar
- **THEN** falla indicando que hay que inicializar los submódulos, antes de intentar
  compilar el código nativo
