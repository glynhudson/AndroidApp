package org.emoncms.myapps;

import android.app.Fragment;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.formatter.XAxisValueFormatter;
import com.github.mikephil.charting.formatter.YAxisValueFormatter;
import com.github.mikephil.charting.utils.ViewPortHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

public class MyElectricMainFragment extends Fragment
{
    static final String TAG = "MyElectricMainFragment";
    static final String watt_default_feed_name = "use";
    static final String kwh_default_feed_name = "use_kwh";
    static final int dailyChartUpdateInterval = 60000;

    static String emoncms_url = "https://emoncms.org";
    static String emoncms_apikey = "892268eb10dd998c50f7cfbfc6f75f24";
    static String powerCostSymbol;
    static float powerCost = 85;
    static float powerScale;
    float dpWidth = 0;

    TextView txtPower;
    TextView txtUseToday;
    LineChart chart1;
    BarChart chart2;
    Button chart1_3h;
    Button chart1_6h;
    Button chart1_D;
    Button chart1_W;
    Button chart1_M;
    SwitchCompat costSwitch;
    Handler mHandler = new Handler();

    int wattFeedId = 119861;
    int kWhFeelId = 120884;
    long timezone = 0;
    double yesterdaysPowerUsage;
    double totalPowerUsage;
    int powerGraphLength = -6;
    boolean resetPowerGraph = false;
    long nextDailyChartUpdate = 0;

    ArrayList<String> chart1_labels;
    ArrayList<Double> chart1_values;
    ArrayList<String> chart2_labels;
    ArrayList<Double> chart2_values;
    int [] chart2_colors;

    double powerNow = 0;
    double powerToday = 0;

    boolean blnShowCost = false;

    Snackbar snackbar;

    private Runnable mGetFeedsRunner = new Runnable()
    {
        @Override
        public void run()
        {
            String url = String.format(getActivity().getResources().getConfiguration().locale, "%s/feed/list.json?apikey=%s", emoncms_url, emoncms_apikey);
            Log.i("EMONCMS:URL", "mGetFeedsRunner:"+url);

            JsonArrayRequest jsArrayRequest = new JsonArrayRequest
                    (url, new Response.Listener<JSONArray>()
                    {
                        @Override
                        public void onResponse(JSONArray response)
                        {
                            for (int i = 0; i < response.length(); i++)
                            {
                                JSONObject row;

                                try
                                {
                                    row = response.getJSONObject(i);

                                    if (wattFeedId == -1
                                            && watt_default_feed_name.equals(row.getString("name")))
                                        wattFeedId = row.getInt("id");

                                    if (kWhFeelId == -1
                                            && kwh_default_feed_name.equals(row.getString("name")))
                                        kWhFeelId = row.getInt("id");

                                    if (wattFeedId >= 0 && kWhFeelId >= 0) {
                                        mHandler.post(mGetPowerRunner);
                                        return;
                                    }
                                }
                                catch (JSONException e)
                                {
                                    e.printStackTrace();
                                }
                            }
                            snackbar.setText(R.string.me_not_configured_text).show();
                        }
                    },new Response.ErrorListener()
                    {

                        @Override
                        public void onErrorResponse(VolleyError error)
                        {
                            snackbar.setText(R.string.feed_download_error_message).show();
                            mHandler.postDelayed(mGetFeedsRunner, 5000);
                        }
                    });
            jsArrayRequest.setTag(TAG);
            HTTPClient.getInstance(getActivity()).addToRequestQueue(jsArrayRequest);
        }
    };

