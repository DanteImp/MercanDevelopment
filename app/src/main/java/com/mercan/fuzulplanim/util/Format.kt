package com.mercan.fuzulplanim.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val tr = Locale("tr", "TR")
private val moneySymbols = DecimalFormatSymbols(tr).apply { groupingSeparator='.'; decimalSeparator=',' }
private val money = DecimalFormat("#,##0.00 'TL'", moneySymbols)
private val money0 = DecimalFormat("#,##0 'TL'", moneySymbols)
fun tl(v: Double): String = money.format(v)
fun tl0(v: Double): String = money0.format(v)
fun pct(v: Double): String = DecimalFormat("0.0%").format(v)
fun monthLabel(ym: String): String = runCatching { YearMonth.parse(ym).format(DateTimeFormatter.ofPattern("MMMM yyyy", tr)) }.getOrDefault(ym)
fun dateLabel(iso: String): String = runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMMM yyyy", tr)) }.getOrDefault(iso)
fun doubleInput(s: String): Double {
    var x=s.trim().replace("₺","").replace("TL","",true).replace(" ","")
    if(x.contains(',')&&x.contains('.')) x=if(x.lastIndexOf(',')>x.lastIndexOf('.'))x.replace(".","").replace(',', '.') else x.replace(",","")
    else if(x.contains(','))x=x.replace(',', '.')
    return x.toDoubleOrNull() ?: 0.0
}
