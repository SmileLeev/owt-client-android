package owt.sample.p2pandsfu;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import owt.p2pandsfu.MeetActivity;
import owt.p2pandsfu.utils.HttpUtils;
import owt.p2pandsfu.bean.UserInfo;

public class MainActivity extends AppCompatActivity {
    private TextView etServerurl;
    private TextView etRoomId;
    private CheckBox cbScreenSharing;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private View btnStart;
    private UserInfo userInfo;
    private CheckBox cbAudioMute;
    private CheckBox cbVideoMute;
    private CheckBox cbP2P;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initServerUrl();
        initRoomId();
        initEvent();
        initUserInfo();
        initPermission();
    }

    private void initPermission() {
        String[] permissions;
        try {
            permissions = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        if (permissions != null && permissions.length != 0) {
            ActivityCompat.requestPermissions(this, permissions, 1);
        }
    }

    private void initUserInfo() {
        userInfo = new UserInfo();
        String rand = UUID.randomUUID().toString().substring(0, 4);
        userInfo.setUsername(android.os.Build.MODEL + "-" + rand);
        String avatarUrl = getSp().getString("avatarUrl", null);
        if (!TextUtils.isEmpty(avatarUrl)) {
            btnStart.setEnabled(true);
            userInfo.setAvatarUrl(avatarUrl);
            return;
        }
        btnStart.setEnabled(false);
        executor.execute(() -> {
            String json = HttpUtils.request("http://api.btstu.cn/sjtx/api.php?format=json", "GET", "", false);
            String imgurl = JSON.parseObject(json).getString("imgurl");
            getSp().edit().putString("avatarUrl", imgurl).apply();
            userInfo.setAvatarUrl(imgurl);
            runOnUiThread(() -> {
                btnStart.setEnabled(true);
            });
        });
    }

    private void initEvent() {
        btnStart = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(v -> {
            String serverUrl = etServerurl.getText().toString();
            String roomId = etRoomId.getText().toString();
            boolean screenSharing = cbScreenSharing.isChecked();
            boolean p2p = cbP2P.isChecked();
            userInfo.setAudioMuted(cbAudioMute.isChecked());
            userInfo.setVideoMuted(cbVideoMute.isChecked());
            MeetActivity.start(this, serverUrl, roomId, userInfo, screenSharing, p2p);
        });
        cbAudioMute = findViewById(R.id.cbAudioMute);
        cbAudioMute.setChecked(getSp().getBoolean("AudioMute", false));
        cbAudioMute.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSp().edit().putBoolean("AudioMute", isChecked).apply();
        });
        cbVideoMute = findViewById(R.id.cbVideoMute);
        cbVideoMute.setChecked(getSp().getBoolean("VideoMute", false));
        cbVideoMute.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSp().edit().putBoolean("VideoMute", isChecked).apply();
        });
        cbP2P = findViewById(R.id.cbP2P);
        cbP2P.setChecked(getSp().getBoolean("cbP2P", false));
        cbP2P.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSp().edit().putBoolean("cbP2P", isChecked).apply();
        });
    }

    private void initServerUrl() {
        etServerurl = findViewById(R.id.etServerUrl);
        String serverUrl = getSp().getString("serverUrl",
                "https://192.168.0.99:3004"
        );
        etServerurl.setText(serverUrl);
        etServerurl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                getSp().edit().putString("serverUrl", editable.toString()).apply();
            }
        });
    }

    private void initRoomId() {
        cbScreenSharing = findViewById(R.id.cbScreenSharing);
        etRoomId = findViewById(R.id.etRoomId);
        String serverUrl = getSp().getString("roomId",
                ""
        );
        etRoomId.setText(serverUrl);
        etRoomId.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                getSp().edit().putString("roomId", editable.toString()).apply();
            }
        });
    }

    private SharedPreferences getSp() {
        return getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
    }

}