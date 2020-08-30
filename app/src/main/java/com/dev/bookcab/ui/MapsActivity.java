package com.dev.bookcab.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.app.ActivityCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.dev.bookcab.R;
import com.dev.bookcab.services.NotificationService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private boolean loading_route = false, driverFound = false;
    private double searchRadius = 1;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private Location location;
    private LocationManager locationManager;
    private LocationCallback locationCallback;
    private LatLng myPlace = null, destination = null, pickup = null;
    private Marker myMarker = null, destinationMarker = null, driverMarker = null;
    private AppCompatImageView getMyLocation;
    private String destinationName = "", pickupName = "";
    private BottomSheetDialog destinationSelector;

    private int PICKUP_REQUEST = 100, DROP_REQUEST = 101;

    private ArrayList<Marker> arrayMarker = new ArrayList<>();
    private ArrayList<HashMap<String, Double>> arraySteps = new ArrayList<>();
    private ArrayList<HashMap<String, String>> arrayLocations = new ArrayList<>();
    private Polyline direction = null;

    private FirebaseDatabase firebaseDatabase;
    private FirebaseAuth auth;
    private FirebaseUser user;
    private ValueEventListener driverCancellationListener, driverAcceptanceListener, driverLocationListener;
    private DatabaseReference driver, ride, driverLocation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map_ui);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this); //location provider

        //region Firebase
        firebaseDatabase = FirebaseDatabase.getInstance();
        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();
        //endregion

        //Checking permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 100);
            return;
        }
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        //checkForLocation();
        locationCallback = new LocationCallback() { //location callback to get location periodically
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                for (Location location : locationResult.getLocations()) {
                    if (myMarker != null)
                        myMarker.remove();
                    myPlace = new LatLng(location.getLatitude(), location.getLongitude());
                    myMarker = mMap.addMarker(new MarkerOptions().position(myPlace).title("Marker at My place"));
                    //mMap.addMarker(myMarker.position(myPlace));
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(myPlace));
                    //mMap.animateCamera(CameraUpdateFactory.zoomTo(mMap.getMaxZoomLevel()));
                }
                //fusedLocationProviderClient.removeLocationUpdates(locationCallback);
            }

            @Override
            public void onLocationAvailability(LocationAvailability locationAvailability) {
                super.onLocationAvailability(locationAvailability);
            }
        };

        getMyLocation = findViewById(R.id.getMyLocation);
        getMyLocation.setOnClickListener(v -> checkForLocation());


        if (!Places.isInitialized())
            Places.initialize(this, getString(R.string.google_maps_key));
        PlacesClient placesClient = Places.createClient(this);

    }

    private void listenForDriverAcceptance() { //listen if driver accepted our request
        if (ride == null)
            ride = firebaseDatabase.getReference().child("ongoingRides").child(user.getUid());
        driverAcceptanceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Log.d("kaku", "onDataChange: " + snapshot.getValue());
                    HashMap<String, Object> data = (HashMap<String, Object>) snapshot.getValue();
                    getDriverInfo(data.get("driverId").toString());
                    //show notification
                    if (driver != null)
                        driver.removeEventListener(driverCancellationListener);

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        };
        ride.addValueEventListener(driverAcceptanceListener);
    }

    //after driver accept we will get driver info from here
    private void getDriverInfo(String driverId) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.collection("users").document("drivers").collection("all").document(driverId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot snapshot = task.getResult();
                        Log.d("kaku", "getDriverInfo: " + snapshot.getData());
                        Toast.makeText(this, "Driver Found ! ", Toast.LENGTH_SHORT).show();
                        //show a notification
                        new NotificationService(this).showNotification(NotificationService.Type.Accepted);
                        findViewById(R.id.whereToView).setVisibility(View.GONE);
                        //start getting driver location
                        startGettingDriverLocation(driverId);
                    }
                });
        if (destinationSelector != null && destinationSelector.isShowing()) {
            destinationSelector.dismiss();
            destinationSelector = null;
        }
    }

    private void startGettingDriverLocation(String driverId) {
        if (driverLocation != null)
            driverLocation = null;
        driverLocation = firebaseDatabase.getReference().child("occupiedDrivers").child(driverId).child("l");
        driverLocationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    List<Object> data = (List<Object>) snapshot.getValue();
                    double lat = 0, lng = 0;
                    if (data.get(0) != null)
                        lat = Double.parseDouble(data.get(0).toString());
                    if (data.get(1) != null)
                        lng = Double.parseDouble(data.get(1).toString());
                    LatLng driverLatLng = new LatLng(lat, lng);
                    if (driverMarker != null)
                        driverMarker.remove();
                    driverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("Driver"));
                    calculateDistance(driverLatLng);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        };
        driverLocation.addValueEventListener(driverLocationListener);
    }

    private void calculateDistance(LatLng driverLatLng) {
        Location driverLoc = new Location("");
        driverLoc.setLatitude(driverLatLng.latitude);
        driverLoc.setLongitude(driverLatLng.longitude);

        Location customerLoc = new Location("");
        driverLoc.setLatitude(pickup.latitude);
        driverLoc.setLongitude(pickup.longitude);
        if (customerLoc.distanceTo(driverLoc) < 2000) {
            // notify driver arrived
            Toast.makeText(this, "Driver arrived", Toast.LENGTH_SHORT).show();
            //stop listening driver location
            driverLocation.removeEventListener(driverLocationListener);
        }
    }

    private void checkForLocation() {
        if (LocationManagerCompat.isLocationEnabled(locationManager)) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                return;
            }
            LocationRequest locationRequest = new LocationRequest();
            locationRequest
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    .setInterval(1000);
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } else {
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setTitle("Enable Location");
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Retry", (dialog, which) -> {
                checkForLocation();
            });
            alertDialog.show();
        }


    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        //checkForLocation();
        // Add a marker in Sydney and move the camera
        if (location != null) {
            myPlace = new LatLng(location.getLatitude(), location.getLongitude());
        }
        //myPlace = new LatLng(-34, 151);
        //myMarker = mMap.addMarker(new MarkerOptions().position(myPlace).title("Marker in Sydney"));
        //mMap.moveCamera(CameraUpdateFactory.newLatLng(myPlace));
        //mMap.setMyLocationEnabled(true);
        mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.getMaxZoomLevel() / 2));
        //mMap.addCircle(new CircleOptions().center(myPlace).radius(100.0).fillColor(getResources().getColor(R.color.colorAccent)).strokeWidth(2f));
    }


    public void showDestinationSelector(View view) {
        if (destinationSelector == null) {
            destinationSelector = new BottomSheetDialog(this);
            destinationSelector.setContentView(R.layout.destination_selector);
        }
        destinationSelector.show();
        destinationSelector.setCancelable(false);
        destinationSelector.findViewById(R.id.pickupField).setOnClickListener(v -> openPlacesIntent(PICKUP_REQUEST));
        destinationSelector.findViewById(R.id.dropField).setOnClickListener(v -> openPlacesIntent(DROP_REQUEST));
        destinationSelector.findViewById(R.id.destinationNext).setOnClickListener(this::proceed);
        destinationSelector.findViewById(R.id.closeBtn).setOnClickListener(v -> destinationSelector.dismiss());
    }

    private void proceed(View view) {
        //if (((AppCompatTextView)view).getText().toString().toLowerCase().equalsIgnoreCase(""))
        //getRoutes();
        startSearchingDriver();
    }

    private void startSearchingDriver() {
        //DatabaseReference usersdb = firebaseDatabase.getReference("Users").child("customers");
        /*DatabaseReference rideRequest = firebaseDatabase.getReference("RideRequest");
        GeoFire geoFire = new GeoFire(rideRequest);
        geoFire.setLocation(user.getUid(), new GeoLocation(pickup.latitude, pickup.longitude));*/

        getClosestDriver();

    }

    //get closest available driver
    private void getClosestDriver() {
        DatabaseReference driversAvailable = firebaseDatabase.getReference().child("driversAvailable");
        GeoFire geoFire = new GeoFire(driversAvailable);
        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(pickup.latitude, pickup.longitude), searchRadius);
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                driverFound = true;
                assignToDriver(key, location);
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                if (!driverFound) {
                    searchRadius = searchRadius + 0.5;
                    getClosestDriver();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private void assignToDriver(String key, GeoLocation location) {
        DatabaseReference driverRef = firebaseDatabase.getReference().child("Users").child("Drivers").child(key).child("customerRequest");
        HashMap<String, Object> data = new HashMap<>();
        data.put("customerRideId", user.getUid());
        data.put("destination", destination);
        data.put("destinationName", destinationName);
        data.put("pickup", pickup);
        data.put("pickupName", pickupName);
        driverRef.updateChildren(data);

        driver = firebaseDatabase.getReference().child("Users").child("Drivers").child(key);
        driverCancellationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.getValue() instanceof Boolean) {
                    //driver rejected
                    //request another driver
                    new NotificationService(MapsActivity.this).showNotification(NotificationService.Type.Canceled);
                    Toast.makeText(MapsActivity.this, "Ride Canceled ! \nPlease Try again", Toast.LENGTH_SHORT).show();
                    ride.removeEventListener(driverAcceptanceListener);
                    driver.removeEventListener(driverCancellationListener);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        };
        driver.addValueEventListener(driverCancellationListener);

        listenForDriverAcceptance();

    }

    private void getRoutes() {
        loading_route = true;
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, getApiUrl(), null, (Response.Listener<JSONObject>) this::parseDestination, error -> {
            loading_route = false;
        });
        Volley.newRequestQueue(this)
                .add(jsonObjectRequest);
    }

    private void parseDestination(JSONObject response) {
        List<List<HashMap<String, String>>> routesA = new ArrayList<List<HashMap<String, String>>>();
        String distance = null, duration = null;
        try {
            JSONObject routes = response.getJSONArray("routes").getJSONObject(0);
            JSONObject legs = routes.getJSONArray("legs").getJSONObject(0);
            JSONArray steps = legs.getJSONArray("steps");

            distance = legs.getJSONObject("distance").getString("text");
            duration = legs.getJSONObject("duration").getString("text");
            List<HashMap<String, String>> path = new ArrayList<>();

            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.getJSONObject(i);
                List<LatLng> list = decodePoly(step.getJSONObject("polyline").getString("points"));
                for (int k = 0; k < list.size(); k++) {
                    HashMap<String, String> hm = new HashMap<>();
                    hm.put("lat", Double.toString(((LatLng) list.get(k)).latitude));
                    hm.put("lng", Double.toString(((LatLng) list.get(k)).longitude));
                    path.add(hm);
                }
            }
            routesA.add(path);
            //drawPolyline(arraySteps, Color.BLUE, 20);

        } catch (JSONException e) {
            e.printStackTrace();
        }

        ArrayList<LatLng> points = null;
        PolylineOptions lineOptions = null;
        MarkerOptions markerOptions = new MarkerOptions();

        for (int i = 0; i < routesA.size(); i++) {
            points = new ArrayList<LatLng>();
            lineOptions = new PolylineOptions();

            // Fetching i-th route
            List<HashMap<String, String>> path = routesA.get(i);

            // Fetching all the points in i-th route
            for (int j = 0; j < path.size(); j++) {
                HashMap<String, String> point = path.get(j);

                double lat = Double.parseDouble(point.get("lat"));
                double lng = Double.parseDouble(point.get("lng"));
                LatLng position = new LatLng(lat, lng);

                points.add(position);
            }

            // Adding all the points in the route to LineOptions
            lineOptions.addAll(points);
            lineOptions.width(10);
            lineOptions.color(Color.BLACK);
        }
        if (direction != null)
            direction.remove();
        direction = mMap.addPolyline(lineOptions);
        showDistanceAndTime(distance, duration);
        loading_route = false;

    }

    private void showDistanceAndTime(String distance, String duration) {
        if (!destinationSelector.isShowing())
            destinationSelector.show();
        ((AppCompatTextView) destinationSelector.findViewById(R.id.distanceView))
                .setText("Distance between your location and destination is " + distance + " and it will take " + duration + " to reach there. \nThe charges will be Rs. " + calculateCharge(distance) + " @ 4.5 Rs/Km");
        destinationSelector.findViewById(R.id.destinationNext).setVisibility(View.VISIBLE);
    }

    private String calculateCharge(String distance) {
        distance = distance.split(" ")[0];
        int charge = (int) (Float.parseFloat(distance) * 4.5);
        return String.valueOf(charge);
    }

    /**
     * Method available on internet to decode polyline
     * @param encoded
     * @return
     */
    private List<LatLng> decodePoly(String encoded) {

        List<LatLng> poly = new ArrayList<LatLng>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }
        return poly;
    }

    private void drawPolyline(ArrayList<HashMap<String, Double>> arraySteps, int blue, float v) {
        ArrayList<LatLng> arrayLatLng = new ArrayList<>();
        for (int i = 0; i < arraySteps.size(); i++) {
            arrayLatLng.add(new LatLng(arraySteps.get(0).get("start_lat"), arraySteps.get(0).get("start_lng")));
            arrayLatLng.add(new LatLng(arraySteps.get(0).get("stop_lat"), arraySteps.get(0).get("stop_lng")));
        }
        PolylineOptions polylineOptions = new PolylineOptions().clickable(true).addAll(arrayLatLng);
        mMap.addPolyline(polylineOptions);
    }

    private String getApiUrl() {
        return "https://maps.googleapis.com/maps/api/directions/json?origin=" +
                pickup.latitude + "," + pickup.longitude + "&destination=" +
                destination.latitude + "," + destination.longitude + "&mode=driving&sensor=false&key=" +
                getResources().getString(R.string.google_maps_key);
    }

    private void openPlacesIntent(int REQUEST) {
        List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG);
        Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                .build(this);
        startActivityForResult(intent, REQUEST == PICKUP_REQUEST ? PICKUP_REQUEST : DROP_REQUEST);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DROP_REQUEST && resultCode == Activity.RESULT_OK) {
            Place place = Autocomplete.getPlaceFromIntent(data);
            destination = place.getLatLng();
            destinationName = place.getName();
            if (destinationSelector.isShowing()) {
                ((TextInputEditText) destinationSelector.findViewById(R.id.dropField)).setText(destinationName);
            }
            showDestinationMarker();
            if (pickup != null)
                getRoutes();
        } else if (requestCode == PICKUP_REQUEST && resultCode == Activity.RESULT_OK) {
            Place place = Autocomplete.getPlaceFromIntent(data);
            pickup = place.getLatLng();
            pickupName = place.getName();
            if (destinationSelector.isShowing()) {
                ((TextInputEditText) destinationSelector.findViewById(R.id.pickupField)).setText(pickupName);
            }
            showPickupMarker();
            if (destination != null)
                getRoutes();
        }
    }

    private void showPickupMarker() {
        if (pickup == null)
            return;
        if (myMarker != null)
            myMarker.remove();
        myMarker = mMap.addMarker(new MarkerOptions().title("Pickup from").position(pickup));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(pickup));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(11));
    }

    private void showDestinationMarker() {
        if (destination == null)
            return;
        if (destinationMarker != null)
            destinationMarker.remove();
        destinationMarker = mMap.addMarker(new MarkerOptions().title("Destination").position(destination));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(destination));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(11));
    }

}