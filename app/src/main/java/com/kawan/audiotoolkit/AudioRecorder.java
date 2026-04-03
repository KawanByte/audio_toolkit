package com.kawan.audiotoolkit;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.kawan.audiotoolkit.util.*;

import java.util.Locale;

public class AudioRecorder {
    public static class RecordConfig {
        int audioSource;
        int sampleRate;
        int channelCount;
        int format;
        int frameMs;
        int frameSamples;

        int bytesPerSample() {
            return format == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1;
        }

        int bytesPerFrame() {
            return frameSamples * channelCount * bytesPerSample();
        }

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.US, "src=%d,sr=%d,ch=%d,format=%d,frame_size=%dms", audioSource, sampleRate, channelCount, format, frameMs);
        }
    }

    private volatile boolean capturing = false;
    private Thread captureThread;
    private AudioRecord audioRecord;
    private final ByteRingBuffer ring = new ByteRingBuffer(64 * 1024); // 64KB to reduce latency
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public int start(@NonNull Context context, @NonNull RecordConfig cfg) {
        if (!hasRecordPermission(context)) {
            return -1;
        }

        int inMask = cfg.channelCount == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO;
        int min = AudioRecord.getMinBufferSize(cfg.sampleRate, inMask, cfg.format);
        int bufSize = Math.max(min, cfg.bytesPerFrame() * 4);

        try {
            audioRecord = new AudioRecord(cfg.audioSource, cfg.sampleRate, inMask, cfg.format, bufSize);
        } catch (SecurityException se) {
            audioRecord = null;
            return -3;
        }


        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release();
            audioRecord = null;
            return -2;
        }

        capturing = true; // 确保采集标志位为 true
        captureThread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            byte[] buf = new byte[cfg.bytesPerFrame()];
            try {
                audioRecord.startRecording();
                UiShow.log("采集开始: " + cfg);
                while (capturing) {
                    int read = audioRecord.read(buf, 0, buf.length);
                    if (read > 0) {
//                        uplinkProcessor.processBytes(buf, 0, read, cfg.format);
                        ring.offer(buf, 0, read);
                    }
                }
            } catch (Exception e) {
                UiShow.log("采集线程异常: " + Log.getStackTraceString(e));
            }
//            finally {
//                mainHandler.post(this::updateUiState);
//            }
        }, "capture");
        captureThread.start();
        return 0;
    }

    public int stop() {
        capturing = false;

        if (captureThread != null) {
            try {
                captureThread.join(500);
            } catch (InterruptedException ignored) {
            }
            captureThread = null;
        }

        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (Exception ignored) {
            }
            try {
                audioRecord.release();
            } catch (Exception ignored) {
            }
            audioRecord = null;
        }
        return 0;
    }
    public boolean isCapturing() {
        return capturing;
    }

    public ByteRingBuffer getRingBuffer() {
        return ring;
    }

    private boolean hasRecordPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
}
