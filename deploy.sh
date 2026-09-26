#!/data/data/com.termux/files/usr/bin/bash
set -e

cd ~/chessanalyzer

echo "Ждём завершения последней сборки..."
RUN_ID=$(gh run list --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$RUN_ID" --exit-status

echo "Сборка успешна, качаем APK..."
rm -rf ~/apk_download
mkdir -p ~/apk_download
gh run download "$RUN_ID" -n chessanalyzer-apk -D ~/apk_download

APK_PATH=$(ls ~/apk_download/*.apk | head -n 1)

if [ -z "$APK_PATH" ]; then
    termux-notification --title "Chess Analyzer" --content "APK не найден в артефактах ❌"
    exit 1
fi

termux-notification --title "Chess Analyzer" --content "Сборка готова, открываю установщик..."
termux-open "$APK_PATH"
