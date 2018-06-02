package com.simonmerrick.stormy.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Location;
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

import com.simonmerrick.stormy.R;
import com.simonmerrick.stormy.databinding.ActivityMainBinding;
import com.simonmerrick.stormy.weather.Current;
import com.simonmerrick.stormy.weather.Day;
import com.simonmerrick.stormy.weather.Forecast;
import com.simonmerrick.stormy.weather.Hour;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    public static final String TAG = MainActivity.class.getSimpleName();

    private Forecast forecast;
    private ImageView iconImageView;
    private Location location;
    private double latitude = -41.281890;
    private double longitude = 174.754971;
    private Address address = new Address(Locale.getDefault());
    private final String apiKey = "ecfb0eca95bf23229994fca6282936a7";
    private final String units = "ca";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLocationForecast();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLocationForecast();
        }
    }

    private void getLocationForecast() {
        location = new Location("");
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        getForecast(location);
        /*if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PackageManager.PERMISSION_GRANTED);
        } else {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            getForecast(location);
        }*/
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

    private void getAddressFromLocation(Location location) {
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

    private void getForecast(Location l) {
        final ActivityMainBinding binding = DataBindingUtil.setContentView(MainActivity.this,
                R.layout.activity_main);

        TextView darkSky = findViewById(R.id.darkSkyAttributuion);
        darkSky.setMovementMethod(LinkMovementMethod.getInstance());
        iconImageView = findViewById(R.id.iconImageView);

        final HttpUrl forecastURL = getForecastUrl(apiKey, l, units);

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
                            forecast = parseForecastDetails(jsonData);
                            Current current = forecast.getCurrent();

                            String locationString = "Getting Location...";
                            if (address.getSubAdminArea() != null) {
                                locationString = formatLocationString(address);
                            }

                            final Current displayWeather = new Current(
                                    locationString,
                                    current.getIcon(),
                                    current.getTime(),
                                    current.getTemperature(),
                                    current.getHumidity(),
                                    current.getPrecipChance(),
                                    current.getSummary(),
                                    current.getTimeZone()
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

    private Day[] getDailyForecast(String jsonData) throws JSONException {
        JSONObject forecast = new JSONObject(jsonData);
        String timezone = forecast.getString("timezone");
        JSONObject daily = forecast.getJSONObject("daily");
        JSONArray data = daily.getJSONArray("data");
        ArrayList<Day> days = new ArrayList<>();

        for (int i = 0; i < data.length(); i++) {
            JSONObject jsonDay = data.getJSONObject(i);
            Day day = new Day();
            day.setIcon(jsonDay.getString("icon"));
            day.setSummary(jsonDay.getString("summary"));
            day.setTime(jsonDay.getLong("time"));
            day.setTemperatureMax(jsonDay.getDouble("temperatureHigh"));
            day.setTemperatureMin(jsonDay.getDouble("temperatureLow"));
            day.setTimezone(timezone);
            days.add(day);
        }

        return days.toArray(new Day[data.length()]);
    }

    private Hour[] getHourlyForecast(String jsonData) throws JSONException {
        JSONObject forecast = new JSONObject(jsonData);
        String timezone = forecast.getString("timezone");
        JSONObject hourly = forecast.getJSONObject("hourly");
        JSONArray data = hourly.getJSONArray("data");
        ArrayList<Hour> hours = new ArrayList<>();

        for (int i = 0; i < data.length(); i++) {
            JSONObject jsonHour = data.getJSONObject(i);
            Hour h = new Hour();
            h.setIcon(jsonHour.getString("icon"));
            h.setSummary(jsonHour.getString("summary"));
            h.setTime(jsonHour.getLong("time"));
            h.setTemperature(jsonHour.getDouble("temperature"));
            h.setTimezone(timezone);
            hours.add(h);
        }
        return hours.toArray(new Hour[data.length()]);
    }

    private Forecast parseForecastDetails(String jsonData) throws JSONException {
        Forecast forecast = new Forecast();
        forecast.setCurrent(getCurrentDetails(jsonData));
        forecast.setHourlyForecast(getHourlyForecast(jsonData));
        forecast.setDailyForecast(getDailyForecast(jsonData));
        return forecast;
    }

    /**
     * @param jsonData JSON string representation of a forecast
     * @return         an object representation of the current weather
     * @exception JSONException If there is a key error retrieving values from json
     * */
    private Current getCurrentDetails(String jsonData) throws JSONException {

        JSONObject forecastJSON = new JSONObject(jsonData);
        String timezone = forecastJSON.getString("timezone");
        JSONObject currently = forecastJSON.getJSONObject("currently");

        Current current = new Current();
        current.setHumidity(currently.getDouble("humidity"));
        current.setTime(currently.getLong("time"));
        current.setIcon(currently.getString("icon"));
        current.setLocationLabel("Wellington, NZ");
        current.setPrecipChance(currently.getDouble("precipProbability"));
        current.setSummary(currently.getString("summary"));
        current.setTemperature(currently.getDouble("temperature"));
        current.setTimeZone(timezone);
        return current;
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

    private String formatLocationString(Address address) {
        return address.getSubAdminArea() + ", " + address.getCountryCode().toUpperCase();
    }

    private void alertUserAboutError() {
        AlertDialogFragment dialog = new AlertDialogFragment();
        dialog.show(getFragmentManager(), "error_dialog");
    }

    public void refreshOnClick(View view) {
        Toast.makeText(this, "Refreshing Data", Toast.LENGTH_LONG).show();
        getForecast(location);
    }

    public void dailyOnClick(View view) {
        Intent intent = new Intent(this, DailyForecastActivity.class);
        startActivity(intent);
    }
}