package com.gech.antisleepdetector;

import android.content.Context;
import android.graphics.Color;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;

/**
 * Fragment that displays sleep statistics gathered from both Camera and Arduino.
 */
public class ArduinoFragment extends Fragment implements SerialInputOutputManager.Listener {

    private BarChart barChart;
    private TextView statusText;
    private UsbSerialPort usbPort;
    private SerialInputOutputManager usbIoManager;
    
    private List<List<Integer>> sleepMinutes = new ArrayList<>(8);
    private String[] hourLabels = new String[8];
    private int[] windowHours = new int[8];

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_arduino, container, false);
        barChart = view.findViewById(R.id.sleep_chart);
        statusText = view.findViewById(R.id.status_text);
        
        initializeWindow();
        loadData();
        setupChart();
        connectUsb();
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh data when returning to this fragment (e.g. after using camera)
        initializeWindow();
        loadData();
        updateChart();
    }

    private void initializeWindow() {
        Calendar cal = Calendar.getInstance();
        int currentHour = cal.get(Calendar.HOUR_OF_DAY);
        
        sleepMinutes.clear();
        for (int i = 0; i < 8; i++) {
            sleepMinutes.add(new ArrayList<>());
            int hourInWindow = (currentHour - 7 + i + 24) % 24;
            windowHours[i] = hourInWindow;
            
            int displayHour = hourInWindow % 12;
            if (displayHour == 0) displayHour = 12;
            String amPm = (hourInWindow < 12) ? "AM" : "PM";
            hourLabels[i] = displayHour + " " + amPm;
        }
    }

    private void loadData() {
        if (getContext() == null) return;
        Set<String> timestamps = SleepDataManager.getSleepEvents(getContext());
        
        long twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        
        for (String tsStr : timestamps) {
            try {
                long ts = Long.parseLong(tsStr);
                if (ts > twentyFourHoursAgo) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(ts);
                    int hour = cal.get(Calendar.HOUR_OF_DAY);
                    int minute = cal.get(Calendar.MINUTE);
                    
                    for (int i = 0; i < 8; i++) {
                        if (windowHours[i] == hour) {
                            sleepMinutes.get(i).add(minute);
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void setupChart() {
        barChart.setBackgroundColor(Color.parseColor("#1A1A1A"));
        barChart.setNoDataText("Waiting for sleep data...");
        barChart.setNoDataTextColor(Color.GRAY);
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.LTGRAY);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(hourLabels));
        xAxis.setLabelCount(8);

        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setTextColor(Color.LTGRAY);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#333333"));
        leftAxis.setAxisMinimum(0f);

        barChart.getAxisRight().setEnabled(false);

        updateChart();

        barChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int index = (int) e.getX();
                if (index >= 0 && index < 8) {
                    List<Integer> minutes = sleepMinutes.get(index);
                    String details = "Time: " + hourLabels[index] + "\nSleep Count: " + minutes.size();
                    Toast.makeText(getContext(), details, Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onNothingSelected() {}
        });
    }

    private void updateChart() {
        List<BarEntry> entries = new ArrayList<>();
        int maxVal = 0;
        for (int i = 0; i < 8; i++) {
            int count = sleepMinutes.get(i).size();
            entries.add(new BarEntry(i, count));
            if (count > maxVal) maxVal = count;
        }

        barChart.getAxisLeft().setAxisMaximum(Math.max(5f, maxVal + 1f));

        BarDataSet dataSet = new BarDataSet(entries, "Sleep Events");
        dataSet.setColor(Color.parseColor("#66E0A3"));
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(Color.WHITE);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.5f);
        barChart.setData(barData);
        barChart.notifyDataSetChanged();
        barChart.invalidate();
    }

    private void connectUsb() {
        if (getActivity() == null) return;
        UsbManager manager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        if (manager == null) return;
        
        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (drivers.isEmpty()) {
            statusText.setText("Arduino Status: Not Found");
            return;
        }

        UsbSerialDriver driver = drivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) return;

        usbPort = driver.getPorts().get(0);
        try {
            usbPort.open(connection);
            usbPort.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            usbIoManager = new SerialInputOutputManager(usbPort, this);
            usbIoManager.start();
            statusText.setText("Arduino Status: Connected");
        } catch (IOException ignored) {}
    }

    @Override
    public void onNewData(byte[] data) {
        String msg = new String(data).trim();
        if (msg.contains("sleep")) {
            long now = System.currentTimeMillis();
            SleepDataManager.saveSleepEvent(requireContext(), now);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Calendar cal = Calendar.getInstance();
                    int h = cal.get(Calendar.HOUR_OF_DAY);
                    if (h != windowHours[7]) {
                        initializeWindow();
                        loadData();
                        updateChart();
                    } else {
                        sleepMinutes.get(7).add(cal.get(Calendar.MINUTE));
                        updateChart();
                    }
                });
            }
        }
    }

    @Override public void onRunError(Exception e) {}
    @Override public void onDestroy() {
        super.onDestroy();
        if (usbIoManager != null) usbIoManager.stop();
        try { if (usbPort != null) usbPort.close(); } catch (IOException ignored) {}
    }
}
