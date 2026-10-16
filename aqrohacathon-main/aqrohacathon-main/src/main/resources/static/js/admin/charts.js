// Chart.js üçün ortaq üslub: nazik işarələr, 4px yuvarlaq uclar, səssiz şəbəkə, tooltip (hover).
import { fmt } from '../core.js';

const INK = '#5a6860';
const GRID = '#eef0ec';
export const SERIES = '#2a78d6';

export function baseOptions({ money = false, horizontal = false } = {}) {
  const val = (v) => (money ? fmt.compactMoney(v) : fmt.num(v));
  const valueAxis = { grid: { color: GRID }, border: { display: false }, ticks: { color: INK, callback: val, maxTicksLimit: 6 }, beginAtZero: true };
  const catAxis = { grid: { display: false }, border: { color: '#dfe3dc' }, ticks: { color: INK, autoSkip: false } };
  return {
    responsive: true,
    maintainAspectRatio: false,
    indexAxis: horizontal ? 'y' : 'x',
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: false },
      tooltip: { backgroundColor: '#111a14', padding: 10, cornerRadius: 8, callbacks: { label: (c) => ` ${c.dataset.label}: ${money ? fmt.money(c.parsed[horizontal ? 'x' : 'y']) : fmt.num(c.parsed[horizontal ? 'x' : 'y'])}` } },
    },
    scales: horizontal ? { x: valueAxis, y: catAxis } : { x: catAxis, y: valueAxis },
  };
}

export function barDataset(label, data, color = SERIES) {
  return { label, data, backgroundColor: color, borderRadius: 4, borderSkipped: 'start', maxBarThickness: 24, categoryPercentage: 0.7 };
}

const charts = new Set();
export function chart(canvas, config) {
  const c = new window.Chart(canvas, config);
  charts.add(c);
  return c;
}
export function destroyAll() {
  charts.forEach((c) => c.destroy());
  charts.clear();
}
