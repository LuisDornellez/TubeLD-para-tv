# tubeLD

Visor de YouTube con aspecto premium, bloqueo de anuncios y reproducción en segundo plano.

- Escudo dorado: activa o desactiva el bloqueo de anuncios.
- Auriculares dorados: sigue sonando con la pantalla apagada.
- Si YouTube cambia su código, el bloqueo puede dejar de funcionar y hay que ajustar el script en MainActivity.kt.
- Iniciar sesión con Google puede no funcionar dentro de un WebView.

Compilación en modo release (firmado con app/tubeld.jks, así las versiones se actualizan una sobre otra). Sube el ZIP a GitHub; el APK queda en el Release `apk-latest` (tubeLD.apk).

## Novedades de la versión 1.3
- **Controles en la notificación** (también en la pantalla de bloqueo): anterior, pausa/play, siguiente y cerrar. Muestra título, canal, carátula y barra de progreso (la barra para saltar a otro punto aparece en Android 13 o más). También responden los botones de los auriculares y del Bluetooth.
- **Se pausa solo si desconectás los auriculares.**
- **Temporizador para dormir:** botón del reloj en la barra de abajo (15, 30, 45 min o 1 hora). Al terminar, pausa la reproducción.
- **Cerrar** en la notificación ahora cierra la app de verdad (antes solo quitaba la notificación).
- Los botones Siguiente/Anterior usan los botones de la propia página de YouTube. Si YouTube cambia su código, pueden dejar de funcionar y hay que ajustar `JS_MEDIA` en MainActivity.kt.

## Novedades de la versión 1.4 — Ecualizador
- Botón de ecualizador en la barra de abajo. Ecualizador de 10 bandas (31 Hz a 16 kHz, ±12 dB) que se arrastra con el dedo.
- Modos: Plano, Cine, Música, Voz, Graves y Noche. Más tres controles: **Graves**, **Envolvente** (ensancha el sonido estéreo; se nota más con auriculares) y **Claridad**.
- **Nivelar volumen:** reduce la diferencia entre voces bajas y escenas fuertes (útil de noche).
- Un limitador final evita que el sonido distorsione al subir las bandas.
- Importante: no es Dolby (esa tecnología es de licencia). Es un procesador de audio propio que imita ese tipo de efectos.
- Funciona dentro de la página (Web Audio). Arranca apagado; el audio solo pasa por el ecualizador cuando lo encendés.

## Novedades de la versión 1.5 — Modo día y noche
- Botón del sol/luna arriba a la derecha: cambia entre **modo noche** (oscuro, como antes) y **modo día** (claro). Se recuerda al cerrar la app. Por defecto sigue en modo noche.
- Cambia toda la app: fondos, barra de abajo, logo, barras del sistema, ecualizador y el temporizador. El dorado se oscurece un poco en modo día para que se lea bien.
- También le avisa a YouTube (cookie `PREF`, campo `f6`) para que su página use el tema claro u oscuro; al cambiar, la página se recarga. Si YouTube cambia ese ajuste, la app seguirá cambiando de color pero la página podría no acompañar.

## Novedades de la versión 1.6 — Diseño más premium
- **Barra de abajo:** esquinas más redondas, borde dorado fino y sombra. La función activa (escudo, auriculares, ecualizador, reloj) tiene un resalte dorado suave detrás del icono.
- **Al tocar un botón:** se achica un poco y vibra suave.
- **Aviso en píldora** sobre la barra, con los colores de la app, en vez del aviso gris del sistema.
- **Etiqueta "Sin anuncios"** como píldora con borde dorado, y logo con mejor tipografía.
- **Cambio día/noche con transición suave** (los fondos se mezclan en vez de saltar).
- **Ecualizador y temporizador:** fondos y colores iguales a los de la app (antes salían con un tono violeta del sistema), chips dorados y subtítulo.

## Novedades de la versión 1.7 — Pantalla inicial y botón de salir
- **Pantalla inicial:** fondo oscuro con el icono dorado, el nombre tubeLD y una línea de texto, con tres fundidos suaves. No usa imágenes ni librerías: es solo un icono vectorial y texto, así que abre al instante. Se va sola cuando YouTube termina de cargar (se ve al menos 1 segundo) y, si la página tarda, desaparece a los 3,5 segundos. En Android 12 o más, el sistema muestra primero su propia pantalla con el mismo fondo y el mismo icono, así que la transición es continua.
- **Botón de salir** (icono de apagado, arriba a la derecha): pregunta "¿Salir de tubeLD?" y, si confirmas, pausa la reproducción y cierra la app por completo.

