package com.zoomself.ai.ui

import android.os.Bundle
import android.util.Log

import android.view.View
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.lifecycle.Observer
import com.zoomself.ai.R
import com.zoomself.ai.utils.TFModel
import kotlinx.android.synthetic.main.fragment_ai.*
import kotlinx.android.synthetic.main.fragment_style_transfer.*


/**
 * 风格转换
 */
class StyleTransferFragment : AiFragment() {

    companion object {
        const val STYLE_MODEL_NAME = "style_predict_quantized_256.tflite"
        const val CONTENT_MODEL_NAME = "style_transfer_quantized_384.tflite"
    }

    private val styleModel: TFModel by lazy {
        TFModel.Builder(
            requireContext(),
            STYLE_MODEL_NAME
        )
            .setNumThreads(2)
            .build()
    }

    private val contentModel: TFModel by lazy {
        TFModel.Builder(
            requireContext(),
            CONTENT_MODEL_NAME
        )
            .setNumThreads(2)
            .build()
    }

    override fun getControllerLayout(): Int {
        return R.layout.fragment_style_transfer
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        styleTransferViewModel.styleTransferResultBeanLiveData.observe(viewLifecycleOwner, Observer {
            iv.visibility = View.VISIBLE
            iv.setImageBitmap(it.styledBitmap)
            Log.i("zoomself", it.aiBasicInfoBean.toString())
        })

        iv_picture.setOnClickListener {
            iv.visibility = View.GONE
            camera.takePicture(cameraExecutor, object :
                ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    styleTransferViewModel.styleTransfer(
                        styleModel,
                        contentModel,
                        image,
                        cameraExecutor
                    )
                }
            })

        }

    }


}