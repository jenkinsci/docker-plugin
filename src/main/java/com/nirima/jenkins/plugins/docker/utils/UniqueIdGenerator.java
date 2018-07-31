package com.nirima.jenkins.plugins.docker.utils;

public class UniqueIdGenerator {
    private final int radix;
    private final int idLength;
    private long lastNanoTimeUsed;

    public UniqueIdGenerator(int radix) {
        this.radix = radix;
        this.lastNanoTimeUsed = System.nanoTime() - 1L;
        this.idLength = Long.toUnsignedString(-1L, radix).length();
    }

    public String getUniqueId() {
        final long uniqueNumber = getNextUniqueNumber();
        final String uniqueString = Long.toUnsignedString(uniqueNumber, radix);
        final int lengthOfUniqueString = uniqueString.length();
        final int paddingRequired = idLength - lengthOfUniqueString;
        final StringBuilder paddedBuffer = new StringBuilder(idLength);
        paddedBuffer.setLength(idLength);
        for (int i = 0; i < paddingRequired; i++) {
            paddedBuffer.setCharAt(i, '0');
        }
        paddedBuffer.replace(paddingRequired, idLength, uniqueString);
        return paddedBuffer.toString();
    }

    private long getNextUniqueNumber() {
        final long currentNanoTime = System.nanoTime();
        synchronized (this) {
            final long nanosSinceLastTime = currentNanoTime - lastNanoTimeUsed;
            if (nanosSinceLastTime > 0) {
                lastNanoTimeUsed = currentNanoTime;
                return currentNanoTime;
            }
            lastNanoTimeUsed++;
            return lastNanoTimeUsed;
        }
    }
}
