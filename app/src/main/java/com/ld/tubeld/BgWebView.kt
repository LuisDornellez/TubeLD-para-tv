package com.ld.tubeld

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/** WebView que no se entera de que la ventana quedó oculta, para que el audio no se pause. */
class BgWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    @Volatile var keepAlive = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (keepAlive && visibility != View.VISIBLE) return
        super.onWindowVisibilityChanged(visibility)
    }
}
