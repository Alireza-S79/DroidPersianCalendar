package com.byagowi.persiancalendar.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.byagowi.persiancalendar.*
import io.github.persiancalendar.calendar.AbstractDate
import com.byagowi.persiancalendar.entities.DeviceCalendarEvent
import io.github.persiancalendar.praytimes.Clock
import com.byagowi.persiancalendar.service.ApplicationService
import com.byagowi.persiancalendar.ui.MainActivity
import java.util.*
import java.util.concurrent.TimeUnit.MINUTES
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.ViewGroup
import com.byagowi.persiancalendar.ui.calendar.times.SunView
import com.cepmuvakkit.times.posAlgo.SunMoonPosition
import io.github.persiancalendar.praytimes.PrayTimesCalculator


private const val NOTIFICATION_ID = 1001
private var pastDate: AbstractDate? = null
private var deviceCalendarEvents = SparseArray<ArrayList<DeviceCalendarEvent>>()
@StringRes
private val timesOn4x2Shia = intArrayOf(R.string.fajr, R.string.dhuhr, R.string.sunset, R.string.maghrib, R.string.midnight)
@StringRes
private val timesOn4x2Sunna = intArrayOf(R.string.fajr, R.string.dhuhr, R.string.asr, R.string.maghrib, R.string.isha)
@IdRes
private val owghatPlaceHolderId = intArrayOf(R.id.textPlaceholder4owghat_1_4x2, R.id.textPlaceholder4owghat_2_4x2, R.id.textPlaceholder4owghat_3_4x2, R.id.textPlaceholder4owghat_4_4x2, R.id.textPlaceholder4owghat_5_4x2)

