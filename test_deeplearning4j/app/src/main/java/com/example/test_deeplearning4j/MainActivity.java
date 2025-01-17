package com.example.test_deeplearning4j;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nd4j.evaluation.classification.Evaluation;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
//import com.example.learn.GreetingServiceGrpc;

//import io.grpc.ManagedChannel;
//import io.grpc.ManagedChannelBuilder;
//import io.grpc.examples.helloworld.GreeterGrpc;
//import io.grpc.examples.helloworld.HelloReply;
//import io.grpc.examples.helloworld.HelloRequest;

public class MainActivity extends AppCompatActivity {
    private EditText clientAddress;
    private TextView stepText;
    private TextView logArea;
    private static final String TAG = "FederatedActivity";
    private String mUsername;
    Handler handler = null;
    private CNNModel cnn_model;
    private int model_version;
    private String temp_modelstate;
    private OkHttpClient client;
    private int currentRound = 0;
    private int max_train_round = 0;
    private Response response = null;
    private String responseString;
    private String return_task;
    private JSONObject response_json = null;
    private String address = "192.168.0.108";
    private double ui_testacc = 0.0;
    private boolean ModelBuildCheck = false;

    private String grpc_addr = "192.168.0.108";
    private String grpc_port = "50050";

    // Device Info
    private float batteryPct = 0.0f;
    private boolean isCharging = false;
    private float temp = 0.0f;
    private IntentFilter intentfilter;
    private float batteryTemp;
    private String currentBatterytemp;

    // Evaluate Info
    private String acc_f1 = "";

    List<Entry> entryList = new ArrayList<>();

    // grpc
    private ManagedChannel channel;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        MultiDex.install(this);

        // grpc managechannel
        channel =  ManagedChannelBuilder.forAddress("192.168.0.108", 50050)
                .usePlaintext()
                .build();


        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MODE_PRIVATE);

        System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0");
        System.setProperty("org.bytedeco.javacpp.maxbytes", "0");

//        mChart = findViewById(R.id.chart);
        clientAddress = (EditText) findViewById(R.id.ClientAddress);
        stepText = findViewById(R.id.step);
        logArea = findViewById(R.id.log_area);

        Button startManualBtn = findViewById(R.id.btn_start_manual);
        Button preProcessBtn = findViewById(R.id.btn_preprocess);
        entryList.add(new Entry(0, 0));
        LineDataSet lineDataSet = new LineDataSet(entryList, "loss");
        LineData lineData = new LineData(lineDataSet);

        handler = new Handler(msg -> {
            Bundle bundle = msg.getData();
            String str = bundle.getString("data");
            logArea.append(str + "\n");
            return false;
        });

        intentfilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);

        preProcessBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(clientAddress.getText().length() != 7) {
                    address = clientAddress.getText().toString();
                }

                mUsername = "android_" + Math.floor((Math.random() * 1000) + 1);
                client = new OkHttpClient.Builder()
                        .connectTimeout(6000, TimeUnit.MINUTES)
                        .writeTimeout(6000, TimeUnit.MINUTES)
                        .readTimeout(6000, TimeUnit.MINUTES)
                        .build();

                // previous training weight file check >> if exist : delete
