package com.zoomself.ai.ui


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.zoomself.ai.R
import com.zoomself.ai.vm.StyleTransferViewModel
import kotlinx.android.synthetic.main.fragment_ai.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * display.rotation 正常竖直方向为 0， 逆时针方向旋转（90°,180°,270°）对应 1,2,3，
 */

abstract class AiFragment : Fragment() {

    protected lateinit var styleTransferViewModel: StyleTransferViewModel
    protected lateinit var cameraExecutor: ExecutorService

    /**
     * 控制层布局
     */
    abstract fun getControllerLayout(): Int

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_ai, container, false) as ViewGroup
        View.inflate(requireContext(), getControllerLayout(), v)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        camera.bindToLifecycle(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        styleTransferViewModel =
            ViewModelProvider.AndroidViewModelFactory(requireActivity().application)
                .create(StyleTransferViewModel::class.java)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }
    }


}