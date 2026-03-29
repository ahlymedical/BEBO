let csInterface = null;
let srtContent = "";

document.addEventListener("DOMContentLoaded", function() {
  try {
    csInterface = new CSInterface();
  } catch (e) {
    logMsg("❌ خطأ في تهيئة CSInterface. تأكد من تشغيل الإضافة داخل Premiere Pro.");
  }

  const fileInput = document.getElementById("srtFile");
  const fileInfo = document.getElementById("fileInfo");
  const btnRemove = document.getElementById("btnRemove");
  const btnClose = document.getElementById("btnClose");
  const minGapInput = document.getElementById("minGap");
  const paddingBeforeInput = document.getElementById("paddingBefore");
  const paddingAfterInput = document.getElementById("paddingAfter");

  fileInput.addEventListener("change", function(e) {
    const file = e.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = function(evt) {
      srtContent = evt.target.result;

      const subtitleRegex = /\d{2}:\d{2}:\d{2}[,\.]\d{3}\s*-->/g;
      const match = srtContent.match(subtitleRegex);
      const count = match ? match.length : 0;

      if (count > 0) {
        fileInfo.textContent = `✅ تم تحميل: ${file.name} (${count} ترجمة)`;
        btnRemove.disabled = false;
        logMsg(`✅ تم تحميل ملف SRT: ${file.name}، يحتوي على ${count} جملة.`);
      } else {
        fileInfo.textContent = `❌ لم يتم العثور على ترجمات صالحة في ${file.name}`;
        btnRemove.disabled = true;
        srtContent = "";
        logMsg(`❌ خطأ: ملف SRT غير صالح (${file.name}).`);
      }
    };
    reader.onerror = function() {
      logMsg("❌ خطأ أثناء قراءة الملف.");
    };
    reader.readAsText(file, "UTF-8");
  });

  btnRemove.addEventListener("click", function() {
    if (!srtContent) {
      logMsg("❌ يرجى تحميل ملف SRT أولاً.");
      return;
    }

    const minGap = parseFloat(minGapInput.value) || 0.5;
    const paddingBefore = parseInt(paddingBeforeInput.value) || 100;
    const paddingAfter = parseInt(paddingAfterInput.value) || 100;

    const escapedSRT = srtContent
      .replace(/\\/g, "\\\\")
      .replace(/"/g, '\\"')
      .replace(/\n/g, "\\n")
      .replace(/\r/g, "");

    const script = `removeSilenceGaps("${escapedSRT}", ${minGap}, ${paddingBefore}, ${paddingAfter})`;

    logMsg(`⏳ جاري البحث عن فترات الصمت وحذفها...`);
    btnRemove.disabled = true;

    if (csInterface) {
      csInterface.evalScript(script, function(result) {
        btnRemove.disabled = false;
        try {
          const resObj = JSON.parse(result);
          if (resObj.success) {
            logMsg(resObj.message);
            btnClose.disabled = false;
          } else {
            logMsg(`❌ ${resObj.message}`);
          }
        } catch (e) {
          logMsg(`❌ فشل تحليل النتيجة: ${result}`);
        }
      });
    } else {
      btnRemove.disabled = false;
      logMsg("❌ CSInterface غير متوفر. يتم محاكاة العملية.");
    }
  });

  btnClose.addEventListener("click", function() {
    logMsg(`⏳ جاري تجميع الكليبات...`);
    btnClose.disabled = true;

    if (csInterface) {
      csInterface.evalScript("closeAllGaps()", function(result) {
        btnClose.disabled = false;
        try {
          const resObj = JSON.parse(result);
          if (resObj.success) {
            logMsg(resObj.message);
          } else {
            logMsg(`❌ ${resObj.message}`);
          }
        } catch (e) {
          logMsg(`❌ فشل تحليل النتيجة: ${result}`);
        }
      });
    } else {
      btnClose.disabled = false;
      logMsg("❌ CSInterface غير متوفر. يتم محاكاة العملية.");
    }
  });
});

function logMsg(msg) {
  const logArea = document.getElementById("logArea");
  const now = new Date();
  const timeStr = now.toLocaleTimeString('ar-EG', { hour12: false });

  const entry = document.createElement("div");
  entry.className = "log-entry";
  entry.textContent = `[${timeStr}] ${msg}`;

  logArea.appendChild(entry);
  logArea.scrollTop = logArea.scrollHeight;
}
