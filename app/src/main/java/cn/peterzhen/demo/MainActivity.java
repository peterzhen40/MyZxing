package cn.peterzhen.demo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import cn.peterzhen.R;
import cn.peterzhen.zxing.CaptureActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static String BASE_URL = "http://192.168.11.109:7001";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final TextView textView = findViewById(R.id.textView);
        final EditText etAddress = findViewById(R.id.et_address);
        final EditText etUsername = findViewById(R.id.et_username);
        final EditText etPassword = findViewById(R.id.et_password);
        Button button = findViewById(R.id.button);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CaptureActivity.requestScan(MainActivity.this,100);
            }
        });
    }

}