//                String weight_file_path = "/storage/self/primary/Download/save_weight";
//                File weight_check_file = new File(weight_file_path);
//                for(int i = 0; i < weight_check_file.length(); i++) {
//                    if(!weight_check_file.listFiles()[i].equals(mUsername)) {
//
//                    }
//                }

                // battery_status / is_charging
                String bsic = BatteryStatus_IsCharging();

                // wifi connect status
                Boolean wc = WifiConnected();

                // battery temperature
                currentBatterytemp = Temperature();

                // cpu frequency
                StringBuffer cf = CpuFreq();

                Message msg = new Message();
                Bundle bundle = new Bundle();
                bundle.putString("data", "create android user " + mUsername + "\nbattery status and is charging? " + bsic + "\nwifi connected? " + wc
                                                + "\ntemperature? " + currentBatterytemp + "\ncpu frequency? " + cf + "GHz");
                msg.setData(bundle);
                handler.sendMessage(msg);
            }
        });

        startManualBtn.setOnClickListener(v -> {
            try {
                // 예외상황 처리해야함
                // 1. 연결 끊겼을 경우 disconnect기능 구현해서 서버쪽에도 알려줘야함
                // 2.

                // client wake up!
                wakeup();
                response_json = new JSONObject(responseString);
                if(response_json.has("state")) {
                    do {
                        model_version = response_json.getInt("model_version");
                        Thread.sleep(60000);
                        version();
                        response_json = new JSONObject(responseString);
                    } while (temp_modelstate.equals("WAIT"));

                    currentRound = response_json.getInt("current_round");
                    model_version = response_json.getInt("model_version");
                }

//                classmanagement();
                // client ready!
                oninit();

//                Message msg = new Message();
//                Bundle bundle = new Bundle();
//                bundle.putString("data", "success init");
//                msg.setData(bundle);
//                handler.sendMessage(msg);
                Log.d("INFO", "success init");

                // download model (server -> client)
                download();
                // first model build
                cnn_model.buildModel("/storage/self/primary/Download/save_model");
                acc_f1 = cnn_model.eval();
                // First Round Training
                response_json = new JSONObject(responseString);
                max_train_round = response_json.getInt("max_train_round");
                update();

                response_json = new JSONObject(responseString);
                Log.d("RESPONSE INFO", String.valueOf(response_json));
                while(response_json.getInt("current_round") <= max_train_round) {
                    // 서버와 클라이언트 라운드 학습 스케쥴링
                    if (response_json.getString("state").equals("RESP_ARY")) { // 1 라운드의 모든 클라이언트 학습은 끝났지만 모든 라운드 학습은 하지 못함
                        download();
                        cnn_model.buildModel("/storage/self/primary/Download/save_model");
                        acc_f1 = cnn_model.eval();
                        update();
                    } else if (response_json.getString("state").equals("RESP_ACY")) { // 1 라운드에서 아직 다른 클라이언트가 학습을 완료하지 못함
                        do {
                            Thread.sleep(60000);
                            version();
                            response_json = new JSONObject(responseString);
                        } while (temp_modelstate.equals("WAIT"));

                        if(response_json.getInt("current_round") > max_train_round) {
                            break;
                        }

                        download();
                        cnn_model.buildModel("/storage/self/primary/Download/save_model");
                        acc_f1 = cnn_model.eval();
                        update();
                    } else if (response_json.getString("state").equals("NEC")) {
//                        Message msg = new Message();
//                        Bundle bundle = new Bundle();
//                        bundle.putString("data", "Not equal currentRound error");
//                        msg.setData(bundle);
//                        handler.sendMessage(msg);
                        Log.d("WARNING", "Not equal currentRound error");
                    }
                    response_json = new JSONObject(responseString);
                    model_version = response_json.getInt("model_version");
                }

//                Message msg = new Message();
//                Bundle bundle = new Bundle();
//                bundle.putString("data", "Final accuracy " + Double.toString(response_json.getDouble("model_acc")));
//                msg.setData(bundle);
//                handler.sendMessage(msg);
                Log.d("RESULT INFO", "Final accuracy " + Double.toString(response_json.getDouble("model_acc")));

                if(response_json.getString("state").equals("FIN")) {
//                    msg = new Message();
//                    bundle = new Bundle();
//                    bundle.putString("data", "Final accuracy " + Double.toString(response_json.getDouble("model_acc")));
//                    msg.setData(bundle);
//                    handler.sendMessage(msg);
                    Log.d("RESULT INFO", "Final accuracy " + Double.toString(response_json.getDouble("model_acc")));
                }

            } catch (JSONException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        });
    }

    private String BatteryStatus_IsCharging() {
        // current battery status
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        batteryPct = level * 100 / (float)scale;

        // Are we charging / charged?
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        return Float.toString(batteryPct) + "," + Boolean.toString(isCharging);
    }

    private Boolean WifiConnected() {
        // wifi connect status
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        boolean connected_wifi = mWifi.isConnected();

        return connected_wifi;
    }

    private StringBuffer CpuFreq() {
        StringBuffer sb_cur_freq = new StringBuffer();
        String cur_freq_file = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";

        if (new File(cur_freq_file).exists()) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(new File(cur_freq_file)));
                String aLine;
                while ((aLine = br.readLine()) != null)
                    sb_cur_freq.append(Float.toString(((float)(Integer.parseInt(aLine))/1000000)) + "\n");

                if (br != null)
                    br.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        return sb_cur_freq;
    }

    private String Temperature() {
        batteryTemp = (float)(MainActivity.this.registerReceiver(broadcastreceiver, intentfilter).getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0))/10;
        return Float.toString(batteryTemp);
    }

    private BroadcastReceiver broadcastreceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            batteryTemp = (float)(intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0))/10;
            currentBatterytemp = batteryTemp +" "+ (char) 0x00B0 +"C";
        }
    };

    private String ClassSize_DataSize() {
        String class_folder_path = "/storage/self/primary/Download/data_balance/client1_train";
        File classfile = new File(class_folder_path);
        File[] files = classfile.listFiles();

        String class_data_size_str = "";
        int class_size = 0;
        for(int i = 0; i < files.length; i++) {
            if(files[i].isDirectory()) {
                class_size++;
                String class_name = files[i].getName();
                long class_length = new File(class_folder_path+"/"+class_name).listFiles().length;
                class_data_size_str += class_name + "-" + Long.toString(class_length) + "/";
            }
        }

        return Integer.toString(class_size) + "," + class_data_size_str;
    }

    private void wakeup() throws ExecutionException, InterruptedException {
        return_task = new WakeupClientTask().execute(grpc_addr, grpc_port).get();
    }

