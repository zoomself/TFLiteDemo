package com.zoomself.ai.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;
import android.util.Rational;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.impl.ImageOutputConfig.RotationValue;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility class for image related operations.
 */
public class TFImageUtils {
    // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges
    // are normalized to eight bits.
    static final int kMaxChannelValue = 262143;

    private static int YUV2RGB(int y, int u, int v) {
        // Adjust and check YUV values
        y = (y - 16) < 0 ? 0 : (y - 16);
        u -= 128;
        v -= 128;

        // This is the floating point equivalent. We do the conversion in integer
        // because some Android devices do not have floating point in hardware.
        // nR = (int)(1.164 * nY + 2.018 * nU);
        // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
        // nB = (int)(1.164 * nY + 1.596 * nV);
        int y1192 = 1192 * y;
        int r = (y1192 + 1634 * v);
        int g = (y1192 - 833 * v - 400 * u);
        int b = (y1192 + 2066 * u);

        // Clipping RGB values to be inside boundaries [ 0 , kMaxChannelValue ]
        r = r > kMaxChannelValue ? kMaxChannelValue : (r < 0 ? 0 : r);
        g = g > kMaxChannelValue ? kMaxChannelValue : (g < 0 ? 0 : g);
        b = b > kMaxChannelValue ? kMaxChannelValue : (b < 0 ? 0 : b);

        return 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
    }

    public static void convertYUV420ToARGB8888(
            byte[] yData,
            byte[] uData,
            byte[] vData,
            int width,
            int height,
            int yRowStride,
            int uvRowStride,
            int uvPixelStride,
            int[] out) {
        int yp = 0;
        for (int j = 0; j < height; j++) {
            int pY = yRowStride * j;
            int pUV = uvRowStride * (j >> 1);

            for (int i = 0; i < width; i++) {
                int uv_offset = pUV + (i >> 1) * uvPixelStride;

                out[yp++] = YUV2RGB(0xff & yData[pY + i], 0xff & uData[uv_offset], 0xff & vData[uv_offset]);
            }
        }
    }


    private static final String TAG = "ImageUtil";

    private TFImageUtils() {
    }

    /**
     * {@link android.media.Image} to JPEG byte array.
     */
    @Nullable
    public static byte[] imageToJpegByteArray(@NonNull ImageProxy image)
            throws TFImageUtils.CodecFailedException {
        byte[] data = null;
        if (image.getFormat() == ImageFormat.JPEG) {
            data = jpegImageToJpegByteArray(image);
        } else if (image.getFormat() == ImageFormat.YUV_420_888) {
            data = yuvImageToJpegByteArray(image);
        } else {
            Log.w(TAG, "Unrecognized image format: " + image.getFormat());
        }
        return data;
    }

