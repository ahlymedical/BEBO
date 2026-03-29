function parseSRT(content) {
  var lines = content.replace(/\\n/g, "\n").replace(/\r/g, "");
  var blocks = lines.split(/\n\n+/);
  var entries = [];

  var timecodeRegex = /(\d{2}):(\d{2}):(\d{2})[,\.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,\.](\d{3})/;

  for (var i = 0; i < blocks.length; i++) {
    var match = blocks[i].match(timecodeRegex);
    if (match) {
      var startH = parseInt(match[1], 10);
      var startM = parseInt(match[2], 10);
      var startS = parseInt(match[3], 10);
      var startMS = parseInt(match[4], 10);
      var start = startH * 3600 + startM * 60 + startS + startMS / 1000;

      var endH = parseInt(match[5], 10);
      var endM = parseInt(match[6], 10);
      var endS = parseInt(match[7], 10);
      var endMS = parseInt(match[8], 10);
      var end = endH * 3600 + endM * 60 + endS + endMS / 1000;

      entries.push({ start: start, end: end });
    }
  }

  entries.sort(function(a, b) {
    return a.start - b.start;
  });

  return entries;
}

function removeSilenceGaps(srtContent, minGap, padBefore, padAfter) {
  try {
    if (!app.project.activeSequence) {
      return JSON.stringify({ success: false, message: "لا يوجد سيكوانس نشط، افتح سيكوانس أولاً." });
    }

    var entries = parseSRT(srtContent);
    if (entries.length === 0) {
      return JSON.stringify({ success: false, message: "لم يُعثر على توقيتات في ملف SRT." });
    }

    var pbSec = padBefore / 1000;
    var paSec = padAfter / 1000;
    var gaps = [];

    for (var i = 0; i < entries.length - 1; i++) {
      var gapStart = entries[i].end + paSec;
      var gapEnd = entries[i + 1].start - pbSec;

      if ((gapEnd - gapStart) >= minGap) {
        gaps.push({ start: gapStart, end: gapEnd });
      }
    }

    gaps.sort(function(a, b) {
      return b.start - a.start;
    });

    var seq = app.project.activeSequence;
    var removed = 0;

    for (var j = 0; j < gaps.length; j++) {
      var gap = gaps[j];
      seq.setInPoint(gap.start);
      seq.setOutPoint(gap.end);

      try {
        var extractCmd = app.findMenuCommand("Extract");
        if (extractCmd) {
          app.executeCommand(extractCmd);
        } else {
          app.executeCommand(3001); // Fallback command ID for Extract
        }
      } catch (err) {
        app.executeCommand(3001);
      }
      removed++;
    }

    return JSON.stringify({ success: true, message: "✅ تم حذف " + removed + " فترة صمت من أصل " + gaps.length, count: removed });
  } catch (e) {
    return JSON.stringify({ success: false, message: "خطأ: " + e.toString() });
  }
}

function closeAllGaps() {
  try {
    if (!app.project.activeSequence) {
      return JSON.stringify({ success: false, message: "لا يوجد سيكوانس نشط." });
    }

    try {
      var closeGapCmd = app.findMenuCommand("Close Gap");
      if (closeGapCmd) {
        app.executeCommand(closeGapCmd);
      } else {
        throw new Error("Close Gap command not found");
      }
      return JSON.stringify({ success: true, message: "✅ تم تجميع الكليبات بنجاح! 🎉" });
    } catch (cmdErr) {
      var selectAllCmd = app.findMenuCommand("Select All");
      if (selectAllCmd) app.executeCommand(selectAllCmd);

      var closeGapCmdFallback = app.findMenuCommand("Close Gap");
      if (closeGapCmdFallback) app.executeCommand(closeGapCmdFallback);

      return JSON.stringify({ success: true, message: "✅ تم التجميع (fallback)." });
    }
  } catch (e) {
    return JSON.stringify({ success: false, message: "خطأ: " + e.toString() });
  }
}
