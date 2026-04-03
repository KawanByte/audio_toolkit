package com.kawan.audiotoolkit;

import static com.kawan.audiotoolkit.util.UiShow.log;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.kawan.audiotoolkit.util.ByteRingBuffer;
import com.kawan.audiotoolkit.util.UiShow;
import com.kawan.audiotoolkit.util.WavReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class AudioTracker {
    public static class PlayConfig {
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
            return String.format(Locale.US, "sr=%d,ch=%d,enc=%d,frame=%dms", sampleRate, channelCount, format, frameMs);
        }
    }


    private volatile boolean playing = false;
    private Thread playThread;
    private AudioTrack audioTrack;
    UiShow.DeviceItem outputDevice;
    ByteRingBuffer captureSource;
    InputStream wavInputStream;
//    private AudioProcessor downlinkProcessor;

    public int start(@NonNull Context context, @NonNull PlayConfig cfg) {
        int outMask = cfg.channelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int min = AudioTrack.getMinBufferSize(cfg.sampleRate, outMask, cfg.format);
        int bufSize = Math.max(min, cfg.bytesPerFrame() * 8);

        AudioFormat outFormat = new AudioFormat.Builder().setEncoding(cfg.format).setSampleRate(cfg.sampleRate).setChannelMask(outMask).build();
        AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();

        try {
            audioTrack = new AudioTrack(attrs, outFormat, bufSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        } catch (SecurityException se) {
            audioTrack = null;
            return -3;
        }

        if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            audioTrack.release();
            audioTrack = null;
            return -2;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (outputDevice != null && outputDevice.deviceInfo != null) {
                boolean ok = audioTrack.setPreferredDevice(outputDevice.deviceInfo);
                UiShow.log("设置输出路由: " + outputDevice.label + ", ok=" + ok);
            }
        }
        playing = true;
        playThread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            try {
                audioTrack.play();
                String source;
                if (captureSource != null) {
                    source = "回环(采集)";
                } else if (wavInputStream != null) {
                    source = "外部源";
                } else {
                    source = "静音数据";
                }
                log("播放开始: " + cfg + ", source=" + source);

                if (captureSource != null) {
                    playLoopback(cfg);
                } else if (wavInputStream != null) {
                    playWavStream(cfg);
                } else {
                    playSilence(cfg);
                }
            } catch (Exception e) {
                log("播放线程异常: " + Log.getStackTraceString(e));
            } finally {
//                mainHandler.post(this::updateUiState);
                stop();
            }
        }, "play");
        playThread.start();
        return 0;
    }

    public int stop() {
        playing = false;

        if (playThread != null) {
            // 【修复】防止自己 join 自己
            if (Thread.currentThread() != playThread) {
                try {
                    playThread.join(500);
                } catch (InterruptedException ignored) {
                }
            }
            playThread = null;
        }

        if (audioTrack != null) {
            try {
                audioTrack.pause();
                audioTrack.flush();
                audioTrack.stop();
            } catch (Exception ignored) {
            }
            try {
                audioTrack.release();
            } catch (Exception ignored) {
            }
            audioTrack = null;
        }
        return 0;
    }

    public void setCaptureSource(ByteRingBuffer buffer) {
        captureSource = buffer;
    }

    public void setExternalFileStream(InputStream stream) {
        wavInputStream = stream;
    }

    private void playLoopback(PlayConfig cfg) {
        if (captureSource != null) {
            captureSource.clear();
        }
        byte[] buf = new byte[cfg.bytesPerFrame()];
        while (playing) {
            int got = captureSource.poll(buf, 0, buf.length, 50);
            if (got <= 0) {
                // buffer underrun：补静音
                for (int i = 0; i < buf.length; i++) buf[i] = 0;
                got = buf.length;
            }
//            processor.processBytes(buf, 0, got, cfg.format);
            int wrote = audioTrack.write(buf, 0, got);
            if (wrote < 0) {
                UiShow.log("AudioTrack.write(loopback) failed: " + wrote);
                break;
            }
        }
    }

    private boolean playWavStream(PlayConfig playCfg) throws IOException {
        WavReader wav = new WavReader(wavInputStream);
        if (!wav.open()) {
            return false;
        };
        log("WAV header: sr=" + wav.getSampleRate() + ", ch=" + wav.getChannels() + ", bits=" + wav.getBitsPerSample());

        if (wav.getAudioFormat() != 1) {
            UiShow.log("仅支持 PCM WAV(audioFormat=1)，当前=" + wav.getAudioFormat());
            return false;
        }
        if (wav.getBitsPerSample() != 16 && wav.getBitsPerSample() != 8) {
            UiShow.log("仅支持 8/16bit WAV，当前=" + wav.getBitsPerSample());
            return false;
        }

        int wavEncoding = (wav.getBitsPerSample() == 16) ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT;
        if (wav.getSampleRate() != playCfg.sampleRate || wav.getChannels() != playCfg.channelCount || wavEncoding != playCfg.format) {
            UiShow.log("WAV 参数与播放参数不一致，请在UI中选择一致的采样率/声道/格式");
            return false;
        }

        byte[] buf = new byte[playCfg.bytesPerFrame()];
        while (playing) {
            int n = wav.read(buf, 0, buf.length);
            if (n <= 0) {
                UiShow.log("WAV 播放结束");
                break;
            }
//            processor.processBytes(buf, 0, n, playCfg.format);
            int wrote = audioTrack.write(buf, 0, n);
            if (wrote < 0) {
                UiShow.log("AudioTrack.write(wav) failed: " + wrote);
                break;
            }
        }
        return true;
    }

    private void playSilence(PlayConfig playCfg) {
        byte[] zeros = new byte[playCfg.bytesPerFrame()];
        while (playing) {
            int wrote = audioTrack.write(zeros, 0, zeros.length);
            if (wrote < 0) {
                log("AudioTrack.write(silence) failed: " + wrote);
                break;
            }
        }
    }
}