    /**
     * Crops byte array with given {@link Rect}.
     */
    @NonNull
    public static byte[] cropByteArray(@NonNull byte[] data, @Nullable Rect cropRect)
            throws TFImageUtils.CodecFailedException {
        if (cropRect == null) {
            return data;
        }

        Bitmap bitmap = null;
        try {
            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(data, 0, data.length,
                    false);
            bitmap = decoder.decodeRegion(cropRect, new BitmapFactory.Options());
            decoder.recycle();
        } catch (IllegalArgumentException e) {
            throw new TFImageUtils.CodecFailedException("Decode byte array failed with illegal argument." + e,
                    TFImageUtils.CodecFailedException.FailureType.DECODE_FAILED);
        } catch (IOException e) {
            throw new TFImageUtils.CodecFailedException("Decode byte array failed.",
                    TFImageUtils.CodecFailedException.FailureType.DECODE_FAILED);
        }

        if (bitmap == null) {
            throw new TFImageUtils.CodecFailedException("Decode byte array failed.",
                    TFImageUtils.CodecFailedException.FailureType.DECODE_FAILED);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        if (!success) {
            throw new TFImageUtils.CodecFailedException("Encode bitmap failed.",
                    TFImageUtils.CodecFailedException.FailureType.ENCODE_FAILED);
        }
        bitmap.recycle();

        return out.toByteArray();
    }

    /**
     * True if the given aspect ratio is meaningful.
     */
    public static boolean isAspectRatioValid(@Nullable Rational aspectRatio) {
        return aspectRatio != null && aspectRatio.floatValue() > 0 && !aspectRatio.isNaN();
    }

    /**
     * True if the given aspect ratio is meaningful and has effect on the given size.
     */
    public static boolean isAspectRatioValid(@NonNull Size sourceSize,
                                             @Nullable Rational aspectRatio) {
        return aspectRatio != null
                && aspectRatio.floatValue() > 0
                && isCropAspectRatioHasEffect(sourceSize, aspectRatio)
                && !aspectRatio.isNaN();
    }

    /**
     * Calculates crop rect with the specified aspect ratio on the given size. Assuming the rect is
     * at the center of the source.
     */
    @Nullable
    public static Rect computeCropRectFromAspectRatio(@NonNull Size sourceSize,
                                                      @NonNull Rational aspectRatio) {
        if (!isAspectRatioValid(aspectRatio)) {
            Log.w(TAG, "Invalid view ratio.");
            return null;
        }

        int sourceWidth = sourceSize.getWidth();
        int sourceHeight = sourceSize.getHeight();
        float srcRatio = sourceWidth / (float) sourceHeight;
        int cropLeft = 0;
        int cropTop = 0;
        int outputWidth = sourceWidth;
        int outputHeight = sourceHeight;
        int numerator = aspectRatio.getNumerator();
        int denominator = aspectRatio.getDenominator();

        if (aspectRatio.floatValue() > srcRatio) {
            outputHeight = Math.round((sourceWidth / (float) numerator) * denominator);
            cropTop = (sourceHeight - outputHeight) / 2;
        } else {
            outputWidth = Math.round((sourceHeight / (float) denominator) * numerator);
            cropLeft = (sourceWidth - outputWidth) / 2;
        }

        return new Rect(cropLeft, cropTop, cropLeft + outputWidth, cropTop + outputHeight);
    }

    /**
     * Rotate rational by rotation value, which inverse it if the degree is 90 or 270.
     *
     * @param rational Rational to be rotated.
     * @param rotation Rotation value being applied.
     */
    @NonNull
    public static Rational rotate(
            @NonNull Rational rational, @RotationValue int rotation) {
        if (rotation == 90 || rotation == 270) {
            return inverseRational(rational);
        }

        return rational;
    }

    private static byte[] nv21ToJpeg(byte[] nv21, int width, int height, @Nullable Rect cropRect)
            throws TFImageUtils.CodecFailedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        boolean success =
                yuv.compressToJpeg(
                        cropRect == null ? new Rect(0, 0, width, height) : cropRect, 100, out);
        if (!success) {
            throw new TFImageUtils.CodecFailedException("YuvImage failed to encode jpeg.",
                    TFImageUtils.CodecFailedException.FailureType.ENCODE_FAILED);
        }
        return out.toByteArray();
    }

