package com.deange.speakeasy.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.deange.speakeasy.generated.Templates;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final String greeting = Templates.greeting().name("Christian").build();
        final String mike = Templates.greeting_2().name("Mike").age("24").build();

        int i = 0;
    }
}
