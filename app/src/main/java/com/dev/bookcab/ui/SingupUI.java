package com.dev.bookcab.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.dev.bookcab.R;

public class SingupUI extends AppCompatActivity {

    private FirebaseAuth auth;
    private TextInputEditText emailField, passwordField;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signup_ui);

        //Checking permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 100);
            return;
        }

        auth = FirebaseAuth.getInstance();

        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        findViewById(R.id.enterApp).setOnClickListener(this::singupUser);
        findViewById(R.id.enterAsDriver).setOnClickListener(this::signupDriver);

    }


    /**
     * @param view param has nothing to do with this method, its required to access lambda function
     *             this method signs up user into this app as a driver
     */
    private void signupDriver(View view) {
        if (auth.getCurrentUser() != null) {
            startInfoUI(0);
            return;
        }
        String email = emailField.getText().toString().trim(); //getting strings from email input field
        String password = passwordField.getText().toString().trim(); //getting password from password input field
        if (email.length() == 0) return; //normal validation
        else if (password.length() < 6)
            return;
        /**
         * Firebase function to sign in user with email and password
         */
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        startInfoUI(0); //after success response, navigates to information update page
                        return;
                    }
                    Toast.makeText(this, "Failed ! " + task.getException(), Toast.LENGTH_SHORT).show();
                    create(email, password, 0); //on failing, we are registering the user
                });

    }

    /**
     * Firebase auth function to create user with email and password
     * @param email
     * @param password
     * @param i
     */
    private void create(String email, String password, int i) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        startInfoUI(i); //after success response, navigates to information update page
                        return;
                    }
                    Toast.makeText(this, "Failed ! " + task.getException(), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(this, e -> {
                    Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * @param view param has nothing to do with this method, its required to access lambda function
     *             this method signs up user into this app as an end user or rider
     */
    private void singupUser(View view) {
        if (auth.getCurrentUser() != null) {
            startInfoUI(1);
            return;
        }
        String email = emailField.getText().toString().trim(); //getting strings from email input field
        String password = passwordField.getText().toString().trim(); //getting password from password input field
        if (email.length() == 0)
            return;
        else if (password.length() < 6)
            return;
        /**
         * Firebase function to sign in user with email and password
         */
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        startInfoUI(1); //after success response, navigates to information update page
                        return;
                    }
                    Toast.makeText(this, "Failed ! " + task.getException(), Toast.LENGTH_SHORT).show();
                    create(email, password, 1); //on failing, we are registering the user
                });
    }


    private void startInfoUI(int i) {
        Intent intent = new Intent(this, InfoUI.class);
        intent.putExtra("IS_DRIVER", i == 0); //passing a boolean value to InfoUI Activity with intent
        startActivity(intent);
        finish();
    }

}
