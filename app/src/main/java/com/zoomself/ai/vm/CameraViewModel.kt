package com.zoomself.ai.vm

import android.app.Application
import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import java.io.File
import java.util.concurrent.Executor

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        const val TAG = "CameraViewModel"
    }

    private lateinit var mPreview: Preview
    lateinit var mImageCapture: ImageCapture
    lateinit var mImageAnalysis: ImageAnalysis

    private lateinit var mContext: Context
    private lateinit var mLifecycleOwner: LifecycleOwner
    private lateinit var mPreViewView: PreviewView
    private var mLensFacing = CameraSelector.LENS_FACING_BACK//默认后置摄像头


    var analysisImageLiveData = MutableLiveData<ImageProxy>()
    var saveImageProxyLiveData = MutableLiveData<ImageCapture.OutputFileResults>()

    /**
     * 初始化camera的配置 绑定 Preview，ImageCapture，ImageAnalysis实例
     */
    fun initCamera(
        context: Context, lifecycleOwner: LifecycleOwner,
        preViewView: PreviewView, lensFacing: Int = CameraSelector.LENS_FACING_BACK
    ) {
        //preViewView正常布局好了以后才会执行，要不然横竖屏切换的时候preViewView.display会为空
        preViewView.post {
            mContext = context
            mLifecycleOwner = lifecycleOwner
            mPreViewView = preViewView
            mLensFacing = lensFacing

            val rotation = preViewView.display.rotation
            val metrics = DisplayMetrics().also { preViewView.display.getRealMetrics(it) }
            val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)

            Log.i(TAG, "w:${metrics.widthPixels},h:${metrics.heightPixels},d:${metrics.densityDpi}")
            Log.i(TAG, "rotation:${rotation},screenAspectRatio:${screenAspectRatio}")

            val cameraSelector =
                CameraSelector.Builder().requireLensFacing(mLensFacing).build()

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val cameraProvider = cameraProviderFuture.get()
            cameraProviderFuture.addListener(Runnable {
                mPreview = Preview.Builder()
                    .setTargetAspectRatio(screenAspectRatio)
                    .setTargetRotation(rotation)
                    .build()

                mImageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetAspectRatio(screenAspectRatio)
                    .setTargetRotation(rotation)
                    .build()

                mImageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetAspectRatio(screenAspectRatio)
                    .setTargetRotation(rotation)
                    .build()

                cameraProvider.unbindAll()
                val camera=cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    mPreview,
                    mImageCapture,
                    mImageAnalysis
                )
                mPreview.setSurfaceProvider(preViewView.createSurfaceProvider(camera.cameraInfo))

            }, ContextCompat.getMainExecutor(context))
        }

    }

    /**
     * 旋转摄像头
     */
    fun switchLensFacing(lensFacing: Int) {
        mLensFacing = lensFacing
        initCamera(mContext, mLifecycleOwner, mPreViewView)
    }


    /**
     * 拍照
     */
    fun takePicture(executor: Executor,outputFile: File? = null) {
        if (outputFile != null) {
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outputFile)
                .build()
            mImageCapture.takePicture(outputFileOptions,
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        Log.i(TAG, "takePicture onImageSaved :${outputFileResults.savedUri} ")
                        saveImageProxyLiveData.postValue(outputFileResults)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "takePicture onError : ${exception.message}")
                        Toast.makeText(mContext, "${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                })

        } else {
            mImageCapture.takePicture(
                executor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        analysisImageLiveData.postValue(image)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "takePicture onError : ${exception.message}")
                        Toast.makeText(mContext, "${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                })
        }


    }

    /**
     * 分析图片
     */
    fun analysisImage(executor: Executor) {
        mImageAnalysis.setAnalyzer(executor, ImageAnalysis.Analyzer {
            analysisImageLiveData.postValue(it)

        })
    }





    /**
     * 通过实际的屏幕分辨率计算最适合的宽高比
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - 4.0 / 3.0) <= abs(previewRatio - 16.0 / 9.0)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }
}