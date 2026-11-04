// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

private interface DwmApi : StdCallLibrary {
    fun DwmSetWindowAttribute(window: Pointer, attribute: Int, value: IntByReference, size: Int): Int
}

internal fun darkWindowsTitleBar(window: Window) {
    if (!System.getProperty("os.name").startsWith("Windows")) return
    try {
        val dwm = Native.load("dwmapi", DwmApi::class.java)
        val handle = Native.getWindowPointer(window)
        dwm.DwmSetWindowAttribute(handle, 20, IntByReference(1), 4) // Native dark caption buttons.
        // DWM uses COLORREF (BGR), matching the app's #100F0F background and #F5F2EE text.
        dwm.DwmSetWindowAttribute(handle, 35, IntByReference(0x000F0F10), 4)
        dwm.DwmSetWindowAttribute(handle, 36, IntByReference(0x00EEF2F5), 4)
    } catch (failure: LinkageError) {
        System.err.println("Windows title-bar styling unavailable: ${failure.message}")
    }
}