    private static byte[] yuv_420_888toNv21(ImageProxy image) {
        ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
        ImageProxy.PlaneProxy uPlane = image.getPlanes()[1];
        ImageProxy.PlaneProxy vPlane = image.getPlanes()[2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();
        yBuffer.rewind();
        uBuffer.rewind();
        vBuffer.rewind();

        int ySize = yBuffer.remaining();

        int position = 0;
        // TODO(b/115743986): Pull these bytes from a pool instead of allocating for every image.
        byte[] nv21 = new byte[ySize + (image.getWidth() * image.getHeight() / 2)];

        // Add the full y buffer to the array. If rowStride > 1, some padding may be skipped.
        for (int row = 0; row < image.getHeight(); row++) {
            yBuffer.get(nv21, position, image.getWidth());
            position += image.getWidth();
            yBuffer.position(
                    Math.min(ySize, yBuffer.position() - image.getWidth() + yPlane.getRowStride()));
        }

        int chromaHeight = image.getHeight() / 2;
        int chromaWidth = image.getWidth() / 2;
        int vRowStride = vPlane.getRowStride();
        int uRowStride = uPlane.getRowStride();
        int vPixelStride = vPlane.getPixelStride();
        int uPixelStride = uPlane.getPixelStride();

        // Interleave the u and v frames, filling up the rest of the buffer. Use two line buffers to
        // perform faster bulk gets from the byte buffers.
        byte[] vLineBuffer = new byte[vRowStride];
        byte[] uLineBuffer = new byte[uRowStride];
        for (int row = 0; row < chromaHeight; row++) {
            vBuffer.get(vLineBuffer, 0, Math.min(vRowStride, vBuffer.remaining()));
            uBuffer.get(uLineBuffer, 0, Math.min(uRowStride, uBuffer.remaining()));
            int vLineBufferPosition = 0;
            int uLineBufferPosition = 0;
            for (int col = 0; col < chromaWidth; col++) {
                nv21[position++] = vLineBuffer[vLineBufferPosition];
                nv21[position++] = uLineBuffer[uLineBufferPosition];
                vLineBufferPosition += vPixelStride;
                uLineBufferPosition += uPixelStride;
            }
        }

        return nv21;
    }

    private static boolean isCropAspectRatioHasEffect(Size sourceSize, Rational aspectRatio) {
        int sourceWidth = sourceSize.getWidth();
        int sourceHeight = sourceSize.getHeight();
        int numerator = aspectRatio.getNumerator();
        int denominator = aspectRatio.getDenominator();

        return sourceHeight != Math.round((sourceWidth / (float) numerator) * denominator)
                || sourceWidth != Math.round((sourceHeight / (float) denominator) * numerator);
    }

    private static Rational inverseRational(Rational rational) {
        if (rational == null) {
            return rational;
        }
        return new Rational(
                /*numerator=*/ rational.getDenominator(),
                /*denominator=*/ rational.getNumerator());
    }

    private static boolean shouldCropImage(ImageProxy image) {
        Size sourceSize = new Size(image.getWidth(), image.getHeight());
        Size targetSize = new Size(image.getCropRect().width(), image.getCropRect().height());

        return !targetSize.equals(sourceSize);
    }

    private static byte[] jpegImageToJpegByteArray(ImageProxy image) throws TFImageUtils.CodecFailedException {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] data = new byte[buffer.capacity()];
        buffer.rewind();
        buffer.get(data);
        if (shouldCropImage(image)) {
            data = cropByteArray(data, image.getCropRect());
        }
        return data;
    }

    private static byte[] yuvImageToJpegByteArray(ImageProxy image)
            throws TFImageUtils.CodecFailedException {
        return TFImageUtils.nv21ToJpeg(
                TFImageUtils.yuv_420_888toNv21(image),
                image.getWidth(),
                image.getHeight(),
                shouldCropImage(image) ? image.getCropRect() : null);
    }

    /**
     * Exception for error during transcoding image.
     */
    public static final class CodecFailedException extends Exception {
        enum FailureType {
            ENCODE_FAILED,
            DECODE_FAILED,
            UNKNOWN
        }

        private TFImageUtils.CodecFailedException.FailureType mFailureType;

        CodecFailedException(String message) {
            super(message);
            mFailureType = TFImageUtils.CodecFailedException.FailureType.UNKNOWN;
        }

        CodecFailedException(String message, TFImageUtils.CodecFailedException.FailureType failureType) {
            super(message);
            mFailureType = failureType;
        }

        @NonNull
        public TFImageUtils.CodecFailedException.FailureType getFailureType() {
            return mFailureType;
        }
    }

    //--------------自己添加----------------------

