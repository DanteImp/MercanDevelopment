package com.mercan.fuzulplanim.importer

import android.content.Context
import android.net.Uri
import com.mercan.fuzulplanim.data.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.abs

data class BudgetImportResult(
    val fixedExpenses: List<FixedExpenseEntity>,
    val installments: List<InstallmentEntity>,
    val months: List<MonthlyPlanEntity>,
    val contract: FuzulContractEntity?,
    val transactions: List<TransactionEntity>,
    val cards: List<CardEntity>,
    val summary: String
)

data class ParsedStatementRow(
    val date: String,
    val description: String,
    val amount: Double,
    val category: String,
    val source: String,
    val raw: String = ""
)

data class StatementPreview(
    val fileName: String,
    val fileType: String,
    val rows: List<ParsedStatementRow>,
    val note: String = ""
)

object FileImportService {
    fun fileName(context: Context, uri: Uri): String {
        var name = "dosya"
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { c ->
            if (c.moveToFirst()) name = c.getString(0) ?: name
        }
        return name
    }

    fun readBytes(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Dosya açılamadı")

    fun importBudgetWorkbook(context: Context, uri: Uri): BudgetImportResult {
        val bytes = readBytes(context, uri)
        val xlsx = SimpleXlsx(bytes)
        val monthly = parseMonthlyPlan(xlsx.sheet("Aylık Plan"))
        val firstMonth = monthly.minByOrNull { it.month }?.month ?: YearMonth.now().toString()
        val fixed = parseFixedExpenses(xlsx.sheet("SABIT_GİDER"), firstMonth)
        val inst = parseInstallments(xlsx.sheet("Taksit Takibi"))
        val contract = parseContract(xlsx.sheet("FUZUL_SOZLESME"))
        val tx = parseTransactions(xlsx.sheet("Tüm İşlemler"), fileName(context, uri))
        val cards = inst.map { it.bank }
            .filter { it.isNotBlank() }
            .distinct()
            .map { bank ->
                CardEntity(
                    id = "xlsx-card-${hash(bank).take(12)}",
                    bank = bank,
                    name = bank
                )
            }

        if (monthly.isEmpty() && fixed.isEmpty() && contract == null) {
            error("Bu dosya beklenen bütçe Excel yapısını içermiyor")
        }

        return BudgetImportResult(
            fixedExpenses = fixed,
            installments = inst,
            months = monthly,
            contract = contract,
            transactions = tx,
            cards = cards,
            summary = "${fixed.size} sabit gider • ${inst.size} taksit • ${monthly.size} aylık plan • ${tx.size} işlem"
        )
    }

    fun parseStatement(context: Context, uri: Uri): StatementPreview {
        val name = fileName(context, uri)
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val bytes = readBytes(context, uri)
        return when (ext) {
            "xlsx" -> parseGenericXlsx(name, bytes)
            "csv" -> parseCsv(name, bytes.toString(StandardCharsets.UTF_8))
            "txt" -> parseText(name, bytes.toString(StandardCharsets.UTF_8), "TXT")
            "pdf" -> parsePdf(name, bytes)
            else -> {
                val text = runCatching { bytes.toString(StandardCharsets.UTF_8) }.getOrDefault("")
                if (text.contains(";") || text.contains(",")) {
                    parseCsv(name, text)
                } else {
                    parseText(name, text, ext.uppercase())
                }
            }
        }
    }

    fun toTransactions(
        preview: StatementPreview,
        sourceOverride: String = ""
    ): List<TransactionEntity> = preview.rows.map { row ->
        val source = sourceOverride.ifBlank {
            row.source.ifBlank { preview.fileName.substringBeforeLast('.') }
        }
        TransactionEntity(
            id = fingerprint(row.date, source, row.description, row.amount),
            date = row.date,
            source = source,
            description = row.description,
            amount = row.amount,
            category = row.category,
            subCategory = "",
            includedInBudget = true,
            transactionType = if (row.amount >= 0) "INCOME" else "EXPENSE",
            sourceFile = preview.fileName,
            note = row.raw.take(300)
        )
    }

    private fun parseFixedExpenses(
        rows: List<List<String?>>,
        firstMonth: String
    ): List<FixedExpenseEntity> {
        val out = mutableListOf<FixedExpenseEntity>()
        for (i in 1 until rows.size) {
            val row = rows[i]
            val category = row.getOrNull(0).clean()
            val name = row.getOrNull(1).clean()
            val amount = row.getOrNull(2).num()
            val source = row.getOrNull(3).clean()
            if (category.isBlank() || name.isBlank() || amount == 0.0) continue

            val categoryLow = category.lowercase(Locale("tr", "TR"))
            val nameLow = name.lowercase(Locale("tr", "TR"))
            if (categoryLow.contains("bordro") || nameLow.contains("öz.sğl") || nameLow.contains("corolla club")) continue

            val group = when {
                categoryLow.contains("servis") || categoryLow.contains("harç") -> "education"
                categoryLow.contains("kuaf") -> "other"
                categoryLow.contains("ulaş") || categoryLow.contains("yakıt") -> "transport"
                categoryLow.contains("market") || categoryLow.contains("giyim") || categoryLow.contains("yeme") -> "living"
                else -> "bills"
            }

            out += FixedExpenseEntity(
                id = "xlsx-fixed-${hash("$category|$name|$source").take(20)}",
                name = name,
                category = category,
                amount = amount,
                baselineAmount = amount,
                paymentSource = source,
                budgetGroup = group,
                frequency = "MONTHLY",
                startMonth = firstMonth,
                endMonth = "",
                dayOfMonth = 1,
                inflationLinked = false,
                annualInflationPct = 0.0,
                active = true,
                imported = true
            )
        }
        return out
    }

    private fun parseInstallments(rows: List<List<String?>>): List<InstallmentEntity> {
        val out = mutableListOf<InstallmentEntity>()
        for (i in 3 until rows.size) {
            val row = rows[i]
            val bank = row.getOrNull(0).clean()
            if (bank.isBlank() || bank.contains("TOPLAM", true) || bank.contains("AYLIK", true)) continue
            val merchant = row.getOrNull(1).clean()
            val monthly = row.getOrNull(4).num()
            if (merchant.isBlank() || monthly == 0.0) continue

            val total = row.getOrNull(3).num()
            val totalCount = row.getOrNull(5).num().toInt()
            val paid = row.getOrNull(6).num().toInt()
            val remaining = row.getOrNull(8).num()

            out += InstallmentEntity(
                id = "xlsx-inst-${hash("$bank|$merchant|$total|$monthly").take(22)}",
                bank = bank,
                merchant = merchant,
                category = row.getOrNull(2).clean(),
                totalAmount = total,
                monthlyAmount = monthly,
                totalInstallments = totalCount,
                paidInstallments = paid,
                remainingDebt = remaining,
                nextMonth = excelMonth(row.getOrNull(9)),
                endMonth = excelMonth(row.getOrNull(10)),
                active = true,
                imported = true
            )
        }
        return out
    }

    private fun parseMonthlyPlan(rows: List<List<String?>>): List<MonthlyPlanEntity> {
        val out = mutableListOf<MonthlyPlanEntity>()
        for (i in 1 until rows.size) {
            val row = rows[i]
            val month = excelMonth(row.getOrNull(1))
            if (month.isBlank()) continue
            val mete = row.getOrNull(2).num()
            val esma = row.getOrNull(3).num()
            if (mete == 0.0 && esma == 0.0 && row.getOrNull(15).num() == 0.0) continue

            out += MonthlyPlanEntity(
                month = month,
                meteIncome = mete,
                esmaIncome = esma,
                otherIncome = row.getOrNull(4).num(),
                orgFee = row.getOrNull(6).num(),
                billsSnapshot = row.getOrNull(7).num(),
                living = row.getOrNull(8).num(),
                cardsSnapshot = row.getOrNull(9).num(),
                educationSnapshot = row.getOrNull(10).num(),
                transport = row.getOrNull(11).num(),
                otherSnapshot = row.getOrNull(12).num(),
                fuzulPayment = row.getOrNull(15).num(),
                fuzulPaid = false
            )
        }
        return out
    }

    private fun parseContract(rows: List<List<String?>>): FuzulContractEntity? {
        if (rows.isEmpty()) return null
        val map = mutableMapOf<String, String>()
        rows.forEach { row ->
            val key = row.getOrNull(0).clean()
            val value = row.getOrNull(1).clean()
            if (key.isNotBlank() && value.isNotBlank()) {
                map[key.lowercase(Locale("tr", "TR"))] = value
            }
        }

        fun find(token: String): String =
            map.entries.firstOrNull { it.key.contains(token) }?.value.orEmpty()

        val amount = find("sözleşme tutarı").num()
        if (amount == 0.0) return null

        val openingFromPrimary = rows.firstOrNull {
            it.getOrNull(5).clean().contains("Peşinat sonrası elde kalan", true)
        }?.getOrNull(6).num() ?: 0.0

        val openingFromFallback = rows.firstOrNull {
            it.getOrNull(5).clean().contains("Modelde kullanılan başlangıç devir", true)
        }?.getOrNull(6).num() ?: 0.0

        val opening = if (openingFromPrimary != 0.0) openingFromPrimary else openingFromFallback

        return FuzulContractEntity(
            id = 1,
            fundingType = find("finansman türü"),
            amount = amount,
            downPayment = find("peşinat").num(),
            organizationFee = find("organizasyon ücreti").num(),
            contractDate = excelDate(find("sözleşme tarihi")),
            allocationPeriod = find("tahsisat dönemi").num().toInt(),
            allocationDate = excelDate(find("tahsisat tarihi")),
            installmentCount = 19,
            lastPaymentDate = excelDate(find("son taksit tarihi")),
            openingCash = opening
        )
    }

    private fun parseTransactions(
        rows: List<List<String?>>,
        sourceWorkbook: String
    ): List<TransactionEntity> {
        if (rows.isEmpty()) return emptyList()
        val out = mutableListOf<TransactionEntity>()
        for (i in 1 until rows.size) {
            val row = rows[i]
            val date = excelDate(row.getOrNull(0).clean())
            val source = row.getOrNull(1).clean()
            val description = row.getOrNull(2).clean()
            val amount = row.getOrNull(3).num()
            if (date.isBlank() || description.isBlank() || amount == 0.0) continue

            out += TransactionEntity(
                id = fingerprint(date, source, description, amount),
                date = date,
                source = source,
                description = description,
                amount = amount,
                category = row.getOrNull(4).clean().ifBlank { classify(description) },
                subCategory = row.getOrNull(5).clean(),
                includedInBudget = !row.getOrNull(6).clean().equals("Hayır", true),
                transactionType = if (amount >= 0) "INCOME" else "EXPENSE",
                sourceFile = row.getOrNull(10).clean().ifBlank { sourceWorkbook },
                note = row.getOrNull(11).clean()
            )
        }
        return out
    }

    private fun parseGenericXlsx(name: String, bytes: ByteArray): StatementPreview {
        val workbook = SimpleXlsx(bytes)
        val sheetName = workbook.sheetNames().firstOrNull() ?: error("Excel sayfası bulunamadı")
        return StatementPreview(
            fileName = name,
            fileType = "XLSX",
            rows = parseTabularRows(workbook.sheet(sheetName), name.substringBeforeLast('.')),
            note = "Sayfa: $sheetName"
        )
    }

    private fun parseCsv(name: String, text: String): StatementPreview {
        val lines = text.replace("\uFEFF", "").lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) error("CSV boş")
        val delimiter = listOf(';', ',', '\t').maxByOrNull { delimiterCandidate ->
            lines.take(5).sumOf { line -> line.count { it == delimiterCandidate } }
        } ?: ';'
        val rows = lines.map { line ->
            parseDelimitedLine(line, delimiter).map { value -> value as String? }
        }
        return StatementPreview(
            fileName = name,
            fileType = "CSV",
            rows = parseTabularRows(rows, name.substringBeforeLast('.'))
        )
    }

