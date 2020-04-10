package com.zoomself.ai.vm

import android.app.Application
import android.graphics.*
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.zoomself.ai.App
import com.zoomself.ai.utils.TFImageUtils
import com.zoomself.ai.utils.TFModel
import com.zoomself.ai.bean.AiBasicInfoBean
import com.zoomself.ai.bean.StyleTransferResultBean
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.lang.IllegalArgumentException
import java.util.concurrent.Executor


class StyleTransferViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "StyleTransferViewModel"
    }

    val styleTransferResultBeanLiveData = MutableLiveData<StyleTransferResultBean>()

    /**
     * @param styleModel (1,256,256,3) --->  (1,1,1,100)
     *
     * @param contentModel (1,384,384,3)(1,1,1,100) --->  (1,384,384,3)
     */
    fun styleTransfer(
        styleModel: TFModel,
        contentModel: TFModel,
        imageProxy: ImageProxy,
        executor: Executor,
        styleImageName: String = "style0.jpg"
    ) {

        executor.execute {
            val startTime=SystemClock.currentThreadTimeMillis()
            val context = getApplication<App>().applicationContext
            val styleImageInputStream = context.assets.open("style_images/$styleImageName")

            Log.i(TAG, "---------------styleModel----------------")

            //处理 styleModel
            val styleBitmap = BitmapFactory.decodeStream(styleImageInputStream)
            var styleTensorImage = TensorImage()

            val styleInputShape = styleModel.getInputTensorShape(0)
            val styleInputDataType = styleModel.getInputTensorDataType(0)
            val styleOutputShape = styleModel.getOutputTensorShape(0)
            val styleOutputDataType = styleModel.getOutputTensorDataType(0)
            val styleOutputTensorBuffer =
                TensorBuffer.createFixedSize(styleOutputShape, styleOutputDataType)

            val styleRotationDegrees = imageProxy.imageInfo.rotationDegrees
            val styleTargetX = styleInputShape[1]
            val styleTargetY = styleInputShape[2]
            val styleCropSize = styleBitmap.width.coerceAtMost(styleBitmap.height)

            Log.i(TAG, "styleRotationDegrees:$styleRotationDegrees")
            Log.i(TAG, "styleCropSize:$styleCropSize")
            Log.i(
                TAG,
                "styleInputShape:${styleTargetX},${styleTargetY},${styleInputShape[3]}, styleInputDataType:$styleInputDataType"
            )
            Log.i(
                TAG,
                "styleOutputShape:${styleOutputShape[1]},${styleOutputShape[2]},${styleOutputShape[3]}, styleOutputDataType:$styleOutputDataType"
            )

            when (styleInputDataType) {
                DataType.UINT8 -> {
                    styleTensorImage.load(styleBitmap)
                    val styleImageProcessor = ImageProcessor.Builder()
                        .add(ResizeWithCropOrPadOp(styleCropSize, styleCropSize))
                        .add(
                            ResizeOp(
                                styleTargetX,
                                styleTargetY,
                                ResizeOp.ResizeMethod.NEAREST_NEIGHBOR
                            )
                        )
                        .add(Rot90Op(-styleRotationDegrees / 90))
                        .build()

                    styleTensorImage = styleImageProcessor.process(styleTensorImage)
                }
                DataType.FLOAT32 -> {
                    val styleTensorBuffer =
                        TensorBuffer.createFixedSize(styleInputShape, styleInputDataType)
                    TFImageUtils.convertBitmapToTensorBuffer(
                        styleBitmap,
                        styleTensorBuffer
                    )
                    styleTensorImage.load(styleTensorBuffer)

                }
                else -> {
                    throw Exception("异常的输入类型，请更换模型${styleModel.path}")
                }
            }

            styleModel.run(styleTensorImage.buffer, styleOutputTensorBuffer.buffer)


            Log.i(TAG, "---------------contentModel----------------")

            //处理 contentModel
            val imageToJpegByteArray =
                TFImageUtils.imageToJpegByteArray(imageProxy)
                    ?: throw IllegalArgumentException("imageToJpegByteArray can't be null")

            val contentBitmap =
                BitmapFactory.decodeByteArray(imageToJpegByteArray, 0, imageToJpegByteArray.size)
            var contentTensorImage = TensorImage()

            val contentInputShape0 = contentModel.getInputTensorShape(0)
            val contentInputDataType0 = contentModel.getInputTensorDataType(0)
            val contentInputShape1 = contentModel.getInputTensorShape(1)
            val contentInputDataType1 = contentModel.getInputTensorDataType(1)

            val contentOutputShape = contentModel.getOutputTensorShape(0)
            val contentOutputDataType = contentModel.getOutputTensorDataType(0)
            val contentOutputTensorBuffer =
                TensorBuffer.createFixedSize(contentOutputShape, contentOutputDataType)

            val contentRotationDegrees = imageProxy.imageInfo.rotationDegrees
            val contentTargetX = contentInputShape0[1]
            val contentTargetY = contentInputShape0[2]
            val contentCropSize = contentBitmap.width.coerceAtMost(contentBitmap.height)

            Log.i(TAG, "contentRotationDegrees:$contentRotationDegrees")
            Log.i(TAG, "contentCropSize:$contentCropSize")
            Log.i(
                TAG,
                "contentInputShape0:${contentTargetX},${contentTargetY},${contentInputShape0[3]}, contentInputDataType0:$contentInputDataType0"
            )
            Log.i(
                TAG,
                "contentInputShape1:${contentInputShape1[1]},${contentInputShape1[2]},${contentInputShape1[3]}, contentInputDataType1:$contentInputDataType1"
            )
            Log.i(
                TAG,
                "contentOutputShape:${contentOutputShape[1]},${contentOutputShape[2]},${contentOutputShape[3]}, contentOutputDataType:$contentOutputDataType"
            )

            when (contentInputDataType0) {
                DataType.UINT8 -> {
                    contentTensorImage.load(contentBitmap)
                    val contentImageProcessor = ImageProcessor.Builder()
                        .add(ResizeWithCropOrPadOp(contentCropSize, contentCropSize))
                        .add(
                            ResizeOp(
                                contentTargetX,
                                contentTargetY,
                                ResizeOp.ResizeMethod.NEAREST_NEIGHBOR
                            )
                        )
                        .add(Rot90Op(-contentRotationDegrees / 90))
                        .build()
                    contentTensorImage = contentImageProcessor.process(contentTensorImage)
                }
                DataType.FLOAT32 -> {

                    val contentTensorBuffer =
                        TensorBuffer.createFixedSize(contentInputShape0, contentInputDataType0)
                    TFImageUtils.convertBitmapToTensorBuffer(
                        contentBitmap,
                        contentTensorBuffer
                    )
                    contentTensorImage.load(contentTensorBuffer)
                }
                else -> {
                    throw Exception("异常的输入类型，请更换模型: ${contentModel.path}")
                }
            }

            val composeInputs = arrayOf(contentTensorImage.buffer, styleOutputTensorBuffer.buffer)
            val outputsMap = hashMapOf<Int, Any>()
            outputsMap[0] = contentOutputTensorBuffer.buffer
            contentModel.run(composeInputs, outputsMap)

            val resultBitmap =
                TFImageUtils.convertTensorBufferToBitmap(
                    contentOutputTensorBuffer
                )
            val endTime=SystemClock.currentThreadTimeMillis()
            val duration=endTime-startTime
            val aiBasicInfoBean =
                AiBasicInfoBean(
                    contentModel.device,
                    contentModel.threadCount,
                    duration
                )
            val resultBean = StyleTransferResultBean(
                aiBasicInfoBean,
                resultBitmap
            )
            styleTransferResultBeanLiveData.postValue(resultBean)
            //一定要释放，要不然连续拍照分析会导致内存不足无法开启camera继续拍照
            imageProxy.close()

        }
    }


}