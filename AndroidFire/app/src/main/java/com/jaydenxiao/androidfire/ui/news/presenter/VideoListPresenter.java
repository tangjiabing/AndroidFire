package com.jaydenxiao.androidfire.ui.news.presenter;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.jaydenxiao.androidfire.R;
import com.jaydenxiao.androidfire.app.AppConstant;
import com.jaydenxiao.androidfire.bean.VideoData;
import com.jaydenxiao.androidfire.ui.news.contract.VideosListContract;
import com.jaydenxiao.common.baserx.RxSubscriber;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rx.functions.Action1;

/**
 * des:
 * Created by xsf
 * on 2016.09.14:53
 */
public class VideoListPresenter extends VideosListContract.Presenter {

    @Override
    public void onStart() {
        super.onStart();
        //监听返回顶部动作
        mRxManage.on(AppConstant.NEWS_LIST_TO_TOP, new Action1<Object>() {
            @Override
            public void call(Object o) {
                mView.scrolltoTop();
            }
        });
    }

    /**
     * 获取视频列表请求
     *
     * @param type
     * @param startPage
     */
    @Override
    public void getVideosListDataRequest(String type, int startPage) {
        mRxManage.add(mModel.getVideosListData(type, startPage).subscribe(new RxSubscriber<List<VideoData>>(mContext, false) {
            @Override
            public void onStart() {
                super.onStart();
                mView.showLoading(mContext.getString(R.string.loading));
            }

            @Override
            protected void _onNext(List<VideoData> videoDatas) {
                mView.returnVideosListData(videoDatas);
                mView.stopLoading();
            }

            @Override
            protected void _onError(String message) {
                mView.showErrorTip(message);
            }
        }));
    }

    // ******************************************************************************************************
    // 替换Retrofit2获取视频列表的方式，因为它为产生403 Forbidden。

    private static ExecutorService mSingleTaskExecutor = Executors.newSingleThreadExecutor();
    private Gson mGson = new Gson();
    private Handler mUIHandler = new Handler(Looper.getMainLooper());

    public void getVideoListDatasRequest(final String type, int startPage) {
        mView.showLoading(mContext.getString(R.string.loading));
        final String url = "http://c.m.163.com/nc/video/list/" + type + "/n/" + startPage + "-10.html";
        mSingleTaskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                BufferedReader reader = null;
                try {
                    HttpURLConnection connection = ((HttpURLConnection) new URL(url).openConnection());
                    connection.setDoInput(true);
                    connection.setDoOutput(true);
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    final int code = connection.getResponseCode();
                    final String message = connection.getResponseMessage();
                    if (code == 200) {
                        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder builder = new StringBuilder();
                        int len = 2048;
                        char[] cbuf = new char[len];
                        while ((len = reader.read(cbuf)) != -1)
                            builder.append(cbuf, 0, len);
                        String content = builder.toString();
                        content = content.substring(("{\"" + type + "\":").length(), content.length() - 1);
                        final ArrayList<VideoData> videoDatas = jsonToList(content, VideoData.class);
                        mUIHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mView.returnVideosListData(videoDatas);
                                mView.stopLoading();
                            }
                        });
                    } else {
                        mUIHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mView.showErrorTip("code=" + code + "，message=" + message);
                            }
                        });
                    }
                } catch (IOException e) {
                    final String message = e.getMessage();
                    mUIHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mView.showErrorTip(message);
                        }
                    });
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    private <T> ArrayList<T> jsonToList(String json, Class<T> classOfT) {
        Type type = new TypeToken<ArrayList<JsonObject>>() {
        }.getType();
        ArrayList<JsonObject> jsonObjs = mGson.fromJson(json, type);

        ArrayList<T> listOfT = new ArrayList<T>();
        for (JsonObject jsonObj : jsonObjs)
            listOfT.add(mGson.fromJson(jsonObj, classOfT));

        return listOfT;
    }
}