//    private void classmanagement() throws ExecutionException, InterruptedException {
//        AsyncTask<Void, Integer, String> management_task = new ClassManagement().execute();
//        return_task = management_task.get();
//        management_task.cancel(true);
//    }

    private void oninit() throws ExecutionException, InterruptedException {
        AsyncTask<Void, Integer, String> init_task = new OninitTask().execute();
        return_task = init_task.get();
        init_task.cancel(true);
        if(return_task == "NOT REGISTER CLIENT") {
//            Message msg = new Message();
//            Bundle bundle = new Bundle();
//            bundle.putString("data", "not register client");
//            msg.setData(bundle);
//            handler.sendMessage(msg);
            Log.d("INFO", "Not register client");

            // 예외상황 처리해야함
        }
    }

    private void update() throws ExecutionException, InterruptedException {
        AsyncTask<Void, Integer, String> update_task = new RequestUpdateTask().execute();
        return_task = update_task.get();
        update_task.cancel(true);
    }

    private void download() throws ExecutionException, InterruptedException {
        AsyncTask<Void, String, String> download_task = new DownloadFileFromURL().execute();
        return_task = download_task.get();
        download_task.cancel(true);
    }

    private void version() throws ExecutionException, InterruptedException {
        AsyncTask<Void, Integer, String> version_task = new CheckModelVersion().execute();
        return_task = version_task.get();
        version_task.cancel(true);
    }

    class DownloadFileFromURL extends AsyncTask<Void, String, String> {
        @Override
        protected String doInBackground(Void... voids) {
            int count;
            try {
//                URL url = new URL("http://" + address + ":8890/saved_model/MyMultiLayerNetwork.zip");
                URL url = new URL("http://" + address + ":8891/download");
                URLConnection conection = url.openConnection();
                conection.connect();

                // download the file
                InputStream input = new BufferedInputStream(url.openStream(),
                        10240);

                // Output stream
                OutputStream output = new FileOutputStream("/storage/self/primary/Download/save_model/MyMultiLayerNetwork.zip");

                byte data[] = new byte[10240];

//                long total = 0;

                while ((count = input.read(data)) != -1) {
                    // writing data to file
                    output.write(data, 0, count);
                }

                // flushing output
                output.flush();

                // closing streams
                output.close();
                input.close();
            } catch (Exception e) {
                Log.e("Error: ", e.getMessage());
            }

            return "finish download";
        }
    }

    class WakeupClientTask extends AsyncTask<String, Integer, String> {


        @Override
        protected String doInBackground(String... params) {
            String host = params[0];
            int port = Integer.parseInt(params[1]);

            String bsic = BatteryStatus_IsCharging();
            String bat_pct = bsic.split(",")[0];
            String charging = bsic.split(",")[1];
            Boolean wc = WifiConnected();
            String csds = ClassSize_DataSize();

            // client wake up!
//            try {
//                channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
//                GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
//                HelloRequest request = HelloRequest.newBuilder().setName(message).build();
//                HelloReply reply = stub.sayHello(request);
//                return reply.getMessage();
//            } catch (Exception e) {
//                StringWriter sw = new StringWriter();
//                PrintWriter pw = new PrintWriter(sw);
//                e.printStackTrace(pw);
//                pw.flush();
//                return String.format("Failed... : %n%s", sw);
//            }

            Request request = new Request.Builder()
                    .url("http://" + address + ":8891/client_wake_up?client_name=" + mUsername + "&battery_pct=" + bat_pct + "&is_charging=" + charging
                                                                        + "&wifi_conn=" + Boolean.toString(wc) + "&classsize_datasize=" + csds)
                    .build();

            try {
                response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body != null) {
                        responseString = body.string();
                    }
                }
                else
                    Log.d("INFO", "Connect Error Occurred");
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                response.body().close();
            }

            return responseString;
        }

        @Override
        protected void onPostExecute(String result) {
//            logArea.append("send wake up \n");
            Log.d("INFO", "send wake up");
            Log.d("INFO", "Response from the server : " + responseString);
        }
    }

