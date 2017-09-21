package com.deange.speakeasy.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.deange.speakeasy.generated.Templates;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Templates.greeting(this).name("Christian").build();
        Templates.name_greeting(this).name("Michael").nickname("Mike").build();
        Templates.age_greeting(this).age(24).build();

        Templates.greeting(this).name(getString(R.string.mock_user)).build();
    }
}