    private fun parseText(name: String, text: String, type: String): StatementPreview =
        StatementPreview(
            fileName = name,
            fileType = type,
            rows = parseLines(text.lines(), name.substringBeforeLast('.')),
            note = "Metin satırlarından ayrıştırıldı"
        )

    private fun parsePdf(name: String, bytes: ByteArray): StatementPreview {
        val doc = PDDocument.load(ByteArrayInputStream(bytes))
        val text = doc.use { PDFTextStripper().getText(it) }
        if (text.isBlank()) error("PDF metin içermiyor; taranmış görüntü olabilir")
        return StatementPreview(
            fileName = name,
            fileType = "PDF",
            rows = parseLines(text.lines(), name.substringBeforeLast('.')),
            note = "Metin tabanlı PDF"
        )
    }

    private fun parseTabularRows(
        rows: List<List<String?>>,
        defaultSource: String
    ): List<ParsedStatementRow> {
        if (rows.isEmpty()) return emptyList()

        var headerIndex = -1
        var dateCol = -1
        var descCol = -1
        var amountCol = -1
        var debitCol = -1
        var creditCol = -1
        var sourceCol = -1

        for (i in 0 until minOf(rows.size, 25)) {
            val cells = rows[i].map { it.clean().lowercase(Locale("tr", "TR")) }
            val date = cells.indexOfFirst { it.contains("tarih") || it == "date" }
            val desc = cells.indexOfFirst {
                it.contains("açıklama") || it.contains("işlem") || it.contains("description") || it.contains("merchant")
            }
            val amount = cells.indexOfFirst {
                it == "tutar" || it.contains("işlem tutarı") || it.contains("amount")
            }
            val debit = cells.indexOfFirst { it.contains("borç") || it.contains("debit") }
            val credit = cells.indexOfFirst { it.contains("alacak") || it.contains("credit") }
            val source = cells.indexOfFirst { it.contains("kaynak") || it.contains("hesap") || it.contains("kart") }

            if (date >= 0 && desc >= 0 && (amount >= 0 || debit >= 0 || credit >= 0)) {
                headerIndex = i
                dateCol = date
                descCol = desc
                amountCol = amount
                debitCol = debit
                creditCol = credit
                sourceCol = source
                break
            }
        }

        if (headerIndex < 0) {
            return parseLines(
                rows.map { row -> row.joinToString(" ") { it.clean() } },
                defaultSource
            )
        }

        val out = mutableListOf<ParsedStatementRow>()
        for (i in headerIndex + 1 until rows.size) {
            val row = rows[i]
            val date = parseDateFlexible(row.getOrNull(dateCol).clean())
            val description = row.getOrNull(descCol).clean()
            if (date.isBlank() || description.isBlank()) continue

            val amount = if (amountCol >= 0) {
                money(row.getOrNull(amountCol).clean())
            } else {
                val debit = if (debitCol >= 0) money(row.getOrNull(debitCol).clean()) else 0.0
                val credit = if (creditCol >= 0) money(row.getOrNull(creditCol).clean()) else 0.0
                if (credit != 0.0) abs(credit) else -abs(debit)
            }
            if (amount == 0.0) continue

            val source = if (sourceCol >= 0) row.getOrNull(sourceCol).clean() else defaultSource
            out += ParsedStatementRow(
                date = date,
                description = description,
                amount = amount,
                category = classify(description),
                source = source.ifBlank { defaultSource },
                raw = row.joinToString(" | ") { it.clean() }
            )
        }
        return out
    }

