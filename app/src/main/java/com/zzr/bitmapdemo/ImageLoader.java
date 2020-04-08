package com.zzr.bitmapdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实现图片加载功能
 */
public class ImageLoader {
    private static final String TAG = "ImageLoader";
    private static final int MESSAGE_POST_RESULT = 1;
    private static final int TAG_KEY_URI = R.id.imageloader_uri;
    private static final int DISK_CACHE_INDEX = 0;
    private static final int IO_BUFFER_SIZE = 8 * 1024;

    private boolean isDiskCacheCreated = false;
    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;//50M
    private Context context;
    private LruCache<String, Bitmap> lruCache;
    private DiskLruCache diskLruCache;
    private ImageCompressor imageCompressor = new ImageCompressor();

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final long KEEP_ALIVE = 10L;
    private static final ThreadFactory threadFactory = new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "ImageLoader#" + count.getAndIncrement());
        }
    };

    public static Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
            KEEP_ALIVE, TimeUnit.SECONDS,
            new LinkedBlockingDeque<Runnable>(), threadFactory);

    private Handler mainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == MESSAGE_POST_RESULT) {
                LoaderResult result = (LoaderResult) msg.obj;
                ImageView imageView = result.imageView;
                String uri = (String) imageView.getTag(TAG_KEY_URI);
                if (uri.equals(result.url)) {
                    imageView.setImageBitmap(result.bitmap);
                } else {
                    Log.w(TAG, "set image bitmap ,but url has changed ,ignore!");
                }
            }
        }
    };

    private ImageLoader(Context context) {
        this.context = context.getApplicationContext();
        //可用的最大内存 单位 KB
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        Log.i(TAG, "cacheSize: " + cacheSize / 1024);
        lruCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                //Bitmap所占用的内存空间数等于Bitmap的每一行所占用的空间数乘以Bitmap的行数
                return value.getRowBytes() * value.getHeight() / 1024;
            }
        };
        File diskCacheDir = getDiskCacheDir(context, "bitmap");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
        if (getUsableSpace(diskCacheDir) > DISK_CACHE_SIZE) {
            try {
                diskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
                isDiskCacheCreated = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static ImageLoader getInstance(Context context) {
        return new ImageLoader(context);
    }

    public Bitmap getBitmapFromMemCache(String key) {
        return lruCache.get(key);
    }

    public void addBitmapToMemCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            lruCache.put(key, bitmap);
        }
    }

    /**
     * 从网络下载图片并缓存到磁盘
     */
    private Bitmap loadBitmapFromHttp(String url, int reqWidth, int reqHeight) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("can not visit netWork form UI Thread!");
        }
        if (diskLruCache == null) {
            return null;
        }
        String key = hasKeyFromUrl(url);
        DiskLruCache.Editor editor = diskLruCache.edit(key);
        if (editor != null) {
            OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
            if (downloadUrlToStream(url, outputStream)) {
                editor.commit();
                Log.i(TAG, "loadBitmapFromHttp: 写入磁盘缓存成功");
            } else {
                editor.abort();
                Log.i(TAG, "loadBitmapFromHttp: 写入磁盘缓存失败");
            }
            diskLruCache.flush();
        }

        return loadBitmapFromDiskCache(url, reqWidth, reqHeight);
    }

    private Bitmap loadBitmapFromDiskCache(String url, int reqWidth, int reqHeight) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("can not visit netWork form UI Thread!");
        }
        if (diskLruCache == null) {
            return null;
        }
        Bitmap bitmap = null;
        String key = hasKeyFromUrl(url);
        DiskLruCache.Snapshot snapshot = diskLruCache.get(key);
        if (snapshot != null) {
            FileInputStream fileInputStream = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
            FileDescriptor fd = fileInputStream.getFD();
            bitmap = imageCompressor.decodeSampleBitmapFromFileDescriptor(fd, reqWidth, reqHeight);
            if (bitmap != null) {
                Log.i(TAG, "从磁盘加载图片，并写入内存缓存");
                addBitmapToMemCache(key, bitmap);
            }
        }
        return bitmap;
    }

    /**
     * 同步加载图片接口 在工作线程中执行
     */
    public Bitmap loadBitmap(String url, int reqWidth, int reqHeight) {
        //从内存缓存中拿
        Bitmap bitmap = loadBitmapFromMemCache(url);
        Log.i(TAG, "loadBitmap: bitmap = " + bitmap);
        if (bitmap != null) {
            Log.i(TAG, "loadBitmapFromMemCache,url: " + url);
            return bitmap;
        }
        //从磁盘缓存中拿
        try {
            bitmap = loadBitmapFromDiskCache(url, reqWidth, reqHeight);
            if (bitmap != null) {
                Log.i(TAG, "loadBitmapFromDiskCache,url: " + url);
                return bitmap;
            }
            //从网络下载
            bitmap = loadBitmapFromHttp(url, reqWidth, reqHeight);
            Log.i(TAG, "loadBitmapFromHttp,url: " + url);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (bitmap == null && !isDiskCacheCreated) {
            Log.w(TAG, "DiskLruCache is not created");
            bitmap = downloadBitmapFromUrl(url);
        }
        return bitmap;
    }

    public void bindBitmap(final String url, final ImageView imageView) {
        bindBitmap(url, imageView, 0, 0);
    }

    /**
     * 异步加载图片
     */
    public void bindBitmap(final String url, final ImageView imageView, final int reqWidth, final int reqHeight) {
        imageView.setTag(TAG_KEY_URI, url);
        //从内存缓存拿
        final Bitmap bitmap = loadBitmapFromMemCache(url);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            return;
        }
        Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap1 = loadBitmap(url, reqWidth, reqHeight);
                if (bitmap1 != null) {
                    LoaderResult result = new LoaderResult(imageView, url, bitmap1);
                    mainHandler.obtainMessage(MESSAGE_POST_RESULT, result).sendToTarget();

                }
            }
        };
        THREAD_POOL_EXECUTOR.execute(loadBitmapTask);
    }

    private Bitmap downloadBitmapFromUrl(String urlString) {
        Bitmap bitmap = null;
        HttpURLConnection connection = null;
        BufferedInputStream in = null;
        try {
            final URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(connection.getInputStream(), IO_BUFFER_SIZE);
            bitmap = BitmapFactory.decodeStream(in);

        } catch (IOException e) {
            Log.e(TAG, "download bitmap failed: " + e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            IOCloseUtil.close(in);
        }
        return bitmap;
    }

    private Bitmap loadBitmapFromMemCache(String url) {
        String key = hasKeyFromUrl(url);
        return getBitmapFromMemCache(key);
    }

    private long getUsableSpace(File path) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
//            return path.getUsableSpace();
//        }
//        final StatFs statFs = new StatFs(path.getPath());
//        return statFs.getBlockSize() * statFs.getAvailableBlocks();
        //返回分区剩余空间的大小
        return path.getUsableSpace();
    }

    private File getDiskCacheDir(Context context, String uniqueName) {
        //sd卡是否挂载
        boolean available = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        final String cachePath;
        if (available) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }

        return new File(cachePath + File.separator + uniqueName);
    }

    /**
     * 使用MD5算法对传入的key进行加密并返回。
     */
    private String hasKeyFromUrl(String url) {
        String cacheKey;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(url.getBytes());
            cacheKey = bytesToHexString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(url.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    private boolean downloadUrlToStream(String urlString, OutputStream outputStream) {
        HttpURLConnection connection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try {
            final URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(connection.getInputStream(), IO_BUFFER_SIZE);
            out = new BufferedOutputStream(outputStream, IO_BUFFER_SIZE);

            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }

        } catch (IOException e) {
            Log.e(TAG, "download bitmap failed: " + e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            IOCloseUtil.close(in);
            IOCloseUtil.close(out);
        }
        return true;
    }

    private static class LoaderResult {
        public ImageView imageView;
        public String url;
        public Bitmap bitmap;

        public LoaderResult(ImageView imageView, String url, Bitmap bitmap) {
            this.imageView = imageView;
            this.url = url;
            this.bitmap = bitmap;
        }
    }
}
