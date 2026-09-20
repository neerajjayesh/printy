// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app.printing

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException

class UserPrintException(message: String) : IOException(message)
object PrintErrors {
    fun message(error: Throwable, endpoint: String? = null): String = when (error) {
        is UserPrintException -> error.message ?: "Printing couldn't continue. Please try again."
        is ConnectException, is NoRouteToHostException -> "Couldn't reach printer at $endpoint. Check it's powered on and connected to the same network."
        is SocketTimeoutException -> "The printer at $endpoint stopped responding. Check its connection, paper and ink before trying again."
        is SocketException -> "The connection to $endpoint was lost. Some pages may have printed. Check the printer before trying again."
        is SecurityException -> "This file is locked or permission to open it has expired. Choose an unlocked copy again."
        is OutOfMemoryError -> "This file needs too much memory. Try a smaller document or photo."
        is IllegalArgumentException, is IllegalStateException -> "This file or print setting isn't supported. Try an unlocked PDF, JPEG or PNG."
        else -> "Couldn't read or send this document. Check the file and printer, then try again."
    }
}
