package org.commcare.utils;

import androidx.core.util.Pair;

import org.jetbrains.annotations.Nullable;

public class AndroidXUtils {
    public static Pair<Long, Long> toPair(Long first, Long second) {
        return new Pair<>(first, second);
    }
}
