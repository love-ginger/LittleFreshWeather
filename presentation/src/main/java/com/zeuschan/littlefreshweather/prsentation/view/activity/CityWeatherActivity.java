package com.zeuschan.littlefreshweather.prsentation.view.activity;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.zeuschan.littlefreshweather.common.util.Constants;
import com.zeuschan.littlefreshweather.common.util.DensityUtil;
import com.zeuschan.littlefreshweather.common.util.FileUtil;
import com.zeuschan.littlefreshweather.common.util.NetUtil;
import com.zeuschan.littlefreshweather.model.entity.WeatherEntity;
import com.zeuschan.littlefreshweather.prsentation.R;
import com.zeuschan.littlefreshweather.prsentation.presenter.CityWeatherPresenter;
import com.zeuschan.littlefreshweather.prsentation.service.WeatherNotificationService;
import com.zeuschan.littlefreshweather.prsentation.service.WeatherUpdateService;
import com.zeuschan.littlefreshweather.prsentation.view.CityWeatherView;
import com.zeuschan.littlefreshweather.prsentation.view.adapter.CityWeatherAdapter;

import java.util.Random;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import okhttp3.internal.tls.AndroidTrustRootIndex;
import rx.android.schedulers.AndroidSchedulers;

public class CityWeatherActivity extends BaseActivity implements CityWeatherView, View.OnClickListener {
    public static final String TAG = CityWeatherActivity.class.getSimpleName();
    public static final String CITY_ID = "city_id";
    public static final String WEATHER_UPDATE_ACTION = "com.zeuschan.littlefreshweather.prsentation.WEATHER_UPDATE";

    public static final int MSG_WEATHER_UPDATE = 0;

    private static final int RAIN_GEN_INTERVAL = 50;
    private static final int RAIN_NUM_H = 120;
    private static final int RAIN_NUM_M = 80;
    private static final int RAIN_NUM_L = 50;
    private static final int RAIN_SPEED_H = 500;
    private static final int RAIN_SPEED_M = 1000;
    private static final int RAIN_SPEED_L = 1500;
    private static final int RAIN_SPEED_OFFSET = 500;
    private int mRainIconId = R.drawable.raindrop_l;
    private int mSpecialWeatherNumRain = 0;
    private int mSpecialWeatherNumLimitRain;
    private int mSpecialWeatherSpeedLimitRain;


    private static final int SNOW_GEN_INTERVAL = 150;
    private static final int SNOW_SPEED_H = 4500;
    private static final int SNOW_SPEED_M = 4500;
    private static final int SNOW_SPEED_L = 4500;
    private int mSnowIconDarkId = R.drawable.snow_dark_l;
    private int mSnowIconLightId = R.drawable.snow_light_l;
    private int mSpecialWeatherNumSnow = 0;
    private int mSpecialWeatherNumLimitSnow;
    private int mSpecialWeatherSpeedLimitSnow;

    private static final int CLOUD_GEN_INTERVAL = 500;
    private static final int CLOUD_SPEED_H = 40000;
    private static final int CLOUD_SPEED_M = 60000;
    private static final int CLOUD_SPEED_L = 80000;
    private int mCloudIconFrontId = R.drawable.cloudy_day_2;
    private int mCloudIconBackId = R.drawable.cloudy_day_1;
    private int mSpecialWeatherNumCloud = 0;
    private int mSpecialWeatherNumLimitCloud;
    private int mSpecialWeatherSpeedLimitCloud;

    private static final int LIGHTNING_GEN_INTERVAL = 3000;
    private static final int LIGHTNING_SPEED_H = 300;
    private static final int LIGHTNING_SPEED_L = 1000;
    private int mSpecialWeatherNumLightning = 0;
    private int mSpecialWeatherNumLimitLightning;
    private int mSpecialWeatherSpeedLimitLightning;

    private int mAnimationType = 10;

    private Random mRandom = new Random();
    private CityWeatherPresenter mPresenter;
    private CityWeatherAdapter mCityWeatherAdapter;
    private LocalBroadcastManager mLocalBroadcastManager;
    private WeatherUpdateReceiver mWeatherUpdateReceiver;
    private UIHandler mHandler = new UIHandler();
    private Unbinder mUnbinder = null;

