package com.kawan.audiotoolkit.util;

public class ByteRingBuffer {
    private final byte[] buf;
    private int head = 0;
    private int tail = 0;
    private int size = 0;

    public ByteRingBuffer(int capacity) {
        buf = new byte[Math.max(1024, capacity)];
    }

    public synchronized void clear() {
        head = 0;
        tail = 0;
        size = 0;
        notifyAll();
    }

    public synchronized int offer(byte[] src, int off, int len) {
        int toWrite = len;
        // 如果缓冲区空间不足，则丢弃旧数据
        if (size + toWrite > buf.length) {
            int overflow = size + toWrite - buf.length;
            tail = (tail + overflow) % buf.length;
            size -= overflow;
        }

        // 环形写入
        int firstPart = Math.min(toWrite, buf.length - head);
        System.arraycopy(src, off, buf, head, firstPart);
        int secondPart = toWrite - firstPart;
        if (secondPart > 0) {
            System.arraycopy(src, off + firstPart, buf, 0, secondPart);
            head = secondPart;
        } else {
            head += firstPart;
        }
        if (head == buf.length) {
            head = 0;
        }
        size += toWrite;

        notifyAll();
        return toWrite;
    }

    public synchronized int poll(byte[] dst, int off, int len, long waitMs) {
        if (waitMs > 0) {
            long start = System.nanoTime();
            long waitNs = waitMs * 1000000L;
            while (size == 0) {
                long elapsed = System.nanoTime() - start;
                long remainNs = waitNs - elapsed;
                if (remainNs <= 0) break;
                try {
                    long ms = remainNs / 1000000L;
                    int ns = (int) (remainNs % 1000000L);
                    wait(ms, ns);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        int toRead = Math.min(len, size);
        if (toRead > 0) {
            int firstPart = Math.min(toRead, buf.length - tail);
            System.arraycopy(buf, tail, dst, off, firstPart);
            int secondPart = toRead - firstPart;
            if (secondPart > 0) {
                System.arraycopy(buf, 0, dst, off + firstPart, secondPart);
                tail = secondPart;
            } else {
                tail += firstPart;
            }
            if (tail == buf.length) {
                tail = 0;
            }
            size -= toRead;
        }
        return toRead;
    }
}
