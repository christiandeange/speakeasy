package com.deange.speakeasy.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.deange.speakeasy.generated.Templates;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Templates.greeting().name("Christian").build();
        Templates.name_greeting().name("Michael").nickname("Mike").build();
        Templates.age_greeting().age(24).build();

        Templates.greeting().name(getString(R.string.mock_user)).build();
    }
}
