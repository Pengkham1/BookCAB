package com.dev.bookcab.ui;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.dev.bookcab.R;

import java.util.HashMap;
import java.util.Map;

public class DriverMapUI extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap map;
    private FirebaseAuth auth;
    private FirebaseUser user;
    private FirebaseDatabase firebaseDatabase;
    private DatabaseReference driversAvailable, workingDatabase, occupied;
    private ValueEventListener workingEventListener;


    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationManager locationManager;
    private LocationCallback locationCallback;


    private SwitchMaterial workingSwitch;
    private AppCompatTextView workingStatusView;
    private MaterialCardView bottomView;
    private boolean working = false, startedWorking = false, rideStarted = false;

    private Marker pickupMarker;
    private LatLng pickupLocation;


    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driver_map_ui);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();
        firebaseDatabase = FirebaseDatabase.getInstance();

        workingStatusView = findViewById(R.id.workingStatusView);
        workingSwitch = findViewById(R.id.workingSwitch);
        bottomView = findViewById(R.id.bottomView);

        //firebaseDatabase.getReference().child("Users").child(user.getUid()).setValue(false);

        workingSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            updateWorking(b);
            working = b;
            if (b) {
                workingStatusView.setText("Accepting Rides");
                return;
            }
            workingStatusView.setText("Not Accepting Ride");

        });

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                for (Location location : locationResult.getLocations()) {
                    if (startedWorking) {
                        updateLocationToWorking(location);
                        return;
                    }
                    updateLocation(location);
                }
            }

            @Override
            public void onLocationAvailability(LocationAvailability locationAvailability) {
                super.onLocationAvailability(locationAvailability);
            }
        };

        LocationRequest locationRequest = new LocationRequest();
        locationRequest
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000 * 30);
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());

        getAssignedCustomer();

    }


    private void getAssignedCustomer() {
        if (workingDatabase == null)
            workingDatabase = firebaseDatabase.getReference().child("Users").child("Drivers").child(user.getUid());//.child("customerRequest").child("customerRideId");
        workingEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Log.d("kaku", "onDataChange: " + snapshot.getValue());
                    if (snapshot.getValue() instanceof Boolean)
                        return;
                    if (snapshot.hasChild("customerRequest")) {
                        //show notification
                        Map<String, Object> data = (Map<String, Object>) snapshot.child("customerRequest").getValue();
                        showCustomerInfo(data);

                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        };
        workingDatabase.addValueEventListener(workingEventListener);
    }

    private void showCustomerInfo(Map<String, Object> data) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        bottomSheetDialog.setContentView(R.layout.customer_info);
        bottomSheetDialog.show();
        bottomSheetDialog.setCancelable(false);
        ((MaterialTextView) bottomSheetDialog.findViewById(R.id.rideInfo)).setText("a new ride request has ben received. The ride will start from " + data.get("pickupName").toString() + "and will end at " + data.get("destinationName").toString());
        bottomSheetDialog.findViewById(R.id.acceptRide).setOnClickListener(view -> {
            HashMap<String, Double> pickupLocation = (HashMap<String, Double>) data.get("pickup");
            showDrivePickupLocation(pickupLocation);
            acceptRide(data);
            bottomSheetDialog.dismiss();
        });
        bottomSheetDialog.findViewById(R.id.cancelRide).setOnClickListener(view -> {
            cancelRideRequest(data);
            bottomSheetDialog.dismiss();
        });

    }

    private void acceptRide(Map<String, Object> data) {
        removeFromAvailable();
        DatabaseReference ride = firebaseDatabase.getReference().child("ongoingRides").child(data.get("customerRideId").toString());
        HashMap<String, Object> rideInfo = new HashMap<>();
        rideInfo.put("rider", data); //need to add name and etc
        //HashMap<String, Object> driver = new HashMap<>();
        rideInfo.put("driverId", user.getUid());
        ride.updateChildren(rideInfo);
    }

    private void removeFromAvailable() {
        startedWorking = true;
        if (driversAvailable != null)
            driversAvailable = firebaseDatabase.getReference().child("driversAvailable").child(user.getUid());
        driversAvailable.removeValue();
        updateUI(true);
    }

    private void updateUI(boolean b) {
        if (b) {
            bottomView.findViewById(R.id.workingSwitchView).setVisibility(View.GONE);
            bottomView.findViewById(R.id.startRideBtn).setVisibility(View.VISIBLE);
            addButtonListener();
            return;
        }
        bottomView.findViewById(R.id.workingSwitchView).setVisibility(View.VISIBLE);
        bottomView.findViewById(R.id.startRideBtn).setVisibility(View.GONE);
    }

    private void addButtonListener() {
        bottomView.findViewById(R.id.startRideBtn).setOnClickListener(view -> {
            if (rideStarted) {
                AlertDialog alertDialog = new AlertDialog.Builder(this).create();
                alertDialog.setTitle("Are you sure ?");
                alertDialog.setMessage("Are you sure to end ride ? \nThis action can not be undone !");
                alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "End Ride", (dialogInterface, i) -> {
                    // ride has been ended
                    //show amount and end task
                    rideStarted = false;
                    dialogInterface.dismiss();
                });
                alertDialog.show();
                return;
            }
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setTitle("Are you sure ?");
            alertDialog.setMessage("Are you sure to start ride ? \nThis action can not be undone !");
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Start Ride", (dialogInterface, i) -> {
                //start ride
                //update button
                rideStarted = true;
                showDirectionButton();
                changeBtnText("End Ride");
                dialogInterface.dismiss();
            });
            alertDialog.show();
        });
    }

    private void showDirectionButton() {
        if (rideStarted) {
            findViewById(R.id.showDirectionView).setVisibility(View.VISIBLE);
        } else
            findViewById(R.id.showDirectionView).setVisibility(View.GONE);
    }

    private void changeBtnText(String btnText) {
        ((MaterialButton) bottomView.findViewById(R.id.startRideBtn)).setText(btnText);
    }

    private void cancelRideRequest(Map<String, Object> data) {
        workingDatabase.setValue(working);
        DatabaseReference ride = firebaseDatabase.getReference().child("ongoingRides").child(data.get("customerRideId").toString());
        ride.removeValue();
    }

    private void showDrivePickupLocation(HashMap<String, Double> pickupLocationMap) {
        if (pickupMarker != null)
            pickupMarker.remove();
        if (pickupLocation != null)
            pickupLocation = null;
        pickupLocation = new LatLng(pickupLocationMap.get("latitude"), pickupLocationMap.get("longitude"));
        pickupMarker = map.addMarker(new MarkerOptions()
                .position(pickupLocation)
                .title("Pick up from here"));
        map.animateCamera(CameraUpdateFactory.newLatLng(pickupLocation));
        map.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition(pickupLocation, 11, 0, 0)));
    }

    private void updateWorking(boolean b) {
        if (workingDatabase == null) {
            workingDatabase = firebaseDatabase.getReference().child("Users").child("Drivers").child(user.getUid());
            workingDatabase.setValue(b);
            return;
        }
        workingDatabase.removeValue();
        workingDatabase.setValue(b);

    }

    private void updateLocationToWorking(Location location) {
        if (occupied == null)
            occupied = firebaseDatabase.getReference().child("occupiedDrivers");
        GeoFire geoFire = new GeoFire(occupied);
        geoFire.setLocation(user.getUid(), new GeoLocation(location.getLatitude(), location.getLongitude()));
    }

    private void updateLocation(Location location) {
        if (driversAvailable == null)
            driversAvailable = firebaseDatabase.getReference().child("driversAvailable");
        GeoFire geoFire = new GeoFire(driversAvailable);
        geoFire.setLocation(user.getUid(), new GeoLocation(location.getLatitude(), location.getLongitude()));

    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setMyLocationEnabled(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        updateWorking(false);
        if (workingDatabase != null)
            workingDatabase.removeEventListener(workingEventListener);
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }
}
