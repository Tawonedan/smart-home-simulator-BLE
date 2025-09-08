# 📡 Smart Home Simulator

Este proyecto es un simulador de propagación inalámbrica en un entorno de hogar inteligente.  
Permite visualizar rayos de señal, calcular parámetros de comunicación y próximamente representar ondas de forma más realista.

---

## 🔧 Parámetros medidos (RayMetrics)
En cada simulación se registran los siguientes parámetros de cada rayo:

- `distanceTraveled` → Distancia recorrida (m)  
- `numBounces` → Número de rebotes  
- `txDbm` → Potencia de transmisión efectiva (dBm)  
- `fsplDb` → Pérdida en espacio libre (dB)  
- `prxDbm` → Potencia recibida final (dBm)  
- `snrDb` → Relación señal/ruido (dB)  
- `rssi` → Intensidad de señal recibida (dBm)  
- `linkMargin` → Margen de enlace respecto a la sensibilidad (dB)  
- `ber` → Tasa de error de bit (aprox. para BPSK)  
- `channelCapacityMbps` → Capacidad de canal (Mbps, teórica de Shannon)  

---

## 🔦 Modelo actual: Ray Tracing
Actualmente el simulador funciona mediante un **modelo geométrico de rayos**:

1. **Emisión**: Cada sensor emite rayos isotrópicos (360°) o directivos (sector de 90°).  
2. **Colisiones**: Los rayos detectan intersecciones con obstáculos.  
3. **Reflexión**: Se calcula el vector reflejado según la normal de la superficie.  
4. **Atenuaciones**: Se aplican pérdidas por rebote (≈3 dB) y por cruce de materiales (configurable).  
5. **Recepción**: Si un rayo impacta en el Hub, se calculan:
   - Distancia recorrida  
   - FSPL  
   - Potencia recibida  
   - Métricas de enlace (SNR, BER, Capacidad, etc.)  

Este modelo permite ver trayectorias y reflexiones, pero no reproduce fenómenos ondulatorios como interferencias ni difracción.

---

## 🌊 Futuro: Modelo basado en Ondas
Vamos a evolucionar hacia una representación más **realista de ondas electromagnéticas** en interiores.  

### ✔️ Fase 0 — Base estable
- Sistema de unidades consistente (1 celda = 1 m).  
- FSPL ya implementado.  
- Rebotes máximos / límite de distancia.  

### ✔️ Fase 1 — Campo escalar (heatmap)
- Mapa de calor de potencia acumulada en celdas.  
- Pérdidas por materiales al atravesar o reflejar paredes.  
- Antenas isotrópicas o directivas (sectorial).  

### ✔️ Fase 2 — Interferencias y multipath
- Cálculo de **fase** (\(\phi = 2\pi d / \lambda\)).  
- Acumulación de **fasores complejos** en cada celda (\(E += A e^{j\phi}\)).  
- Patrón de interferencias con zonas de cancelación/refuerzo.  
- Modelos de desvanecimiento (Rayleigh / Rician).  
- Coeficientes de reflexión/transmisión por material.  

### ✔️ Fase 3 — Fenómenos adicionales
- Difracción (modelo de filo de cuchillo).  
- Dispersión en superficies rugosas.  
- Pérdidas indoor log-distance con exponente \(n\).  

### ✔️ Fase 4 — Antenas realistas
- Patrones de antena personalizados \(G(\theta)\).  
- Directividad más fina que cuadrantes.  
- Polarización simplificada (pérdidas por orientación).  

### ✔️ Fase 5 — Métricas de comunicación
- SNR por celda.  
- BER según modulación.  
- Capacidad de Shannon en todo el mapa.  
- Interferencia entre sensores múltiples.  

### ✔️ Fase 6 — Visualización
- Heatmap en dB de Potencia, SNR, BER y Capacidad.  
- Botones para cambiar entre **modo rayos** y **modo ondas**.  
- Animación de frente de onda (opcional).  

### ✔️ Fase 7 — Optimización
- Paralelismo en el cálculo de rayos.  
- Step adaptativo.  
- Culling de rayos con potencia < umbral.  
- Posible aceleración en GPU para el heatmap.  

---

## 📂 Estructura del proyecto
- `core/` → Clases principales (Sensor, Hub, Obstacle, RayMetrics, Propagation).  
- `env/` → Entornos preconfigurados (paredes, pasillos, materiales).  
- `UI/` → Interfaz gráfica con JavaFX (`VisualGridApp.java`).  

---

## ⚙️ Dependencias
- **JavaFX SDK 24.0.2** (añadido al `--module-path`)  
- **JDK 22** (recomendado, compatible con `classfile version 66.0`)  

---

## 🚀 Roadmap
- ✅ Ray tracing con rebotes y parámetros básicos.  
- 🔜 Heatmap de potencia (Fase 1).  
- 🔜 Interferencias con fasores (Fase 2).  
- 🔜 Mapa de SNR / BER / Capacidad (Fases 5–6).  
- 🔜 Animación y optimización (Fase 7).  

---
