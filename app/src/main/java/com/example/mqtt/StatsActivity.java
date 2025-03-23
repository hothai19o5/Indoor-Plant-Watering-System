package com.example.mqtt;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;

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
import java.util.TimeZone;

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

    // Thêm ProgressBar và TextView
    private ProgressBar loadingProgressBar;
    private TextView noDataTextView;
    private CardView statsCardView;

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
        titleTextView = findViewById(R.id.statsTitleTextView);
        maxValueTextView = findViewById(R.id.maxValueTextView);
        minValueTextView = findViewById(R.id.minValueTextView);
        avgValueTextView = findViewById(R.id.avgValueTextView);

        // Ánh xạ ProgressBar và TextView
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        noDataTextView = findViewById(R.id.noDataTextView);
        statsCardView = findViewById(R.id.statsCardView); // Lấy CardView

        // Thay thế DatabaseHelper bằng FirebaseDataHelper
        firebaseDataHelper = new FirebaseDataHelper();

        // Thiết lập tiêu đề theo loại thống kê
        setupTitle();

        // Cấu hình biểu đồ
        setupChart();

        // Tải dữ liệu và hiển thị
        loadAndDisplayData();
    }

    private void setupTitle() {
        switch (statType) {
            case TYPE_TEMPERATURE:
                titleTextView.setText(R.string.temperature_statistics_for_the_last_1_hour);
                if(getSupportActionBar() != null)
                    getSupportActionBar().setTitle(R.string.temperature_statistics);
                break;
            case TYPE_HUMIDITY:
                titleTextView.setText(R.string.statistics_of_air_humidity_for_the_last_1_hour);
                if(getSupportActionBar() != null)
                    getSupportActionBar().setTitle(R.string.air_humidity_statistics);
                break;
            case TYPE_SOIL_MOISTURE:
                titleTextView.setText(R.string.soil_moisture_statistics_for_the_last_1_hour);
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
            private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            {
                // Thiết lập múi giờ UTC để không tự động chuyển đổi
                dateFormat.setTimeZone(TimeZone.getTimeZone("UTC")); 
            }

            @Override
            public String getFormattedValue(float value) {
                long millis = (long) value;  // Chuyển giây -> mili giây
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
        // Hiển thị ProgressBar, ẩn các view khác
        loadingProgressBar.setVisibility(View.VISIBLE);
        chart.setVisibility(View.GONE);
        noDataTextView.setVisibility(View.GONE);
        statsCardView.setVisibility(View.GONE); // Ẩn CardView

        firebaseDataHelper.getDataFromLast1Hour(historicalData -> {
            // Dữ liệu đã được tải xong
            loadingProgressBar.setVisibility(View.GONE);

            if (historicalData == null || historicalData.isEmpty()) {
                // Không có dữ liệu
                noDataTextView.setVisibility(View.VISIBLE);
                chart.setVisibility(View.GONE);
                statsCardView.setVisibility(View.GONE); // Ẩn CardView
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

                entries.add(new Entry(x, y));

                if (y > maxValue) maxValue = y;
                if (y < minValue) minValue = y;
                totalValue += y;
            }

            float avgValue = totalValue / historicalData.size();

            float finalMaxValue = maxValue;
            float finalMinValue = minValue;

            // Cập nhật UI trên UI Thread
            runOnUiThread(() -> {
                // Hiển thị biểu đồ và thông tin thống kê
                chart.setVisibility(View.VISIBLE);
                statsCardView.setVisibility(View.VISIBLE); // Hiện CardView
                noDataTextView.setVisibility(View.GONE);

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