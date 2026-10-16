import { renderLayout, $, params } from '../core.js';
import { mountAdvisor } from '../advisor.js';

renderLayout('advisor');
const chat = mountAdvisor($('#advisor'));
$('#resetBtn').addEventListener('click', () => chat.reset());
const q = params().get('q');
if (q) chat.ask(q);
