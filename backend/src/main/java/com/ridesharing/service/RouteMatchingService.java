package com.ridesharing.service;

import com.ridesharing.entity.Ride;
import com.ridesharing.repository.RideRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class RouteMatchingService {

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private OSRMFareService osrmService;

    private static final double ALONG_ROUTE_THRESHOLD_KM = 50.0;
    private static final double MAX_DETOUR_PERCENTAGE = 0.4;

    // Thread pool for parallel ride matching
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    public List<Ride> findSmartMatches(String searchSource, String searchDest, int seatsNeeded) {
        List<Ride> allRides = rideRepository.findByStatus("active");

        // Geocode search locations once (cached after first call)
        double[] searchSourceCoords = osrmService.getCoordinates(searchSource);
        double[] searchDestCoords = osrmService.getCoordinates(searchDest);

        if (searchSourceCoords == null || searchDestCoords == null) {
            return findStringBasedMatches(allRides, searchSource, searchDest, seatsNeeded);
        }

        // Filter by seats first to reduce work
        List<Ride> candidates = allRides.stream()
                .filter(r -> r.getAvailableSeats() >= seatsNeeded)
                .collect(Collectors.toList());

        // Submit each ride evaluation as a parallel task
        List<Future<Ride>> futures = new ArrayList<>();
        for (Ride ride : candidates) {
            final double[] srcCoords = searchSourceCoords;
            final double[] dstCoords = searchDestCoords;
            futures.add(executor.submit(() -> evaluateRide(ride, searchSource, searchDest, srcCoords, dstCoords)));
        }

        // Collect results with a per-search timeout of 15s total
        List<Ride> allMatches = new ArrayList<>();
        for (Future<Ride> future : futures) {
            try {
                Ride result = future.get(15, TimeUnit.SECONDS);
                if (result != null) allMatches.add(result);
            } catch (TimeoutException e) {
                future.cancel(true);
            } catch (Exception e) {
                // skip failed evaluations
            }
        }

        allMatches.sort((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()));
        return allMatches;
    }

    private Ride evaluateRide(Ride ride, String searchSource, String searchDest,
                               double[] searchSourceCoords, double[] searchDestCoords) {
        // Fast string check first
        double directScore = calculateDirectMatchScore(ride, searchSource, searchDest);
        if (directScore > 0.8) {
            ride.setMatchScore(directScore);
            return ride;
        }

        // Reject if passenger's destination matches driver's source (wrong direction)
        if (calculateStringSimilarity(ride.getSource(), searchDest) > 0.8) {
            return null;
        }
        // Reject if passenger's source matches driver's destination (wrong direction)
        if (calculateStringSimilarity(ride.getDestination(), searchSource) > 0.8) {
            return null;
        }

        // Geocode ride locations (cached)
        double[] rideSourceCoords = osrmService.getCoordinates(ride.getSource());
        double[] rideDestCoords = osrmService.getCoordinates(ride.getDestination());

        if (rideSourceCoords == null || rideDestCoords == null) {
            return null;
        }

        // Direction check: passenger's pickup must be closer to driver's start than passenger's dropoff
        // This ensures the passenger is travelling in the same direction as the driver
        try {
            double distToPickup = osrmService.getDistanceInKm(
                    String.valueOf(rideSourceCoords[0]), String.valueOf(rideSourceCoords[1]),
                    String.valueOf(searchSourceCoords[0]), String.valueOf(searchSourceCoords[1])
            );
            double distToDropoff = osrmService.getDistanceInKm(
                    String.valueOf(rideSourceCoords[0]), String.valueOf(rideSourceCoords[1]),
                    String.valueOf(searchDestCoords[0]), String.valueOf(searchDestCoords[1])
            );
            // Pickup must be closer to driver's start than dropoff (same direction)
            if (distToPickup >= distToDropoff) {
                return null;
            }
        } catch (Exception ignored) {}

        MatchResult result = calculateAlongRouteScore(
                ride, searchSource, searchDest,
                rideSourceCoords, rideDestCoords,
                searchSourceCoords, searchDestCoords
        );

        if (result.score > 0.4) {
            ride.setMatchScore(result.score);
            return ride;
        }
        return null;
    }

    /**
     * Fallback method when geocoding fails - uses only string matching
     */
    private List<Ride> findStringBasedMatches(List<Ride> allRides, String searchSource, String searchDest, int seatsNeeded) {
        List<Ride> matches = new ArrayList<>();

        for (Ride ride : allRides) {
            if (ride.getAvailableSeats() < seatsNeeded) {
                continue;
            }

            double score = calculateStringSimilarity(ride.getSource(), searchSource) * 0.5 +
                    calculateStringSimilarity(ride.getDestination(), searchDest) * 0.5;

            if (score > 0.4) {
                ride.setMatchScore(score);
                matches.add(ride);
            }
        }

        matches.sort((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()));
        return matches;
    }

    /**
     * Calculate if passenger's desired trip is along the driver's route
     */
    private MatchResult calculateAlongRouteScore(
            Ride ride,
            String searchSource, String searchDest,
            double[] rideSourceCoords, double[] rideDestCoords,
            double[] searchSourceCoords, double[] searchDestCoords) {

        MatchResult result = new MatchResult();

        try {
            // Get driver's full route distance
            double driverTotalDistance = osrmService.getDistanceInKm(
                    String.valueOf(rideSourceCoords[0]), String.valueOf(rideSourceCoords[1]),
                    String.valueOf(rideDestCoords[0]), String.valueOf(rideDestCoords[1])
            );

            // Get distances from driver's start to passenger's pickup/dropoff
            double distStartToPickup = osrmService.getDistanceInKm(
                    String.valueOf(rideSourceCoords[0]), String.valueOf(rideSourceCoords[1]),
                    String.valueOf(searchSourceCoords[0]), String.valueOf(searchSourceCoords[1])
            );

            double distPickupToDropoff = osrmService.getDistanceInKm(
                    String.valueOf(searchSourceCoords[0]), String.valueOf(searchSourceCoords[1]),
                    String.valueOf(searchDestCoords[0]), String.valueOf(searchDestCoords[1])
            );

            double distDropoffToEnd = osrmService.getDistanceInKm(
                    String.valueOf(searchDestCoords[0]), String.valueOf(searchDestCoords[1]),
                    String.valueOf(rideDestCoords[0]), String.valueOf(rideDestCoords[1])
            );

            // Calculate if pickup and dropoff are along the route
            boolean isPickupAlongRoute = isPointAlongRoute(
                    searchSourceCoords, rideSourceCoords, rideDestCoords, driverTotalDistance
            );

            boolean isDropoffAlongRoute = isPointAlongRoute(
                    searchDestCoords, rideSourceCoords, rideDestCoords, driverTotalDistance
            );

            // Calculate total distance with detour
            double totalWithDetour = distStartToPickup + distPickupToDropoff + distDropoffToEnd;
            double detourDistance = totalWithDetour - driverTotalDistance;
            double detourPercentage = detourDistance / driverTotalDistance;

            result.detourDistance = detourDistance;
            result.detourPercentage = detourPercentage * 100;

            // Calculate score based on how well the passenger's trip fits
            double score = 0.0;

            if (isPickupAlongRoute && isDropoffAlongRoute) {
                // Both points are along the route - perfect fit
                score = 0.95;

                // Bonus if passenger's trip is in same direction
                if (distPickupToDropoff > 0 && distStartToPickup < distStartToDropoff(rideSourceCoords, searchDestCoords)) {
                    score += 0.05;
                }
            }
            else if (isPickupAlongRoute) {
                // Pickup on route, dropoff requires detour
                if (detourPercentage <= MAX_DETOUR_PERCENTAGE) {
                    score = 0.8 * (1 - (detourPercentage / MAX_DETOUR_PERCENTAGE));
                }
            }
            else if (isDropoffAlongRoute) {
                // Dropoff on route, pickup requires detour
                if (detourPercentage <= MAX_DETOUR_PERCENTAGE) {
                    score = 0.8 * (1 - (detourPercentage / MAX_DETOUR_PERCENTAGE));
                }
            }
            else {
                // Neither point on route - check if route overlaps partially
                double overlapScore = calculateRouteOverlap(
                        rideSourceCoords, rideDestCoords,
                        searchSourceCoords, searchDestCoords
                );

                if (overlapScore > 0.3 && detourPercentage <= MAX_DETOUR_PERCENTAGE * 1.5) {
                    score = overlapScore * 0.7 * (1 - (detourPercentage / (MAX_DETOUR_PERCENTAGE * 1.5)));
                }
            }

            // Ensure score is between 0 and 1
            result.score = Math.min(1.0, Math.max(0.0, score));

        } catch (Exception e) {
            System.err.println("Error calculating along-route score: " + e.getMessage());
            result.score = 0.0;
        }

        return result;
    }

    /**
     * Check if a point is along the driver's route
     */
    private boolean isPointAlongRoute(double[] pointCoords, double[] startCoords, double[] endCoords, double totalDistance) {
        try {
            // Calculate distances
            double distStartToPoint = osrmService.getDistanceInKm(
                    String.valueOf(startCoords[0]), String.valueOf(startCoords[1]),
                    String.valueOf(pointCoords[0]), String.valueOf(pointCoords[1])
            );

            double distPointToEnd = osrmService.getDistanceInKm(
                    String.valueOf(pointCoords[0]), String.valueOf(pointCoords[1]),
                    String.valueOf(endCoords[0]), String.valueOf(endCoords[1])
            );

            // Check if point is roughly on the path (triangle inequality)
            // distStartToPoint + distPointToEnd should be approximately equal to totalDistance
            double sumDistances = distStartToPoint + distPointToEnd;
            double difference = Math.abs(sumDistances - totalDistance);

            // If the difference is less than threshold, point is along the route
            return difference <= ALONG_ROUTE_THRESHOLD_KM;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Calculate how much of passenger's route overlaps with driver's route
     */
    private double calculateRouteOverlap(
            double[] driverStart, double[] driverEnd,
            double[] passStart, double[] passEnd) {

        try {
            // Get all relevant distances
            double driverDistance = osrmService.getDistanceInKm(
                    String.valueOf(driverStart[0]), String.valueOf(driverStart[1]),
                    String.valueOf(driverEnd[0]), String.valueOf(driverEnd[1])
            );

            double passDistance = osrmService.getDistanceInKm(
                    String.valueOf(passStart[0]), String.valueOf(passStart[1]),
                    String.valueOf(passEnd[0]), String.valueOf(passEnd[1])
            );

            // Check if passenger's route is a subset of driver's route
            double startToPassStart = osrmService.getDistanceInKm(
                    String.valueOf(driverStart[0]), String.valueOf(driverStart[1]),
                    String.valueOf(passStart[0]), String.valueOf(passStart[1])
            );

            double startToPassEnd = osrmService.getDistanceInKm(
                    String.valueOf(driverStart[0]), String.valueOf(driverStart[1]),
                    String.valueOf(passEnd[0]), String.valueOf(passEnd[1])
            );

            // If both passenger points are within driver's route length
            if (startToPassStart <= driverDistance && startToPassEnd <= driverDistance) {
                // Calculate the common portion
                double commonStart = Math.max(startToPassStart, 0);
                double commonEnd = Math.min(startToPassEnd, driverDistance);
                double commonDistance = Math.max(0, commonEnd - commonStart);

                return commonDistance / passDistance;
            }

            return 0.0;

        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Helper method to get distance from start to dropoff point
     */
    private double distStartToDropoff(double[] startCoords, double[] dropoffCoords) {
        try {
            return osrmService.getDistanceInKm(
                    String.valueOf(startCoords[0]), String.valueOf(startCoords[1]),
                    String.valueOf(dropoffCoords[0]), String.valueOf(dropoffCoords[1])
            );
        } catch (Exception e) {
            return Double.MAX_VALUE;
        }
    }

    /**
     * Calculate direct match score using string similarity
     */
    private double calculateDirectMatchScore(Ride ride, String searchSource, String searchDest) {
        double sourceScore = calculateStringSimilarity(ride.getSource(), searchSource);
        double destScore = calculateStringSimilarity(ride.getDestination(), searchDest);

        return (sourceScore * 0.5) + (destScore * 0.5);
    }

    // ==================== EXISTING STRING SIMILARITY METHODS ====================
    // (Keep all your existing string similarity methods below)

    private double calculateStringSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;

        s1 = s1.toLowerCase().trim();
        s2 = s2.toLowerCase().trim();

        // EXACT MATCH - 100%
        if (s1.equals(s2)) {
            return 1.0;
        }

        // CONTAINS MATCH
        if (s1.contains(s2) || s2.contains(s1)) {
            double maxLen = Math.max(s1.length(), s2.length());
            double minLen = Math.min(s1.length(), s2.length());
            return minLen / maxLen * 0.9;
        }

        // ABBREVIATION MATCH
        if ((s1.length() <= 4 && s2.startsWith(s1)) ||
                (s2.length() <= 4 && s1.startsWith(s2))) {
            return 0.8;
        }

        // LEVENSHTEIN DISTANCE
        int distance = levenshteinDistance(s1, s2);
        int maxLength = Math.max(s1.length(), s2.length());

        if (maxLength == 0) return 1.0;

        double similarity = 1.0 - ((double) distance / maxLength);

        if (similarity > 0.5) {
            return similarity * 0.7;
        }

        // Common prefix
        if (s1.length() >= 3 && s2.length() >= 3) {
            String prefix1 = s1.substring(0, 3);
            String prefix2 = s2.substring(0, 3);
            if (prefix1.equals(prefix2)) {
                return 0.6;
            }
        }

        return 0.0;
    }

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(
                            dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1),
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1)
                    );
                }
            }
        }
        return dp[s1.length()][s2.length()];
    }

    // Inner class for match results
    private static class MatchResult {
        double score;
        double detourDistance;
        double detourPercentage;

        MatchResult() {
            this.score = 0.0;
            this.detourDistance = 0.0;
            this.detourPercentage = 0.0;
        }
    }
}