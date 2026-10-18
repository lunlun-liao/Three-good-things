# Android 原生 APK 開發與建置

此專案直接使用 Android 原生元件（文字欄位、按鈕、分類選單、日期選擇器、自製日曆格與檔案選擇器），**不使用 WebView、不啟動 Chrome、不載入 GitHub Pages**。

- Android 8.0（API 26）以上，compile/target SDK 35。
- AGP 8.9.1、Gradle wrapper 8.11.1、JDK 21、Android build-tools 35.0.0。
- 沒有 AndroidX、第三方執行套件、伺服器或 INTERNET 權限。
- `MainActivity.java` 負責原生介面；`JournalStore.java` 負責日期／備份驗證、分類、合併與原子寫入。
- 日記使用 Android `AtomicFile` 儲存在 APP 私有的 `files/entries.json`。寫入失敗保留上次完成的版本，損壞資料會停止寫入。
- JSON 備份格式與網頁版相同。Android 與瀏覽器各自存資料，透過匯出／匯入搬移。
- Android 文件建立器／選擇器處理備份，不申請整個儲存空間存取權。
- 沒有自動更新服務；APK 更新需重新建置並保持原簽章。

## Windows／Android Studio

安裝 Android Studio，開啟此目錄 `android/`（不是根目錄）。用 SDK Manager 安裝 Android SDK Platform 35 與 Build-Tools 35.0.0，使用 JDK 21，等 Gradle Sync 完成。可選擇模擬器或 USB 偵錯手機執行 debug 版。

如需正式 APK，可用 Build → Generate Signed App Bundle or APK → APK，但**更新本次提供的 APK 必須使用原簽署私鑰**，不能另建一把。不要把 keystore、密碼或日記備份提交至儲存庫。

## Linux／雲端命令

先具備上述 JDK 和 SDK，設定 `JAVA_HOME` 與 `ANDROID_HOME`。若使用受限制的工作區，可將 `GRADLE_USER_HOME`、`ANDROID_USER_HOME` 與 `ANDROID_SDK_HOME` 指到可寫的工作區。網路需要 Google Android 套件、Maven Central、Gradle 發行檔。

```sh
./android/gradlew -p android :app:assembleRelease :app:lintRelease
```

這個命令產生的是**未簽署** release APK，不能直接安裝。第一次建置時建立外部私鑰：

```sh
./scripts/create-signing-key.sh
./scripts/build-apk.sh
```

預設私鑰位置為儲存庫外的 `../three-good-things-signing/`；可用 `THREE_GOOD_THINGS_SIGNING_DIR` 指定其他安全位置。建立指令遇到既有私鑰會停止，不會覆蓋。建置指令不會自動產生新私鑰，避免誤換簽章。

- `release.p12` 和 `password` **兩者都要安全備份**，不要放進 GitHub，也不要在聊天貼出。
- 目前雲端建置的私鑰保存在 `/workspace/three-good-things-signing/`。這不是 Git 內容；刪除或重建雲端機器前必須先安全備份。
- `scripts/build-apk.sh` 會執行網頁程式既有檢查與資料測試，再編譯 Android、lint、檢查 zipalign、簽章與驗證，輸出 `downloads/three-good-things-1.0.0.apk` 和 SHA-256。
- 發行新版時，提高 `app/build.gradle` 的 versionCode／versionName，並同步更新建置腳本、下載文件中的版本檔名。

## Android 模擬器測試

只在測試模擬器執行。`NativeSmokeTest` 驗證原生畫面的新增／編輯／刪除、自訂分類、篩選、日曆、Activity 重開與檔案保存，以及 JSON 備份匯出／匯入與重複匯入。也直接驗證日期、錯誤備份與損壞檔案保護，並讀取由網頁版實際匯出的測試備份。測試結果中的 `backup_fixture` 只含合成資料，可交給網頁版 `readBackup` 驗證反向相容性。

```sh
./android/gradlew -p android :app:assembleDebug :app:assembleDebugAndroidTest
adb install --no-incremental -r android/app/build/outputs/apk/debug/app-debug.apk
adb install --no-incremental -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.lunlun.threegoodthings.test/com.lunlun.threegoodthings.NativeSmokeTest
```

以輸出的 `PASS:`／`FAIL:` 以及 `INSTRUMENTATION_CODE` 確認實際結果；這是自訂測試 runner，不是 JUnit 測試數報告。檔案選擇器回傳使用受控測試檔案，仍需在實體手機確認各廠牌的檔案選擇互動。debug APK 不能覆蓋正式私鑰簽署的 APK；測試正式 APK 時需用同一私鑰重簽測試 APK。

Gradle Wrapper 的第三方授權聲明位於 `THIRD_PARTY_NOTICES.txt`。專案原創程式碼的授權不因此變更。

本次已在 Android 15（API 35）模擬器安裝實際簽署的 release APK，原生操作與資料測試通過；另已驗證網頁版與 APK 的 JSON 備份雙向相容。尚未在實體手機驗證各廠牌的檔案選擇器互動。
