# Silence Remover — Adobe Premiere Pro Extension

## Installation
1. Copy folder to:
   Windows: `%APPDATA%\Adobe\CEP\extensions\com.subtitle.silence.remover\`
   macOS:   `~/Library/Application Support/Adobe/CEP/extensions/`

2. Enable Debug Mode:
   Windows: Open `regedit` → `HKCU\SOFTWARE\Adobe\CSXS.12`
            Add String value: `PlayerDebugMode = 1`
   macOS:   `defaults write com.adobe.CSXS.12 PlayerDebugMode 1`

3. Download CSInterface.js from:
   [https://github.com/Adobe-CEP/CEP-Resources](https://github.com/Adobe-CEP/CEP-Resources)
   Place it in the `js/` folder.

4. Restart Premiere Pro
5. Window → Extensions → Silence Remover 🎬

## Usage
1. Open a sequence in Premiere Pro
2. In the panel: click "اختر ملف SRT" and upload your subtitle file
3. Click "✂️ احذف فترات الصمت" — the extension will extract all silence gaps
4. Click "🔗 اجمع الكليبات جنب بعض" to snap all clips together
