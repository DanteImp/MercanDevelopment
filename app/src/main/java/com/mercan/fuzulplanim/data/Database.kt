package com.mercan.fuzulplanim.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "fixed_expenses")
data class FixedExpenseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val amount: Double,
    val baselineAmount: Double = 0.0,
    val paymentSource: String = "",
    val budgetGroup: String = "bills",
    val frequency: String = "MONTHLY",
    val startMonth: String = "",
    val endMonth: String = "",
    val dayOfMonth: Int = 1,
    val inflationLinked: Boolean = false,
    val annualInflationPct: Double = 0.0,
    val active: Boolean = true,
    val imported: Boolean = false
)

@Entity(tableName = "installments")
data class InstallmentEntity(
    @PrimaryKey val id: String,
    val bank: String,
    val merchant: String,
    val category: String,
    val totalAmount: Double,
    val monthlyAmount: Double,
    val totalInstallments: Int,
    val paidInstallments: Int,
    val remainingDebt: Double,
    val nextMonth: String,
    val endMonth: String,
    val active: Boolean = true,
    val imported: Boolean = false
)

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val id: String,
    val type: String,
    val institution: String,
    val name: String,
    val principal: Double,
    val currentValue: Double,
    val annualRatePct: Double = 0.0,
    val withholdingPct: Double = 0.0,
    val startDate: String = "",
    val maturityDate: String = "",
    val note: String = ""
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val date: String,
    val source: String,
    val description: String,
    val amount: Double,
    val category: String,
    val subCategory: String = "",
    val includedInBudget: Boolean = true,
    val transactionType: String = "EXPENSE",
    val sourceFile: String = "",
    val note: String = ""
)

@Entity(tableName = "monthly_plan")
data class MonthlyPlanEntity(
    @PrimaryKey val month: String,
    val meteIncome: Double,
    val esmaIncome: Double,
    val otherIncome: Double,
    val orgFee: Double,
    val billsSnapshot: Double,
    val living: Double,
    val cardsSnapshot: Double,
    val educationSnapshot: Double,
    val transport: Double,
    val otherSnapshot: Double,
    val fuzulPayment: Double,
    val fuzulPaid: Boolean = false
)

@Entity(tableName = "fuzul_contract")
data class FuzulContractEntity(
    @PrimaryKey val id: Int = 1,
    val fundingType: String,
    val amount: Double,
    val downPayment: Double,
    val organizationFee: Double,
    val contractDate: String,
    val allocationPeriod: Int,
    val allocationDate: String,
    val installmentCount: Int,
    val lastPaymentDate: String,
    val openingCash: Double
)

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: String,
    val bank: String,
    val name: String,
    val last4: String = "",
    val statementBalance: Double = 0.0,
    val availableLimit: Double = 0.0,
    val dueDate: String = ""
)

@Entity(tableName = "import_batches")
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val fileType: String,
    val importedAt: String,
    val detectedType: String,
    val rowsFound: Int,
    val rowsImported: Int,
    val status: String,
    val note: String = ""
)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM fixed_expenses ORDER BY active DESC, category, name") fun observeFixedExpenses(): Flow<List<FixedExpenseEntity>>
    @Query("SELECT * FROM installments ORDER BY active DESC, bank, merchant") fun observeInstallments(): Flow<List<InstallmentEntity>>
    @Query("SELECT * FROM assets ORDER BY type, institution, name") fun observeAssets(): Flow<List<AssetEntity>>
    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT 1000") fun observeTransactions(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM monthly_plan ORDER BY month") fun observeMonthlyPlan(): Flow<List<MonthlyPlanEntity>>
    @Query("SELECT * FROM fuzul_contract WHERE id = 1 LIMIT 1") fun observeContract(): Flow<FuzulContractEntity?>
    @Query("SELECT * FROM cards ORDER BY bank, name") fun observeCards(): Flow<List<CardEntity>>
    @Query("SELECT * FROM import_batches ORDER BY importedAt DESC LIMIT 100") fun observeImports(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM fixed_expenses") suspend fun getFixedExpenses(): List<FixedExpenseEntity>
    @Query("SELECT * FROM installments") suspend fun getInstallments(): List<InstallmentEntity>
    @Query("SELECT * FROM assets") suspend fun getAssets(): List<AssetEntity>
    @Query("SELECT * FROM transactions") suspend fun getTransactions(): List<TransactionEntity>
    @Query("SELECT * FROM monthly_plan ORDER BY month") suspend fun getMonthlyPlan(): List<MonthlyPlanEntity>
    @Query("SELECT * FROM fuzul_contract WHERE id = 1 LIMIT 1") suspend fun getContract(): FuzulContractEntity?
    @Query("SELECT * FROM cards") suspend fun getCards(): List<CardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertFixedExpense(item: FixedExpenseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertFixedExpenses(items: List<FixedExpenseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertInstallment(item: InstallmentEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertInstallments(items: List<InstallmentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAsset(item: AssetEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAssets(items: List<AssetEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTransactions(items: List<TransactionEntity>): List<Long>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMonthlyPlans(items: List<MonthlyPlanEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMonthlyPlan(item: MonthlyPlanEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertContract(item: FuzulContractEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCard(item: CardEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCards(items: List<CardEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertImport(item: ImportBatchEntity)

    @Delete suspend fun deleteFixedExpense(item: FixedExpenseEntity)
    @Delete suspend fun deleteInstallment(item: InstallmentEntity)
    @Delete suspend fun deleteAsset(item: AssetEntity)
    @Delete suspend fun deleteCard(item: CardEntity)
    @Delete suspend fun deleteTransaction(item: TransactionEntity)

    @Query("DELETE FROM fixed_expenses") suspend fun clearFixedExpenses()
    @Query("DELETE FROM installments") suspend fun clearInstallments()
    @Query("DELETE FROM monthly_plan") suspend fun clearMonthlyPlans()
    @Query("DELETE FROM fuzul_contract") suspend fun clearContract()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM assets") suspend fun clearAssets()
    @Query("DELETE FROM cards") suspend fun clearCards()
    @Query("DELETE FROM import_batches") suspend fun clearImports()
}

@Database(entities=[FixedExpenseEntity::class,InstallmentEntity::class,AssetEntity::class,TransactionEntity::class,MonthlyPlanEntity::class,FuzulContractEntity::class,CardEntity::class,ImportBatchEntity::class],version=1,exportSchema=false)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao
    companion object {
        @Volatile private var INSTANCE: FinanceDatabase? = null
        fun get(context: Context): FinanceDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, FinanceDatabase::class.java, "fuzul_planim.db").build().also { INSTANCE = it }
        }
    }
}
