/*
 * tubeld.js  -  Script remoto de TubeLD
 * ------------------------------------------------------------------
 * Este archivo vive en tu repositorio PUBLICO de GitHub:
 *   https://github.com/LuisDornellez/TubeLD-para-tv
 * La app lo descarga sola cada vez que se abre. Para corregir algo:
 *   GitHub -> tubeld.js -> lapiz (Edit) -> cambiar -> "Commit changes".
 * No hay que recompilar ni reinstalar la app. Se aplica al reabrir la app.
 *
 * Se ejecuta ANTES que los scripts internos de la app, en youtube.com.
 * Truco: si defines una de estas banderas en true, el script interno
 * correspondiente NO se ejecuta y puedes reemplazarlo por el tuyo:
 *   window.__tldFix  = true;   // reparador de "Algo salio mal / recarga la pagina"
 *   window.__tldAds  = true;   // bloqueo de anuncios interno
 *   window.__tldTv   = true;   // ajustes de TV
 *   window.__tldNav  = true;   // navegacion con puntero
 * Tamano de la pagina en TV (por defecto 1600): mas grande = todo mas pequeno y se ve mas de la
 * columna de videos de la derecha. Para cambiarlo, quita las // de la linea de abajo:
 *   // window.__tldWidth = 1800;
 * Si algo de aqui falla, la app lo ignora y sigue funcionando.
 * Version del script: 1
 */

window.__tldWidth = 2000; // más grande = reproductor más chico (prueba 1800, 2000, 2200)

// Ejemplo: CSS extra para ocultar cosas de la pagina (borra o edita a gusto)
(function () {
  var css = [
    'ytd-statement-banner-renderer',      // banners de promocion
    'ytm-statement-banner-renderer',
    'ytd-popup-container ytd-mealbar-promo-renderer'
  ].join(',') + '{display:none!important}';
  function add() {
    var t = document.head || document.documentElement;
    if (!t) { setTimeout(add, 50); return; }
    var st = document.createElement('style');
    st.textContent = css;
    t.appendChild(st);
  }
  add();
})();

// Aqui abajo puedes agregar parches nuevos cuando YouTube cambie algo.
