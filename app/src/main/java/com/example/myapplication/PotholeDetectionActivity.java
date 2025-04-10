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
import android.media.session.MediaController;
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
import android.widget.VideoView;


import com.example.myapplication.databinding.ActivityPotholeDetectionBinding;
import com.google.android.gms.common.util.IOUtils;
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
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

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
    private static final int REQUEST_VIDEO_CAPTURE = 103;

    private ImageView imagePreview;
    private TextView resultText;
    private TextView locationText;
    private Button captureButton;
    private Button videoButton; //new
    private Button galleryButton;
    private Button detectButton;
    private boolean isImage;
    private ProgressBar progressBar;
    private ActivityPotholeDetectionBinding binding;
    private Uri fileUri;

    private String currentPhotoPath;
    private Bitmap imageBitmap;
    private OkHttpClient client = new  OkHttpClient();
    private FusedLocationProviderClient fusedLocationClient;
    private double latitude = 0;
    private double longitude = 0;
    private boolean hasLocation = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pothole_detection);
        binding = ActivityPotholeDetectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize views
        ImageView previewImage = findViewById(R.id.previewImage);
        VideoView previewVideo = findViewById(R.id.previewVideo);
//        imagePreview = findViewById(R.id.imagePreview);
        resultText = findViewById(R.id.resultText);
        locationText = findViewById(R.id.locationText);
        captureButton = findViewById(R.id.captureButton);
        galleryButton = findViewById(R.id.galleryButton);
        videoButton = findViewById(R.id.videoButton); //new
        detectButton = findViewById(R.id.detectButton);
        progressBar = findViewById(R.id.progressBar);

        // Initialize HTTP client
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
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

        videoButton.setOnClickListener(view -> {
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
                dispatchTakeVideoIntent();

//                // Start the video activity once permissions are granted
//                Intent intent = new Intent(MainActivity.this, VideoActivity.class);
//                startActivity(intent);
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

        binding.detectButton.setOnClickListener(v -> {
            if (fileUri != null) {
                if (isImage) {
                    // Handle image detection
                    try {
                        Bitmap imageBitmap = binding.previewImage.getDrawingCache();
                        if (imageBitmap != null) {
                            detectPothole(imageBitmap);
                        } else {
                            Toast.makeText(this, "Please select or capture an image", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting image from ImageView", e);
                        Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Handle video detection
                    try {
                        String encodedVideo = encodeVideoToBase64(fileUri);
                        if (encodedVideo != null) {
                            detectPothole(encodedVideo);
                        } else {
                            Toast.makeText(this, "Error encoding video", Toast.LENGTH_SHORT).show();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error processing video", e);
                        Toast.makeText(this, "Error processing video: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, "Please capture or select media first", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private String encodeVideoToBase64(Uri videoUri) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(videoUri)) {
            if (inputStream != null) {
                byte[] bytes = IOUtils.toByteArray(inputStream); // Requires Apache Commons IO
                return Base64.encodeToString(bytes, Base64.DEFAULT);
            } else {
                return null;
            }
        }
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

    private void dispatchTakeVideoIntent() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);

        // Ensure there's a camera activity to handle the intent
        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
            // Start the video capture intent
            startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
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
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
        Uri photoUri = Uri.fromFile(new File(currentPhotoPath));
        showPreview(photoUri, true);
    } else if (requestCode == REQUEST_GALLERY_IMAGE && resultCode == RESULT_OK) { // Complete the condition
        if (data != null && data.getData() != null) {
            Uri selectedImageUri = data.getData();
            showPreview(selectedImageUri, true);
        } else {
            Toast.makeText(this, "Failed to select image from gallery", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Gallery intent returned null data or URI");
        }
    }
}

    private void showPreview(Uri fileUri, boolean isImage) {
        Log.d(TAG, "showPreview called: fileUri = " + fileUri + ", isImage = " + isImage);

        if (fileUri == null) {
            Log.e(TAG, "File URI is null");
            Toast.makeText(this, "Error: File not found", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isImage) {
            Log.d(TAG, "Displaying image");
            binding.previewImage.setVisibility(View.VISIBLE);
            binding.previewVideo.setVisibility(View.GONE);

            try {
                binding.previewImage.setImageURI(fileUri);
                Log.d(TAG, "Image displayed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error displaying image", e);
                Toast.makeText(this, "Error displaying image", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.d(TAG, "Displaying video");
            binding.previewImage.setVisibility(View.GONE);
            binding.previewVideo.setVisibility(View.VISIBLE);

            try {
                Log.d(TAG, "Setting video URI: " + fileUri);
                binding.previewVideo.setVideoURI(fileUri);
                android.widget.MediaController mediaController = new android.widget.MediaController(this);
                binding.previewVideo.setMediaController(mediaController);
                mediaController.setAnchorView(binding.previewVideo);
                binding.previewVideo.requestFocus();

                binding.previewVideo.setOnPreparedListener(mp -> {
                    Log.d(TAG, "Video prepared, starting playback");
                    binding.previewVideo.start();
                });

                binding.previewVideo.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "Error displaying video: what=" + what + ", extra=" + extra);
                    Toast.makeText(this, "Error playing video", Toast.LENGTH_SHORT).show();
                    return true;
                });
            } catch (Exception e) {
                Log.e(TAG, "Error setting video URI", e);
                Toast.makeText(this, "Error setting up video", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void detectPothole(String encodedMedia) {
        sendMediaToServer(encodedMedia, "video");
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
    private void sendMediaToServer(Object media, String mediaType) {
        // Show progress
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.resultText.setText("Analyzing " + mediaType + "...");

        String encodedMedia;
        if (mediaType.equals("image") && media instanceof Bitmap) {
            // Convert bitmap to base64 string
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ((Bitmap) media).compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            encodedMedia = Base64.encodeToString(byteArray, Base64.DEFAULT);
        } else if (mediaType.equals("video") && media instanceof String) {
            encodedMedia = (String) media;
        } else {
            Log.e(TAG, "Invalid media type or data");
            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.INVISIBLE);
                binding.resultText.setText("Error: Invalid media");
            });
            return;
        }

        // Create request body
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(mediaType, encodedMedia)  // Use "image" or "video" as the key
                .build();

        // Create request
        Request request = new Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .build();

        // Execute request asynchronously
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "API request failed", e);
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.INVISIBLE);
                    binding.resultText.setText("Error: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseBody = response.body().string(); // Capture the response body
                runOnUiThread(() -> binding.progressBar.setVisibility(View.INVISIBLE)); // Ensure progress bar is hidden

                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);
                        Log.d(TAG, "Received detection response: " + jsonObject.toString());

                        // Adapt response parsing based on your server's output
                        // The following is a placeholder example:

                        boolean potholeDetected = jsonObject.optBoolean("pothole_detected", false); // Default to false if not present
                        double confidence = jsonObject.optDouble("confidence", 0.0); // Default to 0.0 if not present
                        String additionalInfo = jsonObject.optString("additional_info", ""); // Optional additional info

                        String resultString;
                        if (potholeDetected) {
                            resultString = String.format(Locale.getDefault(),
                                    "Result: Pothole Detected!\nConfidence: %.2f%%\n%s", confidence, additionalInfo);
                        } else {
                            resultString = "Result: No Potholes Detected";
                        }

                        runOnUiThread(() -> binding.resultText.setText(resultString));

                        // Handle location if available (similar to image detection)
                        if (hasLocation && potholeDetected) {
                            Log.i(TAG, "Potential pothole detected at: " + latitude + ", " + longitude + " (from " + mediaType + ")");
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing JSON response: " + responseBody, e); // Include body in error message
                        runOnUiThread(() -> binding.resultText.setText("Error parsing response: " + e.getMessage())); // Show specific error
                    }
                } else {
                    Log.e(TAG, "API request unsuccessful: HTTP " + response.code() + ", Body: " + responseBody); // Log code and body
                    runOnUiThread(() -> binding.resultText.setText("Error: HTTP " + response.code()));
                }
            }
        });
    }
}