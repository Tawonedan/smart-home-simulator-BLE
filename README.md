# Smart Home Simulator

Simulador 2D de propagacion inalambrica en interiores orientado a pruebas de cobertura y calidad de enlace en viviendas, hoteles, oficinas, clinicas, almacenes y naves industriales.

El proyecto permite colocar sensores y un hub sobre un plano visto desde arriba, cargar plantillas de interiores, cambiar materiales de paredes, lanzar simulaciones en modo rayos u ondas y analizar mapas de potencia, SNR, BER y capacidad.

## Estado actual

Lo que ya esta implementado y funcionando en el codigo:

- Interfaz JavaFX para construir escenas interiores de forma visual.
- Gestion de sensores y hub sobre una rejilla de 1 celda = 1 metro.
- Motor legacy de ray tracing para visualizar trayectorias y rebotes.
- Motor indoor basado en ondas con suma coherente por fase.
- Trayectorias directas, reflejadas, difractadas y dispersadas.
- Materiales con perdidas de transmision, reflexion y dispersion.
- Fading configurable: sin fading, Rayleigh y Rician.
- Antenas omni y direccionales con orientacion, apertura y patron.
- Polarizacion simplificada con perdidas por desalineacion.
- Heatmaps de potencia, SNR/SINR, BER y capacidad.
- Interferencia entre multiples sensores.
- Ventana de resumen con validacion numerica de formulas.
- Catalogo de plantillas interiores para distintos tipos de edificio.

## Para que sirve

Este simulador esta pensado para estudiar como se comportan las senales de dispositivos inalambricos en entornos cerrados. Algunos usos tipicos:

- Comparar la cobertura de distintas ubicaciones de sensores y hub.
- Ver zonas muertas o sombras por paredes y giros de pasillo.
- Analizar el impacto de materiales como hormigon, ladrillo o cristal.
- Observar interferencias entre varios emisores.
- Estimar SNR, BER y capacidad teorica en distintos puntos.
- Probar escenarios interiores realistas sin dibujar todo desde cero.

## Flujo de trabajo en la interfaz

La app principal vive en `src/main/java/UI/VisualGridApp.java` y organiza el panel lateral en cinco grupos.

### 1. Dispositivos

Permite trabajar con los emisores y el receptor:

- Anadir sensores en una coordenada `(X, Y)`.
- Anadir un hub en una coordenada `(X, Y)`.
- Eliminar el sensor seleccionado o el hub.
- Seleccionar un sensor para editar su antena.
- Guardar configuracion de antena y polarizacion.

Parametros configurables del sensor:

- Tipo de antena: `Omni` o `Direccional`.
- Orientacion en grados.
- Apertura del haz en grados.
- Ganancia Tx en dB.
- Patron `G(theta)` mediante un factor de nitidez.
- Polarizacion en grados.

### 2. Mapa y simulacion

Controla como se calcula y como se pinta el mapa:

- Frecuencia de trabajo: `2.4 GHz` o `5 GHz`.
- Metrica del heatmap:
  - `Potencia (dBm)`
  - `SNR / SINR (dB)`
  - `BER`
  - `Capacidad (Mbps)`
- Modo de propagacion:
  - `Rayos`
  - `Ondas`
- Modelo de fading:
  - `Sin fading`
  - `Rayleigh`
  - `Rician`
- Activacion o desactivacion de:
  - difraccion
  - dispersion
  - calculo en paralelo

Acciones del grupo:

- `Mostrar o actualizar mapa`
- `Ocultar mapa`

### 3. Entorno y radio

Permite ajustar los parametros fisicos principales del simulador:

- Exponente indoor `n` del modelo log-distance.
- `K-factor Rician` en dB.
- Umbral de `culling` en dBm para recortar trayectorias muy debiles.
- Ancho de banda en Hz.
- Figura de ruido en dB.

Estos parametros afectan al ruido termico, la perdida con distancia, el fading y las metricas de enlace.

### 4. Plantillas

Permite cargar escenarios interiores predefinidos o vaciar el plano:

- `Cargar escenario`
- `Vaciar plano`

Cada plantilla define paredes, materiales y espesores para simular interiores vistos desde arriba.

### 5. Acciones rapidas

Accesos directos a las tareas mas habituales:

- `Lanzar rayos`
- `Lanzar ondas`
- `Resumen de parametros`

## Resumen de parametros

La ventana de resumen esta pensada para interpretar la simulacion y comprobar que la interfaz refleja correctamente lo que calcula el motor.

