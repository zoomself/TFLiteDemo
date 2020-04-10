package com.zoomself.ai.bean

import com.zoomself.ai.utils.TFModel
import java.io.Serializable

data class AiBasicInfoBean(val device: TFModel.Device, val threadCount: Int, val duration: Long) :
    Serializable