//    class ClassManagement extends AsyncTask<Void, Integer, String> {
//        @Override
//        protected String doInBackground(Void... voids) {
//            try {
//                int class_length = response_json.getInt("class_size");
//                JSONArray class_list = response_json.getJSONArray("class_list"); // server로부터 받은 class list
//
//                JSONArray new_class_list = class_list;
//                String data_download_path = "/storage/self/primary/Download/data_balance/client1_train";
//                File file = new File(data_download_path);
//                File[] datafolder = file.listFiles();
//                int classsize = datafolder.length;
//
//                // server로부터 받은 class list와 client가 가지고 있는 class list 비교
//                for(int f = 0; f < class_length; f++) {
//                    for(File fil : datafolder) {
//                        if(!class_list.getString(f).endsWith(fil.getName())) {
//                            new_class_list.put(fil.getName());
//                            String create_path = data_download_path + "/" + fil.getName();
//                            File Folder = new File(create_path);
//                            if(!Folder.exists()) Folder.mkdir();
//                        }
//                    }
//                }
//
//                int trainSize = 0;
//                for(int i = 0; i < new_class_list.length(); i++) {
//                    File datafile = new File(data_download_path + "/" + new_class_list.getString(i));
//                    trainSize += datafile.listFiles().length;
//                }
//
//                Request request2 = new Request.Builder()
//                        .url("http://" + address + ":8891/class_management?client_name=" + mUsername + "&train_size=" + Integer.toString(trainSize)
//                                + "&class_size=" + Integer.toString(classsize) + "&class_list=" + new_class_list.toString())
//                        .build();
//
//                response = client.newCall(request2).execute();
//                if (response.isSuccessful()) {
//                    ResponseBody body = response.body();
//                    if (body != null) {
//                        responseString = body.string();
//                    }
//                }
//                else
//                    Log.d("INFO", "Connect Error Occurred");
//            } catch (IOException | JSONException e) {
//                e.printStackTrace();
//            } finally {
//                response.body().close();
//            }
//
//            return responseString;
//        }
//
//        @Override
//        protected void onPostExecute(String result) {
////            logArea.append("client ready \n");
//        }
//    }

    class OninitTask extends AsyncTask<Void, Integer, String> {
        @Override
        protected String doInBackground(Void... voids) {
            // client ready!
            String ModelName = Build.MODEL;

            String Output_file_path = "/storage/self/primary/Download/save_model";
            File check_file = new File(Output_file_path);

            if(!check_file.exists()) {
                boolean success = check_file.mkdir();
            }

            try {
                int class_length = response_json.getInt("class_size");
                JSONArray class_list = response_json.getJSONArray("class_list");

                String data_download_path = "/storage/self/primary/Download/data_balance/client1_train";
                String test_data_download_path = "/storage/self/primary/Download/data_balance/test";
                File file = new File(data_download_path);
                File[] datafolder = file.listFiles();
                File test_file = new File(test_data_download_path);
                File[] testdatafolder = test_file.listFiles();

                int classsize = datafolder.length;
                int trainSize = 0;
                for(int i = 0; i < classsize; i++) {
                    File datafile = new File(data_download_path + "/" + datafolder[i].getName());
                    trainSize += datafile.listFiles().length;
                }
                int testSize = 0;
                for(int i = 0; i < classsize; i++) {
                    File datafile = new File(test_data_download_path + "/" + testdatafolder[i].getName());
                    testSize += datafile.listFiles().length;
                }

                cnn_model = new CNNModel(trainSize, testSize);

//                File[] client_class = datafolder;
//                for(int f = 0; f < class_length; f++) {
//                    for(File fil : datafolder) {
//                        if(!class_list.getString(f).equals(fil)) {
//                            client_class[client_class.length] = new File(class_list.getString(f));
//                        }
//                    }
//                }

                Request request2 = new Request.Builder()
                        .url("http://" + address + ":8891/client_ready?client_name=" + mUsername + "&train_size=" + Integer.toString(trainSize)
                                + "&class_size=" + Integer.toString(classsize) + "&model_ver=" + model_version
                                + "&current_round=" + Integer.toString(currentRound) + "&android_name=" + ModelName)
                        .build();

                response = client.newCall(request2).execute();
                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body != null) {
                        responseString = body.string();
                    }
                }
                else
                    Log.d("INFO", "Connect Error Occurred");
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            } finally {
                response.body().close();
            }

            return responseString;
        }

        @Override
        protected void onPostExecute(String result) {
//            logArea.append("client ready \n");
            Message msg = new Message();
            Bundle bundle = new Bundle();
            bundle.putString("data", "success init");
            msg.setData(bundle);
            handler.sendMessage(msg);
            Log.d("INFO", "success init");
        }
    }

    class RequestUpdateTask extends AsyncTask<Void, Integer, String> {
        @Override
        protected String doInBackground(Void... voids) {
            String Train_time = "";
            // requeest update
            try {
                int mVersion = response_json.getInt("model_version");
                currentRound = response_json.getInt("current_round");
                double testLoss = response_json.getDouble("model_loss");
                double testAcc = response_json.getDouble("model_acc");
                String upload_url = response_json.getString("upload_url");
                String download_url = response_json.getString("model_url");
                String Output_file_path = "/storage/self/primary/Download/save_model";
                File check_file = new File(Output_file_path);

                if(mVersion != model_version || mVersion == 0 || !ModelBuildCheck) {
                    // model build
                    cnn_model.buildModel(Output_file_path);
                    ModelBuildCheck = true;
                }

                ui_testacc = testAcc;

                Train_time = trainOneRound(currentRound, upload_url, mVersion);

                // Memory Usage
                final Runtime runtime = Runtime.getRuntime();
                final long usedMemInMB=(runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
                final long maxHeapSizeInMB=runtime.maxMemory() / 1048576L;
                final long availHeapSizeInMB = maxHeapSizeInMB - usedMemInMB;

                currentBatterytemp = Temperature();
                StringBuffer cf = CpuFreq();

                Request update_client_request = new Request.Builder()
                        .url("http://" + address + ":8891/client_update?client_name=" + mUsername + "&current_round=" + Integer.toString(currentRound) + "&train_time=" + Train_time
                                                            + "&heapsize=" + Long.toString(availHeapSizeInMB) + "&temperature=" + currentBatterytemp + "&cpu_freq=" + cf
                                                            + "&acc_f1=" + acc_f1)
                        .build();

                response = client.newCall(update_client_request).execute();

                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body != null) {
                        responseString = body.string();
                    }
                }
                else
                    Log.d("INFO", "Connect Error Occurred");
            } catch (JSONException | IOException e) {
                e.printStackTrace();
            } finally {
                response.body().close();
            }
            return Train_time;
        }

        @Override
        protected void onPostExecute(String result) {
//            String acc_msg = "global acc: " + ui_testacc + "\n";
//            logArea.append(acc_msg);
//            stepText.setText(getString(R.string.current_round, currentRound));
//
//            Message msg = new Message();
//            Bundle bundle = new Bundle();
//            bundle.putString("data", result);
//            msg.setData(bundle);
//            handler.sendMessage(msg);
            Log.d("TRAINING TIME INFO", result);
        }
    }

    class CheckModelVersion extends AsyncTask<Void, Integer, String> {
        @Override
        protected String doInBackground(Void... voids) {
            Request update_client_request = new Request.Builder()
                    .url("http://" + address + ":8891/model_version?version_client=" + mUsername + "&model_ver=" + model_version)
                    .build();

            try {
                response = client.newCall(update_client_request).execute();

                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body != null) {
                        try {
                            responseString = body.string();
                            temp_modelstate = new JSONObject(responseString).getString("state");
                        } catch (IOException | JSONException e) {
                            e.printStackTrace();
                        }
                    }
                } else
                    Log.d("INFO", "Connect Error Occurred");
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                response.body().close();
            }

            return temp_modelstate;
        }

        @Override
        protected void onPostExecute(String result) {
            String modelVersion = "curent model version: " + result;
//            logArea.append(modelVersion);
//
//            Message msg = new Message();
//            Bundle bundle = new Bundle();
//            bundle.putString("data", modelVersion);
//            msg.setData(bundle);
//            handler.sendMessage(msg);
            Log.d("INFO", modelVersion);
        }
    }

    private String trainOneRound(int currentRound, String upload_url, int modelVersion) throws IOException {
        Log.d(TAG, "execute: train start!");
        long current_time = 0L;
        long train_time = 0L;
        try {
            current_time = System.currentTimeMillis();
            cnn_model.train(4);
            train_time = System.currentTimeMillis();
            Log.d("TRAINING TIME INFO", Long.toString((train_time - current_time)/1000));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "run: train finish!");

        // save trained model
        String AndroidModelPath = "/storage/self/primary/Download/save_weight/";

        cnn_model.saveSerializeModel("weight_" + mUsername + ".json");

//        Message msg = new Message();
//        Bundle bundle = new Bundle();
//        bundle.putString("data", "Complete model save");
//        msg.setData(bundle);
//        handler.sendMessage(msg);
        Log.d("MODEL INFO", "Complete model save!");
        // upload to server trained model
        cnn_model.uploadTo(AndroidModelPath + "weight_" + mUsername + ".json", upload_url, client);

        return Long.toString((train_time - current_time)/1000);
    }
}