package com.atakmap.android.soothsayer;

import android.annotation.SuppressLint;
import android.os.Looper;
import android.widget.EditText;
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

    public static void callAPI(Satellite satellite, double areaLat, double areaLon, PluginDropDownReceiver receiver, String apiKey, String API_URL) {

        String dateTime = receiver.getDate() + "T" + receiver.getTime() + "Z";

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

        String body =
                "{\n" +
                "    \"satellites\": [\n" +
                "        \"" + satellite.name + "\"\n" +
                "    ],\n" +
                "    \"date_time\": \"" + dateTime + "\",\n" +
                "    \"receiver\": {\n" +
                "        \"alt\": 1.0\n" +
                "    },\n" +
                "    \"environment\": {\n" +
                "        \"clt\": \"Minimal.clt\",\n" +
                "        \"elevation\": 1,\n" +
                "        \"landcover\": 0,\n" +
                "        \"buildings\": 0,\n" +
                "        \"obstacles\": 0\n" +
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
                    double satHeight = object.get("satellites").getAsJsonObject().get(satName).getAsJsonObject().get("height_km").getAsDouble();

                    double[] areaXYZ = sphericalToCartesianCoords(areaLat, areaLon, 6371);
                    double[] satXYZ = sphericalToCartesianCoords(satLat, satLon, satHeight);

                    double satAlt = Math.sqrt(
                            (areaXYZ[0] - satXYZ[0]) * (areaXYZ[0] - satXYZ[0])
                            + (areaXYZ[1] - satXYZ[1]) * (areaXYZ[1] - satXYZ[1])
                            + (areaXYZ[2] - satXYZ[2]) * (areaXYZ[2] - satXYZ[2])
                    );

                    double satAzi = -Math.toDegrees(Math.atan2(satLat - areaLat, satLon - areaLon)) + 90;
                    if (satAzi < 0) satAzi += 360;

                    double satElev = calculateElevation(satLon, areaLat, areaLon);

                    Log.d("spotbeam", "in: " + (areaLat - satLat) + ", " + (areaLon - satLon));

                    Log.d("spotbeam", "SatAlt: " + satAlt);
                    Log.d("spotbeam", "SatAzi: " + satAzi);

                    double dist = receiver.getResolution() == 5 ?  2000 : (receiver.getResolution() == 10 ? 4000 : 8000);

                    Double[] areaPoint = {areaLat, areaLon};
                    double[] point = endpointFromPointBearingDistance(areaLat, areaLon, satAzi, 2 * dist);
                    double[] point2 = endpointFromPointBearingDistance(areaLat, areaLon, satAzi + 2.5, 1.9 * dist);
                    double[] point3 = endpointFromPointBearingDistance(areaLat, areaLon, satAzi - 2.5, 1.9 * dist);
                    Double[] secondPoint = { point[0], point[1] };
                    Double[] thirdPoint = { point2[0], point2[1] };
                    Double[] fourthPoint = { point3[0], point3[1] };

                    Log.d("spotbeam", "First Point: " + areaPoint[0] + ", " + areaPoint[1]);
                    Log.d("spotbeam", "Second Point: " + secondPoint[0] + ", " + secondPoint[1]);

                    receiver.removeLines();
                    receiver.drawLine(areaPoint, secondPoint, true, satAzi, satElev);
                    receiver.drawLine(secondPoint, thirdPoint, false, satAzi, satElev);
                    receiver.drawLine(secondPoint, fourthPoint, false, satAzi, satElev);

                    double finalSatAzi = satAzi;
                    Thread updateUIThread = new Thread() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
                            TextView viewAzimuth = receiver.getSpotBeamView().findViewById(R.id.viewAzimuth);
                            TextView viewElevation = receiver.getSpotBeamView().findViewById(R.id.viewElevation);
                            TextView viewAltitude = receiver.getSpotBeamView().findViewById(R.id.viewAltitude);

                            viewAzimuth.setText((Math.round(finalSatAzi * 10) / 10.0) + "°");
                            viewElevation.setText((Math.round(satElev * 10) / 10.0) + "°");
                            viewAltitude.setText(Math.round(satHeight) + "km");
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

    private static double calculateElevation(double satLon, double areaLat, double areaLon) {
        double S = Math.toRadians(satLon);
        double N = Math.toRadians(areaLon);
        double L = Math.toRadians(areaLat);
        double G = S - N;

        return Math.toDegrees(Math.atan(
                  (Math.cos(G) * Math.cos(L) - 0.1512)
                / Math.sqrt(1 - (Math.cos(G) * Math.cos(G)) * (Math.cos(L) * Math.cos(L)))
        ));
    }

}