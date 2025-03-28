package com.example.mqtt.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.mqtt.FirebaseDataHelper;
import com.example.mqtt.MainActivity;
import com.example.mqtt.R;
import com.example.mqtt.SensorData;
import com.example.mqtt.SensorDataRecord;
import com.github.mikephil.charting.charts.LineChart;
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

public class TemperatureFragment extends Fragment implements MainActivity.DataUpdateListener {
    private static final String TAG = "TemperatureFragment";
    // Khai báo các thành phần UI
    private LineChart chart;
    private ProgressBar loadingProgressBar;
    private TextView noDataTextView;
    private CardView statsCardView;
    private TextView maxValueTextView;
    private TextView minValueTextView;
    private TextView avgValueTextView;
    private FirebaseDataHelper firebaseDataHelper;
    private List<Entry> entries = new ArrayList<>();
    private long startTime = System.currentTimeMillis();
    private LineDataSet dataSet;
    private final Context context;
    public long firebaseIntervalMillis;

    public TemperatureFragment(Context context) {
        this.context = context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_temperature_page, container, false);

        // Lấy firebase interval từ SharedPreferences
        String PREFS_NAME = "AppSettings";
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String KEY_FIREBASE_INTERVAL = "firebaseInterval";
        firebaseIntervalMillis = sharedPreferences.getLong(KEY_FIREBASE_INTERVAL, 300000); // Mặc định 5 phút nếu không tìm thấy

        // Ánh xạ các thành phần giao diện
        chart = view.findViewById(R.id.TemperatureStatsChart);
        loadingProgressBar = view.findViewById(R.id.TemperatureLoadingProgressBar);
        noDataTextView = view.findViewById(R.id.TemperatureNoDataTextView);
        statsCardView = view.findViewById(R.id.TemperatureStatsCardView);
        maxValueTextView = view.findViewById(R.id.TemperatureMaxValueTextView);
        minValueTextView = view.findViewById(R.id.TemperatureMinValueTextView);
        avgValueTextView = view.findViewById(R.id.TemperatureAvgValueTextView);

        // Khởi tạo FirebaseDataHelper
        firebaseDataHelper = new FirebaseDataHelper(getContext());

        // Thiết lập biểu đồ
        setupChart();

        // Tải dữ liệu lịch sử độ ẩm từ Firebase
        loadHistoricalTemperatureData();