    private fun parseLines(lines: List<String>, defaultSource: String): List<ParsedStatementRow> {
        val dateRegex = Regex("\\b(\\d{2}[./-]\\d{2}[./-](?:20)?\\d{2})\\b")
        val amountRegex = Regex("[-+]?\\d{1,3}(?:[. ]\\d{3})*(?:,\\d{2})|[-+]?\\d+(?:[.,]\\d{2})")
        val out = mutableListOf<ParsedStatementRow>()

        for (line in lines) {
            val dateMatch = dateRegex.find(line) ?: continue
            val date = parseDateFlexible(dateMatch.groupValues[1])
            if (date.isBlank()) continue

            val amounts = amountRegex.findAll(line).map { it.value }.toList()
            if (amounts.isEmpty()) continue
            val rawAmount = amounts.last()
            var amount = money(rawAmount)
            if (amount == 0.0) continue

            val lower = line.lowercase(Locale("tr", "TR"))
            if (!rawAmount.startsWith("-") &&
                (lower.contains("harcama") || lower.contains("ödeme") || lower.contains("borç") || lower.contains("alışveriş"))
            ) {
                amount = -abs(amount)
            }

            val description = line
                .replace(dateMatch.value, "")
                .replace(rawAmount, "")
                .trim(' ', '-', '|', ':')
            if (description.length < 2) continue

            out += ParsedStatementRow(
                date = date,
                description = description,
                amount = amount,
                category = classify(description),
                source = defaultSource,
                raw = line.take(500)
            )
        }

        return out.distinctBy { "${it.date}|${it.description}|${it.amount}" }
    }

