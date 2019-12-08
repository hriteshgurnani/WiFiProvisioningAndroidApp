//*
//* Copyright (C) 2019 Texas Instruments Incorporated - http://www.ti.com/
//*
//*
//*  Redistribution and use in source and binary forms, with or without
//*  modification, are permitted provided that the following conditions
//*  are met:
//*
//*    Redistributions of source code must retain the above copyright
//*    notice, this list of conditions and the following disclaimer.
//*
//*    Redistributions in binary form must reproduce the above copyright
//*    notice, this list of conditions and the following disclaimer in the
//*    documentation and/or other materials provided with the
//*    distribution.
//*
//*    Neither the name of Texas Instruments Incorporated nor the names of
//*    its contributors may be used to endorse or promote products derived
//*    from this software without specific prior written permission.
//*
//*  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
//*  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
//*  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
//*  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
//*  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
//*  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
//*  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
//*  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
//*  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
//*  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
//*  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//*
//*/
package com.ti.smartconfig;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ti.smartconfig.utils.NetworkUtil;
import com.ti.smartconfig.utils.SharedPreferencesInterface_;
import com.ti.smartconfig.utils.SmartConfigConstants;
import com.ti.smartconfig.utils.WifiNetworkUtils;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EFragment;
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

@EFragment(R.layout.tab_lan_new_graphics_refactored)
public class LanFragmentRefactored extends Fragment {
    boolean tab_flag = false;
    @ViewById
    RecyclerView tab_lan_recycler_view;
    LedPostAsyncTask ledPostAsyncTask;
    String cloudUrl;
    SharedPreferences sharedpreferences;
    public static final String mypreference = "iot";
    public static final String Name = "deviceIP";
    Boolean connectionLost = false;
    String deviceInfoUrl;
    int counter = 0;
    String ledUrl;
    String ledOnOff;
    @Pref
    SharedPreferencesInterface_ prefs;
    private static final String TAG = "LanFragment";
    WifiNetworkUtils mNetworkUtils;
    Boolean deviceInfoCancelProcess = false;
    String deviceIp;
    String deviceType;
    String mac = "";
    String ssid = "";
    String IP = "";
    String accelerometerParamsX;
    String accelerometerParamsY;
    String accelerometerParamsZ;
    Boolean onOffRedBtn = false;
    Boolean cancelAsyncTask = false;