## Corrección de audio entrecortado (1.8.1)
- Bloqueo de Wi-Fi mientras suena en segundo plano (evita cortes con la pantalla apagada).
- El proceso de la página mantiene prioridad alta.
- El ecualizador usa un búfer de audio mayor (`latencyHint: playback`).
- Play/pausa más fiable: usa el reproductor de YouTube y, si no responde, su botón.
- El audio del ecualizador se reanuda solo si el sistema lo deja en pausa. El aviso de diagnóstico ahora muestra `eq=`.
- Primera vez que abres la app: ofrece quitar el límite de batería (permiso REQUEST_IGNORE_BATTERY_OPTIMIZATIONS). Si dices "Ahora no", no vuelve a preguntar; puedes hacerlo a mano en Ajustes > Apps > tubeLD > Ahorro de batería.
- Ecualizador más liviano: el nivelador de volumen solo trabaja cuando "Nivelar volumen" está activo (menos carga, menos trabas).
- Nuevo control en el ecualizador: "Sincronía y estabilidad" (3 niveles: mínimo retraso, equilibrado —por defecto, ~0,12 s— y máxima estabilidad). Cambiarlo recarga la página.
- Notificación: el título, canal y carátula se buscan también en la página y en la miniatura del video si YouTube no los informa.

## Novedades de la versión 1.9 — Smart TV (universal)
La misma app sirve ahora en móvil/tablet (táctil) y en Smart TV (Android TV, Google TV, Fire TV, cajas Android). Se detecta sola; en el móvil todo queda como antes.
- **Aparece en el menú de la TV** con su propio banner (LEANBACK_LAUNCHER) y no exige pantalla táctil.
- **Control remoto:** las flechas mueven un **puntero dorado** por la página y **OK hace clic**. Si llegas al borde de arriba o abajo, la página se desplaza; cuando ya no hay más, el foco pasa a los botones de la barra (arriba: modo día/noche y salir; abajo: atrás, inicio, recargar, escudo, auriculares, ecualizador y reloj). Con las flechas arriba/abajo vuelves al puntero. El puntero se desvanece solo a los 5 s.
- **Botones multimedia del mando:** play, pausa, siguiente, anterior, adelantar y retroceder (10 s).
- **Video a pantalla completa:** izquierda/derecha saltan 10 s, OK pausa o reproduce, y el botón Atrás sale de pantalla completa.
- **Pantalla grande:** se usa la web de escritorio de YouTube (`www.youtube.com`) con el texto un 20 % más grande (`TV_TEXT_ZOOM` en MainActivity.kt) y barras y botones más grandes, con marco dorado en el que tiene el foco. Siempre en horizontal.
- **Ecualizador con mando:** izquierda/derecha eligen la banda, OK activa la edición, arriba/abajo cambian el nivel y OK otra vez suelta el control. El control de "Sincronía y estabilidad" también responde a las flechas (se aplica 0,7 s después del último cambio).
- En la TV no se pide el permiso de batería (no existe allí).
- Para escribir en el buscador de YouTube, pon el puntero sobre la lupa y pulsa OK: sale el teclado de la TV.
- Si el puntero te resulta lento o rápido, ajusta el paso en `moveCursor` (MainActivity.kt).

### Modo ligero (TV con poca memoria)
Se activa solo en una TV de 1 GB de RAM o marcada por el sistema como de poca memoria (en móvil y en TVs con más memoria no cambia nada).
- Usa la web **móvil** de YouTube (mucho más liviana que la de escritorio), con el texto agrandado un 20 % (`TV_TEXT_ZOOM_LITE`) y barras más bajas para que la página no quede apretada.
- Se quitan el ecualizador, el visualizador, el segundo plano (servicio, notificación y bloqueo de Wi-Fi) y los botones de esas funciones; al salir de la app, la página se pausa y libera recursos.
- Se quedan el nombre **tubeLD**, el bloqueo de anuncios, el modo día/noche y el control remoto (puntero, play/pausa, adelantar y retroceder).
- Para que el límite sea otro, cambia `1_600_000_000L` en `detectLite()` (MainActivity.kt).

