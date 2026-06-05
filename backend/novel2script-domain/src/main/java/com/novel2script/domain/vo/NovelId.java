package com.novel2script.domain.vo;

/**
 * Value object for novel identity — enforces non-null, positive ID.
 */
public record NovelId(long value) {

    public NovelId {
        if (value <= 0) {
            throw new IllegalArgumentException("Novel ID must be positive, got: " + value);
        }
    }

    public static NovelId of(long value) {
        return new NovelId(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
