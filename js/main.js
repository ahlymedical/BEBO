var csInterface = new CSInterface();
var srtContent = "";
var isProcessing = false;

function log(msg) {
  var logArea = document.getElementById("logArea");
  var now = new Date();

  var timeStr = now.toLocaleTimeString("ar-EG");
  var newLine = "[" + timeStr + "] " + msg + "\n";

  logArea.textContent = newLine + logArea.textContent;
}

function updateSeqStatus() {
  var seqStatus = document.getElementById("seqStatus");
  if (csInterface) {
    csInterface.evalScript("getSequenceName()", function(result) {
      if (result && result !== "undefined" && result !== "null") {
        seqStatus.textContent = "✅ سيكوانس: " + result;
        seqStatus.style.color = "#4caf50";
      } else {
        seqStatus.textContent = "⚠️ لا يوجد سيكوانس نشط — افتح سيكوانس في بريمير";
        seqStatus.style.color = "#e94560";
      }
    });
  }
}

function enableBtn(btn) {
  btn.disabled = false;
}

function disableBtn(btn) {
  btn.disabled = true;
}

window.onload = function() {
  updateSeqStatus();
  setInterval(updateSeqStatus, 3000);

  var fileInput = document.getElementById("srtFile");
  var fileInfo = document.getElementById("fileInfo");
  var btnRemove = document.getElementById("btnRemove");
  var btnClose = document.getElementById("btnClose");
  var btnClearLog = document.getElementById("btnClearLog");
  var logArea = document.getElementById("logArea");

  fileInput.addEventListener("change", function(event) {
    var file = event.target.files[0];
    if (!file) return;

    var reader = new FileReader();
    reader.onload = function(evt) {
      srtContent = evt.target.result;
      var matches = srtContent.match(/\d{2}:\d{2}:\d{2}[,\.]\d{3}\s*-->/g);
      var count = matches ? matches.length : 0;

      fileInfo.textContent = "✅ " + file.name + " — " + count + " توقيت";
      fileInfo.className = "visible";

      enableBtn(btnRemove);
      log("📄 تم تحميل: " + file.name + " (" + count + " توقيت)");
    };
    reader.onerror = function() {
      log("❌ فشل في قراءة الملف");
    };
    reader.readAsText(file, "UTF-8");
  });

  btnRemove.addEventListener("click", function() {
    if (isProcessing) return;
    if (!srtContent) {
      log("⚠️ ارفع ملف SRT أولاً");
      return;
    }

    isProcessing = true;
    disableBtn(btnRemove);
    disableBtn(btnClose);

    log("⏳ جاري تحليل الترجمة ومسح الفراغات...");

    var escaped = srtContent
      .replace(/\\/g, "\\\\")
      .replace(/"/g, '\\"')
      .replace(/\n/g, "\\n")
      .replace(/\r/g, "");

    csInterface.evalScript('removeSilenceGaps("' + escaped + '")', function(result) {
      isProcessing = false;
      enableBtn(btnRemove);
      try {
        var res = JSON.parse(result);
        if (res.success) {
          log("✅ " + res.message);
          if (res.count > 0) {
            enableBtn(btnClose);
          }
        } else {
          log("❌ " + res.message);
        }
      } catch(e) {
        log("⚠️ نتيجة غير متوقعة: " + result);
      }
    });
  });

  btnClose.addEventListener("click", function() {
    log("⏳ جاري تجميع الكليبات...");
    disableBtn(btnClose);
    csInterface.evalScript("closeAllGaps()", function(result) {
      try {
        var res = JSON.parse(result);
        log(res.success ? "✅ " + res.message : "❌ " + res.message);
      } catch(e) {
        log("⚠️ " + result);
      }
    });
  });

  btnClearLog.addEventListener("click", function() {
    logArea.textContent = "";
  });
};