    public static void convertBitmapToTensorBuffer(Bitmap bitmap, TensorBuffer tensorBuffer) {
        if (bitmap == null) {
            throw new IllegalArgumentException("bitmap can not be null");
        }
        if (tensorBuffer == null) {
            throw new IllegalArgumentException("tensorBuffer can not be null");
        }
        DataType dataType = tensorBuffer.getDataType();
        int[] tensorBufferShape = tensorBuffer.getShape();//一般是带batch的（1,256,256,3）（batch,height,width,channel）
        if (tensorBufferShape.length != 4) {
            throw new IllegalArgumentException("tensorBuffer shape length must is 4");
        }

        int tensorBufferHeight = tensorBufferShape[1];
        int tensorBufferWidth = tensorBufferShape[2];
        int tensorBufferChannel = tensorBufferShape[3];

        int imageWidth = bitmap.getWidth();
        int imageHeight = bitmap.getHeight();

        //需要裁剪图片到模型目标尺寸上去
        if (imageHeight != tensorBufferHeight || imageWidth != tensorBufferWidth) {
            Matrix matrix = new Matrix();
            matrix.setScale(tensorBufferWidth*1.0f/imageWidth,tensorBufferHeight*1.0f/imageHeight);
//            matrix.setRectToRect(
//                    new RectF(0f, 0f, imageWidth, imageHeight),
//                    new RectF(
//                            0f, 0f,
//                            tensorBufferWidth,
//                            tensorBufferHeight
//                    ),
//                    Matrix.ScaleToFit.FILL
//            );
            bitmap = Bitmap.createBitmap(
                    bitmap, 0, 0,
                    imageWidth,
                    imageHeight, matrix, false
            );
        }
        int[] PixelValues = new int[tensorBufferWidth * tensorBufferHeight];
        bitmap.getPixels(PixelValues, 0, tensorBufferWidth, 0, 0, tensorBufferWidth, tensorBufferHeight);

        ByteBuffer byteBuffer = ByteBuffer.allocate(tensorBufferWidth * tensorBufferHeight * tensorBufferChannel * dataType.byteSize());
        byteBuffer.order(ByteOrder.nativeOrder());//不能省略
        byteBuffer.rewind();
        if (dataType == DataType.UINT8) {//这一段要是不行可以参考ImageConversions convertBitmapToTensorBuffer函数修改
            for (int pixel : PixelValues) {
                byteBuffer.putInt(pixel >> 16 & 255);
                byteBuffer.putInt(pixel >> 8 & 255);
                byteBuffer.putInt(pixel & 255);
            }

        } else {
            //做了归一化【0,1】
            for (float pixel : PixelValues) {
                byteBuffer.putFloat((((int) pixel >> 16 & 0xFF) - 0.0f) / 255.0f);
                byteBuffer.putFloat((((int) pixel >> 8 & 0xFF) - 0.0f) / 255.0f);
                byteBuffer.putFloat((((int) pixel & 0xFF) - 0.0f) / 255.0f);
            }
        }
        byteBuffer.rewind();
        tensorBuffer.loadBuffer(byteBuffer);
    }

    public static Bitmap convertTensorBufferToBitmap(TensorBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("tensorBuffer can not be null");
        }
        int[] shape = buffer.getShape();
        if (shape.length != 4) {
            throw new IllegalArgumentException("tensorBuffer shape length must is 4");
        }
        int h = shape[1];
        int w = shape[2];
        int[] pixelValues = new int[w * h];

        DataType dataType = buffer.getDataType();
        if (dataType == DataType.UINT8) {
            int[] rgbValues = buffer.getIntArray();
            int i = 0;
            for (int var8 = 0; i < pixelValues.length; ++i) {
                int r = rgbValues[var8++];
                int g = rgbValues[var8++];
                int b = rgbValues[var8++];
                pixelValues[i] = Color.rgb(r, g, b);
            }
        } else if (dataType == DataType.FLOAT32) {
            float[] rgbValues = buffer.getFloatArray();
            int i = 0;
            for (int var8 = 0; i < pixelValues.length; ++i) {
                float r = rgbValues[var8++];
                float g = rgbValues[var8++];
                float b = rgbValues[var8++];
                pixelValues[i] = rgb(r, g, b);
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixelValues, 0, w, 0, 0, w, h);
        return bitmap;
    }

    /**
     * 针对于float类型颜色值转换
     *
     * @param red
     * @param green
     * @param blue
     * @return
     */
    private static int rgb(float red, float green, float blue) {
        return 0xff000000 |
                ((int) (red * 255.0f + 0.5f) << 16) |
                ((int) (green * 255.0f + 0.5f) << 8) |
                (int) (blue * 255.0f + 0.5f);
    }

}
