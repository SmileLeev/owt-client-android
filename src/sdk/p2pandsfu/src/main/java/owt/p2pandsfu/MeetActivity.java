package owt.p2pandsfu;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;

import com.alibaba.fastjson.JSON;

import owt.p2pandsfu.bean.UserInfo;

public class MeetActivity extends AppCompatActivity {
    public static void start(Context context, String serverUrl, String roomId, UserInfo userInfo, boolean screenSharing) {
        Intent starter = new Intent(context, MeetActivity.class);
        starter.putExtra("serverUrl", serverUrl);
        starter.putExtra("roomId", roomId);
        starter.putExtra("userInfo", JSON.toJSONString(userInfo));
        starter.putExtra("screenSharing", screenSharing);
        context.startActivity(starter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meet);

        String serverUrl = getIntent().getStringExtra("serverUrl");
        String roomId = getIntent().getStringExtra("roomId");
        UserInfo userInfo = JSON.parseObject(getIntent().getStringExtra("userInfo"), UserInfo.class);
        boolean screenSharing = getIntent().getBooleanExtra("screenSharing", false);
        Fragment fragment = MeetFragment.newInstance(serverUrl, roomId, userInfo, screenSharing);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.flFragment, fragment)
                .commit();
    }
}