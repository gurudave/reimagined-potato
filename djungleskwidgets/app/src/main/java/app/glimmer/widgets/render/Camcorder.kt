package app.glimmer.widgets.render

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * How a home-video camera writes the time and date in the corner of the picture:
 * `PM 3:42` and `JUN. 11 2017`. Shared by the app's on-screen display and the widget's
 * camcorder stamp, so both read the same.
 */
object Camcorder {
    // A camera's on-screen lettering was always English and upper case, whatever the country.
    private val date = ThreadLocal.withInitial { SimpleDateFormat("MMM. dd yyyy", Locale.US) }
    private val clock24 = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.US) }
    private val clock12 = ThreadLocal.withInitial { SimpleDateFormat("a h:mm", Locale.US) }

    fun date(millis: Long): String = date.get()!!.format(Date(millis)).uppercase(Locale.US)

    fun time(millis: Long, twentyFourHour: Boolean): String =
        (if (twentyFourHour) clock24 else clock12).get()!!.format(Date(millis)).uppercase(Locale.US)
}
