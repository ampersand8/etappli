package com.nuelto.camperexperience.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency

fun formatCurrency(amount: Double, currencyCode: String): String {
    val format = NumberFormat.getCurrencyInstance()
    runCatching { format.currency = Currency.getInstance(currencyCode) }
    return format.format(amount)
}

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun formatDate(date: LocalDate): String = date.format(dateFormatter)

fun formatDateRange(start: LocalDate, end: LocalDate?): String =
    if (end == null) "${formatDate(start)} – ongoing" else "${formatDate(start)} – ${formatDate(end)}"