Incluye cuatro pestanas:

- `General`: escenario activo, materiales, parametros de radio y configuracion del modelo.
- `Hub`: estado combinado en el receptor, sensor dominante, interferencia, SINR, BER, capacidad y trayectorias dominantes.
- `Sensores`: configuracion individual de cada emisor y, si existe hub, el enlace sensor-hub.
- `Validacion`: comprobaciones internas de ruido, SNR, SINR, BER, capacidad y margen con las mismas formulas del motor.

## Sonda visual y tooltips

Ademas del resumen, la interfaz muestra informacion contextual mientras se usa el plano:

- Al pasar el raton sobre el mapa, aparece una sonda de celda con:
  - sensor dominante
  - potencia total
  - senal util
  - interferencia
  - ruido
  - SNR
  - SINR
  - BER
  - capacidad
  - margen
  - fase del campo
  - numero de trayectorias
- Las paredes muestran su material, espesor y perdida aproximada.
- El hub y los sensores muestran tooltips con sus datos principales.

## Edicion de materiales en paredes

Se puede hacer clic derecho sobre una pared para cambiar rapidamente su material desde la interfaz.

Opciones disponibles en el menu contextual:

- Hormigon
- Ladrillo
- Tabique
- Cristal
- Puerta metalica

Ademas, el motor y las plantillas tambien usan madera cuando el escenario lo requiere.

## Modelos de propagacion

### Modo Rayos

Es el modelo visual y geometrico del proyecto. Cada sensor emite rayos y el sistema calcula intersecciones con paredes, reflexiones y transmisiones.

Sirve especialmente para:

- ver trayectorias
- entender rebotes
- inspeccionar impactos contra el hub
- estudiar perdida por distancia y materiales

Metricas del ray tracing legacy:

- distancia recorrida
- numero de rebotes
- potencia de transmision efectiva
- FSPL
- potencia recibida
- SNR
- RSSI
- margen de enlace
- BER aproximada
- capacidad teorica de Shannon

### Modo Ondas

Es el motor principal para mapas y analisis por celda. Usa contribuciones complejas con fase para sumar campo de forma coherente.

En este modo se modelan:

- trayecto directo
- reflexiones especulares
- difraccion tipo knife-edge simplificada
- dispersion por superficie
- atenuacion por materiales
- perdida indoor tipo log-distance
- fading Rayleigh o Rician
- desajuste de polarizacion

La potencia total del mapa puede incluir interferencias constructivas o destructivas por fase, mientras que la interferencia usada para SINR se agrega de forma incoherente por sensor dominante frente al resto.

## Trayectorias contempladas por el motor

Cada celda puede acumular varias contribuciones. El resumen y la sonda muestran las mas fuertes.

Tipos de contribucion:

- `Directo`
- `Reflexion`
- `Difraccion`
- `Dispersion`

Cada contribucion guarda:

- sensor de origen
- tipo de trayectoria
- distancia recorrida
- ganancia de antena
- perdidas por paredes y materiales
- perdida de difraccion
- perdida de dispersion
- perdida por polarizacion
- potencia recibida
- fase
- numero de rebotes

## Antenas y polarizacion

Los sensores soportan un modelo simple pero util para pruebas comparativas:

- antena omni o direccional
- orientacion angular
- apertura del haz
- ganancia de transmision
- nitidez del patron
- atenuacion lateral
- polarizacion

El hub soporta:

- ganancia de recepcion
- polarizacion

La perdida por polarizacion se calcula con una aproximacion basada en el acoplamiento angular entre emisor y receptor.

## Materiales modelados

La base de materiales incluida actualmente es:

- Tabique (pladur)
- Ladrillo
- Hormigon
- Cristal
- Madera
- Puerta metalica

Cada material aporta informacion para:

- perdida de transmision
- perdida de reflexion
- perdida de dispersion
- coeficiente de transmision
- coeficiente de reflexion

## Parametros fisicos y de simulacion

### Parametros del entorno

- Frecuencia central en MHz.
- Ancho de banda en Hz.
- Figura de ruido en dB.
- Ganancia de sistema en dB.
- Atenuacion adicional por metro en dB/m.

### Parametros del modelo

Parametros visibles en la UI principal:

- Exponente `n` del modelo log-distance.
- `K-factor` del modelo Rician.
- Umbral de `culling`.
- Activacion de difraccion, dispersion y paralelismo.

Parametros internos del motor que tambien aparecen reflejados en el resumen:

