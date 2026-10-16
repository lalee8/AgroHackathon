// Neytral xəritə altlıqları: altlıqda heç bir yer adı yoxdur (kənar və ya uydurma toponim görünmür),
// adlar platformanın öz qatından, Azərbaycan dilində göstərilir.
const L = window.L;

// Zoom zamanı köhnə kafellər yeni kafellər yüklənənə qədər qalır: xəritə "itib-gəlmir"
const SMOOTH = { updateWhenZooming: false, keepBuffer: 4 };
const ESRI = (service, opts = {}) => L.tileLayer(`https://server.arcgisonline.com/ArcGIS/rest/services/${service}/MapServer/tile/{z}/{y}/{x}`, {
  maxZoom: 19, attribution: 'Tiles &copy; Esri', ...SMOOTH, ...opts,
});

/**
 * Altlıqlar: hamısı yazısızdır. "Xəritə" Esri Ocean Base (relyef, çaylar, kanallar, yollar, yazı yoxdur) 11-ci zoom-a qədər,
 * daha yaxında isə avtomatik peyk görüntüsünə keçir (sahə səviyyəsində ən faydalı görüntü).
 */
export function baseLayers() {
  return {
    'Xəritə': L.layerGroup([ESRI('Ocean/World_Ocean_Base', { maxNativeZoom: 10, maxZoom: 11 }), ESRI('World_Imagery', { minZoom: 12 })]),
    'Peyk görüntüsü': ESRI('World_Imagery'),
    'Relyef': ESRI('World_Terrain_Base', { maxNativeZoom: 13 }),
  };
}

// [ad, enlik, uzunluq, görünmə zoomu (min), növ, (maks zoom)]
const PLACES = [
  ['Bakı', 40.4093, 49.8671, 5, 'city'],
  ['Gəncə', 40.6828, 46.3606, 6, 'city'],
  ['Naxçıvan', 39.2089, 45.4122, 6, 'city'],
  ['Sumqayıt', 40.5897, 49.6686, 7, 'town'],
  ['Mingəçevir', 40.7703, 47.0496, 7, 'town'],
  ['Lənkəran', 38.7543, 48.8506, 7, 'town'],
  ['Şirvan', 39.9318, 48.9297, 7, 'town'],
  ['Şəki', 41.1919, 47.1706, 7, 'town'],
  ['Xankəndi', 39.8153, 46.7519, 10, 'town'],
  ['Bərdə', 40.3744, 47.1266, 8, 'town'],
  ['Ağcabədi', 40.053, 47.4594, 8, 'town'],
  ['Beyləqan', 39.7756, 47.6186, 8, 'town'],
  ['Yevlax', 40.6172, 47.15, 8, 'town'],
  ['Qazax', 41.0933, 45.3661, 8, 'town'],
  ['Ordubad', 38.9058, 46.0234, 8, 'town'],
  ['Culfa', 38.9558, 45.6297, 8, 'town'],
  ['Şərur', 39.5536, 44.9846, 8, 'town'],
  ['Horadiz', 39.4488, 47.336, 10, 'town'],
  ['Xəzər dənizi', 40.1, 50.9, 4, 'water', 9],
  ['Mingəçevir su anbarı', 40.86, 46.92, 9, 'water'],
  ['Araz çayı', 39.18, 47.6, 10, 'water'],
  ['Gürcüstan', 41.75, 44.6, 5, 'country', 8],
  ['Rusiya', 42.45, 47.0, 5, 'country', 8],
  ['İran', 37.7, 46.4, 5, 'country', 8],
  ['Türkiyə', 39.9, 42.6, 5, 'country', 8],
  ['Ermənistan', 40.25, 44.75, 6, 'country', 8],
];

/** Azərbaycan dilində yer adları qatı: zoom-a görə görünür. */
export function addPlaceLabels(map) {
  const group = L.layerGroup().addTo(map);
  const items = PLACES.map(([name, lat, lng, min, kind, max = 30]) => ({
    min, max,
    m: L.marker([lat, lng], { interactive: false, keyboard: false, icon: L.divIcon({ className: `az-label az-${kind}`, html: `<span>${name}</span>`, iconSize: null }) }),
  }));
  const update = () => {
    const z = map.getZoom();
    items.forEach((it) => { const on = z >= it.min && z <= it.max; if (on && !group.hasLayer(it.m)) group.addLayer(it.m); else if (!on && group.hasLayer(it.m)) group.removeLayer(it.m); });
  };
  map.on('zoomend', update);
  update();
  return group;
}

/** Rayon adları (azad edilmiş rayonlar): /api/v1/reference cavabındakı koordinatlarla. */
export function addDistrictLabels(map, districts) {
  // Rayon adları markerlərin altında, öz qatında: sahə qrupları və nişanlar həmişə oxunur
  if (!map.getPane('districtPane')) map.createPane('districtPane').style.zIndex = 450;
  districts.forEach((d) => L.tooltip({ permanent: true, direction: 'bottom', offset: [0, 14], className: 'district-label', opacity: 0.88, pane: 'districtPane', interactive: false })
    .setLatLng([d.lat, d.lng]).setContent(d.label).addTo(map));
}

/** Xəritə: yazısız altlıq + Azərbaycan dilində adlar (+ istəyə görə altlıq seçimi). */
export function createMap(el, { base = 'Xəritə', layersControl = false, center = [39.75, 46.75], zoom = 8, ...opts } = {}) {
  const map = L.map(el, opts).setView(center, zoom);
  const layers = baseLayers();
  layers[base].addTo(map);
  if (layersControl) L.control.layers(layers, {}, { position: 'topright' }).addTo(map);
  addPlaceLabels(map);
  return { map, layers };
}
