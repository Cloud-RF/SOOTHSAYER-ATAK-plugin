package com.atakmap.android.soothsayer;

import android.annotation.SuppressLint;
import android.os.Environment;
import android.os.Looper;
import android.widget.TextView;

import com.atakmap.android.soothsayer.plugin.R;
import com.atakmap.coremap.log.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

public class SpotBeamCall {

    public static void callAPI(Satellite satellite, double areaLat, double areaLon, PluginDropDownReceiver receiver, String apiKey, String API_URL, String dateTime) {

        // Compute study area relative to resolution with a 1MP target size
        // 2m res @ 1000m radius = 1MP
        // 10m res @ 5000m = 1MP
        // 20m res @ 10km = 1MP

        double dist = receiver.getResolution() == 2 ?  1000 : (receiver.getResolution() == 10 ? 5000 : 10000);
        double dLat = Math.abs(areaLat - endpointFromPointBearingDistance(areaLat, areaLon, 0, dist)[0]);
        double dLon = Math.abs(areaLon - endpointFromPointBearingDistance(areaLat, areaLon, 90, dist)[1]);

        double north = areaLat + dLat;
        double south = areaLat - dLat;
        double east = areaLon + dLon;
        double west = areaLon - dLon;

        Log.d("spotbeam", "dLat: " + dLat + ", dLon: " + dLon);
        Log.d("spotbeam", "NSEW: \n" + north + ", \n" + south + ", \n" + east + ", \n" + west);

        // https://cloudrf.com/documentation/developer/#/Satellite/satellite%2Farea

        /*
        By default the environment uses DSM/LiDAR data with clutter etc off.
        If your region does not have LiDAR, the next best thing is elevation=2, buildings=1 to use ML generated buildings
        To add trees for shadows, set landcover=1 and ensure your clutter profile heights match your region eg. FOREST.clt
        To check LiDAR coverage: https://api.cloudrf.com/API/terrain/
         */
        String body =
                "{\n" +
                "    \"satellites\": [\n" +
                "        \"" + satellite.name + "\"\n" +
                "    ],\n" +
                "    \"date_time\": \"" + dateTime + "\",\n" +
                "    \"receiver\": {\n" +
                "        \"alt\": 1.0\n" + // Height AGL
                "    },\n" +
                "    \"environment\": {\n" +
                "        \"clt\": \"Minimal.clt\",\n" + // Sets height for trees
                "        \"elevation\": 1,\n" + // 1 = DSM/LiDAR, 2 = DTM
                "        \"landcover\": 0,\n" + // 1 = Trees etc ON
                "        \"buildings\": 0,\n" + // 1 = Buildings ON
                "        \"obstacles\": 0\n" + // 1 = Custom clutter ON
                "    },\n" +
                "    \"output\": {\n" +
                "        \"col\": \"GREEN.dBm\",\n" +
                "        \"res\": \"" + receiver.getResolution() + "\",\n" +
                "        \"rx_units\": \"m\",\n" +
                "        \"bounds\": {\n" +
                "            \"north\": " + north + ",\n" +
                "            \"south\": " + south + ",\n" +
                "            \"east\": " + east + ", \n" +
                "            \"west\": " + west + "\n" +
                "        }\n" +
                "    }\n" +
                "}";

        Log.d("spotbeam", "Body: " + body);

        makeCall(body, receiver, satellite.name, apiKey, areaLat, areaLon, API_URL);

    }