        return view;
    }

    private void setupChart() {

        // Tắt animation để tránh vấn đề khi update dữ liệu
        chart.animateX(0);
        chart.animateY(0);

        // Cấu hình chung cho biểu đồ
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(true);
        chart.setAutoScaleMinMaxEnabled(true); // Tự động điều chỉnh trục

        // Thiết lập không đổi kích thước khi zoom - điều này giúp giữ điểm dữ liệu
        chart.setKeepPositionOnRotation(true);
        chart.setDoubleTapToZoomEnabled(true);
        chart.setHighlightPerDragEnabled(false);  // Tắt highlight khi kéo để tránh lỗi hiển thị

        // Cấu hình trục X
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelCount(5, true);  // Giữ số nhãn cố định
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat mFormat = new SimpleDateFormat("HH:mm dd:MM", Locale.getDefault());

            @Override
            public String getFormattedValue(float value) {
                // Chuyển đổi giá trị float (số giây) sang thời gian thực
                long millis = startTime + (long) value * 1000;
                return mFormat.format(new Date(millis));
            }
        });

        // Cấu hình trục Y bên trái
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(50f); // Nhiệt độ từ 0-50
        leftAxis.setDrawZeroLine(true); // Hiển thị đường 0
        leftAxis.setLabelCount(6, true); // Hiển thị 6 nhãn cố định

        // Tắt trục Y bên phải
        chart.getAxisRight().setEnabled(false);

        // Hiển thị chú thích
        chart.getLegend().setEnabled(true);
        chart.setExtraOffsets(10, 10, 10, 15); // Thêm offset để đảm bảo nhãn được hiển thị đầy đủ

        // Khởi tạo LineDataSet và LineData trống ban đầu
        dataSet = new LineDataSet(new ArrayList<>(), "Temperature (℃)");
        styleDataSet(dataSet);

        LineData data = new LineData(dataSet);
        chart.setData(data);
    }

    private void styleDataSet(LineDataSet dataSet) {
        dataSet.setColor(Color.RED);
        dataSet.setDrawCircles(false);
        dataSet.setCircleRadius(2f);
        dataSet.setCircleColor(Color.RED);
        dataSet.setLineWidth(2f);
        dataSet.setDrawValues(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.RED);
        dataSet.setFillAlpha(50);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawHorizontalHighlightIndicator(false);
    }

    private void showChart() {
        loadingProgressBar.setVisibility(View.GONE);
        chart.setVisibility(View.VISIBLE);
        noDataTextView.setVisibility(View.GONE);
        statsCardView.setVisibility(View.VISIBLE);
    }

    private void showNoData() {
        loadingProgressBar.setVisibility(View.GONE);
        chart.setVisibility(View.GONE);
        noDataTextView.setVisibility(View.VISIBLE);
        statsCardView.setVisibility(View.GONE);
    }

    private void loadHistoricalTemperatureData() {
        firebaseDataHelper.getDataFromInHistory(historicalData -> {

            if (historicalData != null && !historicalData.isEmpty()) {
                entries.clear();
                startTime = historicalData.get(0).getTimestamp();

                // Biến để tính giá trị thống kê
                float maxTemperature = Float.MIN_VALUE;
                float minTemperature = Float.MAX_VALUE;
                float totalTemperature = 0;

                for (SensorDataRecord data : historicalData) {
                    // X là số giây kể từ thời điểm bắt đầu
                    float x = (data.getTimestamp() -  startTime)/1000f;
                    float temperatureValue = data.getTemperature();

                    entries.add(new Entry(x, temperatureValue));

                    // Cập nhật giá trị thống kê
                    maxTemperature = Math.max(maxTemperature, temperatureValue);
                    minTemperature = Math.min(minTemperature, temperatureValue);
                    totalTemperature += temperatureValue;

                }

                // Tính giá trị trung bình
                final float avgTemperature = totalTemperature / historicalData.size();

                // Cập nhật UI
                if (getActivity() != null) {
                    float finalMaxTemperature = maxTemperature;
                    float finalMinTemperature = minTemperature;
                    getActivity().runOnUiThread(() -> {
                        maxValueTextView.setText(String.format(Locale.getDefault(), "%.1f℃", finalMaxTemperature));
                        minValueTextView.setText(String.format(Locale.getDefault(), "%.1f℃", finalMinTemperature));
                        avgValueTextView.setText(String.format(Locale.getDefault(), "%.1f℃", avgTemperature));

                        updateChartWithNewData();
                        showChart();
                    });
                }
            } else {
                Log.w(TAG, "Không có dữ liệu lịch sử độ ẩm");
                if (getActivity() != null) {
                    getActivity().runOnUiThread(this::showNoData);
                }
            }
        });
    }

    private void updateChartWithNewData() {
        if (getActivity() == null || entries.isEmpty()) {
            Log.w(TAG, "Không thể cập nhật biểu đồ: activity = null hoặc không có dữ liệu");
            return;
        }

        // Cách 1: Tạo mới LineDataSet và LineData
        dataSet = new LineDataSet(new ArrayList<>(entries), "Temperature (℃)");
        styleDataSet(dataSet);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.notifyDataSetChanged();
        chart.invalidate(); // Vẽ lại biểu đồ

        // Thiết lập lại phạm vi hiển thị của đồ thị
        chart.setVisibleXRangeMaximum(firebaseIntervalMillis); // 5 phút

        Log.d(TAG, "Biểu đồ đã được cập nhật với " + entries.size() + " điểm dữ liệu");
    }

    private void updateChartWithSinglePoint(Entry newEntry) {
        if (getActivity() == null) {
            return;
        }

        // Thêm điểm mới vào danh sách
        entries.add(newEntry);

        // Giới hạn số điểm dữ liệu
        if (entries.size() > firebaseIntervalMillis/3000) {
            entries.remove(0);
        }

        // Cập nhật dữ liệu vào dataSet
        dataSet.setValues(new ArrayList<>(entries));

        // Thông báo cho biểu đồ biết dữ liệu đã thay đổi
        chart.getData().notifyDataChanged();
        chart.notifyDataSetChanged();

        // Thiết lập lại phạm vi hiển thị của đồ thị
        chart.setVisibleXRangeMaximum(firebaseIntervalMillis); // 5 phút

        chart.invalidate(); // Vẽ lại biểu đồ
    }

    @Override
    public void onDataUpdate(SensorData sensorData) {
        if (sensorData != null && getActivity() != null && chart.getData() != null) {

            getActivity().runOnUiThread(() -> {
                // Thêm điểm mới vào biểu đồ (nếu đã có dữ liệu lịch sử)
                if (!entries.isEmpty()) {
                    float temperatureValue = (float)sensorData.getTemperature();
                    float x = (sensorData.getTimestamp()*1000 - startTime)/1000f;

                    // Tạo entry mới
                    Entry newEntry = new Entry(x, temperatureValue);

                    // Cập nhật đồ thị với điểm dữ liệu mới
                    updateChartWithSinglePoint(newEntry);

                    // Tính lại các giá trị thống kê
                    float maxTemperature = Float.MIN_VALUE;
                    float minTemperature = Float.MAX_VALUE;
                    float totalTemperature = 0;

                    for (Entry entry : entries) {
                        float value = entry.getY();
                        maxTemperature = Math.max(maxTemperature, value);
                        minTemperature = Math.min(minTemperature, value);
                        totalTemperature += value;
                    }

                    float avgTemperature = totalTemperature / entries.size();

                    // Cập nhật các giá trị thống kê
                    maxValueTextView.setText(String.format(Locale.getDefault(), "%.1f%%", maxTemperature));
                    minValueTextView.setText(String.format(Locale.getDefault(), "%.1f%%", minTemperature));
                    avgValueTextView.setText(String.format(Locale.getDefault(), "%.1f%%", avgTemperature));
                } else {
                    // Nếu chưa có dữ liệu, tải lại dữ liệu lịch sử
                    loadHistoricalTemperatureData();
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Tải lại dữ liệu khi fragment trở lại
        loadHistoricalTemperatureData();
    }
}
