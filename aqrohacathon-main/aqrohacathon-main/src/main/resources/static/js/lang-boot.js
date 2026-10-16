// Seçilmiş dil AZ deyilsə, tərcümə tətbiq olunana qədər səhifə gizli qalır (Azərbaycan mətninin bir anlıq görünməməsi üçün).
// Adi (modul olmayan) skript kimi <head>-də işləyir; i18n.js tərcüməni bitirəndə və ya 2.5 saniyədən sonra sinif silinir.
(function () {
  try {
    var l = localStorage.getItem('aqro.lang');
    if (l === 'en' || l === 'ru') {
      var h = document.documentElement;
      h.lang = l;
      h.classList.add('i18n-wait');
      setTimeout(function () { h.classList.remove('i18n-wait'); }, 2500);
    }
  } catch (e) { /* localStorage əlçatan deyil */ }
})();
