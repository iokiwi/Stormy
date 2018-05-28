package com.simonmerrick.stormy;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.simonmerrick.stormy.databinding.ActivityMainBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    public static final String TAG = MainActivity.class.getSimpleName();

    private CurrentWeather currentWeather;
    private ImageView iconImageView;
    private Location location;
    private Address address = new Address(Locale.getDefault());


    private final String apiKey = "ecfb0eca95bf23229994fca6282936a7";
    private final String units = "ca";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLocation();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setLocation();
            getForecast(location);
            getAddressFromLocation(location);
        }
    }

    private void setLocation() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        getForecast(location);
        getAddressFromLocation(location);
    }

    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request Location Permissions
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PackageManager.PERMISSION_GRANTED);
        } else {
            // TODO: Already have permission
            setLocation();
            getForecast(location);
            getAddressFromLocation(location);
        }

    }

    private HttpUrl getForecastUrl(String apiKey, Location location, String units) {
        String locationPair = location.getLatitude() + "," + location.getLongitude();
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.darksky.net")
                .addPathSegment("forecast")
                .addPathSegment(apiKey)
                .addPathSegment(locationPair)
                .addQueryParameter("units", units)
                .build();
        return url;
    }

    private HttpUrl getGeocodeUrl(Location location) {
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("nominatim.openstreetmap.org")
                .addPathSegment("reverse")
                .addQueryParameter("format","json")
                .addQueryParameter("lat", Double.toString(location.getLatitude()))
                .addQueryParameter("lon", Double.toString(location.getLongitude()))
                .addQueryParameter("addressdetails", "1")
                .build();
        return url;
    }

    private void updateUILocation(Address address) {
        final String locationString = formatLocationString(address);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView locationValue = findViewById(R.id.locationValue);
                locationValue.setText(locationString);
            }
        });
    }

    private void getAddressFromLocation(final Location location) {
        HttpUrl geocodeUrl = getGeocodeUrl(location);
        if (isNetworkAvailable()) {

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(geocodeUrl)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try {
                        String jsonData = response.body().string();
                        if (response.isSuccessful()) {
                            Log.v(TAG, jsonData);
                            JSONObject data = new JSONObject(jsonData);
                            JSONObject addressData = data.getJSONObject("address");
                            address.setCountryName(addressData.getString("country"));
                            address.setCountryCode(addressData.getString("country_code"));
                            address.setSubAdminArea(addressData.getString("county"));
                            updateUILocation(address);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "IO Exception caught: ", e);
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON Exception caught: ", e);
                    }
                }
            });
        }
    }

    private String formatLocationString(Address address) {
        return address.getSubAdminArea() + ", " + address.getCountryCode().toUpperCase();
    }

    private void getForecast(final Location location) {
        final ActivityMainBinding binding = DataBindingUtil.setContentView(MainActivity.this,
                R.layout.activity_main);

        TextView darkSky = findViewById(R.id.darkSkyAttributuion);
        darkSky.setMovementMethod(LinkMovementMethod.getInstance());

        iconImageView = findViewById(R.id.iconImageView);

        HttpUrl forecastURL = getForecastUrl(apiKey, location, units);

        if (isNetworkAvailable()) {
            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url(forecastURL)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try {
                        String jsonData = response.body().string();
                        if (response.isSuccessful()) {
                            currentWeather = getCurrentDetails(jsonData);

                            String locationString = "Get Location...";
                            if (address.getSubAdminArea() != null) {
                                locationString = formatLocationString(address);
                            }

                            final CurrentWeather displayWeather = new CurrentWeather(
                                    locationString,
                                    currentWeather.getIcon(),
                                    currentWeather.getTime(),
                                    currentWeather.getTemperature(),
                                    currentWeather.getHumidity(),
                                    currentWeather.getPrecipChance(),
                                    currentWeather.getSummary(),
                                    currentWeather.getTimeZone()
                            );

                            binding.setWeather(displayWeather);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Drawable drawable = getResources().getDrawable(displayWeather.getIconId());
                                    iconImageView.setImageDrawable(drawable);
                                }
                            });
                        } else {
                            alertUserAboutError();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "IO Exception caught: ", e);
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON Exception caught: ", e);
                    }
                }
            });
        }
    }

    private CurrentWeather getCurrentDetails(String jsonData) throws JSONException {
        JSONObject forecast = new JSONObject(jsonData);
        String timezone = forecast.getString("timezone");
        JSONObject currently = forecast.getJSONObject("currently");
        currentWeather = new CurrentWeather();
        currentWeather.setHumidity(currently.getDouble("humidity"));
        currentWeather.setTime(currently.getLong("time"));
        currentWeather.setIcon(currently.getString("icon"));
        currentWeather.setLocationLabel("Wellington, NZ");
        currentWeather.setPrecipChance(currently.getDouble("precipProbability"));
        currentWeather.setSummary(currently.getString("summary"));
        currentWeather.setTemperature(currently.getDouble("temperature"));
        currentWeather.setTimeZone(timezone);
        return currentWeather;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        boolean isAvailable = false;
        if (networkInfo != null && networkInfo.isConnected()) {
            isAvailable = true;
        } else {
            Toast.makeText (this, getString(R.string.network_unavailable_message),
                    Toast.LENGTH_LONG).show();
        }
        return isAvailable;
    }

    private void alertUserAboutError() {
        AlertDialogFragment dialog = new AlertDialogFragment();
        dialog.show(getFragmentManager(), "error_dialog");
    }

    public void refreshOnClick(View view) {
        Toast.makeText(this, "Refreshing Data", Toast.LENGTH_LONG).show();
        getAddressFromLocation(location);
        getForecast(location);
    }
}