    private Runnable mGetPowerRunner = new Runnable()
    {
        @Override
        public void run()
        {
            String url = String.format(getActivity().getResources().getConfiguration().locale, "%s/feed/fetch.json?apikey=%s&ids=%d,%d", emoncms_url, emoncms_apikey, wattFeedId, kWhFeelId);
            Log.i("EMONCMS:URL", "mGetPowerRunner:"+url);
            JsonArrayRequest jsArrayRequest = new JsonArrayRequest
                    (url, new Response.Listener<JSONArray>()
                    {
                        @Override
                        public void onResponse(JSONArray response)
                        {
                            String watt_value = "";
                            String kwh_value = "";

                            if (response.length() == 2)
                            {
                                try
                                {
                                    watt_value = response.getString(0);
                                    kwh_value = response.getString(1);

                                    if (Utils.isNumeric(watt_value))
                                        powerNow = Float.parseFloat(watt_value);

                                    if (Utils.isNumeric(kwh_value))
                                        totalPowerUsage = Float.parseFloat(kwh_value) * powerScale;

                                    updateTextFields();

                                    if (snackbar.isShown())
                                        snackbar.dismiss();
                                }
                                catch (JSONException e)
                                {
                                    snackbar.setText(e.getMessage()).show();
                                }
                            } else {
                                snackbar.setText(R.string.invalid_number_of_responses).show();
                            }

                            if (!Utils.isNumeric(watt_value))
                                snackbar.setText(R.string.invalid_watt_feedid).show();
                            else if (!Utils.isNumeric(kwh_value))
                                snackbar.setText(R.string.invalid_kwh_feedid).show();
                            else
                            {
                                if (Calendar.getInstance().getTimeInMillis() > nextDailyChartUpdate)
                                {
                                    nextDailyChartUpdate = Calendar.getInstance().getTimeInMillis() + dailyChartUpdateInterval;
                                    mHandler.post(mGetUsageByDayRunner);
                                }
                                else
                                    mHandler.post(mGetPowerHistoryRunner);
                            }
                        }
                    }, new Response.ErrorListener()
                    {

                        @Override
                        public void onErrorResponse(VolleyError error)
                        {
                            snackbar.setText(R.string.connection_error)
                                .setDuration(Snackbar.LENGTH_INDEFINITE)
                                .show();
                            mHandler.postDelayed(mGetPowerRunner, 5000);
                        }
                    });

            jsArrayRequest.setTag(TAG);
            HTTPClient.getInstance(getActivity()).addToRequestQueue(jsArrayRequest);
        }
    };