    private fun classify(description: String): String {
        val s = description.lowercase(Locale("tr", "TR"))
        return when {
            listOf("migros", "carrefour", "a101", "bim", "şok", "sok market", "macrocenter").any { s.contains(it) } -> "Market/Gıda"
            listOf("vodafone", "turkcell", "türk telekom", "turk telekom", "tt mobil").any { s.contains(it) } -> "Ev/Faturalar - Telefon"
            listOf("turknet", "kablo tv", "türksat").any { s.contains(it) } -> "Ev/Faturalar - İnternet"
            listOf("sepaş", "enerjisa").any { s.contains(it) } -> "Ev/Faturalar - Elektrik"
            listOf("aksa", "akmercan", "doğalgaz", "dogalgaz").any { s.contains(it) } -> "Ev/Faturalar - Doğalgaz"
            listOf("saski", "su fatur").any { s.contains(it) } -> "Ev/Faturalar - Su"
            listOf("hepsiburada", "hepsipay", "trendyol", "amazon").any { s.contains(it) } -> "E-Ticaret/Diğer"
            listOf("ets tur", "etstur", "otel", "hotel").any { s.contains(it) } -> "Seyahat/Tatil"
            listOf("mavi", "mango", "penti", "bershka", "özdilek", "ozdilek", "suwen").any { s.contains(it) } -> "Giyim/Alışveriş"
            listOf("shell", "opet", "petrol ofisi", "bp ", "totalenergies").any { s.contains(it) } -> "Ulaşım/Yakıt"
            listOf("netflix", "spotify", "google one", "chatgpt", "prime").any { s.contains(it) } -> "Dijital Abonelik"
            else -> "Diğer"
        }
    }

