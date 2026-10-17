package com.rookierc.boysdairy.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.domain.HourBucket
import com.rookierc.boysdairy.domain.NextTimeForecast
import com.rookierc.boysdairy.ui.common.FunCard
import com.rookierc.boysdairy.ui.common.SectionHeader
import com.rookierc.boysdairy.ui.common.quantityString
import com.rookierc.boysdairy.ui.common.rememberFormatter
import com.rookierc.boysdairy.ui.common.stringArrayResource
import com.rookierc.boysdairy.ui.common.stringResource
import com.rookierc.boysdairy.ui.theme.FunTheme
import java.time.LocalDate

/**
 * "🔮 Next-time forecast" card of the home screen. [forecast] comes from [HomeViewModel]; [today]
 * only picks the quip of the day.
 */
@Composable
internal fun ForecastCard(forecast: NextTimeForecast, today: LocalDate, modifier: Modifier = Modifier) {
    val colors = FunTheme.colors
    FunCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = colors.grapeTint,
    ) {
        SectionHeader(emoji = "🔮", title = stringResource(R.string.forecast_title))
        Spacer(Modifier.height(8.dp))
        when (forecast) {
            is NextTimeForecast.NotEnoughDays -> Text(
                text = quantityString(R.plurals.forecast_need_more, forecast.missingDays, forecast.missingDays),
                style = MaterialTheme.typography.bodyLarge,
            )

            is NextTimeForecast.Ready -> ReadyForecast(forecast = forecast, today = today)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.forecast_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun ReadyForecast(forecast: NextTimeForecast.Ready, today: LocalDate) {
    val interval = forecast.typicalIntervalDays.toCount()
    val pace = quantityString(R.plurals.forecast_pace, interval, interval)
    val time = stringResource(forecast.usualTime.forecastLabelRes())
    val dateFormatter = rememberFormatter(R.string.pattern_date_weekday)
    val daysUntil = forecast.daysUntil

    val text = when {
        daysUntil > 0L -> stringResource(
            R.string.forecast_upcoming,
            pace,
            forecast.predictedDate.format(dateFormatter),
            time,
        )

        daysUntil == 0L -> stringResource(R.string.forecast_today, pace, time)

        else -> {
            val overdue = (-daysUntil).toCount()
            quantityString(R.plurals.forecast_overdue, overdue, overdue, pace)
        }
    }
    Text(text = text, style = MaterialTheme.typography.bodyLarge)

    // A day ahead or more needs no comment; today and past it get a no-pressure quip.
    val quipsRes = when {
        daysUntil == 0L -> R.array.forecast_today_quips
        daysUntil < 0L -> R.array.forecast_overdue_quips
        else -> null
    }
    if (quipsRes != null) {
        val quips = stringArrayResource(quipsRes)
        Spacer(Modifier.height(6.dp))
        Text(
            text = quips[pickByDay(today.toEpochDay(), quips.size)],
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Day counts for plurals; absurd clock values saturate instead of wrapping. */
private fun Long.toCount(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

@StringRes
private fun HourBucket.forecastLabelRes(): Int = when (this) {
    HourBucket.NIGHT -> R.string.forecast_time_night
    HourBucket.MORNING -> R.string.forecast_time_morning
    HourBucket.AFTERNOON -> R.string.forecast_time_afternoon
    HourBucket.EVENING -> R.string.forecast_time_evening
}
