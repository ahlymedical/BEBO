var csInterface = null;
var srtContent = "";

document.addEventListener("DOMContentLoaded", function() {
  try {
    csInterface = new CSInterface();
  } catch (e) {
    log("خطأ في تهيئة CSInterface");
  }

  var fileInput = document.getElementById("srtFile");
  var fileInfo = document.getElementById("fileInfo");
  var btnRemove = document.getElementById("btnRemove");
  var btnClose = document.getElementById("btnClose");

  fileInput.addEventListener("change", function(e) {
    var file = e.target.files[0];
    if (!file) return;

    var reader = new FileReader();
    reader.onload = function(evt) {
      srtContent = evt.target.result;

      var count = 0;
      var match = srtContent.match(/\d{2}:\d{2}:\d{2}[,\.]\d{3}\s*-->/g);
      if (match) {
        count = match.length;
      }

      if (count > 0) {
        fileInfo.textContent = "✅ تم تحميل: " + file.name + " (" + count + " ترجمة)";
        btnRemove.disabled = false;
        log("تم تحميل ملف SRT: " + file.name);
      } else {
        fileInfo.textContent = "❌ لم يتم العثور على ترجمات صالحة";
        btnRemove.disabled = true;
        srtContent = "";
        log("خطأ: ملف SRT غير صالح");
      }
    };
    reader.onerror = function() {
      log("خطأ أثناء قراءة الملف.");
    };
    reader.readAsText(file, "UTF-8");
  });

  btnRemove.addEventListener("click", function() {
    if (!srtContent) {
      log("يرجى تحميل ملف SRT أولاً.");
      return;
    }

    var escaped = srtContent
      .replace(/\\/g, "\\\\")
      .replace(/"/g, '\\"')
      .replace(/\n/g, "\\n")
      .replace(/\r/g, "");

    log("جاري البحث عن فترات الصمت وحذفها...");
    btnRemove.disabled = true;

    if (csInterface) {
      csInterface.evalScript('removeSilenceGaps("' + escaped + '")', function(result) {
        btnRemove.disabled = false;
        try {
          var resObj = JSON.parse(result);
          log(resObj.message);
          if (resObj.success) {
            btnClose.disabled = false;
          }
        } catch (e) {
          log("فشل تحليل النتيجة: " + result);
        }
      });
    } else {
      btnRemove.disabled = false;
      log("CSInterface غير متوفر. يتم محاكاة العملية.");
    }
  });

  btnClose.addEventListener("click", function() {
    log("جاري تجميع الكليبات...");
    btnClose.disabled = true;

    if (csInterface) {
      csInterface.evalScript("closeAllGaps()", function(result) {
        btnClose.disabled = false;
        try {
          var resObj = JSON.parse(result);
          log(resObj.message);
        } catch (e) {
          log("فشل تحليل النتيجة: " + result);
        }
      });
    } else {
      btnClose.disabled = false;
      log("CSInterface غير متوفر. يتم محاكاة العملية.");
    }
  });
});

function log(msg) {
  var logArea = document.getElementById("logArea");
  var now = new Date();

  var hours = now.getHours();
  var minutes = now.getMinutes();
  var seconds = now.getSeconds();

  if (hours < 10) hours = "0" + hours;
  if (minutes < 10) minutes = "0" + minutes;
  if (seconds < 10) seconds = "0" + seconds;

  var timeStr = hours + ":" + minutes + ":" + seconds;

  logArea.textContent += "[" + timeStr + "] " + msg + "\n";
  logArea.scrollTop = logArea.scrollHeight;
}
