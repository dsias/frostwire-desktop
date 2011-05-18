package com.frostwire.bittorrent;

import java.io.File;

import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.tracker.client.TRTrackerScraperResponse;
import org.gudy.azureus2.core3.util.DisplayFormatters;


public class BTDownloaderImpl implements BTDownloader {

    private final DownloadManager _downloadManager;

    public BTDownloaderImpl(DownloadManager downloadManager) {
        _downloadManager = downloadManager;
    }

    public long getSize() {
        return _downloadManager.getSize();
    }

    public String getDisplayName() {
        return _downloadManager.getDisplayName();
    }

    public boolean isResumable() {
        return TorrentUtil.isStartable(_downloadManager);
    }

    public boolean isPausable() {
        return TorrentUtil.isStopable(_downloadManager);
    }

    public boolean isCompleted() {
        return _downloadManager.getAssumedComplete();
    }

    public int getState() {
        return _downloadManager.getState();
    }

    public void remove() {
        TorrentUtil.removeDownload(_downloadManager);
    }

    public void pause() {
        if (isPausable()) {
            TorrentUtil.stop(_downloadManager);
        }
    }

    public void resume() {
        if (isResumable()) {
            TorrentUtil.start(_downloadManager);
        }
    }

    public File getSaveLocation() {
        return _downloadManager.getSaveLocation();
    }

    public int getProgress() {
        return _downloadManager.getStats().getDownloadCompleted(true) / 10;
    }

    public String getStateString() {
        return DisplayFormatters.formatDownloadStatus(_downloadManager);
    }

    public long getBytesReceived() {
        return _downloadManager.getStats().getTotalGoodDataBytesReceived();
    }

    public double getDownloadSpeed() {
        return _downloadManager.getStats().getDataReceiveRate();
    }

    public double getUploadSpeed() {
        return _downloadManager.getStats().getDataSendRate();
    }

    public long getETA() {
        return _downloadManager.getStats().getETA();
    }

    public DownloadManager getDownloadManager() {
        return _downloadManager;
    }

    public String getPeersString() {
        long lTotalPeers = -1;
        long lConnectedPeers = 0;
        if (_downloadManager != null) {
            lConnectedPeers = _downloadManager.getNbPeers();

            if (lTotalPeers == -1) {
                TRTrackerScraperResponse response = _downloadManager.getTrackerScrapeResponse();
                if (response != null && response.isValid()) {
                    lTotalPeers = response.getPeers();
                }
            }
        }

        long totalPeers = lTotalPeers;
        if (totalPeers <= 0) {
            DownloadManager dm = _downloadManager;
            if (dm != null) {
                totalPeers = dm.getActivationCount();
            }
        }

        //        long value = lConnectedPeers * 10000000;
        //        if (totalPeers > 0)
        //            value = value + totalPeers;

        int state = _downloadManager.getState();
        boolean started = state == DownloadManager.STATE_SEEDING || state == DownloadManager.STATE_DOWNLOADING;
        boolean hasScrape = lTotalPeers >= 0;

        String tmp;
        if (started) {
            tmp = hasScrape ? (lConnectedPeers > lTotalPeers ? "%1" : "%1 " + "/" + " %2") : "%1";
        } else {
            tmp = hasScrape ? "%2" : "";
        }

        tmp = tmp.replaceAll("%1", String.valueOf(lConnectedPeers));
        tmp = tmp.replaceAll("%2", String.valueOf(totalPeers));

        return tmp;
    }

    @Override
    public String getSeedsString() {
        long lTotalSeeds = -1;
        long lTotalPeers = 0;
        long lConnectedSeeds = 0;
        DownloadManager dm = _downloadManager;
        if (dm != null) {
            lConnectedSeeds = dm.getNbSeeds();

            if (lTotalSeeds == -1) {
                TRTrackerScraperResponse response = dm.getTrackerScrapeResponse();
                if (response != null && response.isValid()) {
                    lTotalSeeds = response.getSeeds();
                    lTotalPeers = response.getPeers();
                }
            }
        }

        // Allows for 2097151 of each type (connected seeds, seeds, peers)
        long value = (lConnectedSeeds << 42);
        if (lTotalSeeds > 0)
            value += (lTotalSeeds << 21);
        if (lTotalPeers > 0)
            value += lTotalPeers;

        //boolean bCompleteTorrent = dm == null ? false : dm.getAssumedComplete();

        int state = dm.getState();
        boolean started = (state == DownloadManager.STATE_SEEDING || state == DownloadManager.STATE_DOWNLOADING);
        boolean hasScrape = lTotalSeeds >= 0;
        String tmp;

        if (started) {
            tmp = hasScrape ? (lConnectedSeeds > lTotalSeeds ? "%1" : "%1 " + "/" + " %2") : "%1";
        } else {
            tmp = hasScrape ? "%2" : "";
        }
        tmp = tmp.replaceAll("%1", String.valueOf(lConnectedSeeds));
        String param2 = "?";
        if (lTotalSeeds != -1) {
            param2 = String.valueOf(lTotalSeeds);
        }
        tmp = tmp.replaceAll("%2", param2);

        return tmp;
    }
}