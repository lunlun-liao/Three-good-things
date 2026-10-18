import test from 'node:test';
import assert from 'node:assert/strict';
import { validDate, localDate, validateEntries, readBackup, makeBackup, mergeEntries } from '../data.js';
const entry = { id: 'one', date: '2026-10-18', text: '看到夕陽', reflection: '今天有出去走走', category: '生活小確幸' };
test('使用當地日期，驗證閏年與不存在的日期', () => {
  assert.equal(localDate(new Date(2026, 9, 18, 0, 1)), '2026-10-18');
  for (const value of ['2024-02-29', '1900-01-01', '2100-12-31']) assert.ok(validDate(value));
  for (const value of ['2026-02-29', '2026-04-31', '2026-13-01', '2026-1-1', '1899-12-31', '', null]) assert.equal(validDate(value), false);
});
test('備份完整保留中文與特殊文字', () => {
  const records = [entry, { ...entry, id: 'two', text: '<script>alert(1)</script>\n小美好 😊' }];
  assert.deepEqual(readBackup(makeBackup(records)), records);
});
test('匯入拒絕錯誤版本、壞資料、重複 ID 與過長內容', () => {
  assert.throws(() => readBackup('{'));
  assert.throws(() => readBackup(JSON.stringify({ app: 'another-app', version: 1, entries: [] })));
  assert.throws(() => readBackup(JSON.stringify({ app: 'three-good-things', version: 2, entries: [] })));
  for (const bad of [{ ...entry, text: ' ' }, { ...entry, date: '2026-02-30' }, { ...entry, category: ' ' }, { ...entry, category: 'a'.repeat(41) }, { ...entry, category: null }, { ...entry, reflection: null }, { ...entry, text: 'a'.repeat(2001) }]) assert.throws(() => validateEntries([bad]));
  assert.throws(() => validateEntries([entry, entry]));
  assert.throws(() => validateEntries({}));
});
test('合併保留現有版本，重複匯入不增加筆數', () => {
  const incoming = [{ ...entry, text: '另一版本' }, { ...entry, id: 'two' }];
  const merged = mergeEntries([entry], incoming);
  assert.equal(merged.length, 2);
  assert.equal(merged[0].text, entry.text);
  assert.deepEqual(mergeEntries(merged, incoming), merged);
});

test('自訂分類可保存、備份與合併，舊版分類仍可讀取', () => {
  const custom = { ...entry, category: '美食時光' };
  assert.deepEqual(readBackup(makeBackup([custom])), [custom]);
  assert.deepEqual(validateEntries([entry]), [entry]);
  assert.equal(mergeEntries([entry], [{ ...custom, id: 'custom' }])[1].category, '美食時光');
});
