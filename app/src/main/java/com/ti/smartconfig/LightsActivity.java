/*
 * Copyright (C) 2019 Texas Instruments Incorporated - http://www.ti.com/
 *
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *    Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 *    Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the
 *    distribution.
 *
 *    Neither the name of Texas Instruments Incorporated nor the names of
 *    its contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 *  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 *  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.ti.smartconfig;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.ti.smartconfig.utils.NetworkUtil;
import com.ti.smartconfig.utils.SharedPreferencesInterface_;
import com.ti.smartconfig.utils.SmartConfigConstants;
import com.ti.smartconfig.utils.WifiNetworkUtils;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.sharedpreferences.Pref;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;

import static com.ti.smartconfig.MainActivity.TAG;

@EActivity(R.layout.activity_lights)
public class LightsActivity extends Activity {
    @ViewById
    TextView lan_tab_ssid_textview;
    String mac = "";
    String ssid = "";
    String IP = "";
    String accelerometerParamsX;
    String accelerometerParamsY;
    String accelerometerParamsZ;
    AccelerometerAsyncTask accelerometerAsyncTask;
    @ViewById
    TextView tab_lan_macaddress_textview;
    @ViewById
    TextView tab_lan_ipaddress_textview;
    @ViewById
    ToggleButton switchId;
    public static final String mypreference = "iot";
    public static final String Name = "deviceIP";
    Boolean connectionLost = false;
    String deviceInfoUrl;
    String cloudUrl;
    int counter = 0;
    String ledUrl;
    String ledOnOff;
    @Pref
    SharedPreferencesInterface_ prefs;
    SharedPreferences sharedpreferences;
    String deviceIp;
    WifiNetworkUtils mNetworkUtils;
    Boolean cancelAsyncTask = false;
    Boolean deviceInfoCancelProcess = false;
    private LedPostAsyncTask ledPostAsyncTask;
    private TextView tab_lan_axisx_respone;
    private TextView tab_lan_axisy_respone;
    private TextView tab_lan_axisz_respone;

    private String httpOrHttps() {
        if (NetworkUtil.didRedirect) {
            return "https";
        } else {
            return "http";
        }
    }

    @AfterViews
    void afterViews() {
        switchId.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean on) {
                Log.d(TAG, "led button pressed");
                String ssid = mNetworkUtils.getConnectedSSID();
                if (ssid == null) {
                    Toast.makeText(LightsActivity.this, "Please check your internet connection", Toast.LENGTH_SHORT).show();
                } else {
                    final HashMap<String, String> data = new HashMap<>();
                    if (ledOnOff.equals("on")) {
                        data.put("redled", "off");
                        ledOnOff = "off";
                        String triggerUrl = httpOrHttps() + "://" + deviceIp + "/light";
                        ledPostAsyncTask = new LedPostAsyncTask(data);
                        ledPostAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, triggerUrl);
                        //red_led_button.setImageResource(R.drawable.opengl_tab_gray_led);
                    } else {
                        data.put("redled", "on");
                        ledOnOff = "on";
                        String triggerUrl = httpOrHttps() + "://" + deviceIp + "/light";
                        ledPostAsyncTask = new LedPostAsyncTask(data);
                        ledPostAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, triggerUrl);
                        //red_led_button.setImageResource(R.drawable.opengl_tab_red_led);
                    }
                }
            }
        });

        ledOnOff = "on";
        sharedpreferences = getSharedPreferences(mypreference, Context.MODE_PRIVATE);
        if (sharedpreferences.contains(Name)) {
            deviceIp = (sharedpreferences.getString(Name, ""));
        }
        mNetworkUtils = WifiNetworkUtils.getInstance(this);
        String ssid = mNetworkUtils.getConnectedSSID();
        if (ssid == null || Objects.equals(deviceIp, "")) {
            Toast.makeText(this, "No WiFi Connection\n please connect to your router first and configure your device", Toast.LENGTH_SHORT).show();
        }
        cloudUrl = httpOrHttps() + "://" + deviceIp + "/cloud?state";
        deviceInfoUrl = httpOrHttps() + "://" + deviceIp + "/device?macaddress&ipaddress&ssid";//
        String accelerometerUrl = httpOrHttps() + "://" + deviceIp + "/sensor?axisx&axisy&axisz";
        ledUrl = httpOrHttps() + "://" + deviceIp + "/light?redled";
        Log.d("", "accelerometer task exe,\nudUrl: " + cloudUrl + "\ndeviceInfoUrl: " + deviceInfoUrl + "\naccelerometerUrl: " + accelerometerUrl + "\nledUrl: " + ledUrl);
        accelerometerAsyncTask = new AccelerometerAsyncTask();
        accelerometerAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, accelerometerUrl);
        cancelAsyncTask = false;
        deviceInfoCancelProcess = false;
        counter = 0;
    }

    boolean tab_flag = false;


    @Override
    public void onPause() {
        super.onPause();
        //if()
        tab_flag = true;
        cancelAsyncTask = true;
        unregisterReceiver(networkChangeReceiver);
    }


    BroadcastReceiver networkChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int networkState = NetworkUtil.getConnectionStatus(context);
            String ssid = mNetworkUtils.getConnectedSSID();
            if (networkState != NetworkUtil.WIFI) {//no wifi connection
                Toast.makeText(LightsActivity.this, "No WiFi Connection\n please connect to your router first and configure your device", Toast.LENGTH_SHORT).show();
                connectionLost = true;
            }
            if (networkState == NetworkUtil.WIFI && ssid != null) // wifi connected
            {
                if (connectionLost == true) {
                    connectionLost = false;
                    deviceInfoUrl = httpOrHttps() + "://" + deviceIp + "/device?macaddress&ipaddress&ssid";//
                    String accelerometerUrl = httpOrHttps() + "://" + deviceIp + "/sensor?axisx&axisy&axisz";
                    ledUrl = httpOrHttps() + "://" + deviceIp + "/light?redled";
                    deviceInfoCancelProcess = false;
                }
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        tab_flag = false;
        ledOnOff = "on";
        registerReceiver(networkChangeReceiver, new IntentFilter(SmartConfigConstants.NETWORK_CHANGE_BROADCAST_ACTION));
        //for now disabled
        deviceInfoUrl = httpOrHttps() + "://" + deviceIp + "/device?macaddress&ipaddress&ssid";//
        String accelerometerUrl = httpOrHttps() + "://" + deviceIp + "/sensor?axisx&axisy&axisz";
        ledUrl = httpOrHttps() + "://" + deviceIp + "/light?redled";

        deviceInfoCancelProcess = false;
        cancelAsyncTask = false;
        deviceInfoCancelProcess = false;
    }

    public void onBackPressed() {
        finish();
    }

    public class LedPostAsyncTask extends AsyncTask<String, String, String> {
        private HashMap<String, String> mData = null;// post data

        /**
         * constructor
         */
        public LedPostAsyncTask(HashMap<String, String> data) {
            mData = data;
        }

        @Override
        protected String doInBackground(String... params) {
            byte[] result = null;
            String str = "";
            HttpClient client = NetworkUtil.getNewHttpClient();
            HttpPost post = new HttpPost(params[0]);// in this case, params[0] is URL
            try {
                // set up post data
                ArrayList<NameValuePair> nameValuePair = new ArrayList<NameValuePair>();
                Iterator<String> it = mData.keySet().iterator();
                while (it.hasNext()) {
                    String key = it.next();
                    nameValuePair.add(new BasicNameValuePair(key, mData.get(key)));
                }
                post.setEntity(new UrlEncodedFormEntity(nameValuePair, "UTF-8"));
                HttpResponse response = client.execute(post);
                StatusLine statusLine = response != null ? response.getStatusLine() : null;
                if ((statusLine != null ? statusLine.getStatusCode() : 0) == HttpStatus.SC_OK || (statusLine != null ? statusLine.getStatusCode() : 0) == HttpStatus.SC_NO_CONTENT || response != null) {
                    result = EntityUtils.toByteArray(response.getEntity());
                    str = new String(result, "UTF-8");
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            //running GET for mac,ipaddress and ssid
            HttpResponse response = null;
            String responseString = null;
            try {
                response = client.execute(new HttpGet(ledUrl));
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
                //Log it
            }
            StatusLine statusLine = response != null ? response.getStatusLine() : null;
            if ((statusLine != null ? statusLine.getStatusCode() : 0) == HttpStatus.SC_OK && (response != null ? response.getStatusLine() : null) != null || response != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try {
                    response.getEntity().writeTo(out);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                    //Log it
                }
                responseString = out.toString();
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return responseString;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            try {
                if (s.equals("off")) {
                    //red_led_button.setImageResource(R.drawable.opengl_tab_gray_led);
                }
                if (s.equals("on")) {
                    //red_led_button.setImageResource(R.drawable.opengl_tab_red_led);
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
    }

    class AccelerometerAsyncTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... uri) {
            HttpParams httpParameters = new BasicHttpParams();
            int timeoutConnection = 1000;
            HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
            // Set the default socket timeout (SO_TIMEOUT)
            // in milliseconds which is the timeout for waiting for data.
            int timeoutSocket = 2000;
            HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
            HttpClient httpclient = NetworkUtil.getNewHttpClient();
            HttpResponse response = null;
            String responseString = null;

            //this loop going to work forever until the user will move to other tab/close the application
            while (!cancelAsyncTask) {
                if (counter >= 30) {
                    counter = 0;
                }
                if( connectionLost ) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    if (counter == 0) {
                        if (!deviceInfoCancelProcess) {
                            //running GET for mac,ipaddress and ssid
                            try {
                                response = httpclient.execute(new HttpGet(deviceInfoUrl));
                            }catch (IllegalArgumentException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (NullPointerException e) {
                                e.printStackTrace();
                            } catch (Exception e) {
                                e.printStackTrace();
                                //Log it
                            }
                            StatusLine statusLine = response != null ? response.getStatusLine() : null;
                            if ((statusLine != null ? statusLine.getStatusCode() : 0) == HttpStatus.SC_OK && (response != null ? response.getStatusLine() : null) != null || response!=null) {
                                ByteArrayOutputStream out = new ByteArrayOutputStream();
                                try {
                                    response.getEntity().writeTo(out);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (NullPointerException e) {
                                    e.printStackTrace();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    //Log it
                                }
                                responseString = out.toString();
                                publishProgress(responseString);
                                counter++;

                                try {
                                    deviceInfoCancelProcess = true;
                                    out.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                    if (counter ==2) {
                        //running GET for led on/off
                        try {
                            response = httpclient.execute(new HttpGet(ledUrl));
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (NullPointerException e) {
                            e.printStackTrace();
                        } catch (Exception e) {
                            e.printStackTrace();
                            //Log it
                        }
                        StatusLine statusLine = response != null ? response.getStatusLine() : null;
                        if ((statusLine != null ? statusLine.getStatusCode() : 0) == HttpStatus.SC_OK && (response != null ? response.getStatusLine() : null) != null || response!=null) {

                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            try {
                                response.getEntity().writeTo(out);
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (NullPointerException e) {
                                e.printStackTrace();
                            } catch (Exception e) {
                                e.printStackTrace();
                                //Log it
                            }
                            responseString = out.toString();
                            publishProgress(responseString);
                            counter++;
                            try {
                                out.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        //running GET for accelerometer axisx,axisy,axisz
                        try {
                            response = httpclient.execute(new HttpGet(uri[0]));
                        } catch (IllegalArgumentException e) {
                            e.printStackTrace();
                        }
                        StatusLine statusLine = response != null ? response.getStatusLine() : null;
                        if ((statusLine != null ? statusLine.getStatusCode() : 0) == HttpStatus.SC_OK && (response != null ? response.getStatusLine() : null) != null || response!=null) {
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            response.getEntity().writeTo(out);
                            responseString = out.toString();
                            publishProgress(responseString);
                            counter++;
                            out.close();
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } catch (NullPointerException e) {
                                e.printStackTrace();
                            } catch (Exception e) {
                                e.printStackTrace();
                                //Log it
                            }
                        } else {
                            //Closes the connection.
                            if (response != null) {
                                response.getEntity().getContent().close();
                            }
                            throw new IOException(statusLine != null ? statusLine.getReasonPhrase() : "");
                        }
                    }
                } catch (ClientProtocolException e) {
                    e.printStackTrace();

                } catch (IOException e) {
                    e.printStackTrace();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            //we will never get here.will keep iterating in while loop or closing the AsyncTask
            return responseString;
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        protected void onProgressUpdate(String... progress) {
            super.onProgressUpdate();
            //updating results every 1 sec
            final String[] split;
            split = progress[0].split("&");
            for (final String tempParameterString : split) {
                final String[] arrTempParameter = tempParameterString.split("=");
                if (arrTempParameter.length >= 2 && !tab_flag ) {
                    final String parameterKey = arrTempParameter[0];
                    final String parameterValue = arrTempParameter[1];
                    if (parameterKey.contains("axisx")) {
                        accelerometerParamsX = parameterValue;
                    }
                    if (parameterKey.contains("axisy")) {
                        accelerometerParamsY = parameterValue;
                    }
                    if (parameterKey.contains("axisz")) {
                        accelerometerParamsZ = parameterValue;
                    }
                    if (parameterKey.contains("macaddress")) {
                        mac = parameterValue;
                        tab_lan_macaddress_textview.setText(mac);
                    }
                    if (parameterKey.contains("ssid")) {
                        ssid = parameterValue;
                        lan_tab_ssid_textview.setText(ssid);
                    }
                    if (parameterKey.contains("ipaddress")) {
                        IP = parameterValue;
                        tab_lan_ipaddress_textview.setText(IP);
                    }
                    if (parameterValue.equals("on")) {
                        switchId.setChecked(true);
                        ledOnOff = "on";
                    }
                    if (parameterValue.equals("off")) {
                        switchId.setChecked(false);
                        ledOnOff = "off";
                    }
                }
            }
            if (tab_lan_axisx_respone != null && tab_lan_axisy_respone != null && tab_lan_axisz_respone != null) {

                tab_lan_axisx_respone.setText(accelerometerParamsX);
                tab_lan_axisy_respone.setText(accelerometerParamsY);
                tab_lan_axisz_respone.setText(accelerometerParamsZ);
            }
        }
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
        }
    }
}