    private Runnable mGetUsageByDayRunner = new Runnable()
    {
        @Override
        public void run()
        {
            int daysToDisplay =  Math.round(dpWidth / 52)-1;
            int interval = 86400;

            // New
//            long end = (long) Math.floor(Calendar.getInstance().getTimeInMillis()*0.001);
            // Old
            long end = (long) Math.floor(((Calendar.getInstance().getTimeInMillis()*0.001)+timezone)/interval)*interval;

            end -= timezone;
            long start = end - (interval * daysToDisplay);

            final long chart2EndTime = end * 1000;
            final long chart2StartTime = start * 1000;

            // New
//            String url = String.format(getActivity().getResources().getConfiguration().locale, "%s/feed/data.json?apikey=%s&id=%d&start=%d&end=%d&mode=daily", emoncms_url, emoncms_apikey, kWhFeelId, chart2StartTime, chart2EndTime);
            // Old
            String url = String.format("%s/feed/data.json?apikey=%s&id=%d&start=%d&end=%d&interval=86400&skipmissing=1&limitinterval=1", emoncms_url, emoncms_apikey, kWhFeelId, chart2StartTime, chart2EndTime);
            Log.i("EMONCMS:URL", "mDaysofWeekRunner:"+url);

            JsonArrayRequest jsArrayRequest = new JsonArrayRequest
                    (url, new Response.Listener<JSONArray>()
                    {

                        @Override
                        public void onResponse(JSONArray response)
                        {
                            List<Long> dates = new ArrayList<>();
                            List<Double> power = new ArrayList<>();
                            String dayOfWeekInitials[] = getResources().getStringArray(R.array.day_of_week_initials);
                            Calendar calendar = Calendar.getInstance();

                            for (int i = 0; i < response.length(); i++)
                            {
                                JSONArray row;

                                try
                                {
                                    row = response.getJSONArray(i);
                                    Long date = row.getLong(0);
                                    if (date <= chart2EndTime)
                                    {
                                        dates.add(date);
                                        power.add(row.getDouble(1) * powerScale);
                                    }
                                }
                                catch (JSONException e)
                                {
                                    e.printStackTrace();
                                }
                            }

                            chart2_values.clear();
                            chart2_labels.clear();
                            chart2_colors = new int[power.size()];

                            for (int i = 0; i < power.size() - 1; i++)
                            {
                                calendar.setTimeInMillis(dates.get(i));
                                chart2_labels.add(dayOfWeekInitials[calendar.get(Calendar.DAY_OF_WEEK)-1]);
                                chart2_values.add(power.get(i + 1) - power.get(i));

                                if (calendar.get(Calendar.DAY_OF_WEEK) == 1 ||
                                        calendar.get(Calendar.DAY_OF_WEEK) == 7)
                                    chart2_colors[i] = ContextCompat.getColor(getActivity(), R.color.chartBlueDark);
                                else
                                    chart2_colors[i] = ContextCompat.getColor(getActivity(), R.color.chartBlue);
                            }

                            if (power.size() > 0)
                            {
                                yesterdaysPowerUsage = power.get(power.size() - 1);
                                powerToday = totalPowerUsage - yesterdaysPowerUsage;
                                updateTextFields();

                                calendar.setTimeInMillis(dates.get(dates.size()-1));
                                chart2_labels.add(dayOfWeekInitials[calendar.get(Calendar.DAY_OF_WEEK)-1]);
                                chart2_values.add(powerToday);

                                if (calendar.get(Calendar.DAY_OF_WEEK) == 1 ||
                                        calendar.get(Calendar.DAY_OF_WEEK) == 7)
                                    chart2_colors[chart2_colors.length-1] = ContextCompat.getColor(getActivity(), R.color.chartBlueDark);
                                else
                                    chart2_colors[chart2_colors.length-1] = ContextCompat.getColor(getActivity(), R.color.chartBlue);
                            }

                            BarData bardata = chart2.getBarData();
                            bardata.getXVals().clear();
                            BarDataSet bdset = (BarDataSet) bardata.getDataSetByLabel("kWh", true);
                            bdset.clear();
                            bdset.setColors(chart2_colors);

                            for (int i = 0; i < chart2_values.size(); i++)
                            {
                                bardata.addEntry(new BarEntry(chart2_values.get(i).floatValue(), i), 0);
                                bardata.addXValue(chart2_labels.get(i));
                            }

                            bardata.notifyDataChanged();
                            chart2.notifyDataSetChanged();
                            chart2.invalidate();

                            if (snackbar.isShown())
                                snackbar.dismiss();

                            mHandler.post(mGetPowerHistoryRunner);
                        }
                    }, new Response.ErrorListener()
                    {

                        @Override
                        public void onErrorResponse(VolleyError error)
                        {
                            snackbar.setText(R.string.connection_error)
                                    .setDuration(Snackbar.LENGTH_INDEFINITE)
                                    .show();
                            mHandler.postDelayed(mGetUsageByDayRunner, 5000);
                        }
                    });

            jsArrayRequest.setTag(TAG);
            HTTPClient.getInstance(getActivity()).addToRequestQueue(jsArrayRequest);
        }
    };

