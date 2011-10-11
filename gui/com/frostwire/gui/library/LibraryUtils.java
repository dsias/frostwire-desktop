package com.frostwire.gui.library;

import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.swing.JOptionPane;

import org.limewire.concurrent.ExecutorsHelper;
import org.limewire.util.FileUtils;
import org.limewire.util.FilenameUtils;
import org.limewire.util.StringUtils;

import com.frostwire.alexandria.Playlist;
import com.frostwire.alexandria.PlaylistItem;
import com.frostwire.alexandria.db.LibraryDatabase;
import com.frostwire.gui.bittorrent.TorrentUtil;
import com.frostwire.gui.library.LibraryPlaylistsTableTransferable.Item;
import com.frostwire.gui.player.AudioPlayer;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;

public class LibraryUtils {

    private static final ExecutorService executor;

    static {
        executor = ExecutorsHelper.newProcessingQueue("LibraryUtils-Executor");
    }
    
    private static void addPlaylistItem(Playlist playlist, File file, boolean starred) {
        addPlaylistItem(playlist, file, starred, -1);
    }

    private static void addPlaylistItem(Playlist playlist, File file, boolean starred, int index) {
        try {
            LibraryMediator.instance().getLibrarySearch().pushStatus(I18n.tr("Importing") + " " + file.getName());
            AudioMetaData mt = new AudioMetaData(file);
            PlaylistItem item = playlist.newItem(file.getAbsolutePath(), file.getName(), file.length(), FileUtils.getFileExtension(file), mt.getTitle(), mt.getDurationInSecs(), mt.getArtist(), mt.getAlbum(), "",// TODO: cover art path
                    mt.getBitrate(), mt.getComment(), mt.getGenre(), mt.getTrack(), mt.getYear(), starred);
            
            List<PlaylistItem> items = playlist.getItems();
            if (index != -1 && index <= items.size()) {
                items.add(index, item);
                item.save();
            } else {
                items.add(item);
                item.save();
                if (isPlaylistSelected(playlist)) {
                    LibraryPlaylistsTableMediator.instance().addUnsorted(item);
                }
            }
        } finally {
            LibraryMediator.instance().getLibrarySearch().revertStatus();
        }
    }

    public static String getSecondsInDDHHMMSS(int s) {
        if (s < 0) {
            s = 0;
        }

        StringBuilder result = new StringBuilder();

        String DD = "";
        String HH = "";
        String MM = "";
        String SS = "";

        //math
        int days = s / 86400;
        int r = s % 86400;

        int hours = r / 3600;
        r = s % 3600;
        int minutes = r / 60;
        int seconds = r % 60;

        //padding
        DD = String.valueOf(days);
        HH = (hours < 10) ? "0" + hours : String.valueOf(hours);
        MM = (minutes < 10) ? "0" + minutes : String.valueOf(minutes);
        SS = (seconds < 10) ? "0" + seconds : String.valueOf(seconds);

        //lazy formatting
        if (days > 0) {
            result.append(DD);
            result.append(" day");
            if (days > 1) {
                result.append("s");
            }
            return result.toString();
        }

        if (hours > 0) {
            result.append(HH);
            result.append(":");
        }

        result.append(MM);
        result.append(":");
        result.append(SS);

        return result.toString();
    }

    public static void createNewPlaylist(final List<? extends AbstractLibraryTableDataLine<?>> lines) {
        String playlistName = (String) JOptionPane.showInputDialog(GUIMediator.getAppFrame(), I18n.tr("Playlist name"), I18n.tr("Playlist name"), JOptionPane.PLAIN_MESSAGE, null, null, calculateName(lines));

        if (playlistName != null && playlistName.length() > 0) {
            final Playlist playlist = LibraryMediator.getLibrary().newPlaylist(playlistName, playlistName);
            playlist.save();
            LibraryMediator.instance().getLibraryPlaylists().addPlaylist(playlist);
            LibraryMediator.instance().getLibraryPlaylists().markBeginImport(playlist);
            Thread t = new Thread(new Runnable() {
                public void run() {
                    addToPlaylist(playlist, lines);
                    playlist.save();
                    asyncAddToPlaylistFinalizer(playlist);
                }
            }, "createNewPlaylist");
            t.setDaemon(true);
            t.start();
        }
    }

