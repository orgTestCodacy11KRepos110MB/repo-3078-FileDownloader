/*
 * Copyright (c) 2015 LingoChamp Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liulishuo.filedownloader.download;

import android.os.Process;

import com.liulishuo.filedownloader.connection.FileDownloadConnection;
import com.liulishuo.filedownloader.exception.FileDownloadGiveUpRetryException;
import com.liulishuo.filedownloader.model.FileDownloadHeader;
import com.liulishuo.filedownloader.util.FileDownloadLog;
import com.liulishuo.filedownloader.util.FileDownloadUtils;

import java.io.IOException;

/**
 * The single download runnable used for establish one connection and fetch data from it.
 */
public class DownloadRunnable implements Runnable {

    private final ConnectTask connectTask;
    private final ProcessCallback callback;
    private final String path;
    private final boolean isWifiRequired;

    private FetchDataTask fetchDataTask;

    private volatile boolean paused;
    private final int downloadId;
    final int connectionIndex;

    private DownloadRunnable(int id, int connectionIndex, ConnectTask connectTask,
                             ProcessCallback callback, boolean isWifiRequired, String path) {
        this.downloadId = id;
        this.connectionIndex = connectionIndex;
        this.paused = false;
        this.callback = callback;
        this.path = path;
        this.connectTask = connectTask;
        this.isWifiRequired = isWifiRequired;
    }

    public void pause() {
        paused = true;
        if (fetchDataTask != null) fetchDataTask.pause();
    }

    public void discard() {
        pause();
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        FileDownloadConnection connection = null;
        do {

            try {
                if (paused) {
                    return;
                }

                connection = connectTask.connect();
                if (FileDownloadLog.NEED_LOG) {
                    FileDownloadLog.d(this, "the connection[%d] for %d, is connected %s",
                            connectionIndex, downloadId, connectTask.getProfile());
                }

                final FetchDataTask.Builder builder = new FetchDataTask.Builder();

                fetchDataTask = builder
                        .setDownloadId(downloadId)
                        .setConnectionIndex(connectionIndex)
                        .setCallback(callback)
                        .setHost(this)
                        .setWifiRequired(isWifiRequired)
                        .setConnection(connection)
                        .setConnectionProfile(this.connectTask.getProfile())
                        .setPath(path)
                        .build();


                fetchDataTask.run();
                break;
            } catch (IllegalAccessException | IOException | FileDownloadGiveUpRetryException e) {
                if (callback.isRetry(e)) {
                    callback.onRetry(e);
                } else {
                    callback.onError(e);
                    break;
                }
            } finally {
                if (connection != null) connection.ending();
            }
        } while (true);

    }

    public static class Builder {
        private final ConnectTask.Builder connectTaskBuilder = new ConnectTask.Builder();
        private ProcessCallback callback;
        private String path;
        private Boolean isWifiRequired;
        private Integer connectionIndex;


        public Builder setCallback(ProcessCallback callback) {
            this.callback = callback;
            return this;
        }

        public Builder setId(int id) {
            connectTaskBuilder.setDownloadId(id);
            return this;
        }

        public Builder setUrl(String url) {
            connectTaskBuilder.setUrl(url);
            return this;
        }

        public Builder setEtag(String etag) {
            connectTaskBuilder.setEtag(etag);
            return this;
        }

        public Builder setHeader(FileDownloadHeader header) {
            connectTaskBuilder.setHeader(header);
            return this;
        }

        public Builder setConnectionModel(ConnectionProfile model) {
            connectTaskBuilder.setConnectionProfile(model);
            return this;
        }

        public Builder setPath(String path) {
            this.path = path;
            return this;
        }

        public Builder setWifiRequired(boolean wifiRequired) {
            isWifiRequired = wifiRequired;
            return this;
        }

        public Builder setConnectionIndex(Integer connectionIndex) {
            this.connectionIndex = connectionIndex;
            return this;
        }

        public DownloadRunnable build() {
            if (callback == null || path == null || isWifiRequired == null || connectionIndex == null)
                throw new IllegalArgumentException(FileDownloadUtils.formatString("%s %s %B"
                        , callback, path, isWifiRequired));

            final ConnectTask connectTask = connectTaskBuilder.build();
            return new DownloadRunnable(connectTask.downloadId, connectionIndex, connectTask,
                    callback, isWifiRequired, path);
        }

    }
}