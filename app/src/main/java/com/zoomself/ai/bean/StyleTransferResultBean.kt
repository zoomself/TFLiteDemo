package com.zoomself.ai.bean

import android.graphics.Bitmap
import com.zoomself.ai.bean.AiBasicInfoBean

data class StyleTransferResultBean(val aiBasicInfoBean: AiBasicInfoBean, val styledBitmap: Bitmap)