    @BindView(R.id.rl_loading_progress) RelativeLayout rlLoadingProgress;
    @BindView(R.id.rl_failed_retry) RelativeLayout rlFailedRetry;
    @BindView(R.id.rv_city_weather) RecyclerView rvCityWeather;
    @BindView(R.id.bt_failed_retry) Button btFailedRetry;
    @BindView(R.id.tv_city_weather_toolbar_title) TextView tvToolbarTitle;
    @BindView(R.id.ib_city_weather_toolbar_cities) ImageButton ibToolbarCities;
    @BindView(R.id.ib_city_weather_toolbar_menu) ImageButton ibToolbarMenu;
    @BindView(R.id.rl_city_weather_background_view) RelativeLayout rlBackgroundView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_city_weather);
        mUnbinder = ButterKnife.bind(this);

        Intent intent = getIntent();
        String cityId = intent.getStringExtra(CITY_ID);
        FileUtil.putStringToPreferences(getApplicationContext(), Constants.GLOBAL_SETTINGS, Constants.PRF_KEY_CITY_ID, cityId);

        mPresenter = new CityWeatherPresenter();
        mPresenter.attachView(this, cityId);

        rvCityWeather.setLayoutManager(new LinearLayoutManager(this));
        mCityWeatherAdapter = new CityWeatherAdapter(this);
        rvCityWeather.setAdapter(mCityWeatherAdapter);

        btFailedRetry.setOnClickListener(this);
        ibToolbarCities.setOnClickListener(this);
        ibToolbarMenu.setOnClickListener(this);

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        mWeatherUpdateReceiver = new WeatherUpdateReceiver();

        startServices();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        String cityId = intent.getStringExtra(CITY_ID);
        FileUtil.putStringToPreferences(getApplicationContext(), Constants.GLOBAL_SETTINGS, Constants.PRF_KEY_CITY_ID, cityId);
        mPresenter.setCityId(cityId);
        mPresenter.stop();
        mPresenter.start();

        startServices();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mPresenter.start();
        mPresenter.getBackgroundImage(rlBackgroundView, R.mipmap.night0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mLocalBroadcastManager.registerReceiver(mWeatherUpdateReceiver, new IntentFilter(WEATHER_UPDATE_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        mLocalBroadcastManager.unregisterReceiver(mWeatherUpdateReceiver);
        stopAnimation();
    }

    @Override
    public void renderCityWeather(WeatherEntity entity) {
        if (entity != null) {
            mCityWeatherAdapter.setWeatherEntity(entity);
            startAnimation(10);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mPresenter.stop();
    }

    @Override
    protected void clearMemory() {
        mPresenter.destroy();
        mHandler.removeMessages(MSG_WEATHER_UPDATE);
        mHandler.removeCallbacks(rainProc);
        mHandler.removeCallbacks(snowProc);
        mHandler.removeCallbacks(cloudProc);
        mHandler.removeCallbacks(lightningProc);
        mHandler = null;
        mPresenter = null;
        mUnbinder.unbind();
        mCityWeatherAdapter = null;
        mLocalBroadcastManager = null;
        mWeatherUpdateReceiver = null;
        setContentView(new FrameLayout(this));

//        Intent intent = new Intent(Intent.ACTION_MAIN);
//        intent.addCategory(Intent.CATEGORY_HOME);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        this.startActivity(intent);
        System.exit(0);
    }

    @Override
    public void setToolbarCity(String cityName) {
        tvToolbarTitle.setText(cityName);
    }

    @Override
    public void showLoading() {
        rlLoadingProgress.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideLoading() {
        rlLoadingProgress.setVisibility(View.GONE);
    }

    @Override
    public void showRetry() {
        rlFailedRetry.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideRetry() {
        rlFailedRetry.setVisibility(View.GONE);
    }

    @Override
    public void showContent() {
        rvCityWeather.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideContent() {
        rvCityWeather.setVisibility(View.GONE);
    }

    @Override
    public void navigateToCitiesActivity() {
        Intent intent = new Intent(this.getApplicationContext(), CitiesActivity.class);
        startActivity(intent);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.bt_failed_retry: {
                mPresenter.loadData();
            } break;
            case R.id.ib_city_weather_toolbar_cities: {
                navigateToCitiesActivity();
            } break;
            case R.id.ib_city_weather_toolbar_menu: {
                ++mAnimationType;
                if (mAnimationType >= 10) {
                    mAnimationType = 0;
                }
                startAnimation(mAnimationType);
            } break;
        }
    }

    private final class UIHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_WEATHER_UPDATE) {
                if (!NetUtil.isNetworkAvailable(getApplication()))
                    return;

                showError(getString(R.string.weather_updated));
                renderCityWeather((WeatherEntity)msg.getData().getParcelable(WeatherUpdateReceiver.WEATHER_ENTITY));
                return;
            }
            super.handleMessage(msg);
        }
    }

    public final class WeatherUpdateReceiver extends BroadcastReceiver {
        public static final String WEATHER_ENTITY = "weather_entity";
        @Override
        public void onReceive(Context context, Intent intent) {
            Message message = mHandler.obtainMessage(MSG_WEATHER_UPDATE);
            Bundle bundle = new Bundle();
            bundle.putParcelable(WEATHER_ENTITY, intent.getParcelableExtra(WEATHER_ENTITY));
            message.setData(bundle);
            message.sendToTarget();
        }
    }

    private void startServices() {
        Intent intent1 = new Intent(this.getApplicationContext(), WeatherUpdateService.class);
        startService(intent1);

        Intent intent2 = new Intent(this.getApplicationContext(), WeatherNotificationService.class);
        startService(intent2);
    }

    private Runnable rainProc = new Runnable() {
        @Override
        public void run() {
            ++mSpecialWeatherNumRain;
            if (mSpecialWeatherNumRain <= mSpecialWeatherNumLimitRain) {
                int screenHeight = DensityUtil.getScreenHeight(CityWeatherActivity.this);
                int screenWidth = DensityUtil.getScreenWidth(CityWeatherActivity.this);
                int fX = mRandom.nextInt(screenWidth << 1);
                int tX = fX - (int)(screenHeight * 0.58);

                ImageView imageView = new ImageView(CityWeatherActivity.this);
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageResource(mRainIconId);
                imageView.setRotation(30);
                RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                layoutParams.setMargins(fX, 0, 0, 0);
                rlBackgroundView.addView(imageView, layoutParams);

                PropertyValuesHolder holderY = PropertyValuesHolder.ofFloat("translationY", -100, screenHeight + 100);
                PropertyValuesHolder holderX = PropertyValuesHolder.ofFloat("translationX", fX, tX);
                ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(imageView, holderX, holderY);
                if ((mSpecialWeatherNumRain & 0x1) == 0) {
                    animator.setDuration(mSpecialWeatherSpeedLimitRain);
                } else {
                    animator.setDuration(mSpecialWeatherSpeedLimitRain + RAIN_SPEED_OFFSET);
                }
                animator.setRepeatMode(ObjectAnimator.RESTART);
                animator.setRepeatCount(ObjectAnimator.INFINITE);
                animator.setInterpolator(new LinearInterpolator());
                animator.start();

                mHandler.postDelayed(rainProc, RAIN_GEN_INTERVAL);
            }
        }
    };

    private Runnable snowProc = new Runnable() {
        @Override
        public void run() {
            ++mSpecialWeatherNumSnow;
            if (mSpecialWeatherNumSnow <= mSpecialWeatherNumLimitSnow) {
                int screenHeight = DensityUtil.getScreenHeight(CityWeatherActivity.this);
                int screenWidth = DensityUtil.getScreenWidth(CityWeatherActivity.this);
                int fX = mRandom.nextInt(screenWidth);

                ImageView imageView = new ImageView(CityWeatherActivity.this);
                imageView.setVisibility(View.VISIBLE);
                if ((mSpecialWeatherNumSnow & 0x1) == 0) {
                    imageView.setImageResource(mSnowIconLightId);
                } else {
                    imageView.setImageResource(mSnowIconDarkId);
                }
                RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                layoutParams.setMargins(fX, 0, 0, 0);
                rlBackgroundView.addView(imageView, layoutParams);

                ObjectAnimator animator = ObjectAnimator.ofFloat(imageView, "translationY", -100, screenHeight + 100);
                if ((mSpecialWeatherNumSnow & 0x1) == 0) {
                    animator.setDuration(mSpecialWeatherSpeedLimitSnow);
                } else {
                    animator.setDuration(mSpecialWeatherSpeedLimitSnow + RAIN_SPEED_OFFSET);
                }
                animator.setRepeatMode(ObjectAnimator.RESTART);
                animator.setRepeatCount(ObjectAnimator.INFINITE);
                animator.setInterpolator(new LinearInterpolator());
                animator.start();

                mHandler.postDelayed(snowProc, SNOW_GEN_INTERVAL);
            }
        }
    };

    private Runnable cloudProc = new Runnable() {
        @Override
        public void run() {
            ++mSpecialWeatherNumCloud;
            if (mSpecialWeatherNumCloud <= mSpecialWeatherNumLimitCloud) {
                int toolbarHeight = ibToolbarCities.getHeight();
                int screenWidth = DensityUtil.getScreenWidth(CityWeatherActivity.this);

                boolean isBack = (mSpecialWeatherNumCloud & 0x1) == 0;

                ImageView imageView = new ImageView(CityWeatherActivity.this);
                imageView.setVisibility(View.VISIBLE);
                if (isBack) {
                    imageView.setImageResource(mCloudIconBackId);
                } else {
                    imageView.setImageResource(mCloudIconFrontId);
                }
                RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                if (isBack) {
                    layoutParams.setMargins(0, toolbarHeight, 0, 0);
                } else {
                    layoutParams.setMargins(0, toolbarHeight + mRandom.nextInt(100), 0, 0);
                }
                rlBackgroundView.addView(imageView, layoutParams);

                ObjectAnimator animator = ObjectAnimator.ofFloat(imageView, "translationX", -(screenWidth * 2 / 3), screenWidth);
                if (isBack) {
                    animator.setDuration(CLOUD_SPEED_L);
                } else {
                    animator.setDuration(CLOUD_SPEED_H);
                }
                animator.setRepeatMode(ObjectAnimator.RESTART);
                animator.setRepeatCount(ObjectAnimator.INFINITE);
                animator.setInterpolator(new LinearInterpolator());
                animator.start();

                mHandler.postDelayed(cloudProc, CLOUD_GEN_INTERVAL);
            }
        }
    };

    private Runnable lightningProc = new Runnable() {
        @Override
        public void run() {
            ++mSpecialWeatherNumLightning;
            if (mSpecialWeatherNumLightning <= mSpecialWeatherNumLimitLightning) {
                int screenHeight = DensityUtil.getScreenHeight(CityWeatherActivity.this);
                int screenWidth = DensityUtil.getScreenWidth(CityWeatherActivity.this);

                ImageView imageView = new ImageView(CityWeatherActivity.this);
                imageView.setVisibility(View.VISIBLE);
                RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                if ((mSpecialWeatherNumLightning & 0x1) == 0) {
                    imageView.setImageResource(R.drawable.lightning_2);
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                    layoutParams.setMargins(0, mRandom.nextInt(screenHeight >> 2), screenWidth >> 2 + mRandom.nextInt(screenWidth >> 2), 0);
                } else {
                    imageView.setImageResource(R.drawable.lightning_1);
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                    layoutParams.setMargins(mRandom.nextInt(screenWidth >> 2), mRandom.nextInt(screenHeight >> 2), 0, 0);
                }
                rlBackgroundView.addView(imageView, layoutParams);

                ObjectAnimator animator = ObjectAnimator.ofFloat(imageView, "alpha", 0, 1);
                animator.setDuration(LIGHTNING_SPEED_H);
                animator.setRepeatMode(ObjectAnimator.REVERSE);
                animator.setRepeatCount(3);
                animator.setInterpolator(new AccelerateInterpolator());
                animator.start();

                mHandler.postDelayed(lightningProc, LIGHTNING_GEN_INTERVAL + mRandom.nextInt(LIGHTNING_GEN_INTERVAL));
            }
        }
    };

    private void stopAnimation() {
        for (int i = 0; i != rlBackgroundView.getChildCount(); ++i) {
            View view = rlBackgroundView.getChildAt(i);
            if (view != null) {
                view.clearAnimation();
            }
        }
        rlBackgroundView.removeAllViews();
        mSpecialWeatherNumRain = 0;
        mSpecialWeatherNumCloud = 0;
        mSpecialWeatherNumSnow = 0;
        mSpecialWeatherNumLightning = 0;
    }

    private void startAnimation(/*WeatherEntity entity*/int animationType) {
        stopAnimation();

        if (/*entity != null*/true) {
            // rain
            // snow
            // cloud
            // lightning
            if (animationType == 0) {
                mRainIconId = R.drawable.raindrop_l;
                mSpecialWeatherNumLimitRain = RAIN_NUM_L;
                mSpecialWeatherSpeedLimitRain = RAIN_SPEED_L;
                mHandler.postDelayed(rainProc, 20);
            } else if (animationType == 1) {
                mRainIconId = R.drawable.raindrop_m;
                mSpecialWeatherNumLimitRain = RAIN_NUM_M;
                mSpecialWeatherSpeedLimitRain = RAIN_SPEED_M;
                mHandler.postDelayed(rainProc, 20);
            } else if (animationType == 2) {
                mRainIconId = R.drawable.raindrop_h;
                mSpecialWeatherNumLimitRain = RAIN_NUM_H;
                mSpecialWeatherSpeedLimitRain = RAIN_SPEED_H;
                mHandler.postDelayed(rainProc, 20);
            } else if (animationType == 3) {
                mSnowIconLightId = R.drawable.snow_light_l;
                mSnowIconDarkId = R.drawable.snow_dark_l;
                mSpecialWeatherNumLimitSnow = RAIN_NUM_L;
                mSpecialWeatherSpeedLimitSnow = SNOW_SPEED_L;
                mHandler.postDelayed(snowProc, 20);
            } else if (animationType == 4) {
                mSnowIconLightId = R.drawable.snow_light_m;
                mSnowIconDarkId = R.drawable.snow_dark_m;
                mSpecialWeatherNumLimitSnow = RAIN_NUM_M;
                mSpecialWeatherSpeedLimitSnow = SNOW_SPEED_M;
                mHandler.postDelayed(snowProc, 20);
            } else if (animationType == 5) {
                mSnowIconLightId = R.drawable.snow_light_h;
                mSnowIconDarkId = R.drawable.snow_dark_h;
                mSpecialWeatherNumLimitSnow = RAIN_NUM_H;
                mSpecialWeatherSpeedLimitSnow = SNOW_SPEED_H;
                mHandler.postDelayed(snowProc, 20);
            } else if (animationType == 6) {
                mCloudIconBackId = R.drawable.cloudy_day_1;
                mCloudIconFrontId = R.drawable.cloudy_day_2;
                mSpecialWeatherNumLimitCloud = 2;
                mHandler.postDelayed(cloudProc, 20);
            } else if (animationType == 7) {
                mCloudIconBackId = R.drawable.cloudy_night1;
                mCloudIconFrontId = R.drawable.cloudy_night2;
                mSpecialWeatherNumLimitCloud = 2;
                mHandler.postDelayed(cloudProc, 20);
            } else if (animationType == 8) {
                mCloudIconBackId = R.drawable.fog_cloud_1;
                mCloudIconFrontId = R.drawable.fog_cloud_2;
                mSpecialWeatherNumLimitCloud = 2;
                mHandler.postDelayed(cloudProc, 20);
            } else if (animationType == 9) {
                mSpecialWeatherNumLimitLightning = 2;
                mHandler.postDelayed(lightningProc, 20);
            } else {

            }
        }
    }
}
