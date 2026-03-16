package com.ridesharing.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OSRMFareService {

    private final String OSRM_URL = "https://router.project-osrm.org";
    private final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory caches to avoid repeated external API calls
    private final ConcurrentHashMap<String, double[]> coordinatesCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> distanceCache = new ConcurrentHashMap<>();

    private final double BASE_FARE = 50.0;
    private final double RATE_PER_KM = 12.0;

    /**
     * Get coordinates from city name — cached
     */
    public double[] getCoordinates(String cityName) {
        if (cityName == null) return null;
        String key = cityName.toLowerCase().trim();

        if (coordinatesCache.containsKey(key)) {
            return coordinatesCache.get(key);
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(NOMINATIM_URL)
                    .queryParam("q", cityName)
                    .queryParam("format", "json")
                    .queryParam("limit", 1)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "RideSharingApp/1.0");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());

            if (root != null && root.size() > 0) {
                double lat = root.get(0).path("lat").asDouble();
                double lon = root.get(0).path("lon").asDouble();
                double[] coords = new double[]{lon, lat};
                coordinatesCache.put(key, coords);
                return coords;
            }

        } catch (Exception e) {
            System.err.println("❌ Geocoding error for " + cityName + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Calculate fare using coordinates (4 arguments)
     */
    public double calculateFare(String sourceLon, String sourceLat,
                                String destLon, String destLat) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(OSRM_URL)
                    .path("/route/v1/driving/{sourceLon},{sourceLat};{destLon},{destLat}")
                    .queryParam("overview", "false")
                    .buildAndExpand(sourceLon, sourceLat, destLon, destLat)
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            JsonNode route = root.path("routes").get(0);
            double distanceMeters = route.path("distance").asDouble();
            double distanceKm = distanceMeters / 1000.0;

            return BASE_FARE + (RATE_PER_KM * distanceKm);

        } catch (Exception e) {
            e.printStackTrace();
            return 100.0; // Default fallback
        }
    }

    /**
     * Get distance in km between coordinates — cached
     */
    public double getDistanceInKm(String sourceLon, String sourceLat,
                                  String destLon, String destLat) {
        String cacheKey = sourceLon + "," + sourceLat + "->" + destLon + "," + destLat;

        if (distanceCache.containsKey(cacheKey)) {
            return distanceCache.get(cacheKey);
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(OSRM_URL)
                    .path("/route/v1/driving/{sourceLon},{sourceLat};{destLon},{destLat}")
                    .queryParam("overview", "false")
                    .buildAndExpand(sourceLon, sourceLat, destLon, destLat)
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            JsonNode route = root.path("routes").get(0);
            double distanceMeters = route.path("distance").asDouble();
            double distanceKm = distanceMeters / 1000.0;

            distanceCache.put(cacheKey, distanceKm);
            return distanceKm;

        } catch (Exception e) {
            e.printStackTrace();
            return 100.0;
        }
    }
}