- Numero maximo de trayectorias de reflexion.
- Numero maximo de trayectorias de difraccion.
- Numero maximo de trayectorias de dispersion.

## Metricas que calcula el simulador

### Metricas por celda y por enlace

- `Potencia total (dBm)`: potencia total recibida en la celda.
- `Senal util (dBm)`: potencia del sensor dominante.
- `Interferencia (dBm)`: potencia agregada del resto de sensores.
- `Ruido (dBm)`: suelo de ruido termico mas figura de ruido.
- `SNR (dB)`: relacion entre senal util y ruido.
- `SINR (dB)`: relacion entre senal util y ruido mas interferencia.
- `BER`: estimacion para BPSK.
- `Capacidad (Mbps)`: capacidad teorica de Shannon a partir de SINR.
- `Margen de enlace (dB)`: diferencia entre senal util y sensibilidad del receptor.
- `Fase del campo`: fase total de la suma coherente.
- `Trayectorias`: numero total de contribuciones consideradas.

### Formulas base usadas en el proyecto

- `FSPL(dB) = 32.45 + 20 log10(f_MHz) + 20 log10(d_km)`
- `Ruido(dBm) = -174 + 10 log10(BW_Hz) + NF`
- `Capacidad = BW * log2(1 + SINR_lineal)`

BER:

- Se usa una aproximacion de BPSK sobre AWGN.

## Plantillas interiores incluidas

El simulador incorpora un catalogo de escenarios listos para probar:

- `Estudio compacto`: vivienda pequena con zona abierta, bano y dormitorio parcialmente separado.
- `Apartamento 2 dormitorios`: piso urbano con salon, cocina, dos dormitorios y nucleo de bano central.
- `Casa con pasillo`: vivienda familiar con corredor principal y estancias a ambos lados.
- `Casa en L`: planta con giros y zonas de sombra utiles para estudiar cobertura irregular.
- `Planta hotel`: corredor central con habitaciones repetidas.
- `Suite de hotel`: mezcla de salon, dormitorio, vestidor y bano con tabiques y cristal.
- `Oficina abierta`: open space con salas de reunion y nucleo lateral.
- `Oficina compartimentada`: despachos a ambos lados de un corredor.
- `Clinica`: boxes y consultas alrededor de un eje central.
- `Aula y laboratorio`: espacio docente con laboratorio y almacen tecnico.
- `Almacen con pasillos`: estanterias metalicas y bloque auxiliar.
- `Nave industrial`: espacio amplio con celdas de maquinaria y cuartos tecnicos.

## Estructura del proyecto

Proyecto Gradle con la estructura estandar (`src/main/java`):

- `src/main/java/UI/`
  - Interfaz JavaFX principal.
  - Ventana principal: `UI.VisualGridApp`; punto de entrada: `UI.Launcher`.
- `src/main/java/core/`
  - Entidades base como `Sensor`, `Hub`, `Wall` y utilidades de propagacion (`Propagation`).
- `src/main/java/core/sim/`
  - Motor indoor de simulacion por celdas: `IndoorWaveEngine`, `CellResult`, `HeatmapResult`, `SimulationSettings`, etc.
- `src/main/java/core/material/`
  - Materiales, interacciones pared-onda y base de materiales.
- `src/main/java/env/`
  - Definicion del entorno fisico y plantillas interiores.
- `build.gradle.kts`, `settings.gradle.kts`, `gradlew`, `gradlew.bat`, `gradle/wrapper/`
  - Build con Gradle Wrapper: no hace falta instalar Gradle ni JavaFX.

## Requisitos

- JDK 21 o superior (`java -version` para comprobarlo).

Gradle y JavaFX (21 LTS) se descargan automaticamente la primera vez que se ejecuta el wrapper.

## Compilacion y ejecucion

```bash
# Windows
gradlew.bat run

# Linux / macOS
./gradlew run
```

Otras tareas utiles:

- `gradlew build`: compila y genera `build/distributions/smart-home-simulator-*.zip` con scripts de arranque.
- `gradlew clean`: borra la carpeta `build/`.

Para abrirlo en un IDE, importalo como proyecto Gradle (IntelliJ IDEA, Eclipse con Buildship o VS Code con Extension Pack for Java).

## Limitaciones actuales

Para interpretar bien los resultados conviene tener presentes estas simplificaciones:

