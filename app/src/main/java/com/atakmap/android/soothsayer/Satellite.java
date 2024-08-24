package com.atakmap.android.soothsayer;

import android.view.View;

import com.atakmap.android.gui.EditText;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.coremap.log.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

public class Satellite {

    public String name;
    public double period;
    public double inclination;
    public int apogee;
    public int perigee;

    public Satellite(String name, double period, double inclination, int apogee, int perigee) {
        this.name = name;
        this.period = period;
        this.inclination = inclination;
        this.apogee = apogee;
        this.perigee = perigee;
    }

    public Satellite() {
        this.name = "EMPTY";
    }

    /*
    Query the satellite API to find satellite(s).
    String can be partial eg. skyn.. but presently only first satellite is used.
    Results are paged and rate limited :p
     */
    public static void getSats(String query, PluginDropDownReceiver receiver, String API_URL) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
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
                    URL url = new URL(API_URL + "/satellite/query?NAME=" + query);
                    HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
                    con.setRequestMethod("GET");

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
                    }

                    JsonParser parser = new JsonParser();

                    if (!responseString.isEmpty()) {
                        JsonArray array = parser.parse(responseString).getAsJsonArray();
                        Satellite[] satellites = new Satellite[array.size()];
                        String[] names = new String[array.size()];

                        for (int i = 0; i < array.size(); i++) {

                            JsonObject obj = array.get(i).getAsJsonObject();

                            String name = obj.get("NAME").getAsString();
                            double period = obj.get("PERIOD").getAsDouble();
                            double inclination = obj.get("INCLINATION").getAsDouble();
                            int apogee = obj.get("APOGEE").getAsInt();
                            int perigee = obj.get("PERIGEE").getAsInt();
                            Satellite satellite = new Satellite(
                                    name,
                                    period,
                                    inclination,
                                    apogee,
                                    perigee
                            );
                            satellites[i] = satellite;
                            names[i] = name;
                        }

                        receiver.setNames(names);

                        if (names.length == 1) {
                            receiver.setSatellite(satellites[0]);
                            receiver.addSpotBeamAreaMarker();
                        }


                    }
                } catch (Exception e) {
                    Log.e("spotbeamsat", e.getMessage() + "\nCause: " + e.getCause());
                }
            }
        });

        thread.start();
    }
}
