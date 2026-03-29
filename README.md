# Silence Remover 🎬 - Adobe Premiere Pro CEP Panel Extension

## 📁 Folder Structure Tree
```
com.subtitle.silence.remover/
├── CSXS/
│   └── manifest.xml
├── css/
│   └── style.css
├── index.html
├── js/
│   ├── CSInterface.js (Download required)
│   └── main.js
└── jsx/
    └── host.jsx
```

## 💻 Installation Path

To install the extension, copy the entire `com.subtitle.silence.remover` folder to the following path depending on your operating system:

**Windows:**
`%APPDATA%\Adobe\CEP\extensions\`

**macOS:**
`~/Library/Application Support/Adobe/CEP/extensions/`

## 🐛 Enable Debug Mode

Since this is an unsigned extension, you need to enable PlayerDebugMode for CEP 12.

**Windows (Registry):**
1. Open `regedit`.
2. Go to `HKEY_CURRENT_USER\SOFTWARE\Adobe\CSXS.12`.
3. Add a new String Value (REG_SZ) named `PlayerDebugMode` and set its value to `1`.

**macOS (Terminal):**
Run the following command:
`defaults write com.adobe.CSXS.12 PlayerDebugMode 1`

## 🛠 Downloading CSInterface.js

The extension requires `CSInterface.js`. You can download it from the Adobe CEP Resources repository:
[Download CSInterface.js](https://github.com/Adobe-CEP/CEP-Resources)

Place the downloaded `CSInterface.js` file inside the `js/` folder.

## 🚀 How to Open the Panel

1. Open Adobe Premiere Pro.
2. Go to `Window` -> `Extensions`.
3. Click on `Silence Remover 🎬`.

## 📖 Usage Steps

1. **الخطوة 1: ارفع ملف SRT**
   - انقر على "اختر ملف SRT أو VTT" وقم بتحديد ملف الترجمة الخاص بك.
2. **الخطوة 2: ضبط الإعدادات**
   - **الحد الأدنى للصمت (ثانية):** لتحديد أقل مدة للصمت المراد حذفه.
   - **هامش قبل الكلام (مللي ثانية):** لتحديد الهامش الزمني قبل بدء الجملة.
   - **هامش بعد الكلام (مللي ثانية):** لتحديد الهامش الزمني بعد نهاية الجملة.
3. **الخطوة 3: التنفيذ**
   - انقر على "✂️ احذف فترات الصمت" لحذف الفترات الزمنية بين الجمل.
   - انقر على "🔗 اجمع الكليبات جنب بعض" لغلق الفراغات في الـ Timeline.

## 📝 Supported SRT/VTT Format Notes
- The extension supports SRT format with both comma (`,`) and dot (`.`) as the millisecond separator.
- VTT files are supported as long as the timecodes match the format `HH:MM:SS,MS` or `HH:MM:SS.MS`.
