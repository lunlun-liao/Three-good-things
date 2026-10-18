# Android 安裝版

[下載三件好事 1.0.0 APK](https://github.com/lunlun-liao/Three-good-things/raw/refs/heads/main/downloads/three-good-things-1.0.0.apk)

需要 Android 8.0 或以上。這是使用 Android 原生介面的獨立 APK，不使用 WebView，也沒有 Chrome／外部瀏覽器或網路權限需求，也沒有廣告、帳號或雲端同步。

## 從舊版搬移紀錄

1. 先在原本的網頁版／主畫面 APP，進入「備份與資料」匯出 JSON，確認下載完成。
2. 下載 APK；用手機的「檔案／我的檔案」開啟它。若系統要求，僅允許這次使用的來源安裝應用程式，完成後可關閉該權限。
3. 開啟新安裝的「三件好事」，到「備份與資料」選擇剛才的 JSON 匯入。
4. 確認紀錄正確後，再決定是否移除舊的主畫面網頁 APP；在確認前不要清除舊資料。

新 APK 與網頁版可能同時出現同名圖示，但資料各自獨立。APK 沒有「安裝到主畫面」步驟。

## 後續更新

APK 不會隨 GitHub Pages 自動更新。需以**相同 applicationId 與簽章、較高 versionCode**建置新版，下載後覆蓋安裝。覆蓋更新通常保留資料；解除安裝或清除資料會刪除紀錄，請先備份。

簽署私鑰與密碼不在 GitHub 內。沒有原本的私鑰就不能建立可直接覆蓋的更新版本。SHA-256 校驗值在同目錄的 `.sha256` 檔，簽章指紋在 `signing-certificate.txt`。