    private fun parseDelimitedLine(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (quoted && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    quoted = !quoted
                }
            } else if (c == delimiter && !quoted) {
                out += sb.toString().trim()
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        out += sb.toString().trim()
        return out
    }

    private fun fingerprint(date: String, source: String, desc: String, amount: Double): String =
        hash("$date|${source.lowercase()}|${desc.lowercase()}|${"%.2f".format(Locale.US, amount)}")

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun parseDateFlexible(raw: String): String {
        val s = raw.trim()
        if (s.matches(Regex("\\d+(?:\\.0+)?"))) return excelDate(s)
        val patterns = listOf("dd.MM.yyyy", "dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "dd.MM.yy", "dd/MM/yy")
        for (pattern in patterns) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(pattern)).toString()
            } catch (_: DateTimeParseException) {
            }
        }
        return ""
    }

    private fun excelDate(raw: String): String {
        val n = raw.num()
        if (n <= 0) return runCatching { LocalDate.parse(raw).toString() }.getOrDefault("")
        return LocalDate.of(1899, 12, 30).plusDays(n.toLong()).toString()
    }

    private fun excelMonth(raw: String?): String {
        val date = excelDate(raw.clean())
        return date.takeIf { it.length >= 7 }?.substring(0, 7).orEmpty()
    }

    private fun money(s: String): Double = s.num()
}

private class SimpleXlsx(bytes: ByteArray) {
    private val entries = mutableMapOf<String, ByteArray>()
    private val sharedStrings = mutableListOf<String>()
    private val sheets = linkedMapOf<String, String>()

    init {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        parseSharedStrings()
        parseWorkbook()
    }

    fun sheetNames(): List<String> = sheets.keys.toList()

