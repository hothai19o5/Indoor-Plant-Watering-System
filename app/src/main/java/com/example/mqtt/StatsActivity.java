package com.example.mqtt;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatsActivity extends AppCompatActivity {

    public static final String EXTRA_STAT_TYPE = "statType";
    public static final int TYPE_TEMPERATURE = 1;
    public static final int TYPE_HUMIDITY = 2;
    public static final int TYPE_SOIL_MOISTURE = 3;

    private LineChart chart;
    private TextView titleTextView;
    private TextView maxValueTextView;
    private TextView minValueTextView;
    private TextView avgValueTextView;
    private FirebaseDataHelper firebaseDataHelper;
    private int statType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Luôn ở chế độ sáng
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        setContentView(R.layout.activity_stats);

        // Lấy loại thống kê từ intent
        statType = getIntent().getIntExtra(EXTRA_STAT_TYPE, TYPE_TEMPERATURE);

        // Khởi tạo các view
        chart = findViewById(R.id.statsChart);
        Log.d("StatsActivity", "Chart initialized with ID: " + chart.getId());
        titleTextView = findViewById(R.id.statsTitleTextView);
        maxValueTextView = findViewById(R.id.maxValueTextView);
        minValueTextView = findViewById(R.id.minValueTextView);
        avgValueTextView = findViewById(R.id.avgValueTextView);

        // Thay thế DatabaseHelper bằng FirebaseDataHelper
        firebaseDataHelper = new FirebaseDataHelper();
        Log.d("StatsActivity", "FirebaseDataHelper initialized");

        // Thiết lập tiêu đề theo loại thống kê
        setupTitle();
        Log.d("StatsActivity", "Title set up");

        // Cấu hình biểu đồ
        setupChart();
        Log.d("StatsActivity", "Chart set up");

        // Tải dữ liệu và hiển thị
        loadAndDisplayData();
        Log.d("StatsActivity", "Data loaded and displayed");
    }

    private void setupTitle() {
        switch (statType) {
            case TYPE_TEMPERATURE:
                titleTextView.setText(R.string.temperature_statistics_for_the_last_3_days);
                if(getSupportActionBar() != null)
                    getSupportActionBar().setTitle("Temperature statistics");
                break;
            case TYPE_HUMIDITY:
                titleTextView.setText(R.string.statistics_of_air_humidity_for_the_last_3_days);
                if(getSupportActionBar() != null)
                    getSupportActionBar().setTitle(R.string.air_humidity_statistics);
                break;
            case TYPE_SOIL_MOISTURE:
                titleTextView.setText(R.string.soil_moisture_statistics_for_the_last_3_days);
                if(getSupportActionBar() != null)
                    getSupportActionBar().setTitle(R.string.soil_moisture_statistics);
                break;
        }
    }

    private void setupChart() {
        // Cấu hình cơ bản cho biểu đồ
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);

        // Cấu hình trục X
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(45f);
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

            @Override
            public String getFormattedValue(float value) {
                long millis = (long) value;
                return dateFormat.format(new Date(millis));
            }
        });

        // Cấu hình trục Y
        YAxis leftAxis = chart.getAxisLeft();
        switch (statType) {
            case TYPE_TEMPERATURE:
                leftAxis.setAxisMinimum(0f);
                leftAxis.setAxisMaximum(50f);
                break;
            case TYPE_HUMIDITY:
            case TYPE_SOIL_MOISTURE:
                leftAxis.setAxisMinimum(0f);
                leftAxis.setAxisMaximum(100f);
                break;
        }

        // Tắt trục Y bên phải
        chart.getAxisRight().setEnabled(false);

        // Hiện chú thích
        Legend legend = chart.getLegend();
        legend.setForm(Legend.LegendForm.LINE);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setTextSize(12f);
    }

    private void loadAndDisplayData() {
        firebaseDataHelper.getDataFromLast3Days(historicalData -> {
            if (historicalData == null || historicalData.isEmpty()) {
                Log.d("StatsActivity", "No data available");
                chart.setNoDataText(getString(R.string.no_data_for_the_last_3_days));
                return;
            }

            List<Entry> entries = new ArrayList<>();
            float maxValue = Float.MIN_VALUE;
            float minValue = Float.MAX_VALUE;
            float totalValue = 0f;

            for (SensorDataRecord data : historicalData) {
                float x = data.getTimestamp();
                float y = 0f;

                switch (statType) {
                    case TYPE_TEMPERATURE:
                        y = data.getTemperature();
                        break;
                    case TYPE_HUMIDITY:
                        y = data.getHumidity();
                        break;
                    case TYPE_SOIL_MOISTURE:
                        y = data.getSoilMoisture();
                        break;
                }

                Log.d("StatsActivity", "Adding entry: x=" + x + ", y=" + y);

                entries.add(new Entry(x, y));

                if (y > maxValue) maxValue = y;
                if (y < minValue) minValue = y;
                totalValue += y;
            }

            float avgValue = totalValue / historicalData.size();

            float finalMaxValue = maxValue;
            float finalMinValue = minValue;
            runOnUiThread(() -> {
                displayStatistics(finalMaxValue, finalMinValue, avgValue);
                LineDataSet dataSet = createDataSet(entries);
                LineData lineData = new LineData(dataSet);
                chart.setData(lineData);
                chart.invalidate();
            });
        });
    }

    private LineDataSet createDataSet(List<Entry> entries) {
        LineDataSet dataSet;
        String label;
        int color;

        switch (statType) {
            case TYPE_TEMPERATURE:
                label = getString(R.string.temperature_c);
                color = Color.RED;
                break;
            case TYPE_HUMIDITY:
                label = getString(R.string.air_humidity);
                color = Color.BLUE;
                break;
            default: // TYPE_SOIL_MOISTURE
                label = getString(R.string.soil_moisture);
                color = Color.GREEN;
                break;
        }

        dataSet = new LineDataSet(entries, label);
        dataSet.setColor(color);
        dataSet.setCircleColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextSize(9f);
        dataSet.setDrawValues(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(color);
        dataSet.setFillAlpha(30);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawCircles(false);

        return dataSet;
    }

    private void displayStatistics(float maxValue, float minValue, float avgValue) {
        String unit;
        if (statType == TYPE_TEMPERATURE) {
            unit = "°C";
        } else {
            unit = "%";
        }

        maxValueTextView.setText(String.format(Locale.getDefault(), "%.1f %s", maxValue, unit));
        minValueTextView.setText(String.format(Locale.getDefault(), "%.1f %s", minValue, unit));
        avgValueTextView.setText(String.format(Locale.getDefault(), "%.1f %s", avgValue, unit));
    }
}