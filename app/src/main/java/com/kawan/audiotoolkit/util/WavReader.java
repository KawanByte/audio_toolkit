package com.kawan.audiotoolkit.util;

import java.io.IOException;
import java.io.InputStream;

public class WavReader {
    final InputStream is;
    int audioFormat;
    int channels;
    int sampleRate;
    int bitsPerSample;
    long dataRemaining;

    public WavReader(InputStream is) {
        this.is = is;
    }

    public boolean open() throws IOException {
        return parseHeader();
    }

    public int read(byte[] dst, int off, int len) throws IOException {
        if (dataRemaining <= 0) return -1;
        int want = (int) Math.min(len, dataRemaining);
        int n = is.read(dst, off, want);
        if (n > 0) {
            dataRemaining -= n;
        }
        return n;
    }

    public int getAudioFormat() {
        return audioFormat;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public int getBitsPerSample() {
        return bitsPerSample;
    }

    private boolean parseHeader() throws IOException {
        byte[] header = new byte[12];
        readFully(header, 0, 12);
        if (!fourcc(header, 0).equals("RIFF") || !fourcc(header, 8).equals("WAVE")) {
            UiShow.log("不是 WAV(RIFF/WAVE)");
            return false;
        }

        boolean gotFmt = false;
        boolean gotData = false;
        while (!gotData) {
            byte[] chunkHeader = new byte[8];
            readFully(chunkHeader, 0, 8);
            String id = fourcc(chunkHeader, 0);
            int size = leInt(chunkHeader, 4);

            if ("fmt ".equals(id)) {
                byte[] fmt = new byte[size];
                readFully(fmt, 0, size);
                audioFormat = leShort(fmt, 0);
                channels = leShort(fmt, 2);
                sampleRate = leInt(fmt, 4);
                bitsPerSample = leShort(fmt, 14);
                gotFmt = true;
            } else if ("data".equals(id)) {
                if (!gotFmt) {
                    UiShow.log("WAV 缺少 fmt chunk");
                    return false;
                }
                dataRemaining = size;
                gotData = true;
            } else {
                // skip
                long skipped = 0;
                while (skipped < size) {
                    long s = is.skip(size - skipped);
                    if (s <= 0) break;
                    skipped += s;
                }
            }
        }
        return true;
    }

    private void readFully(byte[] b, int off, int len) throws IOException {
        int n = 0;
        while (n < len) {
            int r = is.read(b, off + n, len - n);
            if (r < 0) throw new IOException("EOF");
            n += r;
        }
    }

    private static String fourcc(byte[] b, int off) {
        return new String(b, off, 4);
    }

    private static int leInt(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static int leShort(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }
}
