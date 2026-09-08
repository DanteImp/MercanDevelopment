package com.mercan.fuzulplanim

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.fuzulplanim.data.*
import com.mercan.fuzulplanim.importer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = FinanceRepository(app)
    val fixed = repo.fixedExpenses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val installments = repo.installments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val assets = repo.assets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions = repo.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val months = repo.monthlyPlan.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val contract = repo.contract.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val cards = repo.cards.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val imports = repo.imports.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var message by mutableStateOf<String?>(null); private set
    var pendingStatement by mutableStateOf<StatementPreview?>(null); private set
    var busy by mutableStateOf(false); private set
    fun consumeMessage(): String? = message.also { message = null }

    fun importBudget(uri: Uri, onDone: () -> Unit = {}) {
        busy = true
        viewModelScope.launch {
            runCatching {
                val result = withContext(Dispatchers.IO) { FileImportService.importBudgetWorkbook(getApplication(), uri) }
                repo.replaceBudgetFromWorkbook(result.fixedExpenses,result.installments,result.months,result.contract,result.transactions,result.cards,FileImportService.fileName(getApplication(),uri))
                result.summary
            }.onSuccess { message="Excel başarıyla aktarıldı: $it" }.onFailure { message="Excel aktarımı başarısız: ${it.message ?: it.javaClass.simpleName}" }
            busy=false; onDone()
        }
    }
    fun prepareStatement(uri:Uri,onDone:()->Unit={}){busy=true;viewModelScope.launch{runCatching{withContext(Dispatchers.IO){FileImportService.parseStatement(getApplication(),uri)}}.onSuccess{pendingStatement=it;message=if(it.rows.isEmpty())"İşlem bulunamadı. Dosya formatını kontrol et." else "${it.rows.size} işlem bulundu; önizlemeyi kontrol et."}.onFailure{message="Dosya okunamadı: ${it.message ?: it.javaClass.simpleName}"};busy=false;onDone()}}
    fun clearPendingStatement(){pendingStatement=null}
    fun commitPendingStatement(source:String="",onDone:()->Unit={}){val p=pendingStatement?:return;busy=true;viewModelScope.launch{runCatching{repo.importTransactions(FileImportService.toTransactions(p,source),p.fileName,p.fileType,p.note)}.onSuccess{message="$it yeni işlem kaydedildi.";pendingStatement=null}.onFailure{message="İçe aktarma başarısız: ${it.message}"};busy=false;onDone()}}

    fun saveFixed(item:FixedExpenseEntity)=viewModelScope.launch{repo.saveFixedExpense(item)}
    fun deleteFixed(item:FixedExpenseEntity)=viewModelScope.launch{repo.deleteFixedExpense(item)}
    fun saveInstallment(item:InstallmentEntity)=viewModelScope.launch{repo.saveInstallment(item)}
    fun deleteInstallment(item:InstallmentEntity)=viewModelScope.launch{repo.deleteInstallment(item)}
    fun saveAsset(item:AssetEntity)=viewModelScope.launch{repo.saveAsset(item)}
    fun deleteAsset(item:AssetEntity)=viewModelScope.launch{repo.deleteAsset(item)}
    fun saveCard(item:CardEntity)=viewModelScope.launch{repo.saveCard(item)}
    fun deleteCard(item:CardEntity)=viewModelScope.launch{repo.deleteCard(item)}
    fun saveMonth(item:MonthlyPlanEntity)=viewModelScope.launch{repo.saveMonthlyPlan(item)}
    fun saveContract(item:FuzulContractEntity)=viewModelScope.launch{repo.saveContract(item)}
    suspend fun exportBackup():String=withContext(Dispatchers.IO){repo.exportBackupJson()}
    fun restoreBackup(raw:String,onDone:()->Unit={}){busy=true;viewModelScope.launch{runCatching{withContext(Dispatchers.IO){repo.restoreBackupJson(raw)}}.onSuccess{message="Yedek başarıyla geri yüklendi."}.onFailure{message="Yedek okunamadı: ${it.message}"};busy=false;onDone()}}

    fun newFixed(name:String,category:String,amount:Double,source:String,group:String,startMonth:String,day:Int,inflation:Boolean,annualPct:Double)=FixedExpenseEntity("manual-fixed-${UUID.randomUUID()}",name,category,amount,0.0,source,group,"MONTHLY",startMonth,"",day.coerceIn(1,28),inflation,annualPct,true,false)
    fun newInstallment(bank:String,merchant:String,category:String,total:Double,monthly:Double,totalN:Int,paid:Int,next:String,end:String)=InstallmentEntity("manual-inst-${UUID.randomUUID()}",bank,merchant,category,total,monthly,totalN,paid,(total-monthly*paid).coerceAtLeast(0.0),next,end,true,false)
    fun newAsset(type:String,institution:String,name:String,principal:Double,current:Double,rate:Double,tax:Double,start:String,end:String,note:String)=AssetEntity("manual-asset-${UUID.randomUUID()}",type,institution,name,principal,current,rate,tax,start,end,note)
    fun newCard(bank:String,name:String,last4:String,balance:Double,limit:Double,due:String)=CardEntity("manual-card-${UUID.randomUUID()}",bank,name,last4,balance,limit,due)
}