    fun sheet(name: String): List<List<String?>> {
        val path = sheets[name] ?: return emptyList()
        val bytes = entries[path] ?: return emptyList()
        return parseSheet(bytes)
    }

    private fun parseSharedStrings() {
        val bytes = entries["xl/sharedStrings.xml"] ?: return
        val parser = parser(bytes)
        var inSi = false
        var inText = false
        val sb = StringBuilder()

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> {
                        inSi = true
                        sb.setLength(0)
                    }
                    "t" -> if (inSi) inText = true
                }
                XmlPullParser.TEXT -> if (inSi && inText) sb.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "si" -> {
                        sharedStrings += sb.toString()
                        inSi = false
                    }
                }
            }
            parser.next()
        }
    }

    private fun parseWorkbook() {
        val rels = mutableMapOf<String, String>()
        entries["xl/_rels/workbook.xml.rels"]?.let { bytes ->
            val parser = parser(bytes)
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                    val id = parser.getAttributeValue(null, "Id").orEmpty()
                    var target = parser.getAttributeValue(null, "Target").orEmpty()
                    target = when {
                        target.startsWith("/") -> target.removePrefix("/")
                        target.startsWith("xl/") -> target
                        else -> "xl/$target"
                    }
                    rels[id] = target.replace("../", "")
                }
                parser.next()
            }
        }

        val workbook = entries["xl/workbook.xml"] ?: return
        val parser = parser(workbook)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name").orEmpty()
                val rid = (0 until parser.attributeCount).firstNotNullOfOrNull { index ->
                    if (parser.getAttributeName(index) == "id") parser.getAttributeValue(index) else null
                }.orEmpty()
                rels[rid]?.let { path ->
                    if (name.isNotBlank()) sheets[name] = path
                }
            }
            parser.next()
        }
    }

    private fun parseSheet(bytes: ByteArray): List<List<String?>> {
        val rows = mutableListOf<MutableList<String?>>()
        val parser = parser(bytes)
        var cellRef = ""
        var type = ""
        var value: String? = null
        var inValue = false
        var inInlineText = false
        var inline = StringBuilder()

        fun ensure(row: Int, col: Int) {
            while (rows.size <= row) rows.add(mutableListOf())
            while (rows[row].size <= col) rows[row].add(null)
        }

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "c" -> {
                        cellRef = parser.getAttributeValue(null, "r").orEmpty()
                        type = parser.getAttributeValue(null, "t").orEmpty()
                        value = null
                        inline = StringBuilder()
                    }
                    "v" -> inValue = true
                    "t" -> if (type == "inlineStr") inInlineText = true
                }
                XmlPullParser.TEXT -> {
                    if (inValue) value = parser.text
                    if (inInlineText) inline.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        val (row, col) = refToIndex(cellRef)
                        ensure(row, col)
                        val raw = if (type == "inlineStr") inline.toString() else value.orEmpty()
                        rows[row][col] = when (type) {
                            "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw
                        }
                    }
                }
            }
            parser.next()
        }
        return rows
    }

    private fun parser(bytes: ByteArray): XmlPullParser =
        XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }

    private fun refToIndex(ref: String): Pair<Int, Int> {
        val letters = ref.takeWhile { it.isLetter() }
        val digits = ref.dropWhile { it.isLetter() }
        var col = 0
        for (c in letters.uppercase()) col = col * 26 + (c - 'A' + 1)
        return (digits.toIntOrNull()?.minus(1) ?: 0) to (col - 1).coerceAtLeast(0)
    }
}

private fun String?.clean(): String = this?.trim().orEmpty()

private fun String?.num(): Double {
    val raw = this.clean()
    if (raw.isBlank()) return 0.0
    raw.toDoubleOrNull()?.let { return it }

    var s = raw.replace("₺", "").replace("TL", "", true).replace(" ", "")
    if (s.contains(',') && s.contains('.')) {
        s = if (s.lastIndexOf(',') > s.lastIndexOf('.')) {
            s.replace(".", "").replace(',', '.')
        } else {
            s.replace(",", "")
        }
    } else if (s.contains(',')) {
        s = s.replace(',', '.')
    }
    return s.toDoubleOrNull() ?: 0.0
}