    private Runnable mGetPowerHistoryRunner = new Runnable()
    {
        @Override
        public void run()
        {
            final long lastEntry;

            if (resetPowerGraph)
            {
                chart1_labels.clear();
                chart1_values.clear();
            }

            if (chart1_values.size() > 0)
                lastEntry = Long.parseLong(chart1_labels.get(chart1_values.size()-1));
            else
                lastEntry = 0;

            Calendar cal = Calendar.getInstance();
            long endTime = cal.getTimeInMillis();
            cal.add(Calendar.HOUR, powerGraphLength);
            long startTime = cal.getTimeInMillis();

            int npoints = 1500;
            final int interval = Math.round(((endTime - startTime)/1000) / npoints);

            if (lastEntry > startTime)
                startTime = lastEntry;

            String url = String.format(getActivity().getResources().getConfiguration().locale, "%s/feed/data.json?apikey=%s&id=%d&start=%d&end=%d&interval=%d&skipmissing=1&limitinterval=1", emoncms_url, emoncms_apikey, wattFeedId, startTime, endTime, interval);
            Log.i("EMONCMS:URL", "mGetPowerHistoryRunner:"+url);
            JsonArrayRequest jsArrayRequest = new JsonArrayRequest(url, new Response.Listener<JSONArray>()
            {
                @Override
                public void onResponse(JSONArray response)
                {
                    LineData data = chart1.getData();
                    LineDataSet dataset = (LineDataSet) data.getDataSetByLabel("watts", true);

                    for (int i = 0; i < response.length(); i++)
                    {
                        JSONArray row;
                        try
                        {
                            row = response.getJSONArray(i);
                            long time = Long.parseLong(row.getString(0));

                            if (lastEntry == 0)
                            {
                                chart1_labels.add(row.getString(0));
                                chart1_values.add(row.getDouble(1));
                            }
                            else if (time >= (lastEntry+(interval*1000)))
                            {
                                chart1_labels.remove(0);
                                chart1_values.remove(0);
                                chart1_labels.add(row.getString(0));
                                chart1_values.add(row.getDouble(1));
                            }
                        }
                        catch (JSONException e)
                        {
                            e.printStackTrace();
                        }
                    }

                    data.getXVals().clear();
                    dataset.clear();

                    for (int i = 0; i < chart1_values.size(); i++)
                    {
                        data.addXValue(chart1_labels.get(i));
                        data.addEntry(new Entry(chart1_values.get(i).floatValue(), i), 0);
                    }

                    if (resetPowerGraph)
                    {
                        resetPowerGraph = false;
                        chart1.fitScreen();
                    }

                    data.notifyDataChanged();
                    chart1.notifyDataSetChanged();
                    chart1.invalidate();

                    if (snackbar.isShown())
                        snackbar.dismiss();

                    mHandler.postDelayed(mGetPowerRunner, 10000);
                }
            }, new Response.ErrorListener()
            {

                @Override
                public void onErrorResponse(VolleyError error)
                {
                    snackbar.setText(R.string.connection_error)
                            .setDuration(Snackbar.LENGTH_INDEFINITE)
                            .show();
                    mHandler.postDelayed(mGetPowerHistoryRunner, 5000);
                }
            });

            jsArrayRequest.setTag(TAG);

            if (endTime > lastEntry+(interval*1000))
                HTTPClient.getInstance(getActivity()).addToRequestQueue(jsArrayRequest);
            else
                mHandler.postDelayed(mGetPowerRunner, 10000);
        }
    };

    private void updateTextFields()
    {
        if (blnShowCost)
        {
            txtPower.setText(String.format(getActivity().getResources().getConfiguration().locale, "%s%.2f/h", powerCostSymbol, (powerNow*0.001)*powerCost));
            txtUseToday.setText(String.format(getActivity().getResources().getConfiguration().locale, "%s%.2f", powerCostSymbol, powerToday*powerCost));
        }
        else
        {
            txtPower.setText(String.format(getActivity().getResources().getConfiguration().locale, "%.0fW", powerNow));
            txtUseToday.setText(String.format(getActivity().getResources().getConfiguration().locale, "%.1fkWh", powerToday));
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
        emoncms_url = sp.getBoolean(getString(R.string.setting_usessl), false) ? "https://" : "http://";
        emoncms_url += sp.getString(getString(R.string.setting_url), "emoncms.org");
        emoncms_apikey = sp.getString(getString(R.string.setting_apikey), null);
        wattFeedId = Integer.valueOf(sp.getString("myelectric_power_feed", "-1"));
        kWhFeelId = Integer.valueOf(sp.getString("myelectric_kwh_feed", "-1"));

        powerScale = Integer.valueOf(sp.getString("myelectric_escale", "0")) == 0 ? 1.0F : 0.001F;
        powerCost = Float.parseFloat(sp.getString("myelectric_unit_cost", "0"));
        powerCostSymbol = sp.getString("myelectric_cost_symbol", "£");

        chart1_labels = new ArrayList<>();
        chart1_values = new ArrayList<>();
        chart2_labels = new ArrayList<>();
        chart2_values = new ArrayList<>();

        try
        {
            if (powerCostSymbol.equals("0"))
                powerCostSymbol = Currency.getInstance(getActivity().getResources().getConfiguration().locale).getSymbol();
        }
        catch (IllegalArgumentException  e)
        {
            powerCostSymbol = "£";
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.me_fragment, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        View view = getView();

        if (view == null)
            throw new NullPointerException("getView returned null");

        view.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom)
            {
                if (v.getWidth() != 0)
                {
                    DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                    dpWidth = v.getWidth() / displayMetrics.density;
                }
            }
        });

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        dpWidth = displayMetrics.widthPixels / displayMetrics.density;