    BroadcastReceiver networkChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int networkState = NetworkUtil.getConnectionStatus(context);
            String ssid = mNetworkUtils.getConnectedSSID();
            if (networkState != NetworkUtil.WIFI) {//no wifi connection
                Toast.makeText(getActivity(), "No WiFi Connection\n please connect to your router first and configure your device", Toast.LENGTH_SHORT).show();
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
    private MyRecyclerViewAdapter adapter;

    private String httpOrHttps() {
        if (NetworkUtil.didRedirect) {
            return "https";
        } else {
            return "http";
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @AfterViews
    void afterViews() {
        int spanCount = 3; // 3 columns
        int spacing = 16; // 50px
        tab_lan_recycler_view.addItemDecoration(new GridSpacingItemDecoration(spanCount, spacing, false));
        tab_lan_recycler_view.setLayoutManager(new GridLayoutManager(getContext(), 3));

        String[] dataA = {"Light", "Temp", "Water", "Power", "Safety", "Devices"};
        adapter = new MyRecyclerViewAdapter(getContext(), dataA);
        tab_lan_recycler_view.setAdapter(adapter);

        ledOnOff = "on";
        sharedpreferences = getActivity().getSharedPreferences(mypreference, Context.MODE_PRIVATE);
        if (sharedpreferences.contains(Name)) {
            deviceIp = (sharedpreferences.getString(Name, ""));
        }
        mNetworkUtils = WifiNetworkUtils.getInstance(getActivity());
        String ssid = mNetworkUtils.getConnectedSSID();
        if (ssid == null || Objects.equals(deviceIp, "")) {
            Toast.makeText(getActivity(), "No WiFi Connection\n please connect to your router first and configure your device", Toast.LENGTH_SHORT).show();
        }
        cloudUrl = httpOrHttps() + "://" + deviceIp + "/cloud?state";
        deviceInfoUrl = httpOrHttps() + "://" + deviceIp + "/device?macaddress&ipaddress&ssid";//
        String accelerometerUrl = httpOrHttps() + "://" + deviceIp + "/sensor?axisx&axisy&axisz";
        ledUrl = httpOrHttps() + "://" + deviceIp + "/light?redled";
        Log.d(TAG, "accelerometer task exe,\nudUrl: " + cloudUrl + "\ndeviceInfoUrl: " + deviceInfoUrl + "\naccelerometerUrl: " + accelerometerUrl + "\nledUrl: " + ledUrl);
        cancelAsyncTask = false;
        deviceInfoCancelProcess = false;
        counter = 0;
        final HashMap<String, String> data = new HashMap<String, String>();
        /*red_led_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "led button pressed");
                String ssid = mNetworkUtils.getConnectedSSID();
                if (ssid == null) {
                    Toast.makeText(getActivity(), "Please check your internet connection", Toast.LENGTH_SHORT).show();
                } else {
                    if (ledOnOff.equals("on")) {
                        data.put("redled", "off");
                        ledOnOff = "off";
                        String triggerUrl = httpOrHttps() + "://" + deviceIp + "/light";
                        ledPostAsyncTask = new LedPostAsyncTask(data);
                        ledPostAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, triggerUrl);
                        red_led_button.setImageResource(R.drawable.opengl_tab_gray_led);
                    } else {
                        data.put("redled", "on");
                        ledOnOff = "on";
                        String triggerUrl = httpOrHttps() + "://" + deviceIp + "/light";
                        ledPostAsyncTask = new LedPostAsyncTask(data);
                        ledPostAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, triggerUrl);
                        red_led_button.setImageResource(R.drawable.opengl_tab_red_led);
                    }
                }
            }
        });*/
    }

    @Override
    public void onResume() {
        super.onResume();
        tab_flag = false;
        ledOnOff = "on";
        getActivity().registerReceiver(networkChangeReceiver, new IntentFilter(SmartConfigConstants.NETWORK_CHANGE_BROADCAST_ACTION));
        //for now disabled
        deviceInfoUrl = httpOrHttps() + "://" + deviceIp + "/device?macaddress&ipaddress&ssid";//
        String accelerometerUrl = httpOrHttps() + "://" + deviceIp + "/sensor?axisx&axisy&axisz";
        ledUrl = httpOrHttps() + "://" + deviceIp + "/light?redled";

        deviceInfoCancelProcess = false;
        cancelAsyncTask = false;
        deviceInfoCancelProcess = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        //if()
        tab_flag = true;
        cancelAsyncTask = true;
        getActivity().unregisterReceiver(networkChangeReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelAsyncTask = true;
    }

    //Led Post state - on/off
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

    class MyRecyclerViewAdapter extends RecyclerView.Adapter<MyRecyclerViewAdapter.ViewHolder> {

        private String[] mData;
        private LayoutInflater mInflater;

        // data is passed into the constructor
        MyRecyclerViewAdapter(Context context, String[] data) {
            this.mInflater = LayoutInflater.from(context);
            this.mData = data;
        }

        // inflates the cell layout from xml when needed
        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = mInflater.inflate(R.layout.grid_list_item, parent, false);
            return new ViewHolder(view);
        }

        // binds the data to the TextView in each cell
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.myTextView.setText(mData[position]);
            if (position == 0) {
                holder.imageView.setImageResource(R.drawable.light);
            } else if (position == 1) {
                holder.imageView.setImageResource(R.drawable.temp);
            } else if (position == 2) {
                holder.imageView.setImageResource(R.drawable.water);
            } else if (position == 3) {
                holder.imageView.setImageResource(R.drawable.power);
            } else if (position == 4) {
                holder.imageView.setImageResource(R.drawable.safety);
            } else if (position == 5) {
                holder.imageView.setImageResource(R.drawable.devices);
            }
        }

        // total number of cells
        @Override
        public int getItemCount() {
            return mData.length;
        }


        // stores and recycles views as they are scrolled off screen
        public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            TextView myTextView;
            ImageView imageView;

            ViewHolder(View itemView) {
                super(itemView);
                myTextView = itemView.findViewById(R.id.textView);
                imageView = itemView.findViewById(R.id.imageView);
                itemView.setOnClickListener(this);
            }

            @Override
            public void onClick(View view) {
                onItemClick(view, getAdapterPosition());
            }
        }

        // convenience method for getting data at click position
        String getItem(int id) {
            return mData[id];
        }
    }

    private void onItemClick(View view, int adapterPosition) {
        if (adapterPosition == 0) {
            Intent intent = new Intent(getActivity(), LightsActivity_.class);
            getActivity().startActivity(intent);
        }
    }

    public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {

        private int spanCount;
        private int spacing;
        private boolean includeEdge;

        public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
            this.spanCount = spanCount;
            this.spacing = spacing;
            this.includeEdge = includeEdge;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view); // item position
            int column = position % spanCount; // item column

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount; // spacing - column * ((1f / spanCount) * spacing)
                outRect.right = (column + 1) * spacing / spanCount; // (column + 1) * ((1f / spanCount) * spacing)

                if (position < spanCount) { // top edge
                    outRect.top = spacing;
                }
                outRect.bottom = spacing; // item bottom
            } else {
                outRect.left = column * spacing / spanCount; // column * ((1f / spanCount) * spacing)
                outRect.right = spacing - (column + 1) * spacing / spanCount; // spacing - (column + 1) * ((1f /    spanCount) * spacing)
                if (position >= spanCount) {
                    outRect.top = spacing; // item top
                }
            }
        }
    }
}