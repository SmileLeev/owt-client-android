package owt.sample.p2pandsfu;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import owt.p2pandsfu.utils.HttpUtils;
import owt.p2pandsfu.bean.UserInfo;

public class MainActivity extends AppCompatActivity {
    private TextView etServerurl;
    private TextView etRoomId;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private View btnVideo;
    private UserInfo userInfo;

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
        btnVideo.setEnabled(false);
        executor.execute(() -> {
            String json = HttpUtils.request("http://api.btstu.cn/sjtx/api.php?format=json", "GET", "", false);
            String avatarUrl = JSON.parseObject(json).getString("imgurl");
            userInfo = new UserInfo();
            String rand = UUID.randomUUID().toString().substring(0, 4);
            userInfo.setUsername(android.os.Build.MODEL + "-" + rand);
            userInfo.setAvatarUrl(avatarUrl);
            runOnUiThread(() -> {
                btnVideo.setEnabled(true);
            });
        });
    }

    private void initEvent() {
        btnVideo = findViewById(R.id.btnVideo);
        btnVideo.setOnClickListener(v -> {
            String serverUrl = etServerurl.getText().toString();
            String roomId = etRoomId.getText().toString();
            MeetActivity.start(this, serverUrl, roomId, userInfo);
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
                getSp().edit().putString("serverUrl", editable.toString()).apply();
            }
        });
    }

    private SharedPreferences getSp() {
        return getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
    }

}