## Modo flechas / modo ratón, calidad y subtítulos (TV)
- **Botón de flechas/ratón** (arriba a la derecha, solo en TV). **Modo flechas** (el de inicio): ▲▼◀▶ saltan de botón en botón de la página (marco dorado), la página baja sola cuando no hay más abajo, y OK toca lo marcado. Al llegar al borde de arriba/abajo, el foco pasa a las barras. **Modo ratón:** el puntero de siempre. Se recuerda al cerrar.
- **Botón de calidad** (barra de abajo): lista las calidades del video actual (144p a 4K) y la cambia sin buscar el engranaje del reproductor. Si el video no la ofrece, no aparece. Bajar a 360p o 480p ayuda mucho en una TV de poca memoria.
- **Pantalla completa:** OK corto pausa o reproduce, ◀▶ saltan 10 s. **OK largo (medio segundo)** activa el puntero sobre el video para llegar a cualquier control; otro OK largo lo quita.
- **Subtítulos:** el tamaño se limita (15 px antes de la ampliación). Si en algún video siguen grandes, ajusta la regla `.ytp-caption-segment` dentro de `JS_TV`.

## Descanso visual (luz azul)
- Botón del **ojo**, arriba a la derecha (junto al sol/luna). Abre un panel con un interruptor y dos barras: **Luz azul** (tiñe la pantalla de cálido) y **Atenuar** (baja el brillo). El botón "Descanso recomendado" pone 60 % y 25 %.
- Se recuerda al cerrar la app. Funciona en móvil y en TV, también en modo ligero; no gasta casi memoria.
- Es una capa de color sobre la app, no cambia el brillo real de la TV. El panel mismo no se tiñe. Si el video en pantalla completa no se tiñe en algún aparato, es una limitación del reproductor del sistema.

## Nota
- Se eliminó el botón flotante, el reproductor flotante y la imagen en imagen (y el permiso «Mostrar sobre otras apps»).
- Visualizador en la notificación: el nombre «tubeLD» abajo en la carátula y, a su lado, un visualizador radial en paleta Esmeralda (rayitas en círculo: menta, verde y petróleo). Sigue la música real; solo si el sistema no deja medir el audio, el movimiento es simulado. Se anima unas 4 veces por segundo y solo con la pantalla encendida.
- Visualizador radial en la barra superior: junto al nombre «tubeLD», un círculo de rayitas en paleta Esmeralda (menta, verde y petróleo) que crecen con la música mientras el color fluye suavemente. En reposo queda quieto y pequeño. Sigue la música real; solo si el sistema no deja medir el audio, el movimiento es simulado.

## Corrección 1.9.1 — Desplazamiento en TV
- En modo flechas, al llegar al último elemento visible la página ahora baja de verdad (antes saltaba a la barra de abajo sin bajar).
- Se desactiva el desplazamiento suave de la página para que el script detecte bien si se movió, y se prueban más contenedores con scroll propio.

## 1.9.2 — Solo modo ratón (TV)
- Se quitó el modo flechas y el botón que cambiaba de modo. En la TV las flechas siempre mueven el puntero dorado y OK hace clic.

## 1.9.3 — Intento de inicio de sesión con Google
- Se quita la cabecera `X-Requested-With` que delata al WebView (necesita Android System WebView 112 o más). Es lo que suele hacer que Google diga "navegador o app no seguros". No está garantizado: Google puede seguir bloqueando por otras señales. Si es así, usa la app sin iniciar sesión.

## 1.9.4 — Revisión del puntero
- Al volver a mostrar el puntero (por ejemplo al salir de pantalla completa o volver de las barras), si había quedado fuera de la página se mete dentro, para que no aparezca sobre las barras ni se mueva raro.
- El desplazamiento ya no repite un barrido pesado en cada pulsación cuando la página llegó al final (menos trabas en TVs viejas).

## 1.9.5 — Pasar al siguiente video
- Si el video termina y la página no pasó sola al siguiente, la app lo hace a los 9,5 s (deja primero que actúe el autoplay de YouTube). Muestra el aviso «Fin del video: pasando al siguiente».
- El botón Siguiente (notificación, mando y auriculares) ahora también funciona en la web de escritorio de YouTube (la que usa la TV): antes solo buscaba elementos de la versión móvil.
