# Diff de paleta de colores — CSS del código vs. propuesta original

Fuente: `docs/Entrega primer cuatrimestre Policaro (1).md`, sección de paleta de colores
(dentro de "1.4 Interfaces"). Verificado por dos vías: (1) lectura directa de las variables CSS
en `src/main/resources/static/css/global.css`, (2) `getComputedStyle` en vivo vía Playwright
sobre un badge real (`.badge-activo`) en la pantalla de Pacientes filtrada a `QA_`.

| Uso (documento) | Hex documento | Variable CSS | Hex código | ¿Coincide? |
|---|---|---|---|---|
| Azul institucional principal | `#1A5276` | `--azul` | `#1A5276` | ✅ |
| Azul claro secundario | `#D6EAF8` | `--azul-light` | `#D6EAF8` | ✅ |
| Verde confirmación | `#1E8449` | `--verde` | `#1E8449` | ✅ |
| Verde claro (fondo éxito) | `#D5F5E3` | `--verde-light` | `#D5F5E3` | ✅ (confirmado en vivo por `getComputedStyle`: `rgb(213,245,227)`) |
| Rojo error/alerta crítica | `#C0392B` | `--rojo` | `#C0392B` | ✅ |
| Rojo claro (fondo error) | `#FADBD8` | `--rojo-light` | `#FADBD8` | ✅ |
| Naranja aviso preventivo | `#E67E22` | `--naranja` | `#E67E22` | ✅ |
| Naranja claro (fondo aviso) | `#FEF9E7` | `--naranja-light` | `#FEF0E0` | ❌ **No coincide** (diferencia menor, mismo tono naranja pálido) |
| Texto de contenido | `#2C3E50` | `--gris-oscuro` / `--clr-text` | `#2C3E50` | ✅ |
| Fondo de paneles / filas alternas | `#F4F6F7` | `--gris-claro` | `#F4F6F7` | ✅ |

## Badges de estado (mapeo semántico, `global.css` líneas 274-284)
| Badge | Color | Coincide con la intención del documento |
|---|---|---|
| `.badge-disponible`, `.badge-activo`, `.badge-pagado` | Verde (`--verde-light`/`--verde-dim`) | ✅ Confirmación/éxito |
| `.badge-reservado`, `.badge-pendiente` | Naranja (`--naranja-light`/`--naranja-dim`) | ✅ Aviso preventivo |
| `.badge-en-espera`, `.badge-emitida` | Azul (`--azul-light`/`--azul-dim`) | ✅ Institucional/informativo |
| `.badge-atendido` | Gris (`--gris-claro`/`--gris-medio`) | Consistente con "neutro" — el documento no define explícitamente un color para "Atendido" |
| `.badge-cancelado`, `.badge-inactivo`, `.badge-anulada` | Rojo (`--rojo-light`/`--rojo-dim`) | ✅ Error/alerta crítica |

## Conclusión
**9 de 10** valores de la paleta coinciden exactamente. La única diferencia es
`--naranja-light` (`#FEF0E0` en el código vs. `#FEF9E7` en el documento) — visualmente casi
idéntico (ambos son un naranja/durazno muy pálido usado como fondo de alerta), diferencia menor
de implementación, no un defecto funcional. El mapeo semántico estado→color (verde=éxito,
naranja=pendiente/aviso, rojo=error/cancelado, azul=institucional/informativo) coincide con la
intención del documento en todos los badges relevados.
