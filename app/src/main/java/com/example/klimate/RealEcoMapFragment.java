/**
 * RealEcoMapFragment.java
 *
 * Experimental real map screen for Klimate EcoMap.
 * This fragment uses MapLibre to show an actual zoomable map centered on LUMS.
 *
 * Role in design: Safe prototype for the future layered EcoMap. This file does
 * not replace the existing EcoPicksFragment yet, so the current cute PNG map
 * remains intact while the real map is tested.
 *
 * Outstanding issues: This is only a base map test. Tips, logs, hotspots,
 * rivals, and PNG overlay layers will be added after this compiles and runs.
 *
 * @author Izza
 */
package com.example.klimate;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;

public class RealEcoMapFragment extends Fragment {

    private static final String MAP_STYLE_URL = "https://demotiles.maplibre.org/style.json";

    // Approximate center of LUMS campus.
    private static final LatLng LUMS_CENTER = new LatLng(31.46926, 74.40886);

    private MapView mapView;
    private MapLibreMap mapLibreMap;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        MapLibre.getInstance(requireContext());

        View view = inflater.inflate(R.layout.fragment_real_eco_map, container, false);

        mapView = view.findViewById(R.id.real_map_view);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(map -> {
            mapLibreMap = map;

            mapLibreMap.setStyle(MAP_STYLE_URL);

            mapLibreMap.setCameraPosition(new CameraPosition.Builder()
                    .target(LUMS_CENTER)
                    .zoom(15.5)
                    .bearing(0)
                    .tilt(0)
                    .build());

            mapLibreMap.getUiSettings().setCompassEnabled(true);
            mapLibreMap.getUiSettings().setRotateGesturesEnabled(false);
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mapView != null) mapView.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mapView != null) mapView.onDestroy();
        mapView = null;
        mapLibreMap = null;
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }
}