import { CATEGORIES, STORAGE_KEY, localDate, validDate, validateEntries, readBackup, mergeEntries, makeBackup } from './data.js';
const $ = id => document.getElementById(id);
let entries = [], selectedDate = localDate(), view = 'daily', editingId = null, toastTimer, deferredInstall;
let month = new Date(`${selectedDate.slice(0, 7)}-01T12:00:00`);
let storageReadable = true;
try {
  const stored = localStorage.getItem(STORAGE_KEY);
  entries = stored === null ? [] : validateEntries(JSON.parse(stored));
} catch {
  storageReadable = false;
  $('storage-warning').textContent = '無法讀取儲存資料。為保護原有紀錄，暫停寫入；請先確認瀏覽器儲存權限，或使用原本的瀏覽器開啟。';
  $('storage-warning').hidden = false;
}
function notify(message) {
  $('toast').textContent = message;
  $('toast').hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { $('toast').hidden = true; }, 4500);
}
function persist(next) {
  if (!storageReadable) throw new Error('儲存空間無法讀取，目前無法修改資料。');
  const checked = validateEntries(next);
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(checked)); }
  catch { throw new Error('儲存失敗，可能是空間不足或瀏覽器限制。請先匯出備份；這次變更尚未儲存。'); }
  entries = checked;
}
for (const category of CATEGORIES) {
  $('entry-category').add(new Option(category, category));
  $('category-filter').add(new Option(category, category));
}
const formatDate = date => new Date(`${date}T12:00:00`).toLocaleDateString('zh-TW', { month: 'long', day: 'numeric', weekday: 'long' });
function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
function render() {
  $('daily-view').hidden = view !== 'daily';
  $('calendar-view').hidden = view !== 'calendar';
  $('backup-view').hidden = view !== 'backup';
  $('entries-view').hidden = view === 'backup';
  document.querySelectorAll('[data-view]').forEach(button => {
    if (button.dataset.view === view) button.setAttribute('aria-current', 'page');
    else button.removeAttribute('aria-current');
  });
  $('selected-date').value = selectedDate;
  $('date-heading').textContent = formatDate(selectedDate);
  $('day-label').textContent = selectedDate === localDate() ? '今天的美好' : `${selectedDate.slice(0, 4)} 年的美好`;
  const daily = entries.filter(entry => entry.date === selectedDate);
  $('progress-title').textContent = daily.length ? `已收藏 ${daily.length} 件好事` : '今天的第一件好事，會是什麼呢？';
  $('progress-description').textContent = daily.length >= 3 ? '三件小美好，值得好好收藏。想寫更多也可以。' : '三件是邀請，不是功課。一件也值得記下。';
  $('progress-dots').replaceChildren(...Array.from({ length: 3 }, (_, i) => element('span', `dot${i < daily.length ? ' filled' : ''}`)));
  const visible = daily.filter(entry => !$('category-filter').value || entry.category === $('category-filter').value);
  $('entries').replaceChildren();
  if (!visible.length) {
    const empty = element('div', 'empty-state');
    empty.append(element('span', 'sprout', '❋'), element('strong', '', daily.length ? '這個分類還沒有紀錄' : '留一點空間，給今天的美好'), element('p', '', daily.length ? '試試其他分類，或記下一件新的好事。' : '一頓好吃的早餐、一句關心，都算數。'));
    $('entries').append(empty);
  }
  visible.forEach(entry => {
    const card = element('article', 'entry-card');
    const top = element('div', 'entry-top');
    const actions = element('div', 'entry-actions');
    const edit = element('button', 'text-button', '編輯');
    edit.setAttribute('aria-label', `編輯：${entry.text.slice(0, 25)}`);
    edit.addEventListener('click', () => openEditor(entry));
    const remove = element('button', 'text-button', '刪除');
    remove.setAttribute('aria-label', `刪除：${entry.text.slice(0, 25)}`);
    remove.addEventListener('click', () => {
      if (!confirm('確定刪除這件好事？刪除後無法復原。')) return;
      try { persist(entries.filter(item => item.id !== entry.id)); render(); notify('已刪除紀錄'); }
      catch (error) { notify(error.message); }
    });
    actions.append(edit, remove);
    top.append(element('span', 'entry-number', String(daily.indexOf(entry) + 1).padStart(2, '0')), element('span', 'tag', entry.category), actions);
    card.append(top, element('p', 'entry-text', entry.text));
    if (entry.reflection) card.append(element('p', 'reflection', entry.reflection));
    $('entries').append(card);
  });
  $('total-count').textContent = `目前收藏 ${entries.length} 件好事，分布在 ${new Set(entries.map(entry => entry.date)).size} 個日子。`;
  $('export-backup').disabled = !storageReadable;
  $('import-backup').disabled = !storageReadable;
  $('add-entry').disabled = !storageReadable;
  renderCalendar();
}
function renderCalendar() {
  $('month-title').textContent = month.toLocaleDateString('zh-TW', { year: 'numeric', month: 'long' });
  $('calendar-selection').textContent = `${formatDate(selectedDate)} 的好事`;
  $('previous-month').disabled = month.getFullYear() === 1900 && month.getMonth() === 0;
  $('next-month').disabled = month.getFullYear() === 2100 && month.getMonth() === 11;
  const grid = $('calendar-grid');
  grid.replaceChildren();
  for (let i = 0; i < month.getDay(); i++) grid.append(element('span'));
  const days = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  const counts = new Map();
  entries.forEach(entry => counts.set(entry.date, (counts.get(entry.date) || 0) + 1));
  for (let day = 1; day <= days; day++) {
    const date = localDate(new Date(month.getFullYear(), month.getMonth(), day, 12));
    const count = counts.get(date) || 0;
    const button = element('button', [date === selectedDate && 'selected', date === localDate() && 'today', count && 'has-entries'].filter(Boolean).join(' '), String(day));
    button.setAttribute('aria-label', `${date}，${count} 件好事`);
    button.setAttribute('aria-pressed', String(date === selectedDate));
    button.addEventListener('click', () => { selectedDate = date; render(); });
    grid.append(button);
  }
}
function openEditor(entry = null) {
  editingId = entry?.id ?? null;
  $('entry-form').reset();
  $('entry-date').value = entry?.date ?? selectedDate;
  $('entry-text').value = entry?.text ?? '';
  $('entry-reflection').value = entry?.reflection ?? '';
  $('entry-category').value = entry?.category ?? CATEGORIES[0];
  $('editor-title').textContent = entry ? '編輯這件好事' : '記下一件好事';
  $('form-error').textContent = '';
  $('entry-dialog').showModal();
  $('entry-text').focus();
}
$('add-entry').addEventListener('click', () => openEditor());
$('close-dialog').addEventListener('click', () => $('entry-dialog').close());
$('entry-text').addEventListener('input', () => $('entry-text').setCustomValidity(''));
$('entry-form').addEventListener('submit', event => {
  event.preventDefault();
  if (!$('entry-text').value.trim()) { $('entry-text').setCustomValidity('請寫下一件好事。'); $('entry-text').reportValidity(); return; }
  const entry = { id: editingId ?? crypto.randomUUID(), date: $('entry-date').value, text: $('entry-text').value.trim(), category: $('entry-category').value, reflection: $('entry-reflection').value.trim() };
  try {
    persist(editingId ? entries.map(item => item.id === editingId ? entry : item) : [...entries, entry]);
    selectedDate = entry.date;
    month = new Date(`${selectedDate.slice(0, 7)}-01T12:00:00`);
    $('category-filter').value = '';
    $('entry-dialog').close();
    render();
    notify('已儲存這份美好');
  } catch (error) { $('form-error').textContent = error.message; }
});
$('selected-date').addEventListener('change', event => {
  if (!validDate(event.target.value)) { event.target.value = selectedDate; return; }
  selectedDate = event.target.value;
  month = new Date(`${selectedDate.slice(0, 7)}-01T12:00:00`);
  render();
});
$('category-filter').addEventListener('change', render);
for (const button of document.querySelectorAll('[data-view]')) button.addEventListener('click', () => { view = button.dataset.view; render(); });
for (const [id, delta] of [['previous-month', -1], ['next-month', 1]]) $(id).addEventListener('click', () => { month = new Date(month.getFullYear(), month.getMonth() + delta, 1, 12); renderCalendar(); });
$('calendar-today').addEventListener('click', () => { selectedDate = localDate(); month = new Date(`${selectedDate.slice(0, 7)}-01T12:00:00`); render(); });
$('export-backup').addEventListener('click', () => {
  const url = URL.createObjectURL(new Blob([makeBackup(entries)], { type: 'application/json' }));
  const link = element('a');
  link.href = url;
  link.download = `three-good-things-${localDate()}.json`;
  document.body.append(link);
  link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
  notify('已開始下載備份，請確認檔案已保存在裝置上');
});
$('import-backup').addEventListener('change', async event => {
  const file = event.target.files[0];
  if (!file) return;
  try {
    if (file.size > 20 * 1024 * 1024) throw new Error('備份檔案不可超過 20 MB。');
    const incoming = readBackup(await file.text());
    const next = mergeEntries(entries, incoming);
    const added = next.length - entries.length;
    if (!confirm(`備份有 ${incoming.length} 筆紀錄，將新增 ${added} 筆。相同編號保留目前版本。確定匯入？`)) return;
    persist(next); render(); notify(`已匯入 ${added} 件好事`);
  } catch (error) { notify(error instanceof SyntaxError ? '無法讀取 JSON 備份，沒有匯入任何資料。' : error.message); }
  finally { event.target.value = ''; }
});
window.addEventListener('beforeinstallprompt', event => { event.preventDefault(); deferredInstall = event; $('install-app').hidden = false; });
$('install-app').addEventListener('click', async () => { if (!deferredInstall) return; await deferredInstall.prompt(); deferredInstall = null; $('install-app').hidden = true; });
window.addEventListener('appinstalled', () => { $('install-app').hidden = true; });
render();
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('./sw.js').then(async () => {
    await navigator.serviceWorker.ready;
    $('offline-status').textContent = '離線準備完成，下次沒有網路也能開啟與記錄。';
  }).catch(() => { $('offline-status').textContent = '離線功能尚未啟用。請確認使用 HTTPS，並重新連線開啟。'; });
} else $('offline-status').textContent = '此瀏覽器不支援離線安裝，仍可在線上使用。';
