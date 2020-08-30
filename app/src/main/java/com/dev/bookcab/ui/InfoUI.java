package com.dev.bookcab.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.dev.bookcab.R;

import java.util.HashMap;

public class InfoUI extends AppCompatActivity {

    private TextInputEditText nameField, phoneField, carNameField, carRcField;
    private boolean IS_DRIVER = false;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.info_ui);

        /**
         * checking intent's extras and storing the boolean value for global access
         */
        if (getIntent().getExtras() != null) {
            IS_DRIVER = getIntent().getExtras().getBoolean("IS_DRIVER");
        }

        nameField = findViewById(R.id.nameField);
        phoneField = findViewById(R.id.phoneField);
        carNameField = findViewById(R.id.carNameField);
        carRcField = findViewById(R.id.carRCField);

        findViewById(R.id.infoSaveBtn).setOnClickListener(this::saveInfo);

        if (!IS_DRIVER) { // show or hide car name and car rc input field
            carNameField.setVisibility(View.GONE);
            carRcField.setVisibility(View.GONE);
        }

        sharedPreferences = getSharedPreferences("data", MODE_PRIVATE);

        if (sharedPreferences.contains("hasData")) {
            if (sharedPreferences.getBoolean("hasData", true)) {
                finishAndStart();
            }
        }


    }

    /**
     * function to save inputted data
     *
     * @param view
     */
    private void saveInfo(View view) {
        String name = nameField.getText().toString().trim();
        String phone = phoneField.getText().toString().trim();
        String carName = carNameField.getText().toString().trim();
        String carRC = carRcField.getText().toString().trim();
        if (name.length() == 0) {
            Toast.makeText(this, "Enter Name", Toast.LENGTH_SHORT).show();
            return;
        } else if (phone.length() < 10) {
            Toast.makeText(this, "Enter 10 digit Phone No.", Toast.LENGTH_SHORT).show();
            return;
        } else if (IS_DRIVER && carName.length() == 0) {
            Toast.makeText(this, "Enter Car Name", Toast.LENGTH_SHORT).show();
            return;
        } else if (IS_DRIVER && carRC.length() == 0) {
            Toast.makeText(this, "Enter Car RC no.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance(); //firestore instance
        FirebaseAuth auth = FirebaseAuth.getInstance(); //firebase auth instance
        FirebaseUser user = auth.getCurrentUser();
        HashMap<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("phone", phone);
        data.put("email", user.getEmail());
        if (IS_DRIVER) {
            data.put("carName", carName);
            data.put("carRC", carRC);
        }
        /**
         * saving user data to firestore
         * users(Collection) -> drivers/customers(Document) -> all(Collection) -> $user's_uuid(Document)
         */
        firestore.collection("users").document(IS_DRIVER ? "drivers" : "customers").collection("all").document(user.getUid())
                .set(data)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        finishAndStart(); //on success just finishing current activity and starting the maps activity accordingly
                    }
                });

    }

    /**
     * if signed in user is a driver starting DriverMapUI.class
     * if signed in user is a driver starting MapsActivity.class
     */
    private void finishAndStart() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("hasData", true);
        editor.apply();
        startActivity(new Intent(this, IS_DRIVER ? DriverMapUI.class : MapsActivity.class));
        finish();
    }

}