    public static void createNewPlaylist(File[] files) {
        createNewPlaylist(files, false);
    }

    public static void createNewPlaylist(final File[] files, final boolean starred) {

        final StringBuilder plBuilder = new StringBuilder();

        GUIMediator.safeInvokeAndWait(new Runnable() {

            @Override
            public void run() {
                String input = (String) JOptionPane.showInputDialog(GUIMediator.getAppFrame(), I18n.tr("Playlist name"), I18n.tr("Playlist name"), JOptionPane.PLAIN_MESSAGE, null, null, calculateName(files));
                if (!StringUtils.isNullOrEmpty(input, true)) {
                    plBuilder.append(input);
                }
            }
        });

        String playlistName = plBuilder.toString();

        if (playlistName != null && playlistName.length() > 0) {
            final Playlist playlist = LibraryMediator.getLibrary().newPlaylist(playlistName, playlistName);
            playlist.save();

            GUIMediator.safeInvokeLater(new Runnable() {

                @Override
                public void run() {
                    LibraryMediator.instance().getLibraryPlaylists().addPlaylist(playlist);
                    LibraryMediator.instance().getLibraryPlaylists().markBeginImport(playlist);
                }
            });

            Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        Set<File> ignore = TorrentUtil.getIgnorableFiles();
                        addToPlaylist(playlist, files, starred, ignore);
                        playlist.save();
                    } finally {
                        asyncAddToPlaylistFinalizer(playlist);
                    }
                }
            }, "createNewPlaylist");
            t.setDaemon(true);
            t.start();
        }
    }

    public static void createNewPlaylist(final PlaylistItem[] playlistItems) {
        createNewPlaylist(playlistItems, false);
    }

    public static void createNewPlaylist(final PlaylistItem[] playlistItems, boolean starred) {
        if (starred) {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    Playlist playlist = LibraryMediator.getLibrary().getStarredPlaylist();
                    addToPlaylist(playlist, playlistItems, true, -1);
                    GUIMediator.safeInvokeLater(new Runnable() {
                        public void run() {
                            DirectoryHolder dh = LibraryMediator.instance().getLibraryFiles().getSelectedDirectoryHolder();
                            if (dh instanceof StarredDirectoryHolder) {
                                LibraryMediator.instance().getLibraryFiles().refreshSelection();
                            } else {
                                LibraryMediator.instance().getLibraryFiles().selectStarred();
                            }
                        }
                    });
                }
            }, "createNewPlaylist");
            t.setDaemon(true);
            t.start();
        } else {
            String playlistName = (String) JOptionPane.showInputDialog(GUIMediator.getAppFrame(), I18n.tr("Playlist name"), I18n.tr("Playlist name"), JOptionPane.PLAIN_MESSAGE, null, null, calculateName(playlistItems));

            if (playlistName != null && playlistName.length() > 0) {
                final Playlist playlist = LibraryMediator.getLibrary().newPlaylist(playlistName, playlistName);

                Thread t = new Thread(new Runnable() {
                    public void run() {
                        try {
                            playlist.save();
                            addToPlaylist(playlist, playlistItems);
                            playlist.save();
                            GUIMediator.safeInvokeLater(new Runnable() {
                                public void run() {
                                    LibraryMediator.instance().getLibraryPlaylists().addPlaylist(playlist);
                                }
                            });
                        } finally {
                            asyncAddToPlaylistFinalizer(playlist);
                        }
                    }
                }, "createNewPlaylist");
                t.setDaemon(true);
                t.start();
            }
        }
    }

    public static void createNewPlaylist(File m3uFile) {
        createNewPlaylist(m3uFile, false);
    }

    public static void createNewPlaylist(File m3uFile, boolean starred) {
        try {
            List<File> files = M3UPlaylist.load(m3uFile.getAbsolutePath());
            createNewPlaylist(files.toArray(new File[0]), starred);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void asyncAddToPlaylist(final Playlist playlist, final List<? extends AbstractLibraryTableDataLine<?>> lines) {
        LibraryMediator.instance().getLibraryPlaylists().markBeginImport(playlist);
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    addToPlaylist(playlist, lines);
                } finally {
                    asyncAddToPlaylistFinalizer(playlist);
                }
            }
        }, "asyncAddToPlaylist");
        t.setDaemon(true);
        t.start();
    }
    
    public static void asyncAddToPlaylist(Playlist playlist, File[] files) {
        asyncAddToPlaylist(playlist, files, -1);
    }

    public static void asyncAddToPlaylist(final Playlist playlist, final File[] files, final int index) {
        LibraryMediator.instance().getLibraryPlaylists().markBeginImport(playlist);
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    Set<File> ignore = TorrentUtil.getIgnorableFiles();
                    addToPlaylist(playlist, files, false, index, ignore);
                    playlist.save();
                } finally {
                    asyncAddToPlaylistFinalizer(playlist);
                }
            }
        }, "asyncAddToPlaylist");
        t.setDaemon(true);
        t.start();
    }

    private static void asyncAddToPlaylistFinalizer(final Playlist playlist) {
        GUIMediator.safeInvokeLater(new Runnable() {
            public void run() {
                LibraryMediator.instance().getLibraryPlaylists().markEndImport(playlist);
                LibraryMediator.instance().getLibraryPlaylists().refreshSelection();
                LibraryMediator.instance().getLibraryPlaylists().selectPlaylist(playlist);
            }
        });
    }
    
    public static void asyncAddToPlaylist(Playlist playlist, PlaylistItem[] playlistItems) {
        asyncAddToPlaylist(playlist, playlistItems, -1);
    }

    public static void asyncAddToPlaylist(final Playlist playlist, final PlaylistItem[] playlistItems, final int index) {
        Thread t = new Thread(new Runnable() {
            public void run() {
                addToPlaylist(playlist, playlistItems, index);
                playlist.save();
                GUIMediator.safeInvokeLater(new Runnable() {
                    public void run() {
                        LibraryMediator.instance().getLibraryPlaylists().refreshSelection();
                    }
                });
            }
        }, "asyncAddToPlaylist");
        t.setDaemon(true);
        t.start();
    }
    
    public static void asyncAddToPlaylist(Playlist playlist, File m3uFile) {
        asyncAddToPlaylist(playlist, m3uFile, -1);
    }

    public static void asyncAddToPlaylist(Playlist playlist, File m3uFile, int index) {
        try {
            List<File> files = M3UPlaylist.load(m3uFile.getAbsolutePath());
            asyncAddToPlaylist(playlist, files.toArray(new File[0]), index);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<LibraryPlaylistsTableTransferable.Item> convertToItems(List<PlaylistItem> playlistItems) {
        List<LibraryPlaylistsTableTransferable.Item> items = new ArrayList<LibraryPlaylistsTableTransferable.Item>(playlistItems.size());
        for (PlaylistItem playlistItem : playlistItems) {
            Item item = new LibraryPlaylistsTableTransferable.Item();
            item.id = playlistItem.getId();
            item.filePath = playlistItem.getFilePath();
            item.fileName = playlistItem.getFileName();
            item.fileSize = playlistItem.getFileSize();
            item.fileExtension = playlistItem.getFileExtension();
            item.trackTitle = playlistItem.getTrackTitle();
            item.trackDurationInSecs = playlistItem.getTrackDurationInSecs();
            item.trackArtist = playlistItem.getTrackArtist();
            item.trackAlbum = playlistItem.getTrackAlbum();
            item.coverArtPath = playlistItem.getCoverArtPath();
            item.trackBitrate = playlistItem.getTrackBitrate();
            item.trackComment = playlistItem.getTrackComment();
            item.trackGenre = playlistItem.getTrackGenre();
            item.trackNumber = playlistItem.getTrackNumber();
            item.trackYear = playlistItem.getTrackYear();
            item.starred = playlistItem.isStarred();
            items.add(item);
        }
        return items;
    }

    public static PlaylistItem[] convertToPlaylistItems(LibraryPlaylistsTableTransferable.Item[] items) {
        List<PlaylistItem> playlistItems = new ArrayList<PlaylistItem>(items.length);
        for (LibraryPlaylistsTableTransferable.Item item : items) {
            PlaylistItem playlistItem = new PlaylistItem(null, item.id, item.filePath, item.fileName, item.fileSize, item.fileExtension, item.trackTitle, item.trackDurationInSecs, item.trackArtist, item.trackAlbum, item.coverArtPath, item.trackBitrate, item.trackComment, item.trackGenre,
                    item.trackNumber, item.trackYear, item.starred);
            playlistItems.add(playlistItem);
        }
        return playlistItems.toArray(new PlaylistItem[0]);
    }

    private static void addToPlaylist(Playlist playlist, List<? extends AbstractLibraryTableDataLine<?>> lines) {
        for (int i = 0; i < lines.size() && !playlist.isDeleted(); i++) {
            AbstractLibraryTableDataLine<?> line = lines.get(i);
            if (AudioPlayer.isPlayableFile(line.getFile())) {
                LibraryUtils.addPlaylistItem(playlist, line.getFile(), false);
            }
        }
    }
    
    private static int addToPlaylist(Playlist playlist, File[] files, boolean starred, Set<File> ignore) {
        return addToPlaylist(playlist, files, starred, -1, ignore);
    }

    private static int addToPlaylist(Playlist playlist, File[] files, boolean starred, int index, Set<File> ignore) {
        int count = 0;
        for (int i = 0; i < files.length && !playlist.isDeleted(); i++) {
            if (AudioPlayer.isPlayableFile(files[i]) && !ignore.contains(files[i])) {
                LibraryUtils.addPlaylistItem(playlist, files[i], starred, index + count);
                count++;
            } else if (files[i].isDirectory()) {
                count += addToPlaylist(playlist, files[i].listFiles(), starred, index + count, ignore);
            }
        }
        
        return count;
    }

    private static void addToPlaylist(Playlist playlist, PlaylistItem[] playlistItems) {
        addToPlaylist(playlist, playlistItems, false, -1);
    }
    
    private static void addToPlaylist(Playlist playlist, PlaylistItem[] playlistItems, int index) {
        addToPlaylist(playlist, playlistItems, false, index);
    }

    private static void addToPlaylist(Playlist playlist, PlaylistItem[] playlistItems, boolean starred, int index) {
        List<PlaylistItem> items = playlist.getItems();
        if (index != -1 && index <= items.size()) {
            List<Integer> toRemove = new ArrayList<Integer>(playlistItems.length);
            for (int i = 0; i < playlistItems.length && !playlist.isDeleted(); i++) {
                toRemove.add(playlistItems[i].getId());
                playlistItems[i].setId(LibraryDatabase.OBJECT_NOT_SAVED_ID);
                playlistItems[i].setPlaylist(playlist);
                items.add(index + i, playlistItems[i]);
                if (starred) {
                    playlistItems[i].setStarred(starred);
                    playlistItems[i].save();
                }
            }
            for (int i = 0; i < toRemove.size() && !playlist.isDeleted(); i++) {
                int id = toRemove.get(i);
                for (int j = 0; j < items.size() && !playlist.isDeleted(); j++) {
                    if (items.get(j).getId() == id) {
                        items.remove(j);
                        break;
                    }
                }
            }
        } else {
            for (int i = 0; i < playlistItems.length && !playlist.isDeleted(); i++) {
                playlistItems[i].setPlaylist(playlist);
                items.add(playlistItems[i]);
                if (starred) {
                    playlistItems[i].setStarred(starred);
                    playlistItems[i].save();
                }
            }
        }
    }

    public static String getPlaylistDurationInDDHHMMSS(Playlist playlist) {
        List<PlaylistItem> items = playlist.getItems();
        float totalSecs = 0;
        for (PlaylistItem item : items) {
            totalSecs += item.getTrackDurationInSecs();
        }

        return getSecondsInDDHHMMSS((int) totalSecs);
    }


    public static boolean directoryContainsAudio(File directory, int depth) {
        Set<File> ignore = TorrentUtil.getIgnorableFiles();
        return directoryContainsAudio(directory, depth, ignore);
    }

    
    public static boolean directoryContainsAudio(File directory) {
        Set<File> ignore = TorrentUtil.getIgnorableFiles();
        return directoryContainsAudio(directory, 4, ignore);
    }

    private static boolean directoryContainsAudio(File directory, int depth, Set<File> ignore) {
        if (directory == null || !directory.isDirectory()) {
            return false;
        }

        for (File childFile : directory.listFiles()) {
            if (!childFile.isDirectory()) {
                if (AudioPlayer.isPlayableFile(childFile) && !ignore.contains(childFile)) {
                    return true;
                }
            } else {
                if (depth > 0) {
                    if (directoryContainsAudio(childFile, depth - 1, ignore)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static String calculateName(File[] files) {
        List<String> names = new ArrayList<String>(150);
        findNames(names, files);
        return new NameCalculator(names).getName();
    }

    private static String calculateName(List<? extends AbstractLibraryTableDataLine<?>> lines) {
        File[] files = new File[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            files[i] = lines.get(i).getFile();
        }
        return calculateName(files);
    }

    private static String calculateName(PlaylistItem[] playlistItems) {
        File[] files = new File[playlistItems.length];
        for (int i = 0; i < files.length; i++) {
            files[i] = new File(playlistItems[i].getFilePath());
        }
        return calculateName(files);
    }

    private static void findNames(List<String> names, File[] files) {
        if (names.size() > 100) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                String fullPathNoEndSeparator = FilenameUtils.getFullPathNoEndSeparator(file.getAbsolutePath());
                String baseName = FilenameUtils.getBaseName(fullPathNoEndSeparator);
                names.add(baseName);
                findNames(names, file.listFiles());
            } else if (AudioPlayer.isPlayableFile(file)) {
                String baseName = FilenameUtils.getBaseName(file.getAbsolutePath());
                names.add(baseName);
            }
        }
    }

    public static void cleanup(Playlist playlist) {
        if (playlist == null) {
            return;
        }
        try {
            for (PlaylistItem item : playlist.getItems()) {
                if (!new File(item.getFilePath()).exists()) {
                    item.delete();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void refreshID3Tags(Playlist playlist) {
        refreshID3Tags(playlist, playlist.getItems());
    }

    public static void refreshID3Tags(final Playlist playlist, final List<PlaylistItem> items) {
        executor.execute(new Runnable() {
            public void run() {
                for (PlaylistItem item : items) {
                    try {
                        LibraryMediator.instance().getLibrarySearch().pushStatus(I18n.tr("Refreshing") + " " + item.getTrackAlbum() + " - " + item.getTrackTitle());
                        File file = new File(item.getFilePath());
                        if (file.exists()) {
                            AudioMetaData mt = new AudioMetaData(file);
                            LibraryMediator.getLibrary().updatePlaylistItemProperties(item.getFilePath(), mt.getTitle(), mt.getArtist(), mt.getAlbum(), mt.getComment(), mt.getGenre(), mt.getTrack(), mt.getYear());
                        }
                    } catch (Exception e) {
                        // ignore, skip
                    } finally {
                        LibraryMediator.instance().getLibrarySearch().revertStatus();
                    }
                }
                GUIMediator.safeInvokeLater(new Runnable() {
                    public void run() {
                        if (playlist != null) {
                            if (playlist.getId() == LibraryDatabase.STARRED_PLAYLIST_ID) {
                                DirectoryHolder dh = LibraryMediator.instance().getLibraryFiles().getSelectedDirectoryHolder();
                                if (dh instanceof StarredDirectoryHolder) {
                                    LibraryMediator.instance().getLibraryFiles().refreshSelection();
                                }
                            } else {
                                Playlist selectedPlaylist = LibraryMediator.instance().getLibraryPlaylists().getSelectedPlaylist();
                                if (selectedPlaylist != null && selectedPlaylist.equals(playlist)) {
                                    LibraryMediator.instance().getLibraryPlaylists().refreshSelection();
                                }
                            }
                        }
                    }
                });
            }
        });
    }
    
    private static boolean isPlaylistSelected(Playlist playlist) {
        Playlist selectedPlaylist = LibraryMediator.instance().getLibraryPlaylists().getSelectedPlaylist();
        return selectedPlaylist != null && selectedPlaylist.equals(playlist);
    }

	public static boolean isRefreshKeyEvent(KeyEvent e) {
		int keyCode = e.getKeyCode();
		boolean ctrlCmdDown = e.isControlDown() || e.isAltGraphDown() || e.isMetaDown();
		return keyCode  == KeyEvent.VK_F5 || (ctrlCmdDown && keyCode == KeyEvent.VK_R);
	}
}
