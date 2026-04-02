# 🎬 Silence Remover Pro — Adobe Premiere Pro Extension

A reusable tool for editors. Works with any video + any SRT file.

## File Structure
com.subtitle.silence.remover/
├── CSXS/manifest.xml
├── css/style.css
├── js/CSInterface.js  ← download separately
├── js/main.js
├── jsx/host.jsx
└── index.html

## Installation

### Step 1 — Copy the extension folder to:
Windows: `%APPDATA%\Adobe\CEP\extensions\com.subtitle.silence.remover\`
macOS:   `~/Library/Application Support/Adobe/CEP/extensions/com.subtitle.silence.remover/`

### Step 2 — Enable Debug Mode (required for unsigned extensions)
Windows:
  Open `regedit` → navigate to `HKCU\SOFTWARE\Adobe\CSXS.12`
  Create String value: `PlayerDebugMode = 1`
  *(Repeat for CSXS.9 through CSXS.12 for compatibility)*

macOS:
  `defaults write com.adobe.CSXS.12 PlayerDebugMode 1`

### Step 3 — Download CSInterface.js
From: [https://github.com/Adobe-CEP/CEP-Resources](https://github.com/Adobe-CEP/CEP-Resources)
Place `CSInterface.js` inside the `js/` folder.

### Step 4 — Restart Adobe Premiere Pro
Go to: Window → Extensions → Silence Remover Pro 🎬

## How to Use (Repeat for every video)

1. Open your video sequence in the Premiere Pro timeline
2. Open the panel: Window → Extensions → Silence Remover Pro 🎬
3. Confirm the green sequence name appears in the panel status
4. Click "📂 اختر ملف SRT" and select your subtitle file
5. Click "✂️ احذف فترات الصمت"
   → All silence gaps are deleted and clips shift back automatically
6. Click "🔗 اجمع الكليبات جنب بعض"
   → All remaining clips are snapped together
7. ✅ Done — for the next video, simply open its sequence and repeat

## Supported Formats
- .srt (SubRip) with comma separator: `00:00:01,500`
- .vtt (WebVTT) with dot separator:  `00:00:01.500`
