export const CATEGORIES = ['生活小確幸', '家人／朋友', '工作／學習', '健康／照顧自己', '自己的進步', '其他'];
export const STORAGE_KEY = 'three-good-things:v1';
export function localDate(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
export function validDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value) || value < '1900-01-01' || value > '2100-12-31') return false;
  const date = new Date(`${value}T12:00:00`);
  return !Number.isNaN(date.getTime()) && localDate(date) === value;
}
export function validateEntries(entries) {
  if (!Array.isArray(entries) || entries.length > 50000) throw new Error('備份格式不正確，或超過 50,000 筆上限。');
  const ids = new Set();
  return entries.map(entry => {
    if (!entry || typeof entry.id !== 'string' || !entry.id || entry.id.length > 100 || ids.has(entry.id) || !validDate(entry.date) || typeof entry.text !== 'string' || !entry.text.trim() || entry.text.length > 2000 || typeof entry.reflection !== 'string' || entry.reflection.length > 2000 || (typeof entry.category !== 'string' || !entry.category.trim() || entry.category.length > 40)) throw new Error('紀錄格式不正確，沒有匯入任何資料。');
    ids.add(entry.id);
    return { id: entry.id, date: entry.date, text: entry.text.trim(), reflection: entry.reflection.trim(), category: entry.category.trim() };
  });
}
export function readBackup(text) {
  const backup = JSON.parse(text);
  if (backup?.app !== 'three-good-things' || backup.version !== 1) throw new Error('請選擇「三件好事」匯出的版本 1 JSON 備份。');
  return validateEntries(backup.entries);
}
export function mergeEntries(current, incoming) {
  const ids = new Set(current.map(entry => entry.id));
  return validateEntries([...current, ...incoming.filter(entry => !ids.has(entry.id))]);
}
export function makeBackup(entries) {
  return JSON.stringify({ app: 'three-good-things', version: 1, exportedAt: new Date().toISOString(), entries: validateEntries(entries) }, null, 2);
}
