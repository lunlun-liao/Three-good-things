# 三件好事 Three Good Things

給 Android 手機使用的繁體中文日記 APP，同時提供獨立 APK 與網頁版（PWA）。每天留下一點美好，三件是引導，不是限制。

## Android APK

[下載 APK 與安裝說明](downloads/README.md)。APK 可獨立啟動，不需要 Chrome 或網路；使用 Android 原生介面，不需要 WebView 或瀏覽器。舊網頁版的紀錄請先匯出 JSON，再到 APK 匯入。原生開發與簽章方式見 [android/README.md](android/README.md)。

以下開發環境與 Pages 說明是網頁版的流程。

## 第一版功能

- 新增、編輯、刪除好事，選擇日期與補記。
- 每日列表、三件好事進度，以及六種預設分類、自訂分類與篩選。
- 日曆回顧，每天顯示是否有紀錄。
- JSON 備份匯出與合併匯入；相同 ID 保留現有版本，匯入前驗證所有資料。
- Android 安裝到主畫面；首次連線且離線準備完成後可離線開啟與記錄。
- 響應式介面、鍵盤操作與原生表單驗證。

## 需要什麼環境？

這一版是 PWA，沒有外部套件、後端、帳號、API 金鑰或資料庫需求。開發需要文字編輯器、Git 和本機靜態 HTTP 伺服器。自動化檢查需要 Node.js 22 或以上。

手機安裝需要 **HTTPS 網站**與 Android Chrome；無須 Android Studio。另外已提供獨立 APK；未上架 Google Play。APK 使用 Android SDK 建置、正式私鑰簽署，安裝方式見上方說明。

## Windows 本機開發

安裝 Python 3 後，在 PowerShell 執行：

```powershell
git clone https://github.com/lunlun-liao/Three-good-things.git
cd Three-good-things
py -m http.server 8000
```

在電腦瀏覽器開啟 `http://localhost:8000`。不要直接雙擊 HTML，ES modules 和離線功能需要 HTTP 伺服器。

Linux／Codex 雲端：

```sh
cd /workspace/Three-good-things
python3 -m http.server 8000 --bind 0.0.0.0
```

如有安裝 Node.js，也可在 Linux 執行 `npm start`。不需要 `npm install`。

## Android 使用與 GitHub Pages

推送程式碼不代表網站已發布。若想用 GitHub Pages：

1. 到儲存庫的 **Settings → Pages**。
2. 選擇 **Deploy from a branch**，分支 `main`，目錄 `/ (root)`，儲存。
3. 等 GitHub 顯示部署成功後，使用它提供的 HTTPS 網址。一般是 `https://lunlun-liao.github.io/Three-good-things/`，實際以 Pages 顯示為準。
4. 用 Android Chrome 開啟，從「備份與資料」確認離線準備完成，再從 Chrome 選單選「安裝應用程式」或「新增至主畫面」。

Pages 可用性依 GitHub 帳號方案與儲存庫可見性而定。啟用 Pages 會發布網站，請自行確認存取設定。程式碼中不含你的日記資料。

在新增／編輯好事的分類選單選「新增」，即可輸入最多 40 字的分類名稱。儲存好事後，這個分類就會出現在後續的選單與篩選中；取消編輯不會新增分類。自訂分類會隨好事一起備份與還原。

## 資料保存與備份

以下瀏覽器儲存說明適用網頁版；APK 使用 APP 私有的 JSON 檔案與 Android AtomicFile，不使用瀏覽器儲存空間。

日記以 `three-good-things:v1` 為鍵儲存在目前來源的瀏覽器 localStorage。沒有雲端同步或資料加密。共用裝置的其他使用者可能讀取資料，請使用裝置螢幕鎖。

- 請定期在「備份與資料」匯出 JSON，另存至安全位置。
- 更換手機／瀏覽器／網站網址／埠號會使用不同儲存空間；先匯出再匯入。
- 清除網站資料、無痕模式或移除 APP 可能造成資料遺失。
- 備份是日記全文，請勿提交至 GitHub。
- 匯入以 ID 合併，相同 ID 保留本機紀錄；不會覆蓋本機編輯。備份不是雙向同步機制。
- 匯入上限 20 MB，最多 50,000 筆；瀏覽器實際儲存容量可能更小，寫入失敗會顯示提示。

## 檢查

```sh
npm run check
npm test
```

單元測試涵蓋日期、備份格式、資料驗證與重複匯入。手動／瀏覽器驗證流程：新增 → 重載確認保存 → 編輯日期與分類 → 篩選 → 日曆回顧 → 匯出 → 匯入另一個瀏覽器環境 → 離線重載 → 刪除。

瀏覽器端到端測試（選用；另開一個終端先啟動上述 HTTP 伺服器）：

```sh
npm install --no-save --package-lock=false playwright
npx playwright install chromium
npm run test:browser
```

Playwright 僅用於測試，不是 APP 執行相依套件。已有 Chromium 時，可用 `CHROMIUM_PATH` 指定執行檔而不下載測試瀏覽器。測試使用隔離的瀏覽器資料，不會改動你的日記；涵蓋新增、編輯、刪除、重載保存、分類、日曆、備份、錯誤檔案與離線操作。

## 檔案與更新

`index.html`、`styles.css`、`app.js` 是介面，`data.js` 是資料規則，`sw.js` 快取離線資源，`manifest.webmanifest` 定義安裝資訊。所有資源使用相對路徑，支援 Pages 的專案子路徑。

更新離線資源時需同步增加 `sw.js` 的 `CACHE` 版本，新增檔案也需更新 `FILES`。新 service worker 會等待舊視窗關閉後接替；更新後請關閉所有 APP／網站視窗並重新開啟。日記 localStorage 不會隨快取更新刪除。
