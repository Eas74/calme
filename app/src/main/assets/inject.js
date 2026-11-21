// Injecté uniquement sur https://www.instagram.com (voir MainActivity).
// Aucun canal JS -> Android : le script ne peut rien appeler côté application.
(() => {
  'use strict';
  if (window.__calm) return;
  const S = window.__CALM_SELECTORS || {};
  const POST = S.post || 'article';

  // 1) Masquage des entrées Explorer / Reels (le blocage réel est fait dans UrlPolicy.kt)
  const style = document.createElement('style');
  style.textContent = (S.hide || []).map(s => s + '{display:none!important}').join('\n');
  const mount = () => (document.head || document.documentElement).appendChild(style);
  mount();
  document.addEventListener('DOMContentLoaded', () => { if (!style.isConnected) mount(); });

  // 2) Navigation publication par publication (appelée depuis Kotlin via evaluateJavascript)
  const posts = () => Array.from(document.querySelectorAll(POST));
  const currentIndex = list => {
    let idx = -1;
    list.forEach((el, i) => { if (el.getBoundingClientRect().top <= 40) idx = i; });
    return idx;
  };
  const go = el => { el.scrollIntoView({ block: 'start', behavior: 'auto' }); return true; };

  const api = Object.freeze({
    next() { const l = posts(); const t = l[currentIndex(l) + 1]; return t ? go(t) : false; },
    prev() { const l = posts(); const i = currentIndex(l); return i > 0 ? go(l[i - 1]) : false; },
  });
  Object.defineProperty(window, '__calm', { value: api, enumerable: false });
})();
