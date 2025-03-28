package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnTokenCanceledListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

public class PotholeDetectionActivity extends AppCompatActivity {

    private static final String TAG = "PotholeDetection";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_GALLERY_IMAGE = 2;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_STORAGE_PERMISSION = 101;
    private static final int REQUEST_LOCATION_PERMISSION = 102;

    // Configure your API endpoint here
    private static final String API_URL = "https://livestock-seeking-visibility-teens.trycloudflare.com/predict  ";  // Use your actual IP or domain

    private ImageView imagePreview;
    private TextView resultText;
    private TextView locationText;
    private Button captureButton;
    private Button galleryButton;
    private Button detectButton;
    private ProgressBar progressBar;

    private String currentPhotoPath;
    private Bitmap imageBitmap;
    private OkHttpClient client;
    private FusedLocationProviderClient fusedLocationClient;
    private double latitude = 0;
    private double longitude = 0;
    private boolean hasLocation = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pothole_detection);

        // Initialize views
        imagePreview = findViewById(R.id.imagePreview);
        resultText = findViewById(R.id.resultText);
        locationText = findViewById(R.id.locationText);
        captureButton = findViewById(R.id.captureButton);
        galleryButton = findViewById(R.id.galleryButton);
        detectButton = findViewById(R.id.detectButton);
        progressBar = findViewById(R.id.progressBar);

        // Initialize HTTP client
        client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Set up button click listeners
        captureButton.setOnClickListener(v -> {
            boolean cameraPermissionGranted = checkCameraPermission();
            boolean locationPermissionGranted = checkLocationPermission();

            if (!cameraPermissionGranted && !locationPermissionGranted) {
                requestCameraAndLocationPermissions();
            } else if (!cameraPermissionGranted) {
                requestCameraPermission();
            } else if (!locationPermissionGranted) {
                requestLocationPermissions();
            } else {
                // Both permissions granted
                getCurrentLocation();
                dispatchTakePictureIntent();
            }
        });


        galleryButton.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                // Reset location when selecting from gallery
                hasLocation = false;
                locationText.setText("Location: Not available (gallery image)");
                openGallery();
            } else {
                requestStoragePermission();
            }
        });

        detectButton.setOnClickListener(v -> {
            if (imageBitmap != null) {
                detectPothole(imageBitmap);
            } else {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                REQUEST_CAMERA_PERMISSION);
    }
    private static final int LOCATION_TIMEOUT_MS = 10000; // 10 seconds timeout

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permissions not granted");
            locationText.setText("Location: Permissions not granted");
            return;
        }

        // Show that we're attempting to get location
        locationText.setText("Location: Retrieving...");

        // Check if location is enabled
        android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        boolean isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);

        if (!isGpsEnabled && !isNetworkEnabled) {
            Toast.makeText(this, "Please enable location services", Toast.LENGTH_LONG).show();
            locationText.setText("Location: Services disabled");
            return;
        }

        // Use LocationRequest with timeout instead of getCurrentLocation
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000)        // 5 seconds
                .setFastestInterval(2000) // 2 seconds
                .setMaxWaitTime(LOCATION_TIMEOUT_MS);

        // Create a location callback
        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    Log.w(TAG, "Location callback received null result");
                    return;
                }

                // We got a location
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    hasLocation = true;
                    String locationStr = String.format(Locale.getDefault(),
                            "Location: %.6f, %.6f", latitude, longitude);
                    locationText.setText(locationStr);
                    Log.d(TAG, "Location received: " + locationStr);

                    // Stop location updates once we have a location
                    fusedLocationClient.removeLocationUpdates(this);
                }
            }
        };

        // Request location updates
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());

        // Set a timeout to fallback to last location if we don't get updates
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            // Check if we already got a location
            if (locationText.getText().toString().startsWith("Location: Retrieving")) {
                // We didn't get a location within the timeout period
                Log.w(TAG, "Location retrieval timed out after " + LOCATION_TIMEOUT_MS + "ms");
                fusedLocationClient.removeLocationUpdates(locationCallback);
                fallbackToLastLocation();
            }
        }, LOCATION_TIMEOUT_MS);
    }
    private void fallbackToLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        locationText.setText("Location: Trying last known...");

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, lastLocation -> {
                    if (lastLocation != null) {
                        latitude = lastLocation.getLatitude();
                        longitude = lastLocation.getLongitude();
                        hasLocation = true;
                        String locationStr = String.format(Locale.getDefault(),
                                "Location: %.6f, %.6f (last known)", latitude, longitude);
                        locationText.setText(locationStr);
                        Log.d(TAG, "Last location: " + locationStr);
                    } else {
                        hasLocation = false;
                        locationText.setText("Location: Not available");
                        Log.w(TAG, "Last location is also null");
                    }
                })
                .addOnFailureListener(this, e -> {
                    hasLocation = false;
                    locationText.setText("Location: Failed to retrieve");
                    Log.e(TAG, "Failed to get last location: " + e.getMessage(), e);
                });
    }

    private void requestLocationPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                REQUEST_LOCATION_PERMISSION);
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraAndLocationPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_CAMERA_PERMISSION);
    }

    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                getCurrentLocation();
                dispatchTakePictureIntent();
            } else {
                Toast.makeText(this, "Camera and location permissions are required", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Location permission required for geotagging", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Ensure there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e(TAG, "Error creating image file", ex);
                Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
            }

            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.myapplication.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        } else {
            Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                // Load the taken photo
                try {
                    File file = new File(currentPhotoPath);
                    imageBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    imagePreview.setImageBitmap(imageBitmap);
                    resultText.setText("Image captured. Press Detect to analyze.");
                } catch (Exception e) {
                    Log.e(TAG, "Error loading captured image", e);
                    Toast.makeText(this, "Error loading captured image", Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == REQUEST_GALLERY_IMAGE && data != null) {
                // Load the selected gallery image
                try {
                    Uri selectedImage = data.getData();
                    imageBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImage);
                    imagePreview.setImageBitmap(imageBitmap);
                    resultText.setText("Image selected. Press Detect to analyze.");
                } catch (IOException e) {
                    Log.e(TAG, "Error loading gallery image", e);
                    Toast.makeText(this, "Error loading gallery image", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void detectPothole(Bitmap bitmap) {
        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        resultText.setText("Analyzing image...");

        // Convert bitmap to base64 string
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        String encodedImage = Base64.encodeToString(byteArray, Base64.DEFAULT);

        // Create request body
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", encodedImage)
                .build();

        // Create request
        Request request = new Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .build();

        // Execute request asynchronously
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "API request failed", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.INVISIBLE);
                    resultText.setText("Error: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseData = response.body().string();
                        JSONObject jsonObject = new JSONObject(responseData);

                        int classId = jsonObject.getInt("class");
                        String className = jsonObject.getString("class_name");
                        double confidence = jsonObject.getDouble("confidence");

                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.INVISIBLE);

                            // Format the detection result
                            String resultString = String.format(Locale.getDefault(),
                                    "Result: %s\nConfidence: %.2f%%",
                                    className, confidence);

                            resultText.setText(resultString);

                            // If we captured this image with location data, include it in the report
                            if (hasLocation) {
                                // Location data is already displayed in locationText
                                // But we could add actions based on detection result + location here

                                // Example: Log potential pothole location
                                if (classId == 1) {  // If pothole detected
                                    Log.i(TAG, "Pothole detected at: " + latitude + ", " + longitude);
                                    // Here you could save to local database, call another API, etc.
                                }
                            }
                        });
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing JSON response", e);
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.INVISIBLE);
                            resultText.setText("Error parsing response");
                        });
                    }
                } else {
                    Log.e(TAG, "API request unsuccessful: " + response.code());
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.INVISIBLE);
                        resultText.setText("Error: HTTP " + response.code());
                    });
                }
            }
        });
    }
}