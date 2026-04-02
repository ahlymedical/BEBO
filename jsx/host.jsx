function getSequenceName() {
  try {
    var seq = app.project.activeSequence;
    if (!seq) return "null";
    return seq.name;
  } catch(e) {
    return "null";
  }
}

function parseSRT(content) {
  var cleanContent = content.replace(/\\n/g, "\n").replace(/\r/g, "");
  var entries = [];
  var pattern = /(\d{2}):(\d{2}):(\d{2})[,\.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,\.](\d{3})/g;
  var m;

  while ((m = pattern.exec(cleanContent)) !== null) {
    var start = +m[1] * 3600 + +m[2] * 60 + +m[3] + +m[4] / 1000;
    var end   = +m[5] * 3600 + +m[6] * 60 + +m[7] + +m[8] / 1000;
    entries.push({ start: start, end: end });
  }

  entries.sort(function(a, b) {
    return a.start - b.start;
  });

  return entries;
}

function getSilenceGaps(srtContent) {
  try {
    var seq = app.project.activeSequence;
    if (!seq) return JSON.stringify({
      success: false,
      message: "لا يوجد سيكوانس نشط. افتح سيكوانس في بريمير أولاً."
    });

    var entries = parseSRT(srtContent);
    if (entries.length === 0) return JSON.stringify({
      success: false,
      message: "لم يُعثر على توقيتات في الملف. تأكد أن الملف SRT صحيح."
    });

    var gaps = [];
    var i;
    for (i = 0; i < entries.length - 1; i++) {
      var gapStart = entries[i].end;
      var gapEnd   = entries[i + 1].start;
      if (gapEnd - gapStart > 0.05) {
        gaps.push({ start: gapStart, end: gapEnd });
      }
    }

    if (gaps.length === 0) return JSON.stringify({
      success: true,
      message: "لا توجد فراغات في هذا الملف.",
      gaps: []
    });

    // Sort DESCENDING to prevent timeline drift
    gaps.sort(function(a, b) {
      return b.start - a.start;
    });

    return JSON.stringify({
      success: true,
      message: "تم تجهيز " + gaps.length + " فراغ للمسح.",
      gaps: gaps
    });
  } catch(e) {
    return JSON.stringify({
      success: false,
      message: "خطأ: " + e.toString()
    });
  }
}

function extractSingleGap(start, end) {
  try {
    var seq = app.project.activeSequence;
    if (!seq) return JSON.stringify({ success: false, message: "لا يوجد سيكوانس" });

    seq.setInPoint(start);
    seq.setOutPoint(end);

    try {
      app.executeCommand(app.findMenuCommand("Extract"));
    } catch(cmdErr) {
      try {
        app.executeCommand(3001);
      } catch(e2) {
        return JSON.stringify({ success: false, message: "فشل في المسح" });
      }
    }
    return JSON.stringify({ success: true });
  } catch(e) {
    return JSON.stringify({ success: false, message: e.toString() });
  }
}

function closeAllGaps() {
  try {
    var seq = app.project.activeSequence;
    if (!seq) return JSON.stringify({
      success: false,
      message: "لا يوجد سيكوانس نشط."
    });

    app.executeCommand(app.findMenuCommand("Close Gap"));
    return JSON.stringify({
      success: true,
      message: "تم تجميع الكليبات بنجاح! جاهز للفيديو الجاي 🎉"
    });
  } catch(e) {
    return JSON.stringify({
      success: false,
      message: "خطأ في التجميع: " + e.toString()
    });
  }
}
