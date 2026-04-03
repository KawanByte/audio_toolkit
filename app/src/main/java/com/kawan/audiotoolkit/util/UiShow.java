package com.kawan.audiotoolkit.util;

import android.annotation.SuppressLint;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UiShow {
    private static final String TAG = "AudioToolkit";
    private static TextView mTvLog = null;
    private static Handler mHandler = null;

    private AudioManager mAudioManager;
    private final List<DeviceItem> mInputDevices = new ArrayList<>();
    private final List<DeviceItem> mOutputDevices = new ArrayList<>();

    public static class DeviceItem {
        public final String label;
        public final AudioDeviceInfo deviceInfo;

        DeviceItem(String label, AudioDeviceInfo deviceInfo) {
            this.label = label;
            this.deviceInfo = deviceInfo;
        }

        static DeviceItem defaultItem(String label) {
            return new DeviceItem(label, null);
        }

        static DeviceItem from(AudioDeviceInfo d) {
            String name = null;
            String type = null;
            int device_id = -1;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                name = d.getProductName() == null ? "" : d.getProductName().toString();
                type = deviceTypeToStr(d.getType());
                device_id = d.getId();
            }
            String label = String.format(Locale.US, "%s | %s | id=%d", type, name, device_id);
            return new DeviceItem(label, d);
        }

        static String deviceTypeToStr(int type) {
            switch (type) {
                case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                    return "BUILTIN_MIC";
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                    return "WIRED_HEADSET";
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                    return "WIRED_HEADPHONES";
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                    return "BT_SCO";
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                    return "BT_A2DP";
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                    return "SPEAKER";
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                    return "USB_DEVICE";
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    return "USB_HEADSET";
                default:
                    return "TYPE_" + type;
            }
        }
    }

    public static void setTvLog(TextView log) {
        mTvLog = log;
    }

    public static void setMainHandler(Handler handler) {
        mHandler = handler;
    }

    public void setAudioManager(AudioManager audioManager) {
        mAudioManager = audioManager;
    }

    public static void log(String msg) {
        if (mTvLog == null || mHandler == null) {
            return;
        }
        Log.d(TAG, msg);
        mHandler.post(() -> {
            String old = mTvLog.getText() == null ? "" : mTvLog.getText().toString();
            String next = old + (old.isEmpty() ? "" : "\n") + msg;
            if (next.length() > 12000) {
                next = next.substring(next.length() - 12000);
            }
            mTvLog.setText(next);
        });
    }

    public void updateDeviceList(List<String> inLabels, List<String> outLabels) {
        mInputDevices.clear();
        mOutputDevices.clear();

        mInputDevices.add(DeviceItem.defaultItem("默认(系统)"));
        mOutputDevices.add(DeviceItem.defaultItem("默认(系统)"));
        inLabels.add(mInputDevices.get(0).label);
        outLabels.add(mOutputDevices.get(0).label);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioDeviceInfo[] ins = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            AudioDeviceInfo[] outs = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo d : ins) {
                DeviceItem it = DeviceItem.from(d);
                mInputDevices.add(it);
                inLabels.add(it.label);
            }
            for (AudioDeviceInfo d : outs) {
                DeviceItem it = DeviceItem.from(d);
                mOutputDevices.add(it);
                outLabels.add(it.label);
            }
        }
    }

    public List<DeviceItem> getInputDevices() {
        return mInputDevices;
    }

    public List<DeviceItem> getOutputDevices() {
        return mOutputDevices;
    }

    public StringBuilder updateSystemStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("mode=").append(modeToStr(mAudioManager.getMode())).append("\n");
        sb.append("micMute=").append(mAudioManager.isMicrophoneMute()).append("\n");
        sb.append("musicVol=").append(mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)).append("/").append(mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).append("\n");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sb.append("inputs=").append(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).length).append("\n");
            sb.append("outputs=").append(mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).length).append("\n");
        } else {
            sb.append("inputs/outputs=API<23 不支持枚举\n");
        }
        return sb;
    }

    private String modeToStr(int mode) {
        switch (mode) {
            case AudioManager.MODE_NORMAL:
                return "NORMAL";
            case AudioManager.MODE_IN_COMMUNICATION:
                return "IN_COMMUNICATION";
            case AudioManager.MODE_IN_CALL:
                return "IN_CALL";
            default:
                return String.valueOf(mode);
        }
    }
}