fun setDeviceCalendarEvents(context: Context) {
    try {
        deviceCalendarEvents = readDayDeviceEvents(context, -1)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun update(context: Context, updateDate: Boolean) {
    Log.d("UpdateUtils", "update")
    applyAppLanguage(context)
    val calendar = makeCalendarFromDate(Date())
    val mainCalendar = getMainCalendar()
    val date = getTodayOfCalendar(mainCalendar)
    val jdn = date.toJdn()

    val launchAppPendingIntent = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT)

    //
    // Widgets
    //
    //
    val manager = AppWidgetManager.getInstance(context) ?: return
    val colorInt = getSelectedWidgetTextColor()
    val color = Color.parseColor(colorInt)

    // en-US is our only real LTR language for now
    val isRTL = isLocaleRTL()

    // Widget 1x1
    val widget1x1 = ComponentName(context, Widget1x1::class.java)
    val widget4x1 = ComponentName(context, Widget4x1::class.java)
    val widget4x2 = ComponentName(context, Widget4x2::class.java)
    val widget2x2 = ComponentName(context, Widget2x2::class.java)
    val widgetSunView = ComponentName(context, WidgetSunView::class.java)

    if (manager.getAppWidgetIds(widgetSunView)?.isNotEmpty() == true) {
        RemoteViews(context.packageName, R.layout.widget_sunview).apply {
            val sunView = SunView(context)
            sunView.layoutParams = ViewGroup.LayoutParams(800, 800)
            sunView.layout(sunView.left, sunView.top, sunView.right, sunView.bottom)

            val b = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)

//            getCoordinate(context)?.run {
//                val moonPhase = SunMoonPosition(getTodayJdn().toDouble(), latitude,
//                    longitude, .0, .0).moonPhase
//                val prayTimes = PrayTimesCalculator.calculate(getCalculationMethod(), Date(), coordinate)
//                sunView.setSunriseSunsetMoonPhase(prayTimes, moonPhase)
//            }
//            sunView.draw(c)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.RED
            paint.style = Paint.Style.FILL
            c.drawRect(0f, 0f, 100f, 100f, paint)


            setTextColor(R.id.setLocation, color);
//            setViewVisibility(R.id.setLocation, coordinate == null ? View.VISIBLE : View.GONE);
//            setViewVisibility(R.id.sunView, coordinate == null ? View.GONE : View.VISIBLE);
            setImageViewBitmap(R.id.sunView, b)
            setBackgroundColor(this, R.id.widget_sunview)
            setContentDescription(R.id.sunView, sunView.contentDescription)
            setOnClickPendingIntent(R.id.widget_sunview, launchAppPendingIntent)
            manager.updateAppWidget(widgetSunView, this)
        }
    }

    if (manager.getAppWidgetIds(widget1x1)?.isNotEmpty() == true) {
        RemoteViews(context.packageName, R.layout.widget1x1).apply {
            setTextColor(R.id.textPlaceholder1_1x1, color)
            setTextColor(R.id.textPlaceholder2_1x1, color)
            setTextViewText(R.id.textPlaceholder1_1x1,
                    formatNumber(date.dayOfMonth))
            setTextViewText(R.id.textPlaceholder2_1x1,
                    getMonthName(date))
            setOnClickPendingIntent(R.id.widget_layout1x1, launchAppPendingIntent)
            setBackgroundColor(this, R.id.widget_layout1x1)
            manager.updateAppWidget(widget1x1, this)
        }
    }

    var dateHasChanged = false
    if (pastDate == null || pastDate != date || updateDate) {
        Log.d("UpdateUtils", "date has changed")

        loadAlarms(context)
        pastDate = date
        dateHasChanged = true
        setDeviceCalendarEvents(context)
    }

    val weekDayName = getWeekDayName(date)
    var title = dayTitleSummary(date)
    val shiftWorkTitle = getShiftWorkTitle(jdn, false)
    if (shiftWorkTitle.isNotEmpty())
        title += " ($shiftWorkTitle)"
    var subtitle = dateStringOfOtherCalendars(jdn, getSpacedComma())

    val currentClock = Clock(calendar)
    var owghat = ""
    @StringRes
    val nextOwghatId = getNextOwghatTimeId(currentClock, dateHasChanged)
    if (nextOwghatId != 0) {
        owghat = context.getString(nextOwghatId) + ": " +
                getFormattedClock(getClockFromStringId(nextOwghatId), false)
        if (isShownOnWidgets("owghat_location")) {
            val cityName = getCityName(context, false)
            if (cityName.isNotEmpty()) {
                owghat = "$owghat ($cityName)"
            }
        }
    }
    val events = getEvents(jdn, deviceCalendarEvents)

    val enableClock = isWidgetClock() && Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1
    val isCenterAligned = isCenterAlignWidgets()

    if (manager.getAppWidgetIds(widget4x1)?.isNotEmpty() == true || manager.getAppWidgetIds(widget2x2)?.isNotEmpty() == true) {
        val remoteViews4: RemoteViews
        val remoteViews2: RemoteViews
        if (enableClock) {
            if (!isIranTime()) {
                remoteViews4 = RemoteViews(context.packageName, if (isCenterAligned) R.layout.widget4x1_clock_center else R.layout.widget4x1_clock)
                remoteViews2 = RemoteViews(context.packageName, if (isCenterAligned) R.layout.widget2x2_clock_center else R.layout.widget2x2_clock)
            } else {
                remoteViews4 = RemoteViews(context.packageName, if (isCenterAligned) R.layout.widget4x1_clock_iran_center else R.layout.widget4x1_clock_iran)
                remoteViews2 = RemoteViews(context.packageName, if (isCenterAligned) R.layout.widget2x2_clock_iran_center else R.layout.widget2x2_clock_iran)
            }
        } else {
            remoteViews4 = RemoteViews(context.packageName, if (isCenterAligned) R.layout.widget4x1_center else R.layout.widget4x1)
            remoteViews2 = RemoteViews(context.packageName, if (isCenterAligned) R.layout.widget2x2_center else R.layout.widget2x2)
        }

        val mainDateString = formatDate(date)

        remoteViews4.run {
            // Widget 4x1
            setBackgroundColor(this, R.id.widget_layout4x1)
            setTextColor(R.id.textPlaceholder1_4x1, color)
            setTextColor(R.id.textPlaceholder2_4x1, color)
            setTextColor(R.id.textPlaceholder3_4x1, color)

            var text2: String
            var text3 = ""

            if (enableClock) {
                text2 = title
                if (isIranTime()) {
                    text3 = "(" + context.getString(R.string.iran_time) + ")"
                }
            } else {
                remoteViews4.setTextViewText(R.id.textPlaceholder1_4x1, weekDayName)
                text2 = mainDateString
            }
            if (isShownOnWidgets("other_calendars")) {
                text2 += getSpacedComma() + subtitle
            }

            setTextViewText(R.id.textPlaceholder2_4x1, text2)
            setTextViewText(R.id.textPlaceholder3_4x1, text3)
            setOnClickPendingIntent(R.id.widget_layout4x1, launchAppPendingIntent)
            manager.updateAppWidget(widget4x1, this)
        }

        remoteViews2.run {
            var text2: String
            // Widget 2x2
            setBackgroundColor(this, R.id.widget_layout2x2)
            setTextColor(R.id.time_2x2, color)
            setTextColor(R.id.date_2x2, color)
            setTextColor(R.id.event_2x2, color)
            setTextColor(R.id.owghat_2x2, color)

            text2 = if (enableClock) {
                title
            } else {
                setTextViewText(R.id.time_2x2, weekDayName)
                mainDateString
            }

            val holidays = getEventsTitle(events,
                holiday = true,
                compact = true,
                showDeviceCalendarEvents = true,
                insertRLM = isRTL
            )
            if (holidays.isNotEmpty()) {
                setTextViewText(R.id.holiday_2x2, holidays)
                if (isTalkBackEnabled()) {
                    setContentDescription(R.id.holiday_2x2,
                            context.getString(R.string.holiday_reason) + " " +
                                    holidays)
                }
                setViewVisibility(R.id.holiday_2x2, View.VISIBLE)
            } else {
                setViewVisibility(R.id.holiday_2x2, View.GONE)
            }

            val nonHolidays = getEventsTitle(events,
                holiday = false,
                compact = true,
                showDeviceCalendarEvents = true,
                insertRLM = isRTL
            )
            if (isShownOnWidgets("non_holiday_events") && nonHolidays.isNotEmpty()) {
                setTextViewText(R.id.event_2x2, nonHolidays)
                setViewVisibility(R.id.event_2x2, View.VISIBLE)
            } else {
                setViewVisibility(R.id.event_2x2, View.GONE)
            }

            if (isShownOnWidgets("owghat") && owghat.isNotEmpty()) {
                setTextViewText(R.id.owghat_2x2, owghat)
                setViewVisibility(R.id.owghat_2x2, View.VISIBLE)
            } else {
                setViewVisibility(R.id.owghat_2x2, View.GONE)
            }

            if (isShownOnWidgets("other_calendars")) {
                text2 = text2 + "\n" + subtitle + "\n" + getZodiacInfo(context, jdn, true)
            }
            setTextViewText(R.id.date_2x2, text2)

            setOnClickPendingIntent(R.id.widget_layout2x2, launchAppPendingIntent)
            manager.updateAppWidget(widget2x2, this)
        }
    }

    //region Widget 4x2
    if (manager.getAppWidgetIds(widget4x2)?.isNotEmpty() == true) {
        val remoteViews4x2: RemoteViews = if (enableClock) {
            if (!isIranTime()) {
                RemoteViews(context.packageName, R.layout.widget4x2_clock)
            } else {
                RemoteViews(context.packageName, R.layout.widget4x2_clock_iran)
            }
        } else {
            RemoteViews(context.packageName, R.layout.widget4x2)
        }
        setBackgroundColor(remoteViews4x2, R.id.widget_layout4x2)

        remoteViews4x2.run {
            setTextColor(R.id.textPlaceholder0_4x2, color)
            setTextColor(R.id.textPlaceholder1_4x2, color)
            setTextColor(R.id.textPlaceholder2_4x2, color)
            setTextColor(R.id.textPlaceholder4owghat_3_4x2, color)
            setTextColor(R.id.textPlaceholder4owghat_1_4x2, color)
            setTextColor(R.id.textPlaceholder4owghat_4_4x2, color)
            setTextColor(R.id.textPlaceholder4owghat_2_4x2, color)
            setTextColor(R.id.textPlaceholder4owghat_5_4x2, color)

            var text2 = formatDate(date)
            if (enableClock)
                text2 = getWeekDayName(date) + "\n" + text2
            else
                setTextViewText(R.id.textPlaceholder0_4x2, weekDayName)

            if (isShownOnWidgets("other_calendars")) {
                text2 = text2 + "\n" + dateStringOfOtherCalendars(jdn, "\n")
            }

            setTextViewText(R.id.textPlaceholder1_4x2, text2)

            if (nextOwghatId != 0) {
                @StringRes
                val timesOn4x2 = if (isShiaPrayTimeCalculationSelected()) timesOn4x2Shia else timesOn4x2Sunna
                // Set text of owghats
                for (i in owghatPlaceHolderId.indices) {
                    setTextViewText(owghatPlaceHolderId[i],
                            context.getString(timesOn4x2[i]) + "\n" +
                                    getFormattedClock(getClockFromStringId(timesOn4x2[i]), false))
                    setTextColor(owghatPlaceHolderId[i],
                            if (timesOn4x2[i] == nextOwghatId)
                                Color.RED
                            else
                                color)
                }

                var difference = getClockFromStringId(nextOwghatId).toInt() - currentClock.toInt()
                if (difference < 0) difference = 60 * 24 - difference

                val hrs = (MINUTES.toHours(difference.toLong()) % 24).toInt()
                val min = (MINUTES.toMinutes(difference.toLong()) % 60).toInt()

                val remainingTime = when {
                    hrs == 0 -> String.format(context.getString(R.string.n_minutes), formatNumber(min))
                    min == 0 -> String.format(context.getString(R.string.n_hours), formatNumber(hrs))
                    else -> String.format(context.getString(R.string.n_minutes_and_hours), formatNumber(hrs), formatNumber(min))
                }

                setTextViewText(R.id.textPlaceholder2_4x2,
                        String.format(context.getString(R.string.n_till),
                                remainingTime, context.getString(nextOwghatId)))
                setTextColor(R.id.textPlaceholder2_4x2, color)
            } else {
                setTextViewText(R.id.textPlaceholder2_4x2, context.getString(R.string.ask_user_to_set_location))
                setTextColor(R.id.textPlaceholder2_4x2, color)
            }

            setOnClickPendingIntent(R.id.widget_layout4x2, launchAppPendingIntent)

            manager.updateAppWidget(widget4x2, this)
        }
    }
    //endregion


    //
    // Permanent Notification Bar and DashClock Data Extension Update
    //
    //

    // Prepend a right-to-left mark character to Android with sane text rendering stack
    // to resolve a bug seems some Samsung devices have with characters with weak direction,
    // digits being at the first of string on
    if (isRTL && Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        title = RLM + title
        if (subtitle.isNotEmpty()) {
            subtitle = RLM + subtitle
        }
    }

    if (isNotifyDate()) {
        val notificationManager = context.getSystemService<NotificationManager>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_ID.toString(),
                    context.getString(R.string.app_name), importance)
            channel.setShowBadge(false)
            notificationManager?.createNotificationChannel(channel)
        }

        // Don't remove this condition checking ever
        if (isTalkBackEnabled()) {
            // Don't use isToday, per a feedback
            subtitle = getA11yDaySummary(context, jdn, false,
                    deviceCalendarEvents,
                withZodiac = true, withOtherCalendars = true, withTitle = false
            )
            if (owghat.isNotEmpty()) {
                subtitle += getSpacedComma()
                subtitle += owghat
            }
        }

        var builder = NotificationCompat.Builder(context, NOTIFICATION_ID.toString())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSmallIcon(getDayIconResource(date.dayOfMonth))
                .setOngoing(true)
                .setWhen(0)
                .setContentIntent(launchAppPendingIntent)
                .setVisibility(if (isNotifyDateOnLockScreen())
                    NotificationCompat.VISIBILITY_PUBLIC
                else
                    NotificationCompat.VISIBILITY_SECRET)
                .setColor(-0x9f8275)
                .setColorized(true)
                .setContentTitle(title)
                .setContentText(subtitle)

        // Night mode doesn't our custom notification in Samsung and HTC One UI, let's detect them
        val isSamsungOrHtcNightMode = (Build.BRAND == "samsung" || Build.BRAND == "htc") && isNightModeEnabled(context)

        if (!isTalkBackEnabled() && !isSamsungOrHtcNightMode &&
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || BuildConfig.DEBUG)) {
            val cv = RemoteViews(context.packageName, if (isRTL) {
                R.layout.custom_notification
            } else {
                R.layout.custom_notification_ltr
            }).apply {
                setTextViewText(R.id.title, title)
                setTextViewText(R.id.body, subtitle)
            }


            val bcv = RemoteViews(context.packageName, if (isRTL) {
                R.layout.custom_notification_big
            } else {
                R.layout.custom_notification_big_ltr
            }).apply {
                setTextViewText(R.id.title, title)

                if (subtitle.isNotEmpty()) {
                    setTextViewText(R.id.body, subtitle)
                } else {
                    setViewVisibility(R.id.body, View.GONE)
                }

                val holidays = getEventsTitle(events,
                    holiday = true,
                    compact = true,
                    showDeviceCalendarEvents = true,
                    insertRLM = isRTL
                )
                if (holidays.isNotEmpty()) {
                    setTextViewText(R.id.holidays, holidays)
                } else {
                    setViewVisibility(R.id.holidays, View.GONE)
                }

                val nonHolidays = getEventsTitle(events,
                    holiday = false,
                    compact = true,
                    showDeviceCalendarEvents = true,
                    insertRLM = isRTL
                )
                if (isShownOnWidgets("non_holiday_events") && nonHolidays.isNotEmpty()) {
                    setTextViewText(R.id.nonholidays, nonHolidays.trim { it <= ' ' })
                } else {
                    setViewVisibility(R.id.nonholidays, View.GONE)
                }

                if (isShownOnWidgets("owghat") && owghat.isNotEmpty()) {
                    setTextViewText(R.id.owghat, owghat)
                } else {
                    setViewVisibility(R.id.owghat, View.GONE)
                }
            }

            builder = builder
                    .setCustomContentView(cv)
                    .setCustomBigContentView(bcv)
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        }

        if (BuildConfig.DEBUG) {
            builder = builder.setWhen(Calendar.getInstance().timeInMillis)
        }

        if (goForWorker()) {
            notificationManager?.notify(NOTIFICATION_ID, builder.build())
        } else {
            try {
                ApplicationService.getInstance()?.startForeground(NOTIFICATION_ID, builder.build())
            } catch (e: Exception) {
                Log.e("UpdateUtils", "failed to start service with the notification", e)
            }

        }
    } else if (goForWorker()) {
        context.getSystemService<NotificationManager>()?.cancel(NOTIFICATION_ID)
    }
}

private fun setBackgroundColor(remoteView: RemoteViews, @IdRes layoutId: Int) {
    val colorInt = getSelectedWidgetBackgroundColor()
    remoteView.setInt(layoutId, "setBackgroundColor", Color.parseColor(colorInt))
}
