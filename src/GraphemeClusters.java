package com.textanimation;

import android.text.Spannable;

import java.text.BreakIterator;

final class GraphemeClusters {
    private final BreakIterator clusterIterator = BreakIterator.getCharacterInstance();

    void setText(String text) {
        clusterIterator.setText(text);
    }

    int floor(String text, int offset) {
        if (offset <= 0 || offset >= text.length() || clusterIterator.isBoundary(offset)) {
            return Math.max(0, offset);
        }
        int previous = clusterIterator.preceding(offset);
        return previous == BreakIterator.DONE ? offset : previous;
    }

    int ceil(String text, int offset) {
        if (offset <= 0 || offset >= text.length() || clusterIterator.isBoundary(offset)) {
            return Math.min(text.length(), offset);
        }
        int next = clusterIterator.following(offset);
        return next == BreakIterator.DONE ? offset : next;
    }

    int next(int offset, int limit) {
        int next = clusterIterator.following(offset);
        return next == BreakIterator.DONE || next > limit ? limit : next;
    }

    boolean isEmojiLike(Spannable spannable, int start, int end, String cluster) {
        int i = 0;
        while (i < cluster.length()) {
            int cp = cluster.codePointAt(i);
            if (Character.charCount(cp) > 1 || cp == 0x200D || cp == 0xFE0F || cp == 0x20E3 || (cp >= 0x2600 && cp <= 0x27BF)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        if (spannable != null) {
            try {
                Object[] spans = spannable.getSpans(start, end, android.text.style.ReplacementSpan.class);
                return spans != null && spans.length > 0;
            } catch (Throwable error) {
                AnimLog.report("isEmojiLike", error);
            }
        }
        return false;
    }
}
