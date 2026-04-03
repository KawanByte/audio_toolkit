package com.kawan.audiotoolkit;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import com.kawan.audiotoolkit.util.ByteRingBuffer;

import java.io.InputStream;
import java.util.Locale;

public class AudioEngine {
    private Context mContext;
    private AudioRecorder mRecorder;
    private AudioTracker mTracker;
    private ByteRingBuffer mPlayoutSource;
    private InputStream mExternalSource;
    private boolean mLoopbackEnabled = false;

    AudioEngine(@NonNull Context context) {
        mContext = context;
        // 初始化 Recorder 以便能随时开启 Loopback
        mRecorder = new AudioRecorder();
        mTracker = new AudioTracker();
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public int startCapture(AudioRecorder.RecordConfig cfg) {
        return mRecorder.start(mContext, cfg);
    }
    public int stopCapture() {
        return mRecorder.stop();
    }

    public void setExternalAudioSource(InputStream source) {
        mExternalSource = source;

        mTracker.setExternalFileStream(source);
        // 设置外部源时，如果不强制 Loopback，则清空 Loopback 源以优先使用外部源（逻辑取决于 Tracker 内部优先级）
        // 但根据 Tracker 逻辑：if (captureSource != null) ... else if (wavInputStream != null)
        // 所以只要 captureSource 有值，就会忽略 wavInputStream。
        // 如果用户想切换到 wav，应该 setLoopback(false)。
    }

    public void setLoopback(boolean enabled) {
        mLoopbackEnabled = enabled;
        updatePlayoutSource();
    }

    private void updatePlayoutSource() {
        if (mLoopbackEnabled) {
            // 从 Recorder 获取 Buffer
            mPlayoutSource = mRecorder.getRingBuffer();
        } else {
            mPlayoutSource = null;
        }
        mTracker.setCaptureSource(mPlayoutSource);
    }

    public int startPlayout(AudioTracker.PlayConfig cfg) {
        return mTracker.start(mContext, cfg);
    }

    public int stopPlayout() {
        return mTracker.stop();
    }
}