    private static void makeCall(String body, PluginDropDownReceiver receiver, String satName, String apiKey, double areaLat, double areaLon, String API_URL) {
        Thread thread = new Thread(() -> {
            try {
                String str = getJsonString(body, apiKey, receiver, satName, API_URL);

                Log.d("spotbeam", "Response String: " + str);

                if (!str.equals("ERROR")) {

                    JsonParser parser = new JsonParser();

                    JsonObject object = parser.parse(str).getAsJsonObject();

                    String kmz = object.get("kmz").getAsString();
                    String png = object.get("PNG_WGS84").getAsString();
                    JsonArray boundsArray = object.get("bounds").getAsJsonArray();

                    List<Double> bounds = new ArrayList<>();
                    for (int i = 0; i < 4; i++)
                        bounds.add(boundsArray.get(i).getAsDouble());

                    downloadKMZ(kmz, satName);
                    downloadPNG(png, satName, receiver, bounds);

                    double satLat = object.get("satellites").getAsJsonObject().get(satName).getAsJsonObject().get("lat").getAsDouble();
                    double satLon = object.get("satellites").getAsJsonObject().get(satName).getAsJsonObject().get("lon").getAsDouble();
                    double satAlt = object.get("satellites").getAsJsonObject().get(satName).getAsJsonObject().get("height_km").getAsDouble();
                    double satAzi = calculateAzimuth(areaLat, areaLon, satLat, satLon);
                    double satElev = calculateElevation(areaLat, areaLon, 1.0, satLat, satLon, satAlt);
                    double dist = receiver.getResolution() == 5 ?  2000 : (receiver.getResolution() == 10 ? 4000 : 8000);

                    Double[] areaPoint = {areaLat, areaLon};
                    double[] point = endpointFromPointBearingDistance(areaLat, areaLon, satAzi, 4 * dist);
                    Double[] secondPoint = { point[0], point[1] };

                    receiver.removeLines();
                    receiver.drawLine(areaPoint, secondPoint, true, satAzi, satElev);

                    Thread updateUIThread = new Thread() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
                            TextView viewAzimuth = receiver.getSpotBeamView().findViewById(R.id.viewAzimuth);
                            TextView viewElevation = receiver.getSpotBeamView().findViewById(R.id.viewElevation);
                            TextView viewAltitude = receiver.getSpotBeamView().findViewById(R.id.viewAltitude);

                            viewAzimuth.setText((Math.round(satAzi * 10) / 10.0) + "°");
                            viewElevation.setText((Math.round(satElev * 10) / 10.0) + "°");
                            viewAltitude.setText(Math.round(satAlt) + "km");
                        }
                    };

                    receiver.getSpotBeamView().post(updateUIThread);

                }
            } catch (IOException e) {
                Log.e("spotbeam", "ERROR: " + e.getMessage());
            }
        });

        thread.start();
    }

    /*
    Warning: Assumes storage path starts /sdcard/
     */
    private static void downloadKMZ(String urlString, String satName) throws IOException {
        URL url = new URL(urlString);
        BufferedInputStream bis = new BufferedInputStream(url.openStream());

        FileOutputStream fis = new FileOutputStream("/sdcard/atak/SOOTHSAYER/KMZ/" + satName + ".kmz");
        byte[] buffer = new byte[1024];
        int count;
        while ((count = bis.read(buffer, 0, 1024)) != -1)
            fis.write(buffer, 0, count);
        fis.close();
        bis.close();
    }

    private static void downloadPNG(String urlString, String satName, PluginDropDownReceiver receiver, List<Double> bounds) throws IOException {
        URL url = new URL(urlString);
        BufferedInputStream bis = new BufferedInputStream(url.openStream());
        FileOutputStream fis = new FileOutputStream("/sdcard/atak/SOOTHSAYER/KMZ/" + satName + ".png");
        byte[] buffer = new byte[1024];
        int count;
        while ((count = bis.read(buffer, 0, 1024)) != -1)
            fis.write(buffer, 0, count);
        fis.close();
        bis.close();

        receiver.addSingleKMZLayer("SPOTBEAM", "/sdcard/atak/SOOTHSAYER/KMZ/" + satName + ".png", bounds);
    }

    private static double[] endpointFromPointBearingDistance(double latitude, double longitude, double bearingDegrees, double distanceMeters) {
        double latitudeRadian = Math.toRadians(latitude);
        double longitudeRadian = Math.toRadians(longitude);
        double bearingRadian = Math.toRadians(bearingDegrees);
        double angularDistance = distanceMeters / 6.378e+6;

        double latitudePoint = Math.asin(
                Math.sin(latitudeRadian) * Math.cos(angularDistance) +
                        Math.cos(latitudeRadian) * Math.sin(angularDistance) * Math.cos(bearingRadian)
        );

        double longitudePoint = longitudeRadian + Math.atan2(
                Math.sin(bearingRadian) * Math.sin(angularDistance) * Math.cos(latitudeRadian),
                Math.cos(angularDistance) - Math.sin(latitudeRadian) * Math.sin(latitudePoint)
        );

        return new double[] {Math.toDegrees(latitudePoint), Math.toDegrees(longitudePoint)};
    }

    private static String getJsonString(String body, String apiKey, PluginDropDownReceiver receiver, String satName, String API_URL) {
        try {

            SSLContext ctx;
            ctx = SSLContext.getInstance("SSL");

            ctx.init(null, new X509TrustManager[]{new X509TrustManager(){
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, new SecureRandom());

            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((s, sslSession) -> true);

            String responseString = "";
            URL url = new URL(API_URL + "/satellite/area");
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("key", apiKey);

            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(body);
            wr.flush();
            wr.close();

            int responseCode = con.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                responseString = response.toString();
                return responseString;

            } else {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                responseString = response.toString();

                JsonParser parser = new JsonParser();
                JsonObject object = parser.parse(responseString).getAsJsonObject();
                String error = object.get("error").getAsString();

                Looper.prepare();
                receiver.toast("Error: " + error);

                Log.e("spotbeamcall", responseString);
            }

        } catch (Exception e) {
            Log.e("spotbeamsat", e.getMessage() + "\nCause: " + e.getCause());
        }
        return "ERROR";
    }

    private static double[] sphericalToCartesianCoords(double lat, double lon, double rad) {
        return new double[] {
                rad * Math.sin(lon) * Math.cos(lat),
                rad * Math.sin(lon) * Math.sin(lat),
                rad * Math.cos(lon)
        };
    }

    private static double calculateAzimuth(double latA, double lonA, double latB, double lonB) {
        double latARad = Math.toRadians(latA);
        double lonARad = Math.toRadians(lonA);
        double latBRad = Math.toRadians(latB);
        double lonBRad = Math.toRadians(lonB);

        double deltaLon = lonBRad - lonARad;
        double x = Math.cos(latBRad) * Math.sin(deltaLon);
        double y = Math.cos(latARad) * Math.sin(latBRad) - Math.sin(latARad) * Math.cos(latBRad) * Math.cos(deltaLon);
        double r = Math.atan2(x, y);
        double d = Math.toDegrees(r);
    
        while (d < 0) d += 360;
        return d;
    }

    // Verbose but it works.
    // altA is the terminal, altB is the satellite
    private static double calculateElevation(double latA, double lonA, double altA, double latB, double lonB, double altB) {
        double latARad = Math.toRadians(latA);
        double lonARad = Math.toRadians(lonA);
        double latBRad = Math.toRadians(latB);
        double lonBRad = Math.toRadians(lonB);

        double aGroundX = 6371.0 * Math.sin(lonARad) * Math.cos(latARad);
        double aGroundY = 6371.0 * Math.sin(lonARad) * Math.sin(latARad);
        double aGroundZ = 6371.0 * Math.cos(lonARad);

        double aX = (6371.0 + altA) * Math.sin(lonARad) * Math.cos(latARad);
        double aY = (6371.0 + altA) * Math.sin(lonARad) * Math.sin(latARad);
        double aZ = (6371.0 + altA) * Math.cos(lonARad);

        double bGroundX = 6371.0 * Math.sin(lonBRad) * Math.cos(latBRad);
        double bGroundY = 6371.0 * Math.sin(lonBRad) * Math.sin(latBRad);
        double bGroundZ = 6371.0 * Math.cos(lonBRad);

        double bX = (6371.0 + altB) * Math.sin(lonBRad) * Math.cos(latBRad);
        double bY = (6371.0 + altB) * Math.sin(lonBRad) * Math.sin(latBRad);
        double bZ = (6371.0 + altB) * Math.cos(lonBRad);


        double aNormalX = aX - aGroundX;
        double aNormalY = aY - aGroundY;
        double aNormalZ = aZ - aGroundZ;
        double aNormalLen = Math.sqrt(aNormalX * aNormalX + aNormalY * aNormalY + aNormalZ * aNormalZ);
        aNormalX /= aNormalLen;
        aNormalY /= aNormalLen;
        aNormalZ /= aNormalLen;

        double bDirX = bX - bGroundX;
        double bDirY = bY - bGroundY;
        double bDirZ = bZ - bGroundZ;
        double bDirLen = Math.sqrt(bDirX * bDirX + bDirY * bDirY + bDirZ * bDirZ);
        bDirX /= bDirLen;
        bDirY /= bDirLen;
        bDirZ /= bDirLen;

        double d = ((aX - bGroundX) * aNormalX + (aY - bGroundY) * aNormalY + (aZ - bGroundZ) * aNormalZ) / (bDirX * aNormalX + bDirY * aNormalY + bDirZ * aNormalZ);

        double iX = bGroundX + bDirX * d;
        double iY = bGroundY + bDirY * d;
        double iZ = bGroundZ + bDirZ * d;

        double v1X = iX - aX;
        double v1Y = iY - aY;
        double v1Z = iZ - aZ;
        double v1Len = Math.sqrt(v1X * v1X + v1Y * v1Y + v1Z * v1Z);
        v1X /= v1Len;
        v1Y /= v1Len;
        v1Z /= v1Len;

        double v2X = bX - aX;
        double v2Y = bY - aY;
        double v2Z = bZ - aZ;
        double v2Len = Math.sqrt(v2X * v2X + v2Y * v2Y + v2Z * v2Z);
        v2X /= v2Len;
        v2Y /= v2Len;
        v2Z /= v2Len;


        return Math.toDegrees(Math.acos(v1X * v2X + v1Y * v2Y + v1Z * v2Z));
    }

}