        snackbar = Snackbar.make(view, R.string.connection_error, Snackbar.LENGTH_INDEFINITE);
        View snackbar_view = snackbar.getView();
        snackbar_view.setBackgroundColor(Color.GRAY);
        TextView tv = (TextView) snackbar_view.findViewById(android.support.design.R.id.snackbar_text);
        tv.setTypeface(null, Typeface.BOLD);

        setHasOptionsMenu(true);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) actionBar.setTitle(R.string.me_title);

        timezone = (long) Math.floor((Calendar.getInstance().get(Calendar.ZONE_OFFSET) + Calendar.getInstance().get(Calendar.DST_OFFSET))*0.001);

        txtPower = (TextView) view.findViewById(R.id.txtPower);
        txtUseToday = (TextView) view.findViewById(R.id.txtUseToday);
        chart1_3h = (Button) view.findViewById(R.id.btnChart1_3H);
        chart1_6h = (Button) view.findViewById(R.id.btnChart1_6H);
        chart1_D = (Button) view.findViewById(R.id.btnChart1_D);
        chart1_W = (Button) view.findViewById(R.id.btnChart1_W);
        chart1_M = (Button) view.findViewById(R.id.btnChart1_M);

        chart1_3h.setOnClickListener(buttonListener);
        chart1_6h.setOnClickListener(buttonListener);
        chart1_D.setOnClickListener(buttonListener);
        chart1_W.setOnClickListener(buttonListener);
        chart1_M.setOnClickListener(buttonListener);

        chart1 = (LineChart) view.findViewById(R.id.chart1);
        chart1.setDrawGridBackground(false);
        chart1.getLegend().setEnabled(false);
        chart1.getAxisRight().setEnabled(false);
        chart1.setDescription("");
        chart1.setNoDataText("");
        chart1.setHardwareAccelerationEnabled(true);

        YAxis yAxis = chart1.getAxisLeft();
        yAxis.setEnabled(true);
        yAxis.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART);
        yAxis.setDrawTopYLabelEntry(false);
        yAxis.setDrawGridLines(false);
        yAxis.setDrawAxisLine(false);
        yAxis.setTextColor(ContextCompat.getColor(getActivity(), R.color.lightGrey));
        yAxis.setTextSize(getResources().getInteger(R.integer.chartDateTextSize));
        yAxis.setValueFormatter(new Chart1YAxisValueFormatter());

        XAxis xAxis = chart1.getXAxis();
        xAxis.setDrawAxisLine(false);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawLabels(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(ContextCompat.getColor(getActivity(), R.color.lightGrey));
        xAxis.setValueFormatter(new Chart1XAxisValueFormatter());
        xAxis.setSpaceBetweenLabels(0);
        xAxis.setTextSize(getResources().getInteger(R.integer.chartDateTextSize));

        LineDataSet chart1_dataset = new LineDataSet(null, "watts");
        chart1_dataset.setColor(ContextCompat.getColor(getActivity(), R.color.chartBlue));
        chart1_dataset.setValueTextColor(ContextCompat.getColor(getActivity(), R.color.lightGrey));
        chart1_dataset.setDrawCircles(false);
        chart1_dataset.setDrawFilled(true);
        chart1_dataset.setFillColor(ContextCompat.getColor(getActivity(), R.color.chartBlue));
        chart1_dataset.setDrawValues(false);
        chart1_dataset.setValueTextSize(ContextCompat.getColor(getActivity(), R.integer.chartValueTextSize));
        chart1_dataset.setHighlightEnabled(false);
        LineData chart1_linedata = new LineData();
        chart1_linedata.addDataSet(chart1_dataset);
        chart1.setData(chart1_linedata);

        chart2 = (BarChart) view.findViewById(R.id.chart2);
        chart2.setDrawGridBackground(false);
        chart2.getLegend().setEnabled(false);
        chart2.getAxisLeft().setEnabled(false);
        chart2.getAxisRight().setEnabled(false);
        chart2.setHardwareAccelerationEnabled(true);
        chart2.setDescription("");
        chart2.setNoDataText("");
        chart2.setTouchEnabled(false);
        chart2.setExtraBottomOffset(2);

        xAxis = chart2.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(ContextCompat.getColor(getActivity(), R.color.lightGrey));
        xAxis.setTextSize(getResources().getInteger(R.integer.chartValueTextSize));
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);

        ArrayList<BarEntry> entries = new ArrayList<>();
        BarData chart2_bardata = new BarData();
        BarDataSet chart2_dataset = new BarDataSet(entries, "kWh");
        chart2_dataset.setColor(ContextCompat.getColor(getActivity(), R.color.colorAccent));
        chart2_dataset.setValueTextColor(ContextCompat.getColor(getActivity(), R.color.lightGrey));
        chart2_dataset.setValueTextSize(getResources().getInteger(R.integer.chartValueTextSize));
        chart2_dataset.setValueFormatter(new Chart2ValueFormatter());
        chart2_bardata.addDataSet(chart2_dataset);
        chart2.setData(chart2_bardata);

        if (savedInstanceState != null)
        {
            blnShowCost = savedInstanceState.getBoolean("show_cost", false);
            powerGraphLength = savedInstanceState.getInt("power_graph_length", -6);
            powerNow = savedInstanceState.getDouble("power_now", 0);
            powerToday = savedInstanceState.getDouble("power_today", 0);
            wattFeedId = savedInstanceState.getInt("watt_feed_id", -1);
            kWhFeelId = savedInstanceState.getInt("kwh_feed_id", -1);
            chart2_colors = savedInstanceState.getIntArray("chart2_colors");

            updateTextFields();

            chart1_labels = savedInstanceState.getStringArrayList("chart1_labels");
            double saved_chart1_values[] = savedInstanceState.getDoubleArray("chart1_values");
            double saved_chart2_values[] = savedInstanceState.getDoubleArray("chart2_values");
            chart2_labels = savedInstanceState.getStringArrayList("chart2_labels");

            if (chart1_labels != null && saved_chart1_values != null
                    && chart1_labels.size() > 0 && saved_chart1_values.length > 0
                    && chart1_labels.size() == saved_chart1_values.length)
            {
                for (double saved_chart1_value : saved_chart1_values)
                    chart1_values.add(saved_chart1_value);

                for (int i = 0; i < chart1_values.size(); i++)
                {
                    chart1_linedata.addXValue(chart1_labels.get(i));
                    chart1_linedata.addEntry(new Entry(chart1_values.get(i).floatValue(), i), 0);
                }

                chart1_linedata.notifyDataChanged();
                chart1.notifyDataSetChanged();
                chart1.invalidate();
            }

            if (chart2_labels != null && saved_chart2_values != null
                    && chart2_labels.size() > 0 && saved_chart2_values.length > 0
                    && chart2_labels.size() == saved_chart2_values.length)
            {
                for (double saved_chart2_value : saved_chart2_values)
                    chart2_values.add(saved_chart2_value);

                try
                {
                    int daysToDisplay = Math.round(dpWidth / 52);
                    int a = 0;

                    if (chart2_values.size() > daysToDisplay)
                        a = chart2_values.size() - daysToDisplay;

                    int colors[] = new int[daysToDisplay];

                    for (int i = a; i < chart2_values.size(); i++)
                    {
                        chart2_bardata.addEntry(new BarEntry(chart2_values.get(i).floatValue(), i - a), 0);
                        chart2_bardata.addXValue(chart2_labels.get(i));
                        colors[i-a] = chart2_colors[i];
                    }

                    chart2_dataset.setColors(colors);
                    chart2_bardata.notifyDataChanged();
                    chart2.notifyDataSetChanged();
                    chart2.invalidate();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("show_cost", blnShowCost);
        outState.putInt("power_graph_length", powerGraphLength);
        outState.putDouble("power_now", powerNow);
        outState.putDouble("power_today", powerToday);
        outState.putInt("watt_feed_id", wattFeedId);
        outState.putInt("kwh_feed_id", kWhFeelId);
        outState.putIntArray("chart2_colors", chart2_colors);

        double[] values = new double[chart1_values.size()];

        for (int i = 0; i < chart1_labels.size(); i++)
            values[i] = chart1_values.get(i);

        outState.putStringArrayList("chart1_labels", chart1_labels);
        outState.putDoubleArray("chart1_values", values);

        values = new double[chart2_values.size()];

        for (int i = 0; i < chart2_labels.size(); i++)
            values[i] = chart2_values.get(i);

        outState.putStringArrayList("chart2_labels", chart2_labels);
        outState.putDoubleArray("chart2_values", values);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.me_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
        costSwitch = (SwitchCompat) MenuItemCompat.getActionView(menu.findItem(R.id.cost_switch));
        costSwitch.setOnCheckedChangeListener(checkedChangedListener);
        costSwitch.setChecked(blnShowCost);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            ((MainActivity) getActivity()).showFragment(MainActivity.MyAppViews.MyElectricSettingsView);
            return true;
        }
        else if (id == R.id.full_screen) {
            boolean fullScreen = ((MainActivity) getActivity()).setFullScreen();
            if (fullScreen)
                item.setIcon(R.drawable.ic_fullscreen_exit_white_24dp);
            else
                item.setIcon(R.drawable.ic_fullscreen_white_24dp);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (emoncms_apikey == null || emoncms_apikey.equals(""))
            snackbar.setText(R.string.server_not_configured).show();
        else if (wattFeedId == -1 || kWhFeelId == -1)
            mHandler.post(mGetFeedsRunner);
        else if (wattFeedId >= 0 && kWhFeelId >= 0)
        {
            snackbar.dismiss();
            mHandler.post(mGetPowerRunner);
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        snackbar.dismiss();
        HTTPClient.getInstance(getActivity()).cancellAll(TAG);
        mHandler.removeCallbacksAndMessages(null);
    }

    private CompoundButton.OnCheckedChangeListener checkedChangedListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            blnShowCost = isChecked;
            chart2.invalidate();
            updateTextFields();
        }
    };

    private OnClickListener buttonListener = new OnClickListener() {
        public void onClick(View v) {

            switch (v.getId())
            {
                case R.id.btnChart1_3H:
                    powerGraphLength = -3;
                    resetPowerGraph = true;
                    break;
                case R.id.btnChart1_6H:
                    powerGraphLength = -6;
                    resetPowerGraph = true;
                    break;
                case R.id.btnChart1_D:
                    powerGraphLength = -24;
                    resetPowerGraph = true;
                    break;
                case R.id.btnChart1_W:
                    powerGraphLength = -168; // 7 * 24
                    resetPowerGraph = true;
                    break;
                case R.id.btnChart1_M: // 4 Weeks
                    powerGraphLength = -720; // 30 * 24
                    resetPowerGraph = true;
                    break;
            }
            HTTPClient.getInstance(getActivity()).cancellAll(TAG);
            mHandler.removeCallbacksAndMessages(null);
            mHandler.post(mGetPowerHistoryRunner);
        }
    };

    public class Chart1XAxisValueFormatter implements XAxisValueFormatter
    {
        @Override
        public String getXValue(String original, int index, ViewPortHandler viewPortHandler)
        {
            DateFormat df = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(Long.parseLong(original));
            return (df.format(cal.getTime()));
        }
    }

    public class Chart1YAxisValueFormatter implements YAxisValueFormatter
    {
        private DecimalFormat mFormat;

        public Chart1YAxisValueFormatter () {
            mFormat = new DecimalFormat("###,###,##0"); // use one decimal
        }

        @Override
        public String getFormattedValue(float value, YAxis yAxis) {
            return mFormat.format(value);
        }
    }

    public class Chart2ValueFormatter implements ValueFormatter
    {

        private DecimalFormat mFormat;

        public Chart2ValueFormatter() {
            mFormat = new DecimalFormat("###,###,##0.0"); // use one decimal
            mFormat.setNegativePrefix("");
        }

        @Override
        public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {

            if (blnShowCost)
                value = value * powerCost;

            return mFormat.format(value);
        }
    }
}