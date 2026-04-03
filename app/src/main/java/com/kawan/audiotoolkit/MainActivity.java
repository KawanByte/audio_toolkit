package com.kawan.audiotoolkit;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import com.kawan.audiotoolkit.AudioEngine;
import com.kawan.audiotoolkit.util.UiShow;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_ID_RECORD_AUDIO = 1001;
    private static final int REQ_ID_BLUETOOTH_CONNECT = 1001;

    // Views
    private TextView tvStatus;
    private TextView tvSystem;
    private TextView tvLog;
    private TextView tvFile;

    private AutoCompleteTextView recordSourceMenu;
    private AutoCompleteTextView recordSrMenu;
    private AutoCompleteTextView recordChMenu;
    private AutoCompleteTextView recordFmtMenu;
    private AutoCompleteTextView frameLenMenu;
    private AutoCompleteTextView recordDeviceMenu;

    private AutoCompleteTextView playSrMenu;
    private AutoCompleteTextView playChMenu;
    private AutoCompleteTextView playFmtMenu;
    private AutoCompleteTextView playDeviceMenu;
    private AutoCompleteTextView audioModeMenu;
    private AutoCompleteTextView playSourceMenu;
    private TextInputEditText etUrl;

    private SwitchMaterial aecSwitchUplink;
    private SwitchMaterial nsSwitchUplink;
    private SwitchMaterial nsSwitchDownlink;
    private SwitchMaterial agcSwitchUplink;
    private SwitchMaterial agcSwitchDownlink;
    private SwitchMaterial fxGainUplink;
    private SwitchMaterial fxGainDownlink;
    private SwitchMaterial fxInvertUplink;
    private SwitchMaterial fxInvertDownlink;

    private MaterialButton btnStartCapture;
    private MaterialButton btnStopCapture;
    private MaterialButton btnChooseFile;
    private MaterialButton btnStartPlay;
    private MaterialButton btnStopPlay;
    private MaterialButton btnClearLog;

    private AudioManager mAudioManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private UiShow mUiShow;

    private AudioEngine mAudioEngine;

    private volatile boolean capturing = false;
    private volatile boolean playing = false;
    private boolean pendingStartCaptureAfterPerm = false;
    private boolean pendingStartPlayAfterPerm = false;

    private InputStream is;
    private HttpURLConnection conn;
    private Uri selectedFileUri;
    private final ActivityResultLauncher<Intent> filePicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
            return;
        }
        Uri uri = result.getData().getData();
        if (uri == null) {
            return;
        }
        selectedFileUri = uri;
        tvFile.setText(uri.toString());
        UiShow.log("选择文件: " + uri);
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bindViews();
        setupStaticMenus();
        UiShow.setTvLog(tvLog);
        UiShow.setMainHandler(mainHandler);

        setupActions();
        updateUiState();

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mUiShow = new UiShow();
        mUiShow.setAudioManager(mAudioManager);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAudioManager.registerAudioDeviceCallback(new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    refreshDeviceMenus();
                    updateSystemStatus();
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    refreshDeviceMenus();
                    updateSystemStatus();

                }
            }, mainHandler);
        }
        mAudioEngine = new AudioEngine(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDeviceMenus();
        updateSystemStatus();
    }

    @Override
    protected void onDestroy() {
        stopPlayout();
        stopCapture();
        super.onDestroy();
    }

    // -------------------- UI --------------------

    private void bindViews() {
        tvStatus = findViewById(R.id.textView1);
        tvSystem = findViewById(R.id.tv_system);
        tvLog = findViewById(R.id.tv_log);
        tvFile = findViewById(R.id.tv_file);

        // device ui
        recordSourceMenu = findViewById(R.id.record_source_menu);
        recordSrMenu = findViewById(R.id.record_sr_menu);
        recordChMenu = findViewById(R.id.record_ch_menu);
        recordFmtMenu = findViewById(R.id.record_fmt_menu);
        frameLenMenu = findViewById(R.id.frame_len_menu);
        recordDeviceMenu = findViewById(R.id.record_device_menu);

        playSrMenu = findViewById(R.id.play_sr_menu);
        playChMenu = findViewById(R.id.play_ch_menu);
        playFmtMenu = findViewById(R.id.play_fmt_menu);
        playDeviceMenu = findViewById(R.id.play_device_menu);
        audioModeMenu = findViewById(R.id.audio_mode_menu);
        playSourceMenu = findViewById(R.id.play_source_menu);
        etUrl = findViewById(R.id.et_url);

        // algorithm ui
        aecSwitchUplink = findViewById(R.id.aec_switch_uplink);
        nsSwitchUplink = findViewById(R.id.ns_switch_uplink);
        nsSwitchDownlink = findViewById(R.id.ns_switch_downlink);
        agcSwitchUplink = findViewById(R.id.agc_switch_uplink);
        agcSwitchDownlink = findViewById(R.id.agc_switch_downlink);
        fxGainUplink = findViewById(R.id.fx_gain_uplink);
        fxGainDownlink = findViewById(R.id.fx_gain_downlink);
        fxInvertUplink = findViewById(R.id.fx_invert_uplink);
        fxInvertDownlink = findViewById(R.id.fx_invert_downlink);

        btnStartCapture = findViewById(R.id.btn_start_capture);
        btnStopCapture = findViewById(R.id.btn_stop_capture);
        btnChooseFile = findViewById(R.id.btn_choose_file);
        btnStartPlay = findViewById(R.id.btn_start_play);
        btnStopPlay = findViewById(R.id.btn_stop_play);
        btnClearLog = findViewById(R.id.btn_clear_log);
    }

    private void setupStaticMenus() {
        setMenu(recordSourceMenu, getResources().getStringArray(R.array.record_sources), "VOICE_COMMUNICATION");
        setMenu(recordSrMenu, getResources().getStringArray(R.array.sample_rates_numeric), "48000");
        setMenu(recordChMenu, getResources().getStringArray(R.array.channel_counts), "1");
        setMenu(recordFmtMenu, getResources().getStringArray(R.array.pcm_formats), "PCM_16BIT");
        setMenu(frameLenMenu, getResources().getStringArray(R.array.frame_lengths_ms), "20");

        setMenu(playSrMenu, getResources().getStringArray(R.array.sample_rates_numeric), "48000");
        setMenu(playChMenu, getResources().getStringArray(R.array.channel_counts), "1");
        setMenu(playFmtMenu, getResources().getStringArray(R.array.pcm_formats), "PCM_16BIT");

        setMenu(audioModeMenu, getResources().getStringArray(R.array.audio_mode_labels), "IN_COMMUNICATION");
        setMenu(playSourceMenu, getResources().getStringArray(R.array.play_source_labels), "静音数据");

        tvFile.setText("未选择");
    }

    private void setupActions() {
        audioModeMenu.setOnItemClickListener((parent, view, position, id) -> {
//            applyAudioMode(audioModeMenu.getText().toString());

            tvSystem.setText(mUiShow.updateSystemStatus().toString());
        });

//        btnChooseFile.setOnClickListener(v -> chooseFile());

        btnStartCapture.setOnClickListener(v -> startCapture());
        btnStopCapture.setOnClickListener(v -> stopCapture());

        btnStartPlay.setOnClickListener(v -> startPlayout());
        btnStopPlay.setOnClickListener(v -> stopPlayout());

        btnClearLog.setOnClickListener(v -> tvLog.setText(""));
    }

    private void setMenu(AutoCompleteTextView view, String[] items, String defaultValue) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        view.setAdapter(adapter);
        view.setText(defaultValue, false);
    }

    @SuppressLint("MissingPermission")
    private void startCapture() {
        if (capturing) {
            toast("采集已开启请勿重复调用");
            UiShow.log("采集已开启请勿重复调用");
            return;
        }
        if (!requestPermissions()) {
            pendingStartCaptureAfterPerm = true;
            return;
        }
        AudioRecorder.RecordConfig cfg;
        try {
            cfg = readRecordConfigFromUi();
        } catch (Exception e) {
            toast("采集参数解析失败：" + e.getMessage());
            UiShow.log("采集参数解析失败: " + Log.getStackTraceString(e));
            return;
        }
        int ret = mAudioEngine.startCapture(cfg);

        if (ret != 0) {
            toast("采集开启失败：" + ret);
            UiShow.log("采集开启失败: " + ret);
        } else {
            capturing = true;
        }
        updateUiState();
    }

    private void stopCapture() {
        capturing = false;
        mAudioEngine.stopCapture();
        updateUiState();
    }

    private void startPlayout() {
        if (playing) {
            toast("播放已开启请勿重复调用");
            UiShow.log("播放已开启请勿重复调用");
            return;
        }

        AudioTracker.PlayConfig playCfg;
        try {
            playCfg = readPlayConfigFromUi();
        } catch (Exception e) {
            toast("播放参数解析失败：" + e.getMessage());
            UiShow.log("播放参数解析失败: " + Log.getStackTraceString(e));
            return;
        }

        String source = playSourceMenu.getText().toString();
        boolean isLoopbackSource = "回环(采集)".equals(source);
        if (isLoopbackSource) {
            if (!capturing) {
                toast("未开启采集，无法进行回环播放");
                UiShow.log("未开启采集，无法进行回环播放");
                return;
            }
            AudioRecorder.RecordConfig recordCfg;
            try {
                recordCfg = readRecordConfigFromUi();
                if (recordCfg.sampleRate != playCfg.sampleRate || recordCfg.channelCount != playCfg.channelCount || recordCfg.format != playCfg.format) {
                    toast("回环要求采集参数与播放参数一致");
                    UiShow.log("回环参数不一致: record=" + recordCfg + ", play=" + playCfg);
                    return;
                }
            } catch (Exception e) {
                toast("播放参数解析失败：" + e.getMessage());
                UiShow.log("播放参数解析失败: " + Log.getStackTraceString(e));
                return;
            }
            mAudioEngine.setLoopback(true);
        } else {
            // 非 Loopback 模式，显式关闭 Loopback
            mAudioEngine.setLoopback(false);
        }

        try {
            if ("WAV文件".equals(source)) {
                Uri fileUri = selectedFileUri;
                if (fileUri == null) {
                    toast("请先选择音频文件");
                    UiShow.log("WAV文件：未选择 uri");
                    return;
                }
                is = getContentResolver().openInputStream(fileUri);
                if (is == null) {
                    toast("无法打开文件: " + fileUri);
                    UiShow.log("无法打开文件: " + fileUri);
                }

            } else if ("WAV URL".equals(source)) {
                String url = etUrl.getText() == null ? "" : etUrl.getText().toString().trim();
                if (url.isEmpty()) {
                    toast("请填写 URL");
                    UiShow.log("WAV URL：为空");
                    return;
                }

                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setInstanceFollowRedirects(true);
                conn.connect();
                int code = conn.getResponseCode();
                if (code / 100 != 2) {
                    conn.disconnect();
                    throw new IOException("HTTP " + code);
                }

            }
        } catch (IOException e) {
            toast("无法打开音频源: " + e.getMessage());
            return;
        }

        if (is != null) {
            mAudioEngine.setExternalAudioSource(new BufferedInputStream(is));
        }

        int ret = mAudioEngine.startPlayout(playCfg);
        if (ret != 0) {
            toast("播放开启失败：" + ret);
            UiShow.log("播放开启失败: " + ret);
        } else {
            playing = true;
        }
        updateUiState();
    }

    private void stopPlayout() {
        playing = false;
        mAudioEngine.stopPlayout();
        if (conn != null) {
            conn.disconnect();
        }
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            is = null;
        }
        updateUiState();
    }

    private AudioRecorder.RecordConfig readRecordConfigFromUi() {
        AudioRecorder.RecordConfig record = new AudioRecorder.RecordConfig();
        record.audioSource = sourceLabelToValue(recordSourceMenu.getText().toString().trim());
        record.sampleRate = Integer.parseInt(recordSrMenu.getText().toString().trim());
        record.channelCount = Integer.parseInt(recordChMenu.getText().toString().trim());
        record.format = fmtLabelToFormat(recordFmtMenu.getText().toString().trim());
        int frameMs = Integer.parseInt(frameLenMenu.getText().toString().trim());
        record.frameMs = frameMs;
        record.frameSamples = Math.max(1, (record.sampleRate * frameMs) / 1000);
        return record;
    }

    private AudioTracker.PlayConfig readPlayConfigFromUi() {
        AudioTracker.PlayConfig play = new AudioTracker.PlayConfig();
        play.sampleRate = Integer.parseInt(playSrMenu.getText().toString().trim());
        play.channelCount = Integer.parseInt(playChMenu.getText().toString().trim());
        play.format = fmtLabelToFormat(playFmtMenu.getText().toString().trim());
        int frameMs = Integer.parseInt(frameLenMenu.getText().toString().trim());
        play.frameMs = frameMs;
        play.frameSamples = Math.max(1, (play.sampleRate * frameMs) / 1000);
        return play;
    }

    private int fmtLabelToFormat(String label) {
        if ("PCM_8BIT".equals(label)) {
            return AudioFormat.ENCODING_PCM_8BIT;
        }
        return AudioFormat.ENCODING_PCM_16BIT;
    }

    private int sourceLabelToValue(String label) {
        if ("MIC".equals(label)) {
            return MediaRecorder.AudioSource.MIC;
        }
        if ("CAMCORDER".equals(label)) {
            return MediaRecorder.AudioSource.CAMCORDER;
        }
        return MediaRecorder.AudioSource.VOICE_COMMUNICATION;
    }

    // -------------------- system status --------------------
    private void refreshDeviceMenus() {
        List<String> inLabels = new ArrayList<>();
        List<String> outLabels = new ArrayList<>();
        mUiShow.updateDeviceList(inLabels, outLabels);
        setMenu(recordDeviceMenu, inLabels.toArray(new String[0]), inLabels.get(0));
        setMenu(playDeviceMenu, outLabels.toArray(new String[0]), outLabels.get(0));
    }

    private void updateSystemStatus() {
        StringBuilder sb = mUiShow.updateSystemStatus();
        tvSystem.setText(sb.toString());
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void updateUiState() {
        btnStartCapture.setEnabled(!capturing);
        btnStopCapture.setEnabled(capturing);
        btnStartPlay.setEnabled(!playing);
        btnStopPlay.setEnabled(playing);

        String s = "状态：" + (capturing ? "采集中" : "采集停止") + " / " + (playing ? "播放中" : "播放停止");
        tvStatus.setText(s);
    }

    public boolean requestPermissions() {
        boolean needReqBluetoothConnect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && getApplication().getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.S;
        return checkPermission(Manifest.permission.RECORD_AUDIO, REQ_ID_RECORD_AUDIO) && (needReqBluetoothConnect && checkPermission("android.permission.BLUETOOTH_CONNECT", REQ_ID_BLUETOOTH_CONNECT));
    }

    private boolean checkPermission(String permission, int requestCode) {
        if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_ID_RECORD_AUDIO) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            UiShow.log("RECORD_AUDIO permission: " + granted);
            if (granted && pendingStartCaptureAfterPerm) {
                pendingStartCaptureAfterPerm = false;
                startCapture();
            }
            if (granted && pendingStartPlayAfterPerm) {
                pendingStartPlayAfterPerm = false;
                startPlayout();
            }
            if (!granted) {
                pendingStartCaptureAfterPerm = false;
                pendingStartPlayAfterPerm = false;
                toast("没有录音权限，无法开启采集");
            }
        }
    }

}