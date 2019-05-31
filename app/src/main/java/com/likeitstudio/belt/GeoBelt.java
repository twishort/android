package com.likeitstudio.belt;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.likeitstudio.helper.social.Twitter;
import com.likeitstudio.twishort.DefaultApplication;
import com.likeitstudio.twishort.R;
import com.likeitstudio.twishort.WebActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;

/**
 * Created on 11.06.17.
 */

public class GeoBelt {

    private LinearLayout llPlace;
    private TextView tvText;
    private boolean isGettingPlaces;
    private Timer timer;
    private static ArrayList places = new ArrayList();
    private static JSONObject selectedPlace;
    private Runnable authorizeMethod;
    private SmartLocation geo;
    private static Location location;

    public GeoBelt() {
        llPlace = (LinearLayout) DefaultApplication.getCurrentActivity().findViewById(R.id.place_layout);
        llPlace.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedPlace != null) {
                    showSelectPlace();
                }
            }
        });
        tvText = (TextView) DefaultApplication.getCurrentActivity().findViewById(R.id.place_text);

        geo = SmartLocation.with(DefaultApplication.getCurrentActivity());
    }

    public void toggle() {
        if (llPlace.getVisibility() == View.GONE) {
            show();
        } else {
            hide();
        }
    }

    public void show() {
        if (!checkPermission()) {
            return;
        }
        DefaultApplication.hideKeyboard();
        tvText.setText(tvText.getResources().getString(R.string.place_searching));
        llPlace.setTag(1);
        llPlace.setVisibility(View.VISIBLE);

        geo.location()
                .oneFix()
                .start(new OnLocationUpdatedListener() {
                    @Override
                    public void onLocationUpdated(Location l) {
                        location = l;
                        timer.cancel();
                        tvText.setText(String.format("%s %.4f : %.4f", tvText.getResources().getString(R.string.coordinates),
                                location.getLatitude(), location.getLongitude()));
                        if (!isGettingPlaces) {
                            getPlaces();
                        }
                    }
                });

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                new Handler(DefaultApplication.getContext().getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                    timer.cancel();
                    DefaultApplication.toast(R.string.error_geo_timeout);
                    hide();
                    }
                });
            }
        }, 10000, 2000);
    }

    public void showIfNeeded() {
        if (getShowState()) {
            llPlace.setVisibility(View.VISIBLE);
        }
    }

    public void hide() {
        llPlace.setTag(null);
        llPlace.setVisibility(View.GONE);
        clear();
    }

    public void refresh() {
        show();
    }

    public void hideForInput() {
        llPlace.setVisibility(View.GONE);
    }

    public void clear() {
        isGettingPlaces = false;
        places.clear();
        selectedPlace = null;
        location = null;
    }

    public String getPlaceId() {
        if (selectedPlace != null) {
            try {
                return selectedPlace.getString("id");
            } catch (Exception e) {}
        }
        return null;
    }

    public double getLatitude() {
        return llPlace.getVisibility() == View.VISIBLE ? location.getLatitude() : 0;
    }

    public double getLongitude() {
        return llPlace.getVisibility() == View.VISIBLE ? location.getLongitude() : 0;
    }

    public void setAuthorizeMethod(Runnable authorize) {
        authorizeMethod = authorize;
    }

    public void setShowState(boolean show) {
        llPlace.setTag(show ? 1 : null);
    }

    public boolean getShowState() {
        return llPlace.getTag() != null
                && (selectedPlace != null || location != null) ? true : false;
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("showGeo", getShowState());
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState.getBoolean("showGeo")) {
            try {
                tvText.setText(selectedPlace != null ? selectedPlace.getString("full_name")
                    : String.format("%s %.4f : %.4f", tvText.getResources().getString(R.string.coordinates),
                        location.getLatitude(), location.getLongitude()) );
                llPlace.setTag(1);
                llPlace.setVisibility(View.VISIBLE);
            } catch (Exception e) {}
        }
    }

    public boolean isLocationEnabled() {
        int locationMode;
        String locationProviders;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
            try {
                locationMode = Settings.Secure.getInt(DefaultApplication.getCurrentActivity().getContentResolver(), Settings.Secure.LOCATION_MODE);
            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
                return false;
            }
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        }
        locationProviders = Settings.Secure.getString(DefaultApplication.getCurrentActivity().getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        return locationProviders != null && locationProviders.length() > 0;
    }

    private boolean checkPermission() {
        if (!isLocationEnabled()) {
            showSettingsDialog();
            return false;
        }
        if (android.os.Build.VERSION.SDK_INT < 23) {
            return true;
        }
        int permissionCheck1 = DefaultApplication.getCurrentActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        int permissionCheck2 = DefaultApplication.getCurrentActivity().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (permissionCheck1 != PackageManager.PERMISSION_GRANTED
                || permissionCheck2 != PackageManager.PERMISSION_GRANTED) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    hide();
                }
            }, 100);
            DefaultApplication.getCurrentActivity().requestPermissions(new String[] {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION },
                    DefaultApplication.REQUEST_CODE_PERMISSION_GEO);
            return false;
        }
        return true;
    }

    public void getPlaces() {
        if (! DefaultApplication.twishort.isAuthorized()) {
            showAuthorizeDialog();
            hide();
            return;
        }
        isGettingPlaces = true;

        DefaultApplication.twishort.getPlaces(location.getLatitude(), location.getLongitude(), 10, new Twitter.SuccessArray() {
            @Override
            public void done(JSONArray response) {
                isGettingPlaces = false;

                places.clear();
                try {
                    for (int i = 0; i < response.length(); i++) {
                        places.add(response.get(i));
                    }
                } catch (Exception e) {}
                if (places.size() > 0) {
                    showSelectPlace();
                } else {
                    DefaultApplication.toast(R.string.error_places_find);
                }
            }
        }, new Twitter.Error() {
            @Override
            public void done(JSONObject response, int statusCode) {
                isGettingPlaces = false;
                DefaultApplication.toast(R.string.error_places_find);
            }
        });
    }

    public void showSelectPlace() {
        AlertDialog.Builder builder = new AlertDialog.Builder(DefaultApplication.getCurrentActivity());
        String[] array = new String[places.size()];
        try {
            for (int i = 0; i < places.size(); i++) {
                array[i] = ((JSONObject) places.get(i)).getString("name");
            }
        } catch (Exception e) {}
        builder.setTitle(R.string.select_place);
        builder.setIcon(R.mipmap.ic_launcher);
        builder.setItems(array, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        selectedPlace = (JSONObject) places.get(which);
                        try {
                            tvText.setText(selectedPlace.getString("full_name"));
                        } catch (Exception e) {}
                        dialog.cancel();
                    }
                });
        builder.setPositiveButton(selectedPlace == null ? R.string.cancel : R.string.hide, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        hide();
                    }
                });
        if (selectedPlace != null) {
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            });

        }
        builder.show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(DefaultApplication.getCurrentActivity());
        alertDialog.setMessage(R.string.error_geo_disabled);
        alertDialog.setIcon(R.mipmap.ic_launcher);
        alertDialog.setTitle(R.string.geolocation);
        alertDialog.setPositiveButton(R.string.settings,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        DefaultApplication.getCurrentActivity().startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                                DefaultApplication.REQUEST_CODE_GEO_SETTINGS);
                    }
                });
        alertDialog.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        alertDialog.show();
    }

    private void showAuthorizeDialog() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(DefaultApplication.getCurrentActivity());
        alertDialog.setMessage(R.string.error_places_authorize);
        alertDialog.setIcon(R.mipmap.ic_launcher);
        alertDialog.setTitle(R.string.twitter);
        alertDialog.setPositiveButton(R.string.authorize,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        authorizeMethod.run();
                    }
                });
        alertDialog.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        alertDialog.show();
    }

}