- La simulacion es 2D y en vista superior; no modela altura ni volumen 3D.
- La BER esta aproximada para BPSK, no para todos los esquemas de modulacion.
- El modo rayos es util para visualizacion y diagnostico, pero el analisis de mapa lo resuelve principalmente el motor por celdas.
- La frecuencia seleccionable desde la UI esta pensada para 2.4 GHz y 5 GHz.
- No hay movilidad temporal ni trafico real de protocolos; el foco esta en propagacion y enlace.

## Mejoras futuras

Si el objetivo es hacer el simulador mucho mas fiel a la realidad y mas util para diseno y validacion, estas serian las lineas de evolucion con mas impacto.

### 1. Mejoras de mayor impacto

- Pasar de 2D a `2.5D` o `3D`:
  - altura de sensores y hub
  - techo y suelo
  - muebles altos y obstaculos verticales
  - varias plantas o niveles
- Calibracion con medidas reales:
  - importar mediciones RSSI/SNR tomadas en campo
  - ajustar materiales, `n`, perdidas y `K-factor`
  - reducir la distancia entre simulacion y entorno real
- Modelos por tecnologia:
  - perfiles especificos para `Wi-Fi`, `BLE`, `Zigbee`, `LoRa`, etc.
  - sensibilidad, potencia, ancho de banda y canales propios de cada tecnologia
- Metricas de enlace mas realistas:
  - `MCS`
  - modulacion y codificacion
  - `PER` o perdida de paquetes
  - throughput util, no solo capacidad de Shannon
- Antenas mas reales:
  - importar patrones medidos o tablas reales
  - comparar antenas comerciales o configuraciones concretas

### 2. Mejoras para ser mas fiel a la realidad

- Materiales mas detallados:
  - dependencia con frecuencia, angulo, espesor y humedad
  - puertas abiertas o cerradas
  - ventanas y particiones ligeras
- Obstaculos interiores adicionales:
  - personas
  - estanterias
  - armarios
  - maquinaria
  - electrodomesticos
- Multipath mas avanzado:
  - reflexiones de orden superior mas robustas
  - difraccion mejor modelada
  - dispersion ligada a rugosidad real
- Fading espacial y temporal coherente:
  - evitar que el mapa parezca ruido independiente entre celdas
  - modelar pequenas variaciones al mover dispositivos o recorrer el entorno
- Interferencias de canal:
  - canales solapados
  - ocupacion del medio
  - ruido externo
  - coexistencia entre varias redes
- Soporte para `MIMO` y `OFDM`:
  - especialmente importante si el objetivo es aproximarse mas a Wi-Fi moderno

### 3. Mejoras para ser mas util en la practica

- Editor de planos mas completo:
  - dibujar habitaciones, puertas y ventanas
  - ajustar cotas
  - importar planos o referencias
- Biblioteca de escenarios aun mas amplia:
  - viviendas
  - hoteles
  - oficinas
  - hospitales
  - almacenes
  - naves
- Ayuda a la colocacion:
  - sugerir donde poner hub, sensores o puntos de acceso
  - maximizar cobertura
  - minimizar zonas muertas e interferencias
- Objetivos de diseno:
  - por ejemplo, exigir un minimo de potencia, SINR o capacidad en cierto porcentaje del plano
- Comparacion entre configuraciones:
  - antes y despues
  - dos ubicaciones
  - dos tecnologias
  - diferencia entre mapas
- Exportacion de resultados:
  - informes PDF
  - tablas CSV
  - configuraciones en JSON
- Evaluacion por zonas de uso:
  - dormitorio
  - pasillo
  - recepcion
  - almacen
  - area de maquinaria
- Alertas automaticas:
  - zonas sin cobertura
  - enlaces con margen bajo
  - interferencia excesiva
  - materiales especialmente criticos

### 4. Prioridad recomendada

Si hubiese que priorizar el desarrollo futuro, una hoja de ruta muy razonable seria:

1. Calibracion con mediciones reales.
2. Editor e importacion de planos con materiales mas completos.
3. Perfiles por tecnologia con metricas de throughput y enlace mas realistas.
4. Paso a `2.5D` o `3D`.
5. Optimizacion automatica de colocacion de dispositivos.

## Resumen rapido

Hoy el proyecto ya permite crear escenarios interiores, simular cobertura de varios dispositivos, estudiar materiales y comparar metricas de calidad de enlace con una interfaz visual. No es solo un visor de rayos: ya incluye un motor indoor de ondas con interferencia, fading, difraccion, dispersion, polarizacion, BER, capacidad y validacion numerica de resultados.
