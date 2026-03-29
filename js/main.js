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

    log("⏳ جاري تحليل الترجمة واستخراج الفراغات...");

    var escaped = srtContent
      .replace(/\\/g, "\\\\")
      .replace(/"/g, '\\"')
      .replace(/\n/g, "\\n")
      .replace(/\r/g, "");

    csInterface.evalScript('getSilenceGaps("' + escaped + '")', function(result) {
      try {
        var res = JSON.parse(result);
        if (res.success) {
          if (res.gaps && res.gaps.length > 0) {
            log("✅ تم العثور على " + res.gaps.length + " فراغ. جاري المسح التدريجي...");
            processGapsOneByOne(res.gaps, 0);
          } else {
            log("✅ لا توجد فراغات للمسح.");
            isProcessing = false;
            enableBtn(btnRemove);
          }
        } else {
          log("❌ " + res.message);
          isProcessing = false;
          enableBtn(btnRemove);
        }
      } catch(e) {
        log("⚠️ نتيجة غير متوقعة: " + result);
        isProcessing = false;
        enableBtn(btnRemove);
      }
    });
  });

  function processGapsOneByOne(gaps, index) {
    if (index >= gaps.length) {
      log("🎉 انتهى المسح! تم إزالة " + gaps.length + " فراغ.");
      isProcessing = false;
      enableBtn(btnRemove);
      enableBtn(btnClose);
      return;
    }

    var gap = gaps[index];
    csInterface.evalScript("extractSingleGap(" + gap.start + ", " + gap.end + ")", function(res) {
      try {
        var r = JSON.parse(res);
        if (!r.success) {
          log("⚠️ خطأ في مسح الفراغ رقم " + (index+1));
        }
      } catch(e) {}

      // Update UI log roughly every 10 gaps to prevent spamming the UI
      if (index % 10 === 0 || index === gaps.length - 1) {
         log("✂️ جاري مسح... (" + (index + 1) + " من " + gaps.length + ")");
      }

      // Give Premiere 50ms to breathe and update the UI
      setTimeout(function() {
        processGapsOneByOne(gaps, index + 1);
      }, 50);
    });
  }

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
      enableBtn(btnClose);
    });
  });

  btnClearLog.addEventListener("click", function() {
    logArea.textContent = "";
